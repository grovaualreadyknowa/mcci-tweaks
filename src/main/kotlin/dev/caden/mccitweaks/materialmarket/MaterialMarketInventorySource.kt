package dev.caden.mccitweaks.materialmarket

enum class MaterialMarketInventorySource {
    TRADITIONAL,
    API;

    fun next(): MaterialMarketInventorySource = if (this == TRADITIONAL) API else TRADITIONAL
}
