package re.imc.geysermodelengine.listener;

import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import re.imc.geysermodelengine.GeyserModelEngine;
import re.imc.geysermodelengine.managers.model.ModelManager;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The joined Bedrock players: taken on a second after they join, let go when they quit. */
class ModelListenerTest {

    private final GeyserModelEngine plugin = mock(GeyserModelEngine.class);
    private final GlobalRegionScheduler scheduler = mock(GlobalRegionScheduler.class);
    private final Set<UUID> joined = ConcurrentHashMap.newKeySet();
    private final Player player = mock(Player.class);
    private final ModelListener listener = new ModelListener(plugin);

    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getGlobalRegionScheduler).thenReturn(scheduler);
        ModelManager modelManager = mock(ModelManager.class);
        when(plugin.getModelManager()).thenReturn(modelManager);
        when(modelManager.getPlayerJoinedCache()).thenReturn(joined);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getClientBrandName()).thenReturn("Geyser");
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    @Test
    void aBedrockPlayerIsTakenOnASecondAfterJoining() {
        when(player.isOnline()).thenReturn(true);

        join().accept(mock(ScheduledTask.class));

        assertTrue(joined.contains(player.getUniqueId()));
    }

    @Test
    void aPlayerWhoLeftWithinThatSecondIsNotTakenOn() {
        Consumer<ScheduledTask> takeOn = join();
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);
        listener.onPlayerQuit(quit);

        takeOn.accept(mock(ScheduledTask.class));

        assertFalse(joined.contains(player.getUniqueId()), "nothing would take them out again");
    }

    /** The player joins; returns what runs a second later. */
    @SuppressWarnings("unchecked")
    private Consumer<ScheduledTask> join() {
        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);
        listener.onPlayerJoin(event);
        ArgumentCaptor<Consumer<ScheduledTask>> delayed = ArgumentCaptor.forClass(Consumer.class);
        verify(scheduler).runDelayed(eq(plugin), delayed.capture(), eq(20L));
        return delayed.getValue();
    }
}
