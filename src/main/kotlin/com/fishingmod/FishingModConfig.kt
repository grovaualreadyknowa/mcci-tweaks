package com.fishingmod

class FishingModConfig {
    var enabled: Boolean = true
    var debugLogging: Boolean = false
    var teleportEnabled: Boolean? = true
    var targetServerHost: String? = ""
    var instanceCommand: String? = "instance join 11"
    var targetInstance: Int? = null
    var postTeleportDelayMs: Int = 1500
    var travelTimeoutMs: Int = 10000
    var scanCooldownMs: Int = 250
    var commandCooldownMs: Int = 30000
    var navigatorKeywords: MutableList<String>? = ArrayList()
    var fishingDestinationKeywords: MutableList<String>? = ArrayList()
    var currentInstanceKeywords: MutableList<String> = ArrayList()

    @Suppress("SENSELESS_COMPARISON")
    fun applyDefaults() {
        if (navigatorKeywords.isNullOrEmpty()) {
            navigatorKeywords = defaultNavigatorKeywords()
        }
        if (fishingDestinationKeywords.isNullOrEmpty()) {
            fishingDestinationKeywords = defaultFishingDestinationKeywords()
        }

        val target = targetInstance
        if (target == null || !isValidTargetInstance(target)) {
            targetInstance = parseTargetInstance(instanceCommand)
        }
        instanceCommand = commandForTargetInstance()
        currentInstanceKeywords = keywordsForTargetInstance()
        if (postTeleportDelayMs <= 0) postTeleportDelayMs = 1500
        if (travelTimeoutMs <= 0) travelTimeoutMs = 10000
        if (scanCooldownMs <= 0) scanCooldownMs = 250
        if (commandCooldownMs <= 0) commandCooldownMs = 30000
        if (targetServerHost == null) targetServerHost = ""
        if (teleportEnabled == null) teleportEnabled = true
    }

    fun targetInstance(): Int {
        val target = targetInstance
        return if (target == null || !isValidTargetInstance(target)) MAX_TARGET_INSTANCE else target
    }

    fun cycleTargetInstance() {
        val next = targetInstance() + 1
        targetInstance = if (next > MAX_TARGET_INSTANCE) MIN_TARGET_INSTANCE else next
        instanceCommand = commandForTargetInstance()
        currentInstanceKeywords = keywordsForTargetInstance()
    }

    fun selectTargetInstance(instance: Int) {
        targetInstance = instance
        instanceCommand = commandForTargetInstance()
        currentInstanceKeywords = keywordsForTargetInstance()
    }

    fun commandForTargetInstance(): String = "instance join ${targetInstance()}"

    fun keywordsForTargetInstance(): MutableList<String> {
        val instance = targetInstance().toString()
        return mutableListOf(
            "instance $instance",
            "inst $instance",
            "instance: $instance",
            "inst: $instance",
            "fishtance $instance",
            "fishtance: $instance",
            "fishtance #$instance",
            "fishtance-$instance",
            "fishstance $instance",
            "fishstance: $instance",
            "fishstance #$instance",
            "fishstance-$instance",
            "#$instance"
        )
    }

    fun copy(): FishingModConfig = FishingModConfig().also { it.copyFrom(this) }

    fun copyFrom(other: FishingModConfig) {
        enabled = other.enabled
        debugLogging = other.debugLogging
        teleportEnabled = other.teleportEnabled
        targetServerHost = other.targetServerHost
        instanceCommand = other.instanceCommand
        targetInstance = other.targetInstance
        postTeleportDelayMs = other.postTeleportDelayMs
        travelTimeoutMs = other.travelTimeoutMs
        scanCooldownMs = other.scanCooldownMs
        commandCooldownMs = other.commandCooldownMs
        navigatorKeywords = other.navigatorKeywords?.toMutableList()
        fishingDestinationKeywords = other.fishingDestinationKeywords?.toMutableList()
        currentInstanceKeywords = other.currentInstanceKeywords.toMutableList()
    }

    companion object {
        const val MIN_TARGET_INSTANCE = 8
        const val MAX_TARGET_INSTANCE = 11

        @JvmStatic
        fun defaults(): FishingModConfig = FishingModConfig().apply {
            navigatorKeywords = defaultNavigatorKeywords()
            fishingDestinationKeywords = defaultFishingDestinationKeywords()
            targetInstance = MAX_TARGET_INSTANCE
            currentInstanceKeywords = keywordsForTargetInstance()
        }

        private fun defaultNavigatorKeywords() = mutableListOf("navigator", "teleport", "warp")

        private fun defaultFishingDestinationKeywords() =
            mutableListOf("fishing", "fish", "angler", "dock", "harbor", "ocean", "sea", "island")

        private fun isValidTargetInstance(instance: Int): Boolean =
            instance in MIN_TARGET_INSTANCE..MAX_TARGET_INSTANCE

        private fun parseTargetInstance(command: String?): Int {
            val normalized = command?.trim()?.removePrefix("/") ?: return MAX_TARGET_INSTANCE
            return (MIN_TARGET_INSTANCE..MAX_TARGET_INSTANCE).firstOrNull { normalized == "instance join $it" }
                ?: MAX_TARGET_INSTANCE
        }
    }
}
