package dev.caden.mccitweaks.materialmarket;

public final class MaterialMarketConfig {
    public boolean enabled = true;
    public MaterialMarketInventorySource inventorySource = MaterialMarketInventorySource.TRADITIONAL;
    public String apiKey = "";

    public void applyDefaults() {
        if (this.inventorySource == null) {
            this.inventorySource = MaterialMarketInventorySource.TRADITIONAL;
        }
        if (this.apiKey == null) {
            this.apiKey = "";
        }
    }

    public boolean usesTraditionalInventory() {
        return this.inventorySource == MaterialMarketInventorySource.TRADITIONAL;
    }

    public boolean usesApiInventory() {
        return this.inventorySource == MaterialMarketInventorySource.API;
    }
}
