package re.imc.geysermodelengine.managers.model.propertyhandler;

import com.google.common.cache.CacheBuilder;
import kr.toxicity.model.api.animation.AnimationIterator;
import kr.toxicity.model.api.animation.RunningAnimation;
import kr.toxicity.model.api.bone.RenderedBone;
import kr.toxicity.model.api.data.blueprint.BlueprintAnimation;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.data.renderer.RenderPipeline;
import kr.toxicity.model.api.tracker.EntityTracker;
import me.zimzaza4.geyserutils.spigot.api.EntityUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import re.imc.geysermodelengine.BetterModelStub;
import re.imc.geysermodelengine.GeyserModelEngine;
import re.imc.geysermodelengine.managers.ConfigManager;
import re.imc.geysermodelengine.managers.model.entity.BetterModelEntityData;
import re.imc.geysermodelengine.managers.model.taskshandler.BetterModelTaskHandler;
import re.imc.geysermodelengine.packet.entity.PacketEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** The animation properties a BetterModel copy sends to Bedrock players. */
class BetterModelPropertyHandlerTest {

    static {
        BetterModelStub.install();
    }

    private static final int COPY_ID = 7;

    private final GeyserModelEngine plugin = mock(GeyserModelEngine.class);
    private final EntityTracker tracker = mock(EntityTracker.class);
    private final RenderedBone bone = mock(RenderedBone.class);
    private final BetterModelEntityData data = mock(BetterModelEntityData.class);
    private final Player viewer = mock(Player.class);

    private MockedStatic<EntityUtils> geyserUtils;

    @BeforeEach
    void setUp() {
        geyserUtils = mockStatic(EntityUtils.class);

        ConfigManager configManager = mock(ConfigManager.class);
        FileConfiguration config = mock(FileConfiguration.class);
        when(plugin.getConfigManager()).thenReturn(configManager);
        when(configManager.getConfig()).thenReturn(config);
        when(config.getString(anyString())).thenReturn("modelengine");

        // Bits are given in name order: death is anim0's bit 1, idle its bit 2.
        BlueprintAnimation death = new BlueprintAnimation("death", AnimationIterator.Type.PLAY_ONCE, 1.08F, true, Map.of(), null, null);
        BlueprintAnimation idle = new BlueprintAnimation("idle", AnimationIterator.Type.LOOP, 2F, false, Map.of(), null, null);
        Map<String, BlueprintAnimation> animations = new LinkedHashMap<>();
        animations.put("idle", idle);
        animations.put("death", death);
        ModelRenderer renderer = new ModelRenderer("goblin_archer", ModelRenderer.Type.GENERAL, new LinkedHashMap<>(), animations);
        RenderPipeline pipeline = mock(RenderPipeline.class);
        when(tracker.bones()).thenReturn(List.of());
        when(tracker.getPipeline()).thenReturn(pipeline);
        when(tracker.renderer()).thenReturn(renderer);
        when(pipeline.bones()).thenReturn(List.of(bone));
        when(pipeline.getParent()).thenReturn(renderer);

        PacketEntity copy = mock(PacketEntity.class);
        when(copy.getEntityId()).thenReturn(COPY_ID);
        BetterModelTaskHandler task = mock(BetterModelTaskHandler.class);
        when(task.getLastPlayedAnim()).thenReturn(CacheBuilder.newBuilder().expireAfterWrite(30, TimeUnit.MILLISECONDS).build());
        when(task.getLastIntSet()).thenReturn(new ConcurrentHashMap<>());
        when(data.getEntity()).thenReturn(copy);
        when(data.getEntityTracker()).thenReturn(tracker);
        when(data.getEntityTask()).thenReturn(task);
    }

    @AfterEach
    void tearDown() {
        geyserUtils.close();
    }

    @Test
    void aDeathThatEndsWhileTheBoneIsReadIsStillSent() {
        // BetterModel's tick thread clears the bone's running animation as the death ends: a second read would be null.
        when(bone.runningAnimation()).thenReturn(new RunningAnimation("death", AnimationIterator.Type.PLAY_ONCE), (RunningAnimation) null);

        new BetterModelPropertyHandler(plugin).updateEntityProperties(data, Set.of(viewer), false);

        geyserUtils.verify(() -> EntityUtils.sendIntProperties(viewer, COPY_ID, Map.of("modelengine:anim0", 1)));
    }

    @Test
    void anAnimationTheBlueprintDoesNotHaveSetsNoBit() {
        when(bone.runningAnimation()).thenReturn(new RunningAnimation("wave", AnimationIterator.Type.PLAY_ONCE));

        new BetterModelPropertyHandler(plugin).updateEntityProperties(data, Set.of(viewer), false);

        geyserUtils.verify(() -> EntityUtils.sendIntProperties(viewer, COPY_ID, Map.of("modelengine:anim0", 0)));
    }
}
