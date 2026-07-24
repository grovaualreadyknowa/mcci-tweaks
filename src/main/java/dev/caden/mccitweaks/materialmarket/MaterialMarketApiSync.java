package dev.caden.mccitweaks.materialmarket;

import dev.caden.mccitweaks.McciTweaksConfigStore;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

final class MaterialMarketApiSync {
    private static final long AUTOMATIC_COOLDOWN_MS = 60_000L;
    private static final Object LOCK = new Object();
    private static final MaterialMarketApiClient API_CLIENT = new MaterialMarketApiClient();

    private static volatile boolean syncInProgress;
    private static volatile StatusSnapshot status = new StatusSnapshot(
        DisplayStatus.READY,
        "Select API mode and enter a key."
    );
    private static long lastAutomaticAttempt;

    private MaterialMarketApiSync() {
    }

    static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> requestAutomatic(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            synchronized (LOCK) {
                lastAutomaticAttempt = 0L;
            }
        });
    }

    static boolean requestAutomatic(Minecraft client) {
        MaterialMarketConfig config = McciTweaksConfigStore.get().materialMarket;
        if (!config.enabled || !config.usesApiInventory() || config.apiKey.isBlank() || !isOnMccServer(client)) {
            return false;
        }

        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            if (syncInProgress || now - lastAutomaticAttempt < AUTOMATIC_COOLDOWN_MS) {
                return false;
            }
            lastAutomaticAttempt = now;
        }
        return start(client, config.apiKey);
    }

    static boolean requestManual() {
        MaterialMarketConfig config = McciTweaksConfigStore.get().materialMarket;
        if (!config.enabled) {
            status = new StatusSnapshot(DisplayStatus.NOT_CONFIGURED, "Enable Material Market Inventory first.");
            return false;
        }
        if (!config.usesApiInventory()) {
            status = new StatusSnapshot(DisplayStatus.NOT_CONFIGURED, "Select API as the inventory source.");
            return false;
        }
        if (config.apiKey.isBlank()) {
            status = new StatusSnapshot(DisplayStatus.NOT_CONFIGURED, "Enter an MCC Island API key.");
            return false;
        }
        return start(Minecraft.getInstance(), config.apiKey);
    }

    static void keyChanged() {
        if (!syncInProgress) {
            status = new StatusSnapshot(DisplayStatus.READY, "API key changed; sync to test it.");
        }
    }

    static boolean isSyncInProgress() {
        return syncInProgress;
    }

    static StatusSnapshot status() {
        return status;
    }

    private static boolean start(Minecraft client, String apiKey) {
        UUID playerId = client.getGameProfile() == null ? null : client.getGameProfile().id();
        if (playerId == null) {
            status = new StatusSnapshot(DisplayStatus.ERROR, "Minecraft profile UUID is unavailable.");
            return false;
        }

        synchronized (LOCK) {
            if (syncInProgress) {
                return false;
            }
            syncInProgress = true;
        }
        status = new StatusSnapshot(DisplayStatus.SYNCING, "Syncing bag + vault...");

        API_CLIENT.fetchInventory(playerId, apiKey).thenAccept(result -> {
            try {
                client.execute(() -> finish(playerId, result));
            } catch (RuntimeException ignored) {
                status = new StatusSnapshot(DisplayStatus.ERROR, "Could not apply the API response.");
                markFinished();
            }
        });
        return true;
    }

    private static void finish(UUID playerId, MaterialMarketApiClient.SyncResult result) {
        try {
            if (result.successful()) {
                MaterialMarketInventoryStore.replaceFromApi(
                    playerId.toString(),
                    result.inventory(),
                    Instant.now().toEpochMilli()
                );
                status = new StatusSnapshot(
                    DisplayStatus.SUCCESS,
                    "Synced " + result.inventory().size() + " assets from bag + vault."
                );
            } else {
                status = failureStatus(result.status());
            }
        } finally {
            markFinished();
        }
    }

    private static void markFinished() {
        synchronized (LOCK) {
            syncInProgress = false;
        }
    }

    private static StatusSnapshot failureStatus(MaterialMarketApiClient.Status result) {
        return switch (result) {
            case MISSING_KEY -> new StatusSnapshot(DisplayStatus.NOT_CONFIGURED, "Enter an MCC Island API key.");
            case INVALID_KEY -> new StatusSnapshot(DisplayStatus.ERROR, "The MCC Island API key was rejected.");
            case RATE_LIMITED -> new StatusSnapshot(DisplayStatus.ERROR, "API rate limit reached; try again later.");
            case PERMISSION_REQUIRED -> new StatusSnapshot(
                DisplayStatus.ERROR,
                "Enable MCCI's in-game Infinibag API setting."
            );
            case PLAYER_NOT_FOUND -> new StatusSnapshot(DisplayStatus.ERROR, "MCC Island player data was not found.");
            case CONNECTION_ERROR -> new StatusSnapshot(DisplayStatus.ERROR, "Could not reach the MCC Island API.");
            case MALFORMED_RESPONSE -> new StatusSnapshot(DisplayStatus.ERROR, "The API returned an unreadable response.");
            case API_ERROR -> new StatusSnapshot(DisplayStatus.ERROR, "The MCC Island API returned an error.");
            case SUCCESS -> new StatusSnapshot(DisplayStatus.SUCCESS, "Inventory synced.");
        };
    }

    private static boolean isOnMccServer(Minecraft client) {
        ServerData server = client.getCurrentServer();
        return server != null
            && server.ip != null
            && server.ip.toLowerCase(Locale.ROOT).contains("mccisland.net");
    }

    enum DisplayStatus {
        READY,
        NOT_CONFIGURED,
        SYNCING,
        SUCCESS,
        ERROR
    }

    record StatusSnapshot(DisplayStatus type, String message) {
    }
}
