package dev.caden.mccitweaks.materialmarket

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MaterialMarketInventoryStoreTest {
    @TempDir
    lateinit var configDirectory: Path

    @Test
    fun `creates empty native state`() {
        val state = MaterialMarketInventoryStore.loadFromDirectory(configDirectory)

        assertTrue(state.players.isEmpty())
        assertTrue(Files.exists(configDirectory.resolve(MaterialMarketInventoryStore.FILE_NAME)))
    }

    @Test
    fun `imports only material market data from trident state`() {
        val tridentDirectory = configDirectory.resolve("trident")
        Files.createDirectories(tridentDirectory)
        val legacyPath = tridentDirectory.resolve("playerstate.json")
        val legacy = """
            {
              "supplies": {"needsUpdating": false},
              "materialMarketInventory": {
                "players": {
                  "player-1": {
                    "refined wood": {"displayName": "Refined Wood", "amount": 37, "updatedAt": 12345}
                  }
                }
              }
            }
        """.trimIndent()
        Files.writeString(legacyPath, legacy)

        val state = MaterialMarketInventoryStore.loadFromDirectory(configDirectory)

        assertEquals(1, state.players.size)
        assertEquals(37L, state.players["player-1"]!!["refined wood"]!!.amount)
        assertEquals(legacy, Files.readString(legacyPath))
    }

    @Test
    fun `builds complete independent api replacement`() {
        val apiInventory = mapOf(
            "refined wood" to MaterialMarketApiClient.AssetAmount("Refined Wood", 12),
            "zero dust" to MaterialMarketApiClient.AssetAmount("Zero Dust", -2)
        )

        val replacement = MaterialMarketInventoryStore.createApiInventory(apiInventory, 9876L)

        assertEquals(2, replacement.size)
        assertEquals(12L, replacement["refined wood"]!!.amount)
        assertEquals(0L, replacement["zero dust"]!!.amount)
        assertEquals(9876L, replacement["refined wood"]!!.updatedAt)
        assertEquals("Refined Wood", replacement["refined wood"]!!.displayName)
    }
}
