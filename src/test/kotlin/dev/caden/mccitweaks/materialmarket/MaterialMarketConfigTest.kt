package dev.caden.mccitweaks.materialmarket

import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MaterialMarketConfigTest {
    @Test
    fun `traditional mode is the default`() {
        val config = MaterialMarketConfig()

        assertEquals(MaterialMarketInventorySource.TRADITIONAL, config.inventorySource)
        assertEquals("", config.apiKey)
        assertTrue(config.usesTraditionalInventory())
        assertFalse(config.usesApiInventory())
    }

    @Test
    fun `api and traditional modes are mutually exclusive`() {
        val config = MaterialMarketConfig()
        config.inventorySource = MaterialMarketInventorySource.API

        assertTrue(config.usesApiInventory())
        assertFalse(config.usesTraditionalInventory())
        assertEquals(MaterialMarketInventorySource.TRADITIONAL, config.inventorySource.next())
    }

    @Test
    fun `null legacy values return to safe defaults`() {
        val config = Gson().fromJson("""{"inventorySource": null, "apiKey": null}""", MaterialMarketConfig::class.java)

        config.applyDefaults()

        assertEquals(MaterialMarketInventorySource.TRADITIONAL, config.inventorySource)
        assertEquals("", config.apiKey)
    }
}
