package dev.caden.mccitweaks.materialmarket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.caden.mccitweaks.McciTweaksMod;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MaterialMarketInventoryStore {
    public static final String FILE_NAME = "mcci-tweaks-material-inventory.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static State state = new State();

    private MaterialMarketInventoryStore() {
    }

    public static synchronized void load() {
        state = loadFromDirectory(FabricLoader.getInstance().getConfigDir());
    }

    public static synchronized Map<String, MaterialInventoryEntry> inventoryFor(String playerId) {
        return state.players.computeIfAbsent(playerId, ignored -> new HashMap<>());
    }

    public static synchronized void clear(String playerId) {
        state.players.remove(playerId);
        save();
    }

    public static synchronized void replaceFromApi(
        String playerId,
        Map<String, MaterialMarketApiClient.AssetAmount> inventory,
        long updatedAt
    ) {
        state.players.put(playerId, createApiInventory(inventory, updatedAt));
        save();
    }

    public static synchronized void save() {
        saveToDirectory(FabricLoader.getInstance().getConfigDir(), state);
    }

    static State loadFromDirectory(Path configDirectory) {
        Path path = configDirectory.resolve(FILE_NAME);
        if (Files.exists(path)) {
            try {
                State loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), State.class);
                return normalize(loaded);
            } catch (Exception exception) {
                McciTweaksMod.LOGGER.warn("Failed to load Material Market inventory from {}; using empty state.", path, exception);
                return new State();
            }
        }

        State migrated = migrateTridentState(configDirectory);
        saveToDirectory(configDirectory, migrated);
        return migrated;
    }

    static void saveToDirectory(Path configDirectory, State value) {
        Path path = configDirectory.resolve(FILE_NAME);
        try {
            Files.createDirectories(configDirectory);
            Files.writeString(path, GSON.toJson(normalize(value)), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            McciTweaksMod.LOGGER.error("Failed to save Material Market inventory to {}", path, exception);
        }
    }

    static Map<String, MaterialInventoryEntry> createApiInventory(
        Map<String, MaterialMarketApiClient.AssetAmount> inventory,
        long updatedAt
    ) {
        Map<String, MaterialInventoryEntry> replacement = new LinkedHashMap<>();
        inventory.forEach((key, asset) -> replacement.put(
            key,
            new MaterialInventoryEntry(asset.displayName(), Math.max(0L, asset.amount()), updatedAt)
        ));
        return replacement;
    }

    private static State migrateTridentState(Path configDirectory) {
        Path legacyPath = configDirectory.resolve("trident").resolve("playerstate.json");
        if (!Files.exists(legacyPath)) {
            return new State();
        }

        try {
            JsonObject root = JsonParser.parseString(Files.readString(legacyPath, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonElement materialMarket = root.get("materialMarketInventory");
            if (materialMarket == null || materialMarket.isJsonNull()) {
                return new State();
            }

            State migrated = normalize(GSON.fromJson(materialMarket, State.class));
            McciTweaksMod.LOGGER.info("Imported Material Market inventory from {}", legacyPath);
            return migrated;
        } catch (Exception exception) {
            McciTweaksMod.LOGGER.warn("Failed to import Material Market inventory from {}", legacyPath, exception);
            return new State();
        }
    }

    private static State normalize(State value) {
        if (value == null) {
            return new State();
        }
        if (value.players == null) {
            value.players = new HashMap<>();
        }
        value.players.replaceAll((ignored, inventory) -> inventory == null ? new HashMap<>() : inventory);
        return value;
    }

    public static final class State {
        public Map<String, Map<String, MaterialInventoryEntry>> players = new HashMap<>();
    }

    public static final class MaterialInventoryEntry {
        public String displayName = "";
        public long amount;
        public long updatedAt;

        public MaterialInventoryEntry() {
        }

        public MaterialInventoryEntry(String displayName, long amount, long updatedAt) {
            this.displayName = displayName;
            this.amount = amount;
            this.updatedAt = updatedAt;
        }
    }
}
