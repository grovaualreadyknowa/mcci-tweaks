package dev.caden.mccnoflash

import net.minecraft.network.chat.Component

enum class FlashMode(displayName: String) {
    DEFAULT("Default"),
    NORMAL_NO_FLASH("No Flashing"),
    STATIC_GLOW("Static Glow");

    val displayName: Component = Component.literal(displayName)
}
