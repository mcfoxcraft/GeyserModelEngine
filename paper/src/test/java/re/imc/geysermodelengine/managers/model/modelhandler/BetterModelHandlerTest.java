package re.imc.geysermodelengine.managers.model.modelhandler;

import kr.toxicity.model.api.entity.BaseEntity;
import kr.toxicity.model.api.tracker.EntityTracker;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import re.imc.geysermodelengine.BetterModelStub;
import re.imc.geysermodelengine.GeyserModelEngine;
import re.imc.geysermodelengine.managers.ConfigManager;
import re.imc.geysermodelengine.managers.model.EntityTaskManager;
import re.imc.geysermodelengine.managers.model.ModelManager;
import re.imc.geysermodelengine.managers.model.entity.BetterModelEntityData;
import re.imc.geysermodelengine.managers.model.entity.EntityData;
import re.imc.geysermodelengine.managers.model.model.Model;
import re.imc.geysermodelengine.managers.model.propertyhandler.PropertyHandler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

/**
 * A new tracker of a model a mob already has a copy of gets its own copy: BetterModel closes the tracker it
 * replaces, and that one's copy goes then (mcfoxcraft/FoxKingdoms#1553).
 */
class BetterModelHandlerTest {

    static {
        BetterModelStub.install();
    }

    private static final int MOB_ID = 42;

    private final GeyserModelEngine plugin = mock(GeyserModelEngine.class);
    private final ConcurrentHashMap<Integer, Map<Model, EntityData>> entities = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Model> models = new ConcurrentHashMap<>();
    private final BaseEntity mob = mock(BaseEntity.class);

    private MockedConstruction<BetterModelEntityData> copies;
    private BetterModelHandler handler;

    @BeforeEach
    void setUp() {
        ModelManager modelManager = mock(ModelManager.class);
        EntityTaskManager taskManager = mock(EntityTaskManager.class);
        ConfigManager configManager = mock(ConfigManager.class);
        when(plugin.getModelManager()).thenReturn(modelManager);
        when(modelManager.getEntitiesCache()).thenReturn(entities);
        when(modelManager.getModelEntitiesCache()).thenReturn(models);
        when(plugin.getEntityTaskManager()).thenReturn(taskManager);
        when(taskManager.getPropertyHandler()).thenReturn(mock(PropertyHandler.class));
        when(plugin.getConfigManager()).thenReturn(configManager);
        when(configManager.getConfig()).thenReturn(mock(FileConfiguration.class));
        when(mob.id()).thenReturn(MOB_ID);

        // A copy starts its task and its packet entity when made: stand-ins that answer for their tracker.
        copies = mockConstruction(BetterModelEntityData.class,
                (copy, context) -> when(copy.getModelInstance()).thenReturn(context.arguments().get(2)));
        handler = new BetterModelHandler(plugin);
    }

    @AfterEach
    void tearDown() {
        copies.close();
    }

    private EntityTracker tracker(String name) {
        EntityTracker tracker = mock(EntityTracker.class);
        when(tracker.name()).thenReturn(name);
        when(tracker.sourceEntity()).thenReturn(mob);
        return tracker;
    }

    @Test
    void aTrackerGetsACopy() {
        EntityTracker tracker = tracker("goblin_giant");

        handler.createModel(tracker);

        assertEquals(1, copies.constructed().size());
        EntityData copy = copies.constructed().get(0);
        assertEquals(List.of(copy), List.copyOf(entities.get(MOB_ID).values()));
        assertSame(copy, models.get(MOB_ID).getEntityData());
        assertInstanceOf(ConcurrentHashMap.class, entities.get(MOB_ID));
    }

    @Test
    void aNewTrackerOfTheSameModelGetsItsOwnCopy() {
        // A reload closes the mob's tracker and makes a new one; the old copy has not gone yet.
        handler.createModel(tracker("goblin_giant"));
        EntityTracker replacement = tracker("goblin_giant");

        handler.createModel(replacement);

        assertEquals(2, copies.constructed().size());
        EntityData newCopy = copies.constructed().get(1);
        assertEquals(2, entities.get(MOB_ID).size());
        assertSame(newCopy, models.get(MOB_ID).getEntityData());
        assertSame(replacement, newCopy.getModelInstance());
    }

    @Test
    void theSameTrackerTwiceGetsOneCopy() {
        EntityTracker tracker = tracker("goblin_giant");

        handler.createModel(tracker);
        handler.createModel(tracker);

        assertEquals(1, copies.constructed().size());
        assertEquals(1, entities.get(MOB_ID).size());
    }
}
