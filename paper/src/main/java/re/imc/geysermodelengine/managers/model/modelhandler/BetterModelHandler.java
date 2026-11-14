package re.imc.geysermodelengine.managers.model.modelhandler;

import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.entity.BaseEntity;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.Tracker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import re.imc.geysermodelengine.GeyserModelEngine;
import re.imc.geysermodelengine.listener.BetterModelListener;
import re.imc.geysermodelengine.managers.model.propertyhandler.PropertyHandler;
import re.imc.geysermodelengine.managers.model.entity.BetterModelEntityData;
import re.imc.geysermodelengine.managers.model.entity.EntityData;
import re.imc.geysermodelengine.managers.model.model.BetterModelModel;
import re.imc.geysermodelengine.managers.model.model.Model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BetterModelHandler implements ModelHandler {

    private final GeyserModelEngine plugin;

    public BetterModelHandler(GeyserModelEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public void createModel(Object... objects) {
        EntityTracker entityTracker = (EntityTracker) objects[0];
        BaseEntity entitySource = entityTracker.sourceEntity();

        int entityID = entitySource.id();

        Map<Model, EntityData> current = plugin.getModelManager().getEntitiesCache().get(entityID);
        if (current != null) {
            for (EntityData data : current.values()) {
                if (data.getModelInstance() == entityTracker) return; // this tracker has its copy already
            }
        }

        // A copy of the same model on this mob does not stop this one: BetterModel keeps one tracker per model on a
        // mob and closes the one a new tracker replaces (right after this event when the model is made again, before
        // it on a reload), and a copy goes once its tracker is closed.
        PropertyHandler propertyHandler = plugin.getEntityTaskManager().getPropertyHandler();
        EntityData entityData = new BetterModelEntityData(plugin, entitySource, entityTracker);

        Model model = new BetterModelModel(entityTracker, this, entityData, propertyHandler);

        // Concurrent: the update task walks these maps while a copy that goes takes itself out of them.
        plugin.getModelManager().getEntitiesCache().compute(entityID, (id, models) -> {
            Map<Model, EntityData> map = models != null ? models : new ConcurrentHashMap<>();
            map.put(model, entityData);
            return map;
        });
        plugin.getModelManager().getModelEntitiesCache().put(entityID, model);

        if (plugin.getConfigManager().getConfig().getBoolean("options.debug.spawn")) plugin.getLogger().info("Creating model for " + model.getName());
    }

    @Override
    public void processEntities(Entity entity) {
//        if (plugin.getModelManager().getEntitiesCache().containsKey(entity.getEntityId())) return;
//
//        @NotNull Optional<EntityTrackerRegistry> modeledEntity = BetterModel.registry(entity);
//
//        modeledEntity.ifPresent(m -> createModel(modeledEntity.get().entity(), m.));
    }

    @Override
    public void loadListeners() {
        Bukkit.getPluginManager().registerEvents(new BetterModelListener(plugin), plugin);
    }

    @Override
    public boolean canSee(Player player, Object model) {
        Tracker tracker = (Tracker) model;
        return !tracker.getPipeline().isHide(BukkitAdapter.adapt(player));
    }
}
