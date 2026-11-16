package dev.caden.mccitweaks.chat

enum class ChatTab(val label: String, val narrowLabel: String, val fallbackTextColor: Int = 0xFFFFFF) {
    ALL("All", "A"),
    PARTY("Party", "Y", 0xAAABFE),
    ROOMS("Rooms", "R", 0x55FF55),
    PM("PM", "P", 0xFF7EFF),
    LOCAL("Local", "L"),
    TRADE("Trade", "T", 0x00BB99),
    PLOBBY("Plobby", "B", 0xFFBC40)
}
