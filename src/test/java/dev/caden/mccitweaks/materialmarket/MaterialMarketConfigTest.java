package dev.caden.mccitweaks.materialmarket;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MaterialMarketConfigTest {
    @Test
    void traditionalModeIsTheDefault() {
        MaterialMarketConfig config = new MaterialMarketConfig();

        assertEquals(MaterialMarketInventorySource.TRADITIONAL, config.inventorySource);
        assertEquals("", config.apiKey);
        assertTrue(config.usesTraditionalInventory());
        assertFalse(config.usesApiInventory());
    }

    @Test
    void apiAndTraditionalModesAreMutuallyExclusive() {
        MaterialMarketConfig config = new MaterialMarketConfig();
        config.inventorySource = MaterialMarketInventorySource.API;

        assertTrue(config.usesApiInventory());
        assertFalse(config.usesTraditionalInventory());
        assertEquals(MaterialMarketInventorySource.TRADITIONAL, config.inventorySource.next());
    }

    @Test
    void nullLegacyValuesReturnToSafeDefaults() {
        MaterialMarketConfig config = new MaterialMarketConfig();
        config.inventorySource = null;
        config.apiKey = null;

        config.applyDefaults();

        assertEquals(MaterialMarketInventorySource.TRADITIONAL, config.inventorySource);
        assertEquals("", config.apiKey);
    }
}
