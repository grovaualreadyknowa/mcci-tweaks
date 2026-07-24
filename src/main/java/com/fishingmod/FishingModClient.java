package com.fishingmod;

import com.fishingmod.mixin.PlayerTabOverlayAccessor;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.List;
import java.util.Locale;

public final class FishingModClient implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long TARGET_FOOTER_STABLE_MS = 3000L;

    private static volatile FishingModConfig config;
    private final FishingAutomationState state = new FishingAutomationState();

    @Override
    public void onInitializeClient() {
        config = FishingModConfigStore.loadConfig();
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        LOGGER.info("Fishtance loaded. Target server={}, target instance={}", config.targetServerHost, config.targetInstance());
    }

    private void onClientTick(Minecraft client) {
        if (client == null) {
            return;
        }
        if (config == null) {
            config = FishingModConfigStore.loadConfig();
        }
        state.tick(client, config);
    }

    public static void reloadConfigFromDisk() {
        config = FishingModConfigStore.loadConfig();
    }

    private static final class FishingAutomationState {
        private String lastServerKey = "";
        private Screen lastScreen = null;
        private boolean fishingDestinationDetected = false;
        private boolean navigatorScreenWasOpen = false;
        private boolean awaitingJoinCommand = false;
        private boolean awaitingAmbientFishingJoin = false;
        private boolean lastKnownInTargetInstance = false;
        private boolean cycleStartedInTargetInstance = false;
        private boolean ambientJoinEligibleAfterTargetInstance = false;
        private boolean hadStableTargetFishtanceFooter = false;
        private long navigatorScreenClosedAt = 0L;
        private long lastScanAt = 0L;
        private long targetFishtanceFooterSeenSince = 0L;
        private String lastDebugMessage = "";
        private boolean debugLoggingEnabled = false;

        void tick(Minecraft client, FishingModConfig config) {
            debugLoggingEnabled = config.debugLogging;
            LocalPlayer player = client.player;
            if (player == null || client.level == null) {
                logStep("waiting for player and world to be ready");
                resetTransient();
                lastScreen = client.screen;
                return;
            }

            if (!config.enabled) {
                logStep("automation is disabled in config");
                resetTransient();
                lastScreen = client.screen;
                return;
            }

            if (!Boolean.TRUE.equals(config.teleportEnabled)) {
                logStep("teleport automation is disabled in config");
                resetTransient();
                lastScreen = client.screen;
                return;
            }

            String serverKey = serverKey(client);
            if (!serverMatches(config, client)) {
                logStep("server mismatch: current=" + serverKey + ", target=" + normalizeServerHost(config.targetServerHost));
                if (!serverKey.equals(lastServerKey)) {
                    resetSession();
                    lastServerKey = serverKey;
                } else {
                    resetTransient();
                }
                lastScreen = client.screen;
                return;
            }

            if (!serverKey.equals(lastServerKey)) {
                logStep("new server session: " + serverKey);
                resetSession();
                lastServerKey = serverKey;
            }

            Screen currentScreen = client.screen;
            if (currentScreen != null && isInternalScreen(currentScreen)) {
                if (lastScreen != currentScreen) {
                    logStep("ignoring internal screen: " + currentScreen.getTitle().getString());
                }
                lastScreen = currentScreen;
                return;
            }
            boolean navigatorHeld = holdsNavigator(player, config);
            if (debugLoggingEnabled && currentScreen != lastScreen) {
                String screenClass = currentScreen == null ? "null" : currentScreen.getClass().getSimpleName();
                String screenTitle = currentScreen == null ? "" : currentScreen.getTitle().getString();
                logStep("screen changed: class=" + screenClass + ", title=" + screenTitle + ", navigatorHeld=" + navigatorHeld);
            }
            boolean currentScreenIsNavigator = currentScreen != null && screenLooksLikeNavigator(currentScreen, player, config);
            boolean currentScreenIsFishingDestination = currentScreen != null
                    && screenContainsFishingDestination(currentScreen, player, config);
            boolean alreadyInTargetInstance = isAlreadyInTargetInstance(client, config);
            updateTargetFishtanceFooterState(client, config);
            if (alreadyInTargetInstance) {
                lastKnownInTargetInstance = true;
                if (targetFishtanceFooterStable()) {
                    ambientJoinEligibleAfterTargetInstance = true;
                    hadStableTargetFishtanceFooter = true;
                }
            } else if (currentScreen == null && !awaitingJoinCommand && hasTabText(client)) {
                lastKnownInTargetInstance = false;
            }
            if (debugLoggingEnabled && currentScreenIsFishingDestination) {
                logStep("instance state while fishing screen open: alreadyInTargetInstance=" + alreadyInTargetInstance
                        + ", lastKnownInTargetInstance=" + lastKnownInTargetInstance
                        + ", headerFooterMatch=" + hasMatchingInstanceText(client, config)
                        + ", tabHeader=\"" + tabHeaderText(client) + "\""
                        + ", tabFooter=\"" + tabFooterText(client) + "\"");
            }

            if (currentScreenIsNavigator && shouldRescan(config)) {
                lastScanAt = System.currentTimeMillis();
                logStep("navigator screen detected: " + currentScreen.getTitle().getString());
                if (!navigatorScreenWasOpen) {
                    logStep("navigator opened");
                }
                if (currentScreenIsFishingDestination) {
                    navigatorScreenWasOpen = true;
                    cycleStartedInTargetInstance = lastKnownInTargetInstance || alreadyInTargetInstance;
                    if (cycleStartedInTargetInstance) {
                        logStep("fishing submenu detected but already in target instance; skipping join");
                        clearCycle();
                    } else {
                        fishingDestinationDetected = true;
                        logStep("fishing submenu detected");
                    }
                }
            } else if (currentScreenIsFishingDestination && shouldRescan(config)) {
                lastScanAt = System.currentTimeMillis();
                navigatorScreenWasOpen = true;
                cycleStartedInTargetInstance = lastKnownInTargetInstance || alreadyInTargetInstance;
                if (cycleStartedInTargetInstance) {
                    logStep("fishing submenu detected but already in target instance; skipping join");
                    clearCycle();
                } else {
                    fishingDestinationDetected = true;
                    logStep("fishing submenu detected");
                }
            } else if (currentScreen != null && navigatorHeld && !fishingDestinationDetected) {
                logStep("screen is open but it does not look like a navigator yet: " + currentScreen.getTitle().getString());
            }

            if (lastScreen != null && currentScreen == null && navigatorScreenWasOpen && fishingDestinationDetected) {
                awaitingJoinCommand = true;
                awaitingAmbientFishingJoin = false;
                navigatorScreenClosedAt = System.currentTimeMillis();
                logStep("navigator closed; waiting " + config.postTeleportDelayMs + "ms before joining");
            }

            if (currentScreen == null
                    && !awaitingJoinCommand
                    && shouldRescan(config)
                    && !alreadyInTargetInstance
                    && !lastKnownInTargetInstance
                    && ambientJoinEligibleAfterTargetInstance
                    && hadStableTargetFishtanceFooter
                    && ambientFishingAreaDetected(client, config)) {
                lastScanAt = System.currentTimeMillis();
                awaitingJoinCommand = true;
                awaitingAmbientFishingJoin = true;
                ambientJoinEligibleAfterTargetInstance = false;
                hadStableTargetFishtanceFooter = false;
                navigatorScreenClosedAt = System.currentTimeMillis();
                logStep("fishing area detected without navigator; waiting " + config.postTeleportDelayMs + "ms before joining");
            }

            if (currentScreenIsNavigator) {
                navigatorScreenWasOpen = true;
            }

            if (currentScreen == null && awaitingJoinCommand) {
                long elapsed = System.currentTimeMillis() - navigatorScreenClosedAt;
                if (elapsed >= config.postTeleportDelayMs) {
                    if (cycleStartedInTargetInstance || isAlreadyInTargetInstance(client, config)) {
                        logStep("arrived in target instance; skipping join command");
                    } else {
                        logStep("sending join command"
                                + (awaitingAmbientFishingJoin ? " after ambient fishing detection" : "")
                                + ": " + config.commandForTargetInstance());
                        sendJoinCommand(player, config);
                    }
                    clearCycle();
                }
            }

            if (currentScreen != null && !currentScreenIsNavigator && !fishingDestinationDetected) {
                navigatorScreenWasOpen = false;
            }

            lastScreen = currentScreen;
        }

        private boolean shouldRescan(FishingModConfig config) {
            return System.currentTimeMillis() - lastScanAt >= config.scanCooldownMs;
        }

        private void sendJoinCommand(LocalPlayer player, FishingModConfig config) {
            String command = config.commandForTargetInstance();
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            if (command.isBlank() || player.connection == null) {
                return;
            }
            player.connection.sendCommand(command);
            LOGGER.info("Fishtance sent /{}", command);
        }

        private void clearCycle() {
            fishingDestinationDetected = false;
            navigatorScreenWasOpen = false;
            awaitingJoinCommand = false;
            awaitingAmbientFishingJoin = false;
            cycleStartedInTargetInstance = false;
            navigatorScreenClosedAt = 0L;
            lastScanAt = 0L;
        }

        private void resetTransient() {
            clearCycle();
            lastScreen = null;
            lastDebugMessage = "";
        }

        private void resetSession() {
            resetTransient();
            lastScanAt = 0L;
            lastServerKey = "";
            lastKnownInTargetInstance = false;
            ambientJoinEligibleAfterTargetInstance = false;
            hadStableTargetFishtanceFooter = false;
            targetFishtanceFooterSeenSince = 0L;
        }

        private void logStep(String message) {
            if (debugLoggingEnabled && !message.equals(lastDebugMessage)) {
                LOGGER.info("[fishtance-debug] {}", message);
                lastDebugMessage = message;
            }
        }


        private void updateTargetFishtanceFooterState(Minecraft client, FishingModConfig config) {
            if (hasTargetFishtanceFooter(client, config)) {
                if (targetFishtanceFooterSeenSince == 0L) {
                    targetFishtanceFooterSeenSince = System.currentTimeMillis();
                }
            } else {
                targetFishtanceFooterSeenSince = 0L;
            }
        }

        private boolean targetFishtanceFooterStable() {
            return targetFishtanceFooterSeenSince > 0L
                    && System.currentTimeMillis() - targetFishtanceFooterSeenSince >= TARGET_FOOTER_STABLE_MS;
        }
    }

    private static boolean holdsNavigator(LocalPlayer player, FishingModConfig config) {
        return stackMatchesKeywords(player.getMainHandItem(), config.navigatorKeywords)
                || stackMatchesKeywords(player.getOffhandItem(), config.navigatorKeywords);
    }

    private static boolean screenLooksLikeNavigator(Screen screen, LocalPlayer player, FishingModConfig config) {
        String title = normalizeText(screen.getTitle().getString());
        if (matchesAnyKeyword(title, config.navigatorKeywords)) {
            return true;
        }
        if (!(screen instanceof AbstractContainerScreen<?>)) {
            return false;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) {
            return false;
        }
        for (Slot slot : menu.slots) {
            if (stackMatchesKeywords(slot.getItem(), config.navigatorKeywords)) {
                return true;
            }
        }
        return false;
    }

    private static boolean screenContainsFishingDestination(Screen screen, LocalPlayer player, FishingModConfig config) {
        String title = normalizeText(screen.getTitle().getString());
        if (looksLikeFishingSubmenuText(title)) {
            return true;
        }

        if (!(screen instanceof AbstractContainerScreen<?>)) {
            return false;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) {
            return false;
        }

        for (Slot slot : menu.slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            if (looksLikeFishingSubmenuText(normalizeText(stack.getHoverName().getString()))
                    || looksLikeFishingSubmenuText(normalizeText(stack.toString()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean ambientFishingAreaDetected(Minecraft client, FishingModConfig config) {
        String footer = tabFooterText(client);
        return footer.contains("fishtance");
    }

    private static boolean hasTargetFishtanceFooter(Minecraft client, FishingModConfig config) {
        String footer = tabFooterText(client);
        return footer.contains("fishtance")
                && matchesAnyKeyword(footer, config.keywordsForTargetInstance());
    }

    private static boolean isAlreadyInTargetInstance(Minecraft client, FishingModConfig config) {
        return hasMatchingInstanceText(client, config);
    }

    private static boolean hasMatchingInstanceText(Minecraft client, FishingModConfig config) {
        String header = tabHeaderText(client);
        String footer = tabFooterText(client);
        List<String> targetKeywords = config.keywordsForTargetInstance();
        if (matchesAnyKeyword(header, targetKeywords)
                || matchesAnyKeyword(footer, targetKeywords)) {
            return true;
        }
        return false;
    }

    private static boolean hasTabText(Minecraft client) {
        return !tabHeaderText(client).isBlank() || !tabFooterText(client).isBlank();
    }

    private static String tabHeaderText(Minecraft client) {
        return tabComponentText(client, true);
    }

    private static String tabFooterText(Minecraft client) {
        return tabComponentText(client, false);
    }

    private static String tabComponentText(Minecraft client, boolean header) {
        if (client.gui == null) {
            return "";
        }
        PlayerTabOverlay tabList = client.gui.getTabList();
        if (tabList == null) {
            return "";
        }
        if (!(tabList instanceof PlayerTabOverlayAccessor accessor)) {
            return "";
        }
        return componentText(header ? accessor.fishtance$getHeader() : accessor.fishtance$getFooter());
    }

    private static String componentText(Component component) {
        return component == null ? "" : normalizeText(component.getString());
    }

    private static boolean looksLikeFishingText(String text, FishingModConfig config) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = normalizeText(text);
        return matchesAnyKeyword(normalized, config.fishingDestinationKeywords)
                || normalized.contains("fishing islands")
                || normalized.contains("click to view islands")
                || normalized.contains("view islands")
                || normalized.contains("journey across the fishing islands");
    }

    private static boolean looksLikeFishingSubmenuText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = normalizeText(text);
        return normalized.contains("fishing islands")
                || normalized.contains("click to view islands")
                || normalized.contains("journey across the fishing islands");
    }

    private static boolean isInternalScreen(Screen screen) {
        String title = normalizeText(screen.getTitle().getString());
        return title.equals("mods") || title.contains("mcci tweaks");
    }

    private static boolean stackMatchesKeywords(ItemStack stack, List<String> keywords) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        StringBuilder haystack = new StringBuilder();
        haystack.append(normalizeText(stack.getHoverName().getString())).append(' ');
        haystack.append(normalizeText(stack.toString()));
        return matchesAnyKeyword(haystack.toString(), keywords);
    }

    private static boolean matchesAnyKeyword(String text, List<String> keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = normalizeText(text);
        for (String keyword : keywords) {
            String cleanKeyword = normalizeText(keyword);
            if (!cleanKeyword.isBlank() && normalized.contains(cleanKeyword)) {
                return true;
            }
        }
        return false;
    }

    private static boolean serverMatches(FishingModConfig config, Minecraft client) {
        if (config.targetServerHost.isBlank()) {
            return true;
        }
        ServerData server = client.getCurrentServer();
        if (server == null || server.ip == null) {
            return false;
        }
        String host = normalizeServerHost(server.ip);
        String target = normalizeServerHost(config.targetServerHost);
        return host.equals(target) || host.endsWith("." + target) || host.contains(target);
    }

    private static String serverKey(Minecraft client) {
        ServerData server = client.getCurrentServer();
        if (server == null) {
            return "singleplayer";
        }
        return normalizeServerHost(server.ip);
    }

    private static String normalizeServerHost(String value) {
        String host = value.toLowerCase(Locale.ROOT).trim();
        int slash = host.indexOf('/');
        if (slash >= 0) {
            host = host.substring(0, slash);
        }
        int colon = host.indexOf(':');
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        return host;
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }
}
