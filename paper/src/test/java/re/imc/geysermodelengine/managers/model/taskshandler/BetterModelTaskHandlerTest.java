package re.imc.geysermodelengine.managers.model.taskshandler;

import kr.toxicity.model.api.animation.AnimationIterator;
import kr.toxicity.model.api.data.blueprint.BlueprintAnimation;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.entity.BaseEntity;
import kr.toxicity.model.api.tracker.EntityTracker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import re.imc.geysermodelengine.BetterModelStub;
import re.imc.geysermodelengine.GeyserModelEngine;
import re.imc.geysermodelengine.events.GeyserModelEngineEntityDeathEvent;
import re.imc.geysermodelengine.managers.model.EntityTaskManager;
import re.imc.geysermodelengine.managers.model.ModelManager;
import re.imc.geysermodelengine.managers.model.entity.BetterModelEntityData;
import re.imc.geysermodelengine.managers.model.entity.EntityData;
import re.imc.geysermodelengine.managers.model.model.Model;
import re.imc.geysermodelengine.managers.model.propertyhandler.PropertyHandler;
import re.imc.geysermodelengine.packet.entity.PacketEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

        ModelManager modelManager = mock(ModelManager.class);
        ScheduledExecutorService pool = mock(ScheduledExecutorService.class);
        when(plugin.getEntityTaskManager()).thenReturn(taskManager);
        when(taskManager.getPropertyHandler()).thenReturn(mock(PropertyHandler.class));
        when(plugin.getModelManager()).thenReturn(modelManager);
        when(modelManager.getEntitiesCache()).thenReturn(entities);
        when(modelManager.getModelEntitiesCache()).thenReturn(models);
        when(plugin.getSchedulerPool()).thenReturn(pool);
        doReturn(future).when(pool).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());

        // The Goblin Giant's death on the kingdoms test server: 7.2917 s (records, which Mockito cannot mock).
        BlueprintAnimation death = new BlueprintAnimation("death", AnimationIterator.Type.PLAY_ONCE, 7.2917F, false, Map.of(), null, null);
        ModelRenderer renderer = new ModelRenderer("goblin_giant", ModelRenderer.Type.GENERAL, new LinkedHashMap<>(), Map.of("death", death));
        when(mob.id()).thenReturn(MOB_ID);
        when(tracker.renderer()).thenReturn(renderer);

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
}
