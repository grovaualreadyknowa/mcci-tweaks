package dev.caden.mccitweaks.materialmarket

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import dev.caden.mccitweaks.McciTweaksMod
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import net.fabricmc.loader.api.FabricLoader

object MaterialMarketInventoryStore {
    const val FILE_NAME = "mcci-tweaks-material-inventory.json"

    private val GSON: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private var state = State()

    @JvmStatic
    @Synchronized
    fun load() {
        state = loadFromDirectory(FabricLoader.getInstance().configDir)
    }

    @JvmStatic
    @Synchronized
    fun inventoryFor(playerId: String): MutableMap<String, MaterialInventoryEntry> =
        state.players.getOrPut(playerId) { HashMap() }

    @JvmStatic
    @Synchronized
    fun clear(playerId: String) {
        state.players.remove(playerId)
        save()
    }

    @JvmStatic
    @Synchronized
    internal fun replaceFromApi(playerId: String, inventory: Map<String, MaterialMarketApiClient.AssetAmount>, updatedAt: Long) {
        state.players[playerId] = createApiInventory(inventory, updatedAt)
        save()
    }

    @JvmStatic
    @Synchronized
    fun save() {
        saveToDirectory(FabricLoader.getInstance().configDir, state)
    }

    internal fun loadFromDirectory(configDirectory: Path): State {
        val path = configDirectory.resolve(FILE_NAME)
        if (path.exists()) {
            return try {
                normalize(GSON.fromJson(path.readText(), State::class.java))
            } catch (exception: Exception) {
                McciTweaksMod.LOGGER.warn(
                    "Failed to load Material Market inventory from {}; using empty state.", path, exception
                )
                State()
            }
        }

        val migrated = migrateTridentState(configDirectory)
        saveToDirectory(configDirectory, migrated)
        return migrated
    }

    internal fun saveToDirectory(configDirectory: Path, value: State) {
        val path = configDirectory.resolve(FILE_NAME)
        try {
            Files.createDirectories(configDirectory)
            path.writeText(GSON.toJson(normalize(value)))
        } catch (exception: Exception) {
            McciTweaksMod.LOGGER.error("Failed to save Material Market inventory to {}", path, exception)
        }
    }

    internal fun createApiInventory(
        inventory: Map<String, MaterialMarketApiClient.AssetAmount>,
        updatedAt: Long
    ): MutableMap<String, MaterialInventoryEntry> {
        val replacement = LinkedHashMap<String, MaterialInventoryEntry>()
        inventory.forEach { (key, asset) ->
            replacement[key] = MaterialInventoryEntry(asset.displayName, maxOf(0L, asset.amount), updatedAt)
        }
        return replacement
    }

    private fun migrateTridentState(configDirectory: Path): State {
        val legacyPath = configDirectory.resolve("trident").resolve("playerstate.json")
        if (!legacyPath.exists()) return State()

        return try {
            val root = JsonParser.parseString(legacyPath.readText()).asJsonObject
            val materialMarket = root.get("materialMarketInventory")
            if (materialMarket == null || materialMarket.isJsonNull) {
                State()
            } else {
                normalize(GSON.fromJson(materialMarket, State::class.java)).also {
                    McciTweaksMod.LOGGER.info("Imported Material Market inventory from {}", legacyPath)
                }
            }
        } catch (exception: Exception) {
            McciTweaksMod.LOGGER.warn("Failed to import Material Market inventory from {}", legacyPath, exception)
            State()
        }
    }

    @Suppress("SENSELESS_COMPARISON", "UNCHECKED_CAST")
    private fun normalize(value: State?): State {
        if (value == null) return State()
        if (value.players == null) value.players = HashMap()
        val players = value.players as MutableMap<String, MutableMap<String, MaterialInventoryEntry>?>
        players.replaceAll { _, inventory -> inventory ?: HashMap() }
        return value
    }

    class State {
        var players: MutableMap<String, MutableMap<String, MaterialInventoryEntry>> = HashMap()
    }

    class MaterialInventoryEntry() {
        var displayName: String = ""
        var amount: Long = 0
        var updatedAt: Long = 0

        constructor(displayName: String, amount: Long, updatedAt: Long) : this() {
            this.displayName = displayName
            this.amount = amount
            this.updatedAt = updatedAt
        }
    }
}
