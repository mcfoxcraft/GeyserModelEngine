package re.imc.geysermodelengine.runnables;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import re.imc.geysermodelengine.GeyserModelEngine;
import re.imc.geysermodelengine.managers.model.ModelManager;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BedrockMountControlRunnableTest {

    @Test
    void aPlayerWhoLeftWhileTheWalkRanIsPassedOver() {
        // A periodic task that throws is never run again.
        UUID gone = UUID.randomUUID();
        UUID here = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(here);
        when(player.getLocation()).thenReturn(new Location(null, 0, 64, 0, 0, 0));
        GeyserModelEngine plugin = mock(GeyserModelEngine.class);
        ModelManager modelManager = mock(ModelManager.class);
        when(plugin.getModelManager()).thenReturn(modelManager);
        when(modelManager.getPlayerJoinedCache()).thenReturn(Set.of(gone, here));
        when(modelManager.getDriversCache()).thenReturn(new ConcurrentHashMap<>());

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(here)).thenReturn(player);

            new BedrockMountControlRunnable(plugin).run();
        }

        verify(player).getLocation();
    }
}
