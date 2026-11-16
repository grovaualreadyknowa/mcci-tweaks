package dev.caden.mccitweaks.chat

/**
 * MCC Island Champion's Crown evolution bands. The representative RGB values come from the Crown Level
 * icon for each band; the actual crowns are often multi-colour gradients, so the UI uses level ranges as labels.
 */
enum class CrownColor(
    val label: String,
    val rgb: Int,
    val isCrownLevel: Boolean = true,
    private val glyph: Char? = null
) {
    LEVEL_0_9("0-9", 0x69696A, glyph = ''),
    LEVEL_10_19("10-19", 0x71719A, glyph = ''),
    LEVEL_20_29("20-29", 0x04DB42, glyph = ''),
    LEVEL_30_39("30-39", 0x269DFF, glyph = ''),
    LEVEL_40_49("40-49", 0xC039FF, glyph = ''),
    LEVEL_50_59("50-59", 0xFF8F00, glyph = ''),
    LEVEL_60_69("60-69", 0xFF6439, glyph = ''),
    LEVEL_70_79("70-79", 0x50CFB0, glyph = ''),
    LEVEL_80_89("80-89", 0xAAAAFF, glyph = ''),
    LEVEL_90_99("90-99", 0xFF738F, glyph = ''),
    LEVEL_100_PLUS("100+", 0x8119A1, glyph = ''),

    // Retained solely to migrate config files written by the first Chat Separator crown-mute build.
    @Deprecated("Migrated to a crown-level band on load") GRAY("", 0, false),
    @Deprecated("Migrated to a crown-level band on load") GREEN("", 0, false),
    @Deprecated("Migrated to a crown-level band on load") AQUA("", 0, false),
    @Deprecated("Migrated to a crown-level band on load") BLUE("", 0, false),
    @Deprecated("Migrated to a crown-level band on load") PURPLE("", 0, false),
    @Deprecated("Migrated to a crown-level band on load") PINK("", 0, false),
    @Deprecated("Migrated to a crown-level band on load") RED("", 0, false),
    @Deprecated("Migrated to a crown-level band on load") ORANGE("", 0, false),
    @Deprecated("Migrated to a crown-level band on load") YELLOW("", 0, false);

    @Suppress("DEPRECATION")
    fun canonical(): CrownColor = when (this) {
        GRAY -> LEVEL_0_9
        GREEN -> LEVEL_20_29
        AQUA -> LEVEL_70_79
        BLUE -> LEVEL_30_39
        PURPLE -> LEVEL_40_49
        PINK -> LEVEL_90_99
        RED -> LEVEL_60_69
        ORANGE, YELLOW -> LEVEL_50_59
        else -> this
    }

    companion object {
        val crownLevels: List<CrownColor> = entries.filter(CrownColor::isCrownLevel)

        fun fromGlyph(glyph: Char): CrownColor? = crownLevels.firstOrNull { it.glyph == glyph }
    }
}
