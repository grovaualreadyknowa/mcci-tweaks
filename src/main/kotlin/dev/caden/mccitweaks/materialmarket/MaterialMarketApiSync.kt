package dev.caden.mccitweaks.materialmarket

import dev.caden.mccitweaks.McciTweaksConfigStore
import java.time.Instant
import java.util.Locale
import java.util.UUID
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft

internal object MaterialMarketApiSync {
    private const val AUTOMATIC_COOLDOWN_MS = 60_000L
    private val LOCK = Any()
    private val API_CLIENT by lazy { MaterialMarketApiClient() }

    @Volatile
    private var syncInProgress = false

    @Volatile
    private var status = StatusSnapshot(DisplayStatus.READY, "Select API mode and enter a key.")

    private var lastAutomaticAttempt = 0L

    fun register() {
        ClientPlayConnectionEvents.JOIN.register(ClientPlayConnectionEvents.Join { _, _, client ->
            requestAutomatic(client)
        })
        ClientPlayConnectionEvents.DISCONNECT.register(ClientPlayConnectionEvents.Disconnect { _, _ ->
            synchronized(LOCK) { lastAutomaticAttempt = 0L }
        })
    }

    fun requestAutomatic(client: Minecraft): Boolean {
        val config = McciTweaksConfigStore.get().materialMarket
        if (!config.enabled || !config.usesApiInventory() || config.apiKey.isBlank() || !isOnMccServer(client)) {
            return false
        }

        val now = System.currentTimeMillis()
        synchronized(LOCK) {
            if (syncInProgress || now - lastAutomaticAttempt < AUTOMATIC_COOLDOWN_MS) return false
            lastAutomaticAttempt = now
        }
        return start(client, config.apiKey)
    }

    fun requestManual(): Boolean {
        val config = McciTweaksConfigStore.get().materialMarket
        val problem = when {
            !config.enabled -> "Enable Material Market Inventory first."
            !config.usesApiInventory() -> "Select API as the inventory source."
            config.apiKey.isBlank() -> "Enter an MCC Island API key."
            else -> null
        }
        if (problem != null) {
            status = StatusSnapshot(DisplayStatus.NOT_CONFIGURED, problem)
            return false
        }
        return start(Minecraft.getInstance(), config.apiKey)
    }

    fun keyChanged() {
        if (!syncInProgress) {
            status = StatusSnapshot(DisplayStatus.READY, "API key changed; sync to test it.")
        }
    }

    fun isSyncInProgress(): Boolean = syncInProgress

    fun status(): StatusSnapshot = status

    private fun start(client: Minecraft, apiKey: String): Boolean {
        val playerId: UUID? = client.gameProfile?.id()
        if (playerId == null) {
            status = StatusSnapshot(DisplayStatus.ERROR, "Minecraft profile UUID is unavailable.")
            return false
        }

        synchronized(LOCK) {
            if (syncInProgress) return false
            syncInProgress = true
        }
        status = StatusSnapshot(DisplayStatus.SYNCING, "Syncing bag + vault...")

        API_CLIENT.fetchInventory(playerId, apiKey).thenAccept { result ->
            try {
                client.execute { finish(playerId, result) }
            } catch (_: RuntimeException) {
                status = StatusSnapshot(DisplayStatus.ERROR, "Could not apply the API response.")
                markFinished()
            }
        }
        return true
    }

    private fun finish(playerId: UUID, result: MaterialMarketApiClient.SyncResult) {
        try {
            status = if (result.successful()) {
                MaterialMarketInventoryStore.replaceFromApi(playerId.toString(), result.inventory, Instant.now().toEpochMilli())
                StatusSnapshot(DisplayStatus.SUCCESS, "Synced ${result.inventory.size} assets from bag + vault.")
            } else {
                failureStatus(result.status)
            }
        } finally {
            markFinished()
        }
    }

    private fun markFinished() {
        synchronized(LOCK) { syncInProgress = false }
    }

    private fun failureStatus(result: MaterialMarketApiClient.Status): StatusSnapshot = when (result) {
        MaterialMarketApiClient.Status.MISSING_KEY ->
            StatusSnapshot(DisplayStatus.NOT_CONFIGURED, "Enter an MCC Island API key.")
        MaterialMarketApiClient.Status.INVALID_KEY ->
            StatusSnapshot(DisplayStatus.ERROR, "The MCC Island API key was rejected.")
        MaterialMarketApiClient.Status.RATE_LIMITED ->
            StatusSnapshot(DisplayStatus.ERROR, "API rate limit reached; try again later.")
        MaterialMarketApiClient.Status.PERMISSION_REQUIRED ->
            StatusSnapshot(DisplayStatus.ERROR, "Enable MCCI's in-game Infinibag API setting.")
        MaterialMarketApiClient.Status.PLAYER_NOT_FOUND ->
            StatusSnapshot(DisplayStatus.ERROR, "MCC Island player data was not found.")
        MaterialMarketApiClient.Status.CONNECTION_ERROR ->
            StatusSnapshot(DisplayStatus.ERROR, "Could not reach the MCC Island API.")
        MaterialMarketApiClient.Status.MALFORMED_RESPONSE ->
            StatusSnapshot(DisplayStatus.ERROR, "The API returned an unreadable response.")
        MaterialMarketApiClient.Status.API_ERROR ->
            StatusSnapshot(DisplayStatus.ERROR, "The MCC Island API returned an error.")
        MaterialMarketApiClient.Status.SUCCESS ->
            StatusSnapshot(DisplayStatus.SUCCESS, "Inventory synced.")
    }

    private fun isOnMccServer(client: Minecraft): Boolean =
        client.currentServer?.ip?.lowercase(Locale.ROOT)?.contains("mccisland.net") == true

    enum class DisplayStatus {
        READY,
        NOT_CONFIGURED,
        SYNCING,
        SUCCESS,
        ERROR
    }

    data class StatusSnapshot(val type: DisplayStatus, val message: String)
}
