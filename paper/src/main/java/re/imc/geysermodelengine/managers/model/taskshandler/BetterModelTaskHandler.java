package re.imc.geysermodelengine.managers.model.taskshandler;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import kr.toxicity.model.api.data.blueprint.BlueprintAnimation;
import kr.toxicity.model.api.entity.BaseEntity;
import kr.toxicity.model.api.tracker.EntityTracker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import re.imc.geysermodelengine.GeyserModelEngine;
import re.imc.geysermodelengine.events.GeyserModelEngineEntityDeathEvent;
import re.imc.geysermodelengine.managers.model.entity.BetterModelEntityData;
import re.imc.geysermodelengine.managers.model.entity.EntityData;
import re.imc.geysermodelengine.packet.entity.PacketEntity;
import re.imc.geysermodelengine.util.CustomEntitySpawnSynchronizer;

import java.awt.*;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class BetterModelTaskHandler implements TaskHandler {

    /** The animation BetterModel plays on the trackers of a mob that dies ({@code TrackerExtraAnimation.DEATH}). */
    private static final String DEATH_ANIMATION = "death";

    private final GeyserModelEngine plugin;

    private final BetterModelEntityData entityData;

    private int tick = 0;
    private int syncTick = 0;

    private float lastScale = -1.0f;
    private Color lastColor = null;

    private boolean removed = false;
    private final TrackerLifetime lifetime = new TrackerLifetime();

    private final ConcurrentHashMap<String, Integer> lastIntSet = new ConcurrentHashMap<>();
    private final Cache<String, Boolean> lastPlayedAnim = CacheBuilder.newBuilder().expireAfterWrite(30, TimeUnit.MILLISECONDS).build();

    private ScheduledFuture scheduledFuture;

    public BetterModelTaskHandler(GeyserModelEngine plugin, BetterModelEntityData entityData) {
        this.plugin = plugin;
        this.entityData = entityData;

        plugin.getEntityTaskManager().sendHitBoxToAll(entityData);
        scheduledFuture = plugin.getSchedulerPool().scheduleAtFixedRate(() -> {
            try {
                runAsync();
            } catch (Throwable err) {
                err.printStackTrace();
            }
        }, 0, 20, TimeUnit.MILLISECONDS);
    }

    @Override
    public void runAsync() {
        PacketEntity entity = entityData.getEntity();
        if (entity.isDead()) return;

        Set<Player> viewers = entityData.getViewers();
        BaseEntity entitySource = entityData.getEntitySource();
        EntityTracker entityTracker = entityData.getEntityTracker();

        // The copy lives as long as the tracker, so it plays BetterModel's death animation, which closes the tracker
        // when it is over (see TrackerLifetime).
        TrackerLifetime.Stage stage = lifetime.update(entityTracker.isClosed(), entitySource.dead() || entityTracker.forRemoval(), () -> deathSeconds(entityTracker), System.nanoTime());
        if (stage == TrackerLifetime.Stage.GONE) {
            remove();
            return;
        }

        if (stage == TrackerLifetime.Stage.DYING) {
            dropViewersWhoCannotSee(viewers);
        } else {
            plugin.getEntityTaskManager().checkViewers(entityData, viewers);
        }

        entityData.teleportToModel();

        if (tick % 40 == 0) {
            viewers.removeIf(viewer -> !plugin.getEntityTaskManager().canSee(viewer, entityData.getEntity(), entityData.getModelInstance()));
        }

        tick++;
        if (tick > 400) {
            tick = 0;
            plugin.getEntityTaskManager().sendHitBoxToAll(entityData);
        }

        if (viewers.isEmpty()) return;

        plugin.getEntityTaskManager().getPropertyHandler().sendScale(entityData, viewers, lastScale, false);
        plugin.getEntityTaskManager().getPropertyHandler().sendColor(entityData, viewers, lastColor, false);
    }

    /**
     * While the mob dies its copy is shown to nobody new (BetterModel no longer draws it for players who come near
     * once the mob is gone): it stays for the players who see it, until they no longer can.
     */
    private void dropViewersWhoCannotSee(Set<Player> viewers) {
        PacketEntity entity = entityData.getEntity();
        viewers.removeIf(viewer -> {
            if (plugin.getEntityTaskManager().canSee(viewer, entity, entityData.getModelInstance())) return false;
            if (viewer.isOnline()) entity.sendEntityDestroyPacket(Collections.singletonList(viewer));
            return true;
        });
    }

    /**
     * Takes the copy away from the players it is drawn for and out of the caches, and stops this task. Only this
     * tracker's entry goes: a mob can carry other models, and a tracker that replaces this one (BetterModel closes a
     * tracker when a new one of the same model is made for the mob, on a reload for one) is already in.
     */
    private void remove() {
        removed = true;
        entityData.getEntity().remove();

        int id = entityData.getEntitySource().id();
        plugin.getModelManager().getEntitiesCache().computeIfPresent(id, (key, models) -> {
            models.values().remove(entityData);
            return models.isEmpty() ? null : models;
        });
        plugin.getModelManager().getModelEntitiesCache().computeIfPresent(id, (key, model) -> model.getEntityData() == entityData ? null : model);

        cancel();

        Bukkit.getPluginManager().callEvent(new GeyserModelEngineEntityDeathEvent(entityData));
    }

    private static double deathSeconds(EntityTracker tracker) {
        return tracker.renderer().animation(DEATH_ANIMATION).map(BlueprintAnimation::length).orElse(0F);
    }

    @Override
    public void sendEntityData(EntityData entityData, Player player, int delay) {
        BetterModelEntityData betterModelEntityData = (BetterModelEntityData) entityData;
        long propertiesSendDelay = plugin.getConfigManager().getConfig().getInt("models.properties-send-delay", 1);
        String customIdentifier = plugin.getConfigManager().getConfig().getString("models.namespace") + ":" + betterModelEntityData.getEntityTracker().name().toLowerCase();

        if (plugin.getConfigManager().getConfig().getBoolean("options.debug.send-data")) plugin.getLogger().info("Setting custom entity data for " + betterModelEntityData.getEntityTracker().name());

        CustomEntitySpawnSynchronizer.sendAndSpawn(plugin, player, entityData.getEntity().getEntityId(), customIdentifier, delay, () -> {
            entityData.getEntity().sendSpawnPacket(Collections.singletonList(player));

            plugin.getSchedulerPool().schedule(() -> {
                plugin.getEntityTaskManager().getPropertyHandler().sendHitBox(entityData, player);

                plugin.getEntityTaskManager().getPropertyHandler().sendScale(entityData, Collections.singleton(player), lastScale, true);
                plugin.getEntityTaskManager().getPropertyHandler().sendColor(entityData, Collections.singleton(player), lastColor, true);

                plugin.getEntityTaskManager().getPropertyHandler().updateEntityProperties(entityData, Collections.singleton(player), true);
            }, propertiesSendDelay, TimeUnit.MILLISECONDS);
        });
    }

    @Override
    public void cancel() {
        scheduledFuture.cancel(true);
    }

    public void setTick(int tick) {
        this.tick = tick;
    }

    public void setSyncTick(int syncTick) {
        this.syncTick = syncTick;
    }

    public void setRemoved(boolean removed) {
        this.removed = removed;
    }

    public void setLastScale(float lastScale) {
        this.lastScale = lastScale;
    }

    public int getTick() {
        return tick;
    }

    public int getSyncTick() {
        return syncTick;
    }

    public void setLastColor(Color lastColor) {
        this.lastColor = lastColor;
    }

    public float getLastScale() {
        return lastScale;
    }

    public Color getLastColor() {
        return lastColor;
    }

    public boolean isRemoved() {
        return removed;
    }

    public ConcurrentHashMap<String, Integer> getLastIntSet() {
        return lastIntSet;
    }

    public Cache<String, Boolean> getLastPlayedAnim() {
        return lastPlayedAnim;
    }

    public ScheduledFuture getScheduledFuture() {
        return scheduledFuture;
    }
}
