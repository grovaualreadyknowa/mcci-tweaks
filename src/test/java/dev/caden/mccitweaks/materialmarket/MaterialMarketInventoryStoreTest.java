package dev.caden.mccitweaks.materialmarket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MaterialMarketInventoryStoreTest {
    @TempDir
    Path configDirectory;

    @Test
    void createsEmptyNativeState() {
        MaterialMarketInventoryStore.State state =
            MaterialMarketInventoryStore.loadFromDirectory(configDirectory);

        assertTrue(state.players.isEmpty());
        assertTrue(Files.exists(configDirectory.resolve(MaterialMarketInventoryStore.FILE_NAME)));
    }

    @Test
    void importsOnlyMaterialMarketDataFromTridentState() throws Exception {
        Path tridentDirectory = configDirectory.resolve("trident");
        Files.createDirectories(tridentDirectory);
        Path legacyPath = tridentDirectory.resolve("playerstate.json");
        String legacy = """
            {
              "supplies": {"needsUpdating": false},
              "materialMarketInventory": {
                "players": {
                  "player-1": {
                    "refined wood": {
                      "displayName": "Refined Wood",
                      "amount": 37,
                      "updatedAt": 12345
                    }
                  }
                }
              }
            }
            """;
        Files.writeString(legacyPath, legacy);

        MaterialMarketInventoryStore.State state =
            MaterialMarketInventoryStore.loadFromDirectory(configDirectory);

        assertEquals(1, state.players.size());
        assertEquals(37, state.players.get("player-1").get("refined wood").amount);
        assertEquals(legacy, Files.readString(legacyPath));
    }

    @Test
    void buildsCompleteIndependentApiReplacement() {
        Map<String, MaterialMarketApiClient.AssetAmount> apiInventory = Map.of(
            "refined wood", new MaterialMarketApiClient.AssetAmount("Refined Wood", 12),
            "zero dust", new MaterialMarketApiClient.AssetAmount("Zero Dust", -2)
        );

        Map<String, MaterialMarketInventoryStore.MaterialInventoryEntry> replacement =
            MaterialMarketInventoryStore.createApiInventory(apiInventory, 9876L);

        assertEquals(2, replacement.size());
        assertEquals(12, replacement.get("refined wood").amount);
        assertEquals(0, replacement.get("zero dust").amount);
        assertEquals(9876L, replacement.get("refined wood").updatedAt);
        assertEquals("Refined Wood", replacement.get("refined wood").displayName);
    }
}
