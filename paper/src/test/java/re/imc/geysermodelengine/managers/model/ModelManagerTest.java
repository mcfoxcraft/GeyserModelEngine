package re.imc.geysermodelengine.managers.model;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import re.imc.geysermodelengine.GeyserModelEngine;

import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class ModelManagerTest {

    @Test
    void theJoinedBedrockPlayersCanBeReadWhileTheyChange() {
        // The model and mount tasks read this set on the scheduler pool while players join and quit on the server's
        // threads: a plain HashSet answers false for a player in it while it grows, or throws while it is walked.
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            Set<UUID> joined = new ModelManager(mock(GeyserModelEngine.class, RETURNS_DEEP_STUBS)).getPlayerJoinedCache();
            joined.add(UUID.randomUUID());
            joined.add(UUID.randomUUID());

            Iterator<UUID> reading = joined.iterator();
            reading.next();
            joined.add(UUID.randomUUID());

            assertDoesNotThrow(() -> reading.forEachRemaining(uuid -> { }));
        }
    }
}
