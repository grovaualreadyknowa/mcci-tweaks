package dev.caden.mccnoflash;

import dev.caden.mccitweaks.McciTweaksMod;
import org.slf4j.Logger;

/**
 * Internal compatibility shim for the imported no-flash module.
 */
public final class MccNoFlashMod {
    public static final String MOD_ID = McciTweaksMod.MOD_ID;
    public static final Logger LOGGER = McciTweaksMod.LOGGER;

    private MccNoFlashMod() {
    }
}
