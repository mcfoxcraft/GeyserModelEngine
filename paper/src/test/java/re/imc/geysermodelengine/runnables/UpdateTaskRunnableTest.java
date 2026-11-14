package re.imc.geysermodelengine.runnables;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import re.imc.geysermodelengine.GeyserModelEngine;
import re.imc.geysermodelengine.managers.model.EntityTaskManager;
import re.imc.geysermodelengine.managers.model.ModelManager;
import re.imc.geysermodelengine.managers.model.entity.EntityData;
import re.imc.geysermodelengine.managers.model.model.Model;
import re.imc.geysermodelengine.managers.model.propertyhandler.PropertyHandler;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The update task carries every copy's animations to Bedrock (a death animation included); a periodic task that
 * throws is never run again (review GME2 round 3).
 */
class UpdateTaskRunnableTest {

    private final GeyserModelEngine plugin = mock(GeyserModelEngine.class);
    private final PropertyHandler properties = mock(PropertyHandler.class);
    private final ConcurrentHashMap<Integer, Map<Model, EntityData>> entities = new ConcurrentHashMap<>();
    private final List<LogRecord> logged = new ArrayList<>();
    private final AtomicLong now = new AtomicLong();

    private final EntityData failing = copyWithAViewer();
    private final EntityData working = copyWithAViewer();

    @BeforeEach
    void setUp() {
        ModelManager modelManager = mock(ModelManager.class);
        EntityTaskManager taskManager = mock(EntityTaskManager.class);
        when(plugin.getModelManager()).thenReturn(modelManager);
        when(modelManager.getEntitiesCache()).thenReturn(entities);
        when(plugin.getEntityTaskManager()).thenReturn(taskManager);
        when(taskManager.getPropertyHandler()).thenReturn(properties);

        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                synchronized (logged) {
                    logged.add(record);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        when(plugin.getLogger()).thenReturn(logger);

        // A bone whose running animation ended while it was read, as in BetterModelPropertyHandler before the fix.
        doAnswer(call -> {
            throw new NullPointerException("runningAnimation() is null");
        }).when(properties).updateEntityProperties(eq(failing), any(), anyBoolean());

        Map<Model, EntityData> mobModels = new LinkedHashMap<>();
        mobModels.put(mock(Model.class), failing);
        mobModels.put(mock(Model.class), working);
        entities.put(42, mobModels);
    }

    @Test
    void theTaskKeepsRunningAfterACopyFails() throws Exception {
        CountDownLatch runs = new CountDownLatch(10);
        doAnswer(call -> {
            runs.countDown();
            return null;
        }).when(properties).updateEntityProperties(eq(working), any(), anyBoolean());
        ScheduledExecutorService pool = Executors.newScheduledThreadPool(1);
        try {
            ScheduledFuture<?> update = pool.scheduleAtFixedRate(new UpdateTaskRunnable(plugin), 0, 5, TimeUnit.MILLISECONDS);

            assertTrue(runs.await(10, TimeUnit.SECONDS), "the task ran again after the failure");
            assertFalse(update.isDone());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aCopyThatFailsDoesNotHoldUpTheOthers() {
        // The failing copy comes first in the walk.
        Set<Player> viewers = working.getViewers();
        new UpdateTaskRunnable(plugin, now::get).run();

        verify(properties).updateEntityProperties(eq(working), eq(viewers), eq(false));
    }

    @Test
    void aFailureIsLoggedWithItsCauseAtMostOnceAMinute() {
        UpdateTaskRunnable task = new UpdateTaskRunnable(plugin, now::get);

        task.run();
        now.addAndGet(TimeUnit.SECONDS.toNanos(30));
        task.run();
        task.run();

        assertEquals(1, logged.size());
        assertEquals(Level.WARNING, logged.get(0).getLevel());
        assertInstanceOf(NullPointerException.class, logged.get(0).getThrown());

        now.addAndGet(UpdateTaskRunnable.REPORT_EVERY_NANOS);
        task.run();

        assertEquals(2, logged.size());
        assertTrue(logged.get(1).getMessage().contains("2 more failures"), logged.get(1).getMessage());
        verify(properties, times(4)).updateEntityProperties(eq(working), any(), anyBoolean());
    }

    @Test
    void aFailureWhileWalkingTheModelsDoesNotLeaveTheTask() {
        // The ModelEngine path keeps a plain HashMap per mob, which the main thread changes while this task walks it.
        Map<Model, EntityData> changing = mock(Map.class);
        when(changing.values()).thenThrow(new ConcurrentModificationException());
        entities.put(7, changing);
        UpdateTaskRunnable task = new UpdateTaskRunnable(plugin, now::get);

        task.run();
        task.run();

        assertEquals(1, logged.size());
    }

    private static EntityData copyWithAViewer() {
        EntityData data = mock(EntityData.class);
        when(data.getViewers()).thenReturn(Set.of(mock(Player.class)));
        return data;
    }
}
