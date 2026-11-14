package re.imc.geysermodelengine.managers.model.taskshandler;

import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import kr.toxicity.model.api.animation.AnimationIterator;
import kr.toxicity.model.api.data.blueprint.BlueprintAnimation;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.entity.BaseEntity;
import kr.toxicity.model.api.tracker.EntityTracker;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import re.imc.geysermodelengine.BetterModelStub;
import re.imc.geysermodelengine.GeyserModelEngine;
import re.imc.geysermodelengine.events.GeyserModelEngineEntityDeathEvent;
import re.imc.geysermodelengine.managers.ConfigManager;
import re.imc.geysermodelengine.managers.model.EntityTaskManager;
import re.imc.geysermodelengine.managers.model.ModelManager;
import re.imc.geysermodelengine.managers.model.entity.BetterModelEntityData;
import re.imc.geysermodelengine.managers.model.entity.EntityData;
import re.imc.geysermodelengine.managers.model.model.Model;
import re.imc.geysermodelengine.managers.model.propertyhandler.PropertyHandler;
import re.imc.geysermodelengine.packet.entity.PacketEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The Bedrock copy of a BetterModel tracker lives as long as the tracker, through BetterModel's death animation
 * (mcfoxcraft/FoxKingdoms#1553).
 */
class BetterModelTaskHandlerTest {

    static {
        BetterModelStub.install();
    }

    private static final int MOB_ID = 42;

    private final GeyserModelEngine plugin = mock(GeyserModelEngine.class);
    private final EntityTaskManager taskManager = mock(EntityTaskManager.class);
    private final ScheduledFuture<?> future = mock(ScheduledFuture.class);
    private final PluginManager pluginManager = mock(PluginManager.class);
    private final PropertyHandler properties = mock(PropertyHandler.class);
    private final ScheduledExecutorService pool = mock(ScheduledExecutorService.class);
    /** What the pool was given to run later: the spawn that waits for the custom entity sync, then its properties. */
    private final List<Runnable> scheduled = new ArrayList<>();

    private final ConcurrentHashMap<Integer, Map<Model, EntityData>> entities = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Model> models = new ConcurrentHashMap<>();

    private final BaseEntity mob = mock(BaseEntity.class);
    private final EntityTracker tracker = mock(EntityTracker.class);
    private final PacketEntity copy = mock(PacketEntity.class);
    private final Set<Player> viewers = ConcurrentHashMap.newKeySet();
    private final BetterModelEntityData data = mock(BetterModelEntityData.class);
    private final Model model = mock(Model.class);

    private MockedStatic<Bukkit> bukkit;
    private BetterModelTaskHandler handler;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
        bukkit.when(Bukkit::getGlobalRegionScheduler).thenReturn(mock(GlobalRegionScheduler.class));

        ModelManager modelManager = mock(ModelManager.class);
        when(plugin.getEntityTaskManager()).thenReturn(taskManager);
        when(taskManager.getPropertyHandler()).thenReturn(properties);
        when(plugin.getModelManager()).thenReturn(modelManager);
        when(modelManager.getEntitiesCache()).thenReturn(entities);
        when(modelManager.getModelEntitiesCache()).thenReturn(models);
        when(plugin.getSchedulerPool()).thenReturn(pool);
        doReturn(future).when(pool).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
        when(pool.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenAnswer(call -> {
            scheduled.add(call.getArgument(0));
            return null;
        });
        ConfigManager configManager = mock(ConfigManager.class);
        FileConfiguration config = mock(FileConfiguration.class);
        when(plugin.getConfigManager()).thenReturn(configManager);
        when(configManager.getConfig()).thenReturn(config);
        when(config.getInt(anyString(), anyInt())).thenAnswer(call -> call.getArgument(1));
        when(config.getString(anyString())).thenReturn("modelengine");

        // The Goblin Giant's death on the kingdoms test server: 7.2917 s (records, which Mockito cannot mock).
        BlueprintAnimation death = new BlueprintAnimation("death", AnimationIterator.Type.PLAY_ONCE, 7.2917F, false, Map.of(), null, null);
        ModelRenderer renderer = new ModelRenderer("goblin_giant", ModelRenderer.Type.GENERAL, new LinkedHashMap<>(), Map.of("death", death));
        when(mob.id()).thenReturn(MOB_ID);
        when(tracker.renderer()).thenReturn(renderer);
        when(tracker.name()).thenReturn("goblin_giant");
        // Like the real copy, it is dead once removed.
        AtomicBoolean copyRemoved = new AtomicBoolean();
        doAnswer(call -> {
            copyRemoved.set(true);
            return null;
        }).when(copy).remove();
        when(copy.isDead()).thenAnswer(call -> copyRemoved.get());

        when(data.getEntity()).thenReturn(copy);
        when(data.getViewers()).thenReturn(viewers);
        when(data.getEntitySource()).thenReturn(mob);
        when(data.getEntityTracker()).thenReturn(tracker);
        when(data.getModelInstance()).thenReturn(tracker);
        when(model.getEntityData()).thenReturn(data);

        handler = new BetterModelTaskHandler(plugin, data);

        Map<Model, EntityData> mobModels = new ConcurrentHashMap<>();
        mobModels.put(model, data);
        entities.put(MOB_ID, mobModels);
        models.put(MOB_ID, model);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    @Test
    void aLivingMobsCopyIsDrawnAsAlways() {
        handler.runAsync();

        verify(taskManager).checkViewers(data, viewers);
        verify(data).teleportToModel();
        verify(copy, never()).remove();
    }

    @Test
    void aKilledMobsCopyStaysWhileBetterModelPlaysItsDeath() {
        when(mob.dead()).thenReturn(true);
        when(tracker.forRemoval()).thenReturn(true);

        handler.runAsync();
        handler.runAsync();

        verify(copy, never()).remove();
        verify(future, never()).cancel(anyBoolean());
        assertSame(data, entities.get(MOB_ID).get(model), "the update task keeps sending its animation");
        verify(data, times(2)).teleportToModel();
        verify(pluginManager, never()).callEvent(any());
    }

    @Test
    void aMobThatIsDeadBeforeBetterModelMarksItsTrackerKeepsItsCopyToo() {
        // The death event's handlers before BetterModel's: the mob is dead, the tracker not marked yet.
        when(mob.dead()).thenReturn(true);

        handler.runAsync();

        verify(copy, never()).remove();
    }

    @Test
    void theCopyGoesWhenTheDeathAnimationEndsAndClosesTheTracker() {
        when(mob.dead()).thenReturn(true);
        when(tracker.forRemoval()).thenReturn(true);
        handler.runAsync();

        when(tracker.isClosed()).thenReturn(true);
        handler.runAsync();

        verify(copy).remove();
        verify(future).cancel(true);
        assertFalse(entities.containsKey(MOB_ID));
        assertFalse(models.containsKey(MOB_ID));
        verify(pluginManager).callEvent(isA(GeyserModelEngineEntityDeathEvent.class));
        assertTrue(handler.isRemoved());
    }

    @Test
    void aTrackerClosedOnALivingMobTakesItsCopyAway() {
        when(tracker.isClosed()).thenReturn(true);

        handler.runAsync();

        verify(copy).remove();
        verify(future).cancel(true);
        assertFalse(entities.containsKey(MOB_ID));
    }

    @Test
    void onlyThisTrackersEntryLeavesTheCaches() {
        // Another model on the same mob, or the tracker that replaced this one (a reload), keeps its entry.
        BetterModelEntityData other = mock(BetterModelEntityData.class);
        Model otherModel = mock(Model.class);
        when(otherModel.getEntityData()).thenReturn(other);
        entities.get(MOB_ID).put(otherModel, other);
        models.put(MOB_ID, otherModel);
        when(tracker.isClosed()).thenReturn(true);

        handler.runAsync();

        assertEquals(Map.of(otherModel, other), Map.copyOf(entities.get(MOB_ID)));
        assertSame(otherModel, models.get(MOB_ID));
    }

    @Test
    void aDyingCopyIsShownToNobodyNewAndLeavesThoseWhoCanNoLongerSeeIt() {
        Player watching = mock(Player.class);
        Player walkedAway = mock(Player.class);
        when(walkedAway.isOnline()).thenReturn(true);
        viewers.add(watching);
        viewers.add(walkedAway);
        when(taskManager.canSee(watching, copy, tracker)).thenReturn(true);
        when(mob.dead()).thenReturn(true);
        when(tracker.forRemoval()).thenReturn(true);

        handler.runAsync();

        verify(taskManager, never()).checkViewers(any(), anySet());
        assertEquals(Set.of(watching), Set.copyOf(viewers));
        verify(copy).sendEntityDestroyPacket(List.of(walkedAway));
    }

    @Test
    void aViewerWhoLeftTheServerDuringTheDeathIsDroppedWithoutAPacket() {
        Player quit = mock(Player.class);
        viewers.add(quit);
        when(mob.dead()).thenReturn(true);
        when(tracker.forRemoval()).thenReturn(true);

        handler.runAsync();

        assertTrue(viewers.isEmpty());
        verify(copy, never()).sendEntityDestroyPacket(any());
    }

    @Test
    void aRemovedCopyIsLeftAlone() {
        when(copy.isDead()).thenReturn(true);

        handler.runAsync();

        verify(taskManager, never()).checkViewers(any(), anySet());
        verify(data, never()).teleportToModel();
    }

    @Test
    void aLivingCopyLetsGoOfAViewerWithADestroyPacket() {
        // The 40-run sweep: a viewer the copy no longer reaches (here: checkViewers did not see it go) is told so.
        Player gone = mock(Player.class);
        when(gone.isOnline()).thenReturn(true);
        viewers.add(gone);

        handler.runAsync();

        assertTrue(viewers.isEmpty());
        verify(copy).sendEntityDestroyPacket(List.of(gone));
    }

    @Test
    void aSpawnThatWaitedForTheSyncReachesAPlayerWhoStillSeesTheCopy() {
        Player player = joiningViewer();

        runScheduled();

        verify(copy).sendSpawnPacket(List.of(player));
        verify(properties).sendHitBox(data, player);
    }

    @Test
    void aSpawnThatWaitedIsNotSentOnceTheTrackerClosed() {
        // The copy went while the spawn waited: nothing would destroy it again.
        Player player = joiningViewer();
        when(tracker.isClosed()).thenReturn(true);
        handler.runAsync();

        runScheduled();

        verify(copy).remove();
        verify(copy, never()).sendSpawnPacket(any());
        verify(properties, never()).sendHitBox(any(), any());
    }

    @Test
    void aSpawnThatWaitedIsNotSentToAViewerTheDyingCopyLetGo() {
        // The mob died and the player went out of sight while the spawn waited: a dying copy never takes them back.
        Player player = joiningViewer();
        when(taskManager.canSee(player, copy, tracker)).thenReturn(false);
        when(mob.dead()).thenReturn(true);
        handler.runAsync();
        verify(copy).sendEntityDestroyPacket(List.of(player));

        runScheduled();

        assertFalse(viewers.contains(player));
        verify(copy, never()).sendSpawnPacket(any());
    }

    @Test
    void theSpawnsPropertiesAreNotSentToAViewerLetGoInBetween() {
        Player player = joiningViewer();
        scheduled.get(scheduled.size() - 1).run(); // the spawn goes out and schedules its properties
        verify(copy).sendSpawnPacket(List.of(player));
        viewers.remove(player);

        scheduled.get(scheduled.size() - 1).run();

        verifyNoInteractions(properties);
    }

    @Test
    void aSpawnWaitsWhileTheTaskChangesTheViewers() throws InterruptedException {
        // The spawn runs on another pool thread: it must not go out between the task's check and its destroy packet.
        Player player = joiningViewer();
        Runnable spawn = scheduled.get(scheduled.size() - 1);
        AtomicReference<Thread> spawner = new AtomicReference<>();
        doAnswer(call -> {
            // The sync delay elapses while the task is in checkViewers.
            spawner.set(spawnOnAnotherThread(spawn));
            // What checkViewers does for a player who went out of range.
            copy.sendEntityDestroyPacket(List.of(player));
            viewers.remove(player);
            return null;
        }).when(taskManager).checkViewers(data, viewers);

        handler.runAsync();
        spawner.get().join(2000);

        verify(copy, never()).sendSpawnPacket(any());
    }

    @Test
    void aSpawnWaitsWhileTheCopyGoes() throws InterruptedException {
        // The tracker closed: the spawn must not go out between the copy's check and its destroy packet.
        joiningViewer();
        Runnable spawn = scheduled.get(scheduled.size() - 1);
        AtomicReference<Thread> spawner = new AtomicReference<>();
        AtomicBoolean gone = new AtomicBoolean();
        when(copy.isDead()).thenAnswer(call -> gone.get());
        doAnswer(call -> {
            // The sync delay elapses while the copy goes.
            spawner.set(spawnOnAnotherThread(spawn));
            // What PacketEntity#remove does: marks itself removed, then sends the viewers the destroy packet.
            gone.set(true);
            copy.sendEntityDestroyPacket(viewers);
            return null;
        }).when(copy).remove();
        when(tracker.isClosed()).thenReturn(true);

        handler.runAsync();
        spawner.get().join(2000);

        verify(copy).remove();
        verify(copy, never()).sendSpawnPacket(any());
    }

    @Test
    void aSpawnWaitsWhileTheDyingCopyLetsAViewerGo() throws InterruptedException {
        // The mob died and the player went out of sight: the spawn must not go out between the destroy packet the
        // dying copy sends them and their leaving the viewers.
        Player player = joiningViewer();
        Runnable spawn = scheduled.get(scheduled.size() - 1);
        AtomicReference<Thread> spawner = new AtomicReference<>();
        when(taskManager.canSee(player, copy, tracker)).thenReturn(false);
        when(mob.dead()).thenReturn(true);
        doAnswer(call -> {
            // The sync delay elapses while the destroy packet goes out.
            spawner.set(spawnOnAnotherThread(spawn));
            return null;
        }).when(copy).sendEntityDestroyPacket(List.of(player));

        handler.runAsync();
        spawner.get().join(2000);

        verify(copy).sendEntityDestroyPacket(List.of(player));
        verify(copy, never()).sendSpawnPacket(any());
    }

    /**
     * Runs the waiting spawn on another thread, as the pool does when its delay elapses, and returns once that
     * thread waits for the copy's lock (or has run, when nothing holds it).
     */
    private static Thread spawnOnAnotherThread(Runnable spawn) {
        Thread thread = new Thread(spawn);
        thread.start();
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (thread.getState() != Thread.State.BLOCKED && thread.isAlive() && System.nanoTime() < until) {
            Thread.onSpinWait();
        }
        return thread;
    }

    /** A Bedrock player comes into range: checkViewers adds them and asks for the copy's spawn. */
    private Player joiningViewer() {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(taskManager.canSee(player, copy, tracker)).thenReturn(true);
        viewers.add(player);
        handler.sendEntityData(data, player, 1);
        return player;
    }

    /** The sync delay (and the properties delay after it) elapses. */
    private void runScheduled() {
        for (int i = 0; i < scheduled.size(); i++) {
            scheduled.get(i).run();
        }
    }
}
