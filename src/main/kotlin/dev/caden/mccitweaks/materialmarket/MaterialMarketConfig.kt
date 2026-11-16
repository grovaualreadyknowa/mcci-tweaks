package dev.caden.mccitweaks.materialmarket

class MaterialMarketConfig {
    var enabled: Boolean = true
    var inventorySource: MaterialMarketInventorySource = MaterialMarketInventorySource.TRADITIONAL
    var apiKey: String = ""

    /** Gson can leave these null when the file has a missing or unknown value. */
    @Suppress("SENSELESS_COMPARISON")
    fun applyDefaults() {
        if (inventorySource == null) inventorySource = MaterialMarketInventorySource.TRADITIONAL
        if (apiKey == null) apiKey = ""
    }

    fun usesTraditionalInventory(): Boolean = inventorySource == MaterialMarketInventorySource.TRADITIONAL

    fun usesApiInventory(): Boolean = inventorySource == MaterialMarketInventorySource.API

    fun copy(): MaterialMarketConfig = MaterialMarketConfig().also { it.copyFrom(this) }

    fun copyFrom(other: MaterialMarketConfig) {
        enabled = other.enabled
        inventorySource = other.inventorySource
        apiKey = other.apiKey
    }
}
