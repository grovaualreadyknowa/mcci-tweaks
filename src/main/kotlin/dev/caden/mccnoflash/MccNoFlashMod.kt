package dev.caden.mccnoflash

import dev.caden.mccitweaks.McciTweaksMod
import org.slf4j.Logger

/** Internal compatibility shim for the imported no-flash module. */
object MccNoFlashMod {
    const val MOD_ID: String = McciTweaksMod.MOD_ID

    @JvmField
    val LOGGER: Logger = McciTweaksMod.LOGGER
}
