package dev.caden.mccitweaks.materialmarket;

public enum MaterialMarketInventorySource {
    TRADITIONAL,
    API;

    public MaterialMarketInventorySource next() {
        return this == TRADITIONAL ? API : TRADITIONAL;
    }
}
