package com.fishingmod

import com.fishingmod.mixin.PlayerTabOverlayAccessor
import com.mojang.logging.LogUtils
import java.util.Locale
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import org.slf4j.Logger

class FishingModClient : ClientModInitializer {
    private val state = FishingAutomationState()

    override fun onInitializeClient() {
        val loaded = FishingModConfigStore.loadConfig()
        config = loaded
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { client -> onClientTick(client) })
        LOGGER.info(
            "Fishtance loaded. Target server={}, target instance={}",
            loaded.targetServerHost,
            loaded.targetInstance()
        )
    }

    private fun onClientTick(client: Minecraft?) {
        if (client == null) return
        val current = config ?: FishingModConfigStore.loadConfig().also { config = it }
        state.tick(client, current)
    }

    private class FishingAutomationState {
        private var lastServerKey = ""
        private var lastScreen: Screen? = null
        private var fishingDestinationDetected = false
        private var navigatorScreenWasOpen = false
        private var awaitingJoinCommand = false
        private var awaitingAmbientFishingJoin = false
        private var lastKnownInTargetInstance = false
        private var cycleStartedInTargetInstance = false
        private var ambientJoinEligibleAfterTargetInstance = false
        private var hadStableTargetFishtanceFooter = false
        private var navigatorScreenClosedAt = 0L
        private var lastScanAt = 0L
        private var targetFishtanceFooterSeenSince = 0L
        private var lastDebugMessage = ""
        private var debugLoggingEnabled = false

        fun tick(client: Minecraft, config: FishingModConfig) {
            debugLoggingEnabled = config.debugLogging
            val player = client.player
            if (player == null || client.level == null) {
                skipTick(client, "waiting for player and world to be ready")
                return
            }
            if (!config.enabled) {
                skipTick(client, "automation is disabled in config")
                return
            }
            if (config.teleportEnabled != true) {
                skipTick(client, "teleport automation is disabled in config")
                return
            }

            val serverKey = serverKey(client)
            if (!serverMatches(config, client)) {
                logStep("server mismatch: current=$serverKey, target=${normalizeServerHost(config.targetServerHost)}")
                if (serverKey != lastServerKey) {
                    resetSession()
                    lastServerKey = serverKey
                } else {
                    resetTransient()
                }
                lastScreen = client.gui.screen()
                return
            }

            if (serverKey != lastServerKey) {
                logStep("new server session: $serverKey")
                resetSession()
                lastServerKey = serverKey
            }

            val currentScreen = client.gui.screen()
            if (currentScreen != null && isInternalScreen(currentScreen)) {
                if (lastScreen !== currentScreen) {
                    logStep("ignoring internal screen: ${currentScreen.title.string}")
                }
                lastScreen = currentScreen
                return
            }

            val navigatorHeld = holdsNavigator(player, config)
            if (debugLoggingEnabled && currentScreen !== lastScreen) {
                val screenClass = currentScreen?.javaClass?.simpleName ?: "null"
                val screenTitle = currentScreen?.title?.string ?: ""
                logStep("screen changed: class=$screenClass, title=$screenTitle, navigatorHeld=$navigatorHeld")
            }

            val currentScreenIsNavigator = currentScreen != null && screenLooksLikeNavigator(currentScreen, player, config)
            val currentScreenIsFishingDestination =
                currentScreen != null && screenContainsFishingDestination(currentScreen, player)
            val alreadyInTargetInstance = hasMatchingInstanceText(client, config)
            updateTargetFishtanceFooterState(client, config)
            if (alreadyInTargetInstance) {
                lastKnownInTargetInstance = true
                if (targetFishtanceFooterStable()) {
                    ambientJoinEligibleAfterTargetInstance = true
                    hadStableTargetFishtanceFooter = true
                }
            } else if (currentScreen == null && !awaitingJoinCommand && hasTabText(client)) {
                lastKnownInTargetInstance = false
            }
            if (debugLoggingEnabled && currentScreenIsFishingDestination) {
                logStep(
                    "instance state while fishing screen open: alreadyInTargetInstance=$alreadyInTargetInstance" +
                        ", lastKnownInTargetInstance=$lastKnownInTargetInstance" +
                        ", headerFooterMatch=${hasMatchingInstanceText(client, config)}" +
                        ", tabHeader=\"${tabHeaderText(client)}\"" +
                        ", tabFooter=\"${tabFooterText(client)}\""
                )
            }

            if (currentScreenIsNavigator && shouldRescan(config)) {
                lastScanAt = System.currentTimeMillis()
                logStep("navigator screen detected: ${currentScreen!!.title.string}")
                if (!navigatorScreenWasOpen) {
                    logStep("navigator opened")
                }
                if (currentScreenIsFishingDestination) {
                    startFishingCycle(alreadyInTargetInstance)
                }
            } else if (currentScreenIsFishingDestination && shouldRescan(config)) {
                lastScanAt = System.currentTimeMillis()
                startFishingCycle(alreadyInTargetInstance)
            } else if (currentScreen != null && navigatorHeld && !fishingDestinationDetected) {
                logStep("screen is open but it does not look like a navigator yet: ${currentScreen.title.string}")
            }

            if (lastScreen != null && currentScreen == null && navigatorScreenWasOpen && fishingDestinationDetected) {
                awaitingJoinCommand = true
                awaitingAmbientFishingJoin = false
                navigatorScreenClosedAt = System.currentTimeMillis()
                logStep("navigator closed; waiting ${config.postTeleportDelayMs}ms before joining")
            }

            if (currentScreen == null &&
                !awaitingJoinCommand &&
                shouldRescan(config) &&
                !alreadyInTargetInstance &&
                !lastKnownInTargetInstance &&
                ambientJoinEligibleAfterTargetInstance &&
                hadStableTargetFishtanceFooter &&
                ambientFishingAreaDetected(client)
            ) {
                lastScanAt = System.currentTimeMillis()
                awaitingJoinCommand = true
                awaitingAmbientFishingJoin = true
                ambientJoinEligibleAfterTargetInstance = false
                hadStableTargetFishtanceFooter = false
                navigatorScreenClosedAt = System.currentTimeMillis()
                logStep("fishing area detected without navigator; waiting ${config.postTeleportDelayMs}ms before joining")
            }

            if (currentScreenIsNavigator) {
                navigatorScreenWasOpen = true
            }

            if (currentScreen == null && awaitingJoinCommand) {
                val elapsed = System.currentTimeMillis() - navigatorScreenClosedAt
                if (elapsed >= config.postTeleportDelayMs) {
                    if (cycleStartedInTargetInstance || hasMatchingInstanceText(client, config)) {
                        logStep("arrived in target instance; skipping join command")
                    } else {
                        val reason = if (awaitingAmbientFishingJoin) " after ambient fishing detection" else ""
                        logStep("sending join command$reason: ${config.commandForTargetInstance()}")
                        sendJoinCommand(player, config)
                    }
                    clearCycle()
                }
            }

            if (currentScreen != null && !currentScreenIsNavigator && !fishingDestinationDetected) {
                navigatorScreenWasOpen = false
            }

            lastScreen = currentScreen
        }

        private fun skipTick(client: Minecraft, reason: String) {
            logStep(reason)
            resetTransient()
            lastScreen = client.gui.screen()
        }

        private fun startFishingCycle(alreadyInTargetInstance: Boolean) {
            navigatorScreenWasOpen = true
            cycleStartedInTargetInstance = lastKnownInTargetInstance || alreadyInTargetInstance
            if (cycleStartedInTargetInstance) {
                logStep("fishing submenu detected but already in target instance; skipping join")
                clearCycle()
            } else {
                fishingDestinationDetected = true
                logStep("fishing submenu detected")
            }
        }

        private fun shouldRescan(config: FishingModConfig): Boolean =
            System.currentTimeMillis() - lastScanAt >= config.scanCooldownMs

        private fun sendJoinCommand(player: LocalPlayer, config: FishingModConfig) {
            val command = config.commandForTargetInstance().removePrefix("/")
            val connection = player.connection
            if (command.isBlank() || connection == null) return
            connection.sendCommand(command)
            LOGGER.info("Fishtance sent /{}", command)
        }

        private fun clearCycle() {
            fishingDestinationDetected = false
            navigatorScreenWasOpen = false
            awaitingJoinCommand = false
            awaitingAmbientFishingJoin = false
            cycleStartedInTargetInstance = false
            navigatorScreenClosedAt = 0L
            lastScanAt = 0L
        }

        private fun resetTransient() {
            clearCycle()
            lastScreen = null
            lastDebugMessage = ""
        }

        private fun resetSession() {
            resetTransient()
            lastScanAt = 0L
            lastServerKey = ""
            lastKnownInTargetInstance = false
            ambientJoinEligibleAfterTargetInstance = false
            hadStableTargetFishtanceFooter = false
            targetFishtanceFooterSeenSince = 0L
        }

        private fun logStep(message: String) {
            if (debugLoggingEnabled && message != lastDebugMessage) {
                LOGGER.info("[fishtance-debug] {}", message)
                lastDebugMessage = message
            }
        }

        private fun updateTargetFishtanceFooterState(client: Minecraft, config: FishingModConfig) {
            if (hasTargetFishtanceFooter(client, config)) {
                if (targetFishtanceFooterSeenSince == 0L) {
                    targetFishtanceFooterSeenSince = System.currentTimeMillis()
                }
            } else {
                targetFishtanceFooterSeenSince = 0L
            }
        }

        private fun targetFishtanceFooterStable(): Boolean =
            targetFishtanceFooterSeenSince > 0L &&
                System.currentTimeMillis() - targetFishtanceFooterSeenSince >= TARGET_FOOTER_STABLE_MS
    }

    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
        private const val TARGET_FOOTER_STABLE_MS = 3000L

        @Volatile
        private var config: FishingModConfig? = null

        @JvmStatic
        fun reloadConfigFromDisk() {
            config = FishingModConfigStore.loadConfig()
        }

        private fun holdsNavigator(player: LocalPlayer, config: FishingModConfig): Boolean =
            stackMatchesKeywords(player.mainHandItem, config.navigatorKeywords) ||
                stackMatchesKeywords(player.offhandItem, config.navigatorKeywords)

        private fun screenLooksLikeNavigator(screen: Screen, player: LocalPlayer, config: FishingModConfig): Boolean {
            if (matchesAnyKeyword(normalizeText(screen.title.string), config.navigatorKeywords)) return true
            if (screen !is AbstractContainerScreen<*>) return false
            val menu = player.containerMenu ?: return false
            return menu.slots.any { slot -> stackMatchesKeywords(slot.item, config.navigatorKeywords) }
        }

        private fun screenContainsFishingDestination(screen: Screen, player: LocalPlayer): Boolean {
            if (looksLikeFishingSubmenuText(normalizeText(screen.title.string))) return true
            if (screen !is AbstractContainerScreen<*>) return false
            val menu = player.containerMenu ?: return false
            return menu.slots.any { slot ->
                val stack = slot.item
                !stack.isEmpty && (
                    looksLikeFishingSubmenuText(normalizeText(stack.hoverName.string)) ||
                        looksLikeFishingSubmenuText(normalizeText(stack.toString()))
                    )
            }
        }

        private fun ambientFishingAreaDetected(client: Minecraft): Boolean = tabFooterText(client).contains("fishtance")

        private fun hasTargetFishtanceFooter(client: Minecraft, config: FishingModConfig): Boolean {
            val footer = tabFooterText(client)
            return footer.contains("fishtance") && matchesAnyKeyword(footer, config.keywordsForTargetInstance())
        }

        private fun hasMatchingInstanceText(client: Minecraft, config: FishingModConfig): Boolean {
            val targetKeywords = config.keywordsForTargetInstance()
            return matchesAnyKeyword(tabHeaderText(client), targetKeywords) ||
                matchesAnyKeyword(tabFooterText(client), targetKeywords)
        }

        private fun hasTabText(client: Minecraft): Boolean =
            tabHeaderText(client).isNotBlank() || tabFooterText(client).isNotBlank()

        private fun tabHeaderText(client: Minecraft): String = tabComponentText(client, header = true)

        private fun tabFooterText(client: Minecraft): String = tabComponentText(client, header = false)

        private fun tabComponentText(client: Minecraft, header: Boolean): String {
            val gui = client.gui ?: return ""
            val tabList: Any = gui.hud.tabList ?: return ""
            val accessor = tabList as? PlayerTabOverlayAccessor ?: return ""
            return componentText(if (header) accessor.`fishtance$getHeader`() else accessor.`fishtance$getFooter`())
        }

        private fun componentText(component: Component?): String =
            if (component == null) "" else normalizeText(component.string)

        private fun looksLikeFishingSubmenuText(text: String?): Boolean {
            if (text.isNullOrBlank()) return false
            val normalized = normalizeText(text)
            return normalized.contains("fishing islands") ||
                normalized.contains("click to view islands") ||
                normalized.contains("journey across the fishing islands")
        }

        private fun isInternalScreen(screen: Screen): Boolean {
            val title = normalizeText(screen.title.string)
            return title == "mods" || title.contains("mcci tweaks")
        }

        private fun stackMatchesKeywords(stack: ItemStack?, keywords: List<String>?): Boolean {
            if (stack == null || stack.isEmpty) return false
            val haystack = normalizeText(stack.hoverName.string) + " " + normalizeText(stack.toString())
            return matchesAnyKeyword(haystack, keywords)
        }

        private fun matchesAnyKeyword(text: String?, keywords: List<String>?): Boolean {
            if (text.isNullOrBlank() || keywords == null) return false
            val normalized = normalizeText(text)
            return keywords.any { keyword ->
                val cleanKeyword = normalizeText(keyword)
                cleanKeyword.isNotBlank() && normalized.contains(cleanKeyword)
            }
        }

        private fun serverMatches(config: FishingModConfig, client: Minecraft): Boolean {
            val targetHost = config.targetServerHost
            if (targetHost.isNullOrBlank()) return true
            val ip = client.currentServer?.ip ?: return false
            val host = normalizeServerHost(ip)
            val target = normalizeServerHost(targetHost)
            return host == target || host.endsWith(".$target") || host.contains(target)
        }

        private fun serverKey(client: Minecraft): String {
            val server = client.currentServer ?: return "singleplayer"
            return normalizeServerHost(server.ip)
        }

        private fun normalizeServerHost(value: String?): String =
            (value ?: "").lowercase(Locale.ROOT).trim().substringBefore('/').substringBefore(':')

        private fun normalizeText(value: String?): String = value?.lowercase(Locale.ROOT)?.trim() ?: ""
    }
}
