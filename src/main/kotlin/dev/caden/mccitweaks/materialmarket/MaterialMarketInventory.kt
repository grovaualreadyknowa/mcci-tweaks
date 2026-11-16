package dev.caden.mccitweaks.materialmarket

import com.mojang.blaze3d.platform.InputConstants
import dev.caden.mccitweaks.McciTweaksConfigStore
import dev.caden.mccitweaks.McciTweaksMod
import java.time.Instant
import java.util.Locale
import java.util.function.Consumer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

object MaterialMarketInventory {
    private val COST_PATTERN = Regex("""Cost:\s*([\d,]+)/([\d,]+)\s+\[(.+)]""")
    private val RECEIVE_PATTERN = Regex("""You receive:\s*(?:\[(.+?)]|(.+?))(?:\s*x([\d,]+))?\s*$""")
    private val MATERIAL_COUNT_PATTERN = Regex("""\[(.+?)]\s*\[([\d,]+)/([\d,]+)]""")

    private const val SCAN_BATCH_SIZE = 12
    private const val SCAN_TICKS_AFTER_OPEN = 80

    private var pendingPurchase = false
    private val BLUEPRINT_CRAFT = BlueprintCraftTransaction()
    private var activeScanScreen: ContainerScreen? = null
    private var scanTicksRemaining = 0
    private var scanCursor = 0

    @JvmStatic
    fun register() {
        MaterialMarketInventoryStore.load()
        MaterialMarketApiSync.register()
        ClientTickEvents.END_CLIENT_TICK.register { client -> handleTick(client) }
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            ScreenMouseEvents.beforeMouseClick(screen).register { clickedScreen, event ->
                onScreenMouseClick(clickedScreen, event)
            }
            if (BLUEPRINT_CRAFT.hasSelection()) {
                val controls = Screens.getWidgets(screen)
                    .map { widget -> "${widget.javaClass.simpleName}='${widget.message.string}'" }
                    .toString()
                val containerInfo = if (screen is ContainerScreen) {
                    ", containerId=${screen.menu.containerId}, slots=${describeSlots(screen)}"
                } else {
                    ""
                }
                McciTweaksMod.LOGGER.info(
                    "Blueprint material tracking opened candidate screen {} titled '{}' with controls {}{}.",
                    screen.javaClass.name,
                    screen.title.string,
                    controls,
                    containerInfo
                )
            }
        }
        ClientReceiveMessageEvents.ALLOW_GAME.register { message, _ ->
            handleChatMessage(message.string)
            true
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register { MaterialMarketInventoryStore.save() }
    }

    @JvmStatic
    fun onScreenOpened(screen: ContainerScreen) {
        if (!isEnabled() || !isOnMccServer()) return

        if (McciTweaksConfigStore.get().materialMarket.usesApiInventory()) {
            if (isMaterialMarket(screen)) {
                MaterialMarketApiSync.requestAutomatic(Minecraft.getInstance())
            }
            return
        }

        if (!isScannableInventory(screen)) return
        syncFromScreen(screen)
        activeScanScreen = screen
        scanTicksRemaining = SCAN_TICKS_AFTER_OPEN
        scanCursor = 0
    }

    @JvmStatic
    fun onScreenClosed(screen: ContainerScreen) {
        if (activeScanScreen === screen) {
            stopBatchedScan()
        }
    }

    @JvmStatic
    fun onScreenClick(screen: ContainerScreen, clickedSlot: Slot?, event: MouseButtonEvent) {
        if (!isEnabled() || !isOnMccServer()) return

        if (BLUEPRINT_CRAFT.hasSelection()) {
            val clickedItem = clickedSlot?.item
            McciTweaksMod.LOGGER.info(
                "Blueprint material tracking pending click: title='{}', containerId={}, button={}, x={}, y={}, slot={}, item='{}'.",
                screen.title.string,
                screen.menu.containerId,
                event.button(),
                event.x(),
                event.y(),
                clickedSlot?.let { screen.menu.slots.indexOf(it).toString() } ?: "none",
                if (clickedItem == null || clickedItem.isEmpty) "empty" else clickedItem.hoverName.string
            )
        }
        handleBlueprintClick(screen, clickedSlot, event)
        if (!usesTraditionalInventory() || !isMaterialMarket(screen)) return
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT && event.button() != InputConstants.MOUSE_BUTTON_RIGHT) return

        val item = clickedSlot?.item
        val cost = item?.let(::parseCost)
        if (item == null || cost == null) {
            if (pendingPurchase && isCancelClick(clickedSlot, item, event)) {
                pendingPurchase = false
            }
            return
        }

        if (cost.unitCost > 0 && cost.availableBalance >= cost.unitCost) {
            pendingPurchase = true
        }
    }

    private fun onScreenMouseClick(screen: Screen, event: MouseButtonEvent) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !BLUEPRINT_CRAFT.hasSelection()) return
        val widget = Screens.getWidgets(screen).firstOrNull { it.isMouseOver(event.x(), event.y()) } ?: return
        onBlueprintDialogButton(widget.message)
    }

    /** Called after a server packet has updated a container, never from the optimistic client click. */
    @JvmStatic
    fun onServerContainerUpdated(containerId: Int) {
        val confirmed = BLUEPRINT_CRAFT.acknowledgeContainerUpdate(containerId)
        if (confirmed.isEmpty() || !isEnabled() || !isOnMccServer()) return
        applyConfirmedBlueprintCraft(confirmed)
    }

    /** Handles the labeled buttons in MCC's server-driven Blueprint dialog. */
    @JvmStatic
    fun onBlueprintDialogButton(label: Component) {
        if (!isEnabled() || !isOnMccServer() || !BLUEPRINT_CRAFT.hasSelection()) return

        val action = blueprintDialogButtonAction(label.string)
        if (action == BlueprintConfirmationAction.NONE) return

        val screen = Minecraft.getInstance().gui.screen()
        McciTweaksMod.LOGGER.info(
            "Blueprint material tracking observed '{}' button on {}.",
            label.string,
            screen?.javaClass?.name ?: "no screen"
        )
        when (action) {
            BlueprintConfirmationAction.CANCEL -> {
                McciTweaksMod.LOGGER.info("Blueprint material tracking cancelled pending assembly.")
                BLUEPRINT_CRAFT.cancel()
            }
            BlueprintConfirmationAction.CONFIRM -> {
                val confirmed = BLUEPRINT_CRAFT.confirmDialog()
                McciTweaksMod.LOGGER.info(
                    "Blueprint material tracking confirmed assembly with {} material requirements.",
                    confirmed.size
                )
                applyConfirmedBlueprintCraft(confirmed)
            }
            BlueprintConfirmationAction.NONE -> Unit
        }
    }

    @JvmStatic
    fun modifyTooltip(item: ItemStack, consumer: Consumer<Component>) {
        if (!isEnabled() || !isOnMccServer()) return

        if (usesTraditionalInventory()) {
            syncFromFusionForgeTooltip(item)
        }
        syncFromBlueprintTooltip(item)
        if (!isMaterialMarket(Minecraft.getInstance().gui.screen()) || parseCost(item) == null) return

        consumer.accept(Component.empty())
        val boughtName = item.hoverName.string
        val owned = findEntry(boughtName)
        if (owned == null) {
            val guidance = if (usesTraditionalInventory()) {
                "MCCI Tweaks: open the Recipe Book or Fusion Forge to scan material counts."
            } else {
                "MCCI Tweaks: no API count cached; open settings and sync."
            }
            consumer.accept(Component.literal(guidance).withStyle(ChatFormatting.GRAY))
            return
        }

        consumer.accept(
            Component.literal("Owned: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("${format(owned.amount)} $boughtName").withStyle(ChatFormatting.WHITE))
        )
    }

    @JvmStatic
    fun clearLocalData() {
        currentPlayerId()?.let(MaterialMarketInventoryStore::clear)
        pendingPurchase = false
        BLUEPRINT_CRAFT.cancel()
    }

    @JvmStatic
    fun trackedMaterialCount(): Int {
        val playerId = currentPlayerId() ?: return 0
        return MaterialMarketInventoryStore.inventoryFor(playerId).size
    }

    internal fun handleChatMessage(message: String) {
        if (!isEnabled() || !usesTraditionalInventory() || !isOnMccServer()) return

        val match = RECEIVE_PATTERN.find(message) ?: return
        val receivedName = firstNonBlank(match.groupValues[1], match.groupValues[2])
        val quantityText = match.groupValues[3]
        val receivedQuantity = if (quantityText.isBlank()) 1L else parseLong(quantityText)
        if (receivedQuantity > 0) {
            McciTweaksMod.LOGGER.debug("Material Market inventory received {} x{}", receivedName, receivedQuantity)
            adjustAmount(normalize(receivedName), receivedName, receivedQuantity)
            MaterialMarketInventoryStore.save()
        }
        pendingPurchase = false
    }

    private fun handleTick(client: Minecraft) {
        if (!isEnabled() || !usesTraditionalInventory()) {
            stopBatchedScan()
            return
        }

        val screen = activeScanScreen ?: return
        if (client.gui.screen() !== screen || !isOnMccServer() || !isScannableInventory(screen)) {
            stopBatchedScan()
            return
        }
        if (scanTicksRemaining-- <= 0) {
            stopBatchedScan()
            return
        }

        val slots = screen.menu.slots
        if (slots.isEmpty()) return

        val scanned = ArrayList<ScannedMaterial>()
        repeat(minOf(SCAN_BATCH_SIZE, slots.size)) {
            val item = slots[scanCursor % slots.size].item
            scanCursor = (scanCursor + 1) % slots.size
            if (!item.isEmpty) {
                scanned += scanItem(item)
            }
        }
        if (scanned.isNotEmpty()) {
            upsert(scanned)
        }
    }

    private fun stopBatchedScan() {
        activeScanScreen = null
        scanTicksRemaining = 0
        scanCursor = 0
    }

    private fun describeSlots(screen: ContainerScreen): String =
        screen.menu.slots
            .mapIndexedNotNull { index, slot ->
                val item = slot.item
                if (item.isEmpty) null else "$index='${item.hoverName.string}'"
            }
            .toString()

    private fun syncFromScreen(screen: ContainerScreen) {
        val scanned = screen.menu.slots
            .map { it.item }
            .filter { !it.isEmpty }
            .flatMap(::scanItem)
        if (scanned.isNotEmpty()) {
            upsert(scanned)
        }
    }

    private fun syncFromFusionForgeTooltip(item: ItemStack) {
        if (!isFusionForge(Minecraft.getInstance().gui.screen())) return
        val scanned = scanMaterialCountLore(item)
        if (scanned.isNotEmpty()) upsert(scanned)
    }

    private fun syncFromBlueprintTooltip(item: ItemStack) {
        if (!isBlueprintRelatedScreen(Minecraft.getInstance().gui.screen())) return
        val scanned = scanMaterialCountLore(item)
        if (scanned.isNotEmpty()) upsert(scanned)
    }

    private fun scanItem(item: ItemStack): List<ScannedMaterial> =
        listOfNotNull(scanCustomAmount(item)) + scanMaterialCountLore(item)

    private fun scanCustomAmount(item: ItemStack): ScannedMaterial? {
        val customData = item.get(DataComponents.CUSTOM_DATA) ?: return null
        val amount = parseCustomAmount(customData.copyTag()) ?: return null
        val displayName = item.hoverName.string
        if (displayName.isBlank()) return null
        return ScannedMaterial(normalize(displayName), displayName, amount)
    }

    private fun parseCustomAmount(tag: CompoundTag): Long? {
        val values = tag.getCompound("PublicBukkitValues").orElse(null) ?: return null
        return values.getInt("mcc:amount").orElse(null)?.toLong()
    }

    private fun scanMaterialCountLore(item: ItemStack): List<ScannedMaterial> =
        parseBlueprintMaterials(item).map { ScannedMaterial(it.key, it.displayName, it.owned) }

    internal fun parseBlueprintMaterials(item: ItemStack): List<BlueprintCraftTransaction.Material> {
        val lore = item.get(DataComponents.LORE) ?: return emptyList()
        return parseBlueprintMaterialLines(lore.styledLines())
    }

    @JvmStatic
    internal fun parseBlueprintMaterialLines(lines: List<Component>): List<BlueprintCraftTransaction.Material> =
        lines.mapNotNull { line ->
            MATERIAL_COUNT_PATTERN.find(line.string)?.let { match ->
                val displayName = match.groupValues[1]
                BlueprintCraftTransaction.Material(
                    normalize(displayName),
                    displayName,
                    parseLong(match.groupValues[2]),
                    parseLong(match.groupValues[3])
                )
            }
        }

    private fun parseCost(item: ItemStack): Cost? {
        val lore = item.get(DataComponents.LORE) ?: return null
        for (line in lore.styledLines()) {
            val match = COST_PATTERN.find(line.string) ?: continue
            return Cost(parseLong(match.groupValues[1]), parseLong(match.groupValues[2]))
        }
        return null
    }

    private fun isCancelClick(clickedSlot: Slot?, clickedItem: ItemStack?, event: MouseButtonEvent): Boolean {
        if (isNamedAction(clickedItem, "CANCEL")) return true
        if (clickedSlot != null) return false

        val window = Minecraft.getInstance().window
        val panelLeft = (window.guiScaledWidth - 350) / 2
        val panelTop = (window.guiScaledHeight - 188) / 2
        val buttonTop = panelTop + 118
        val buttonBottom = panelTop + 160
        return event.y() >= buttonTop &&
            event.y() <= buttonBottom &&
            event.x() >= panelLeft + 193 &&
            event.x() <= panelLeft + 303
    }

    private fun handleBlueprintClick(screen: ContainerScreen, clickedSlot: Slot?, event: MouseButtonEvent) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return

        val slotIndex = clickedSlot?.let { screen.menu.slots.indexOf(it) } ?: -1
        val containerAction = blueprintContainerAction(screen.title.string, slotIndex)
        if (BLUEPRINT_CRAFT.hasSelection() && containerAction != BlueprintConfirmationAction.NONE) {
            when (containerAction) {
                BlueprintConfirmationAction.CONFIRM -> {
                    val confirmed = BLUEPRINT_CRAFT.confirmDialog()
                    McciTweaksMod.LOGGER.info(
                        "Blueprint material tracking confirmed assembly from container slot {} with {} material requirements.",
                        slotIndex,
                        confirmed.size
                    )
                    applyConfirmedBlueprintCraft(confirmed)
                }
                BlueprintConfirmationAction.CANCEL -> {
                    BLUEPRINT_CRAFT.cancel()
                    McciTweaksMod.LOGGER.info("Blueprint material tracking cancelled assembly from container slot {}.", slotIndex)
                }
                BlueprintConfirmationAction.NONE -> Unit
            }
            return
        }

        val item = clickedSlot?.item
        if (BLUEPRINT_CRAFT.hasSelection() && isNamedAction(item, "CANCEL")) {
            BLUEPRINT_CRAFT.cancel()
            return
        }
        if (BLUEPRINT_CRAFT.hasSelection() && isNamedAction(item, "CONFIRM")) {
            BLUEPRINT_CRAFT.confirm(screen.menu.containerId)
            return
        }
        if (item == null || !isBlueprintRelatedScreen(screen)) return

        val materials = parseBlueprintMaterials(item)
        if (materials.isNotEmpty() && materials.all { it.isAffordable() }) {
            BLUEPRINT_CRAFT.select(materials, screen.menu.containerId)
            McciTweaksMod.LOGGER.info(
                "Blueprint material tracking captured {} requirements from '{}'.",
                materials.size,
                item.hoverName.string
            )
        }
    }

    private fun isNamedAction(item: ItemStack?, action: String): Boolean =
        item != null && item.hoverName.string.uppercase(Locale.ROOT).contains(action)

    @JvmStatic
    internal fun blueprintDialogButtonAction(label: String): BlueprintConfirmationAction {
        val normalized = label.uppercase(Locale.ROOT)
        return when {
            normalized.contains("CONFIRM") -> BlueprintConfirmationAction.CONFIRM
            normalized.contains("CANCEL") -> BlueprintConfirmationAction.CANCEL
            else -> BlueprintConfirmationAction.NONE
        }
    }

    @JvmStatic
    internal fun blueprintContainerAction(title: String, slotIndex: Int): BlueprintConfirmationAction {
        if (!title.uppercase(Locale.ROOT).contains("ASSEMBLE THIS BLUEPRINT")) return BlueprintConfirmationAction.NONE
        return when (slotIndex) {
            48 -> BlueprintConfirmationAction.CONFIRM
            50 -> BlueprintConfirmationAction.CANCEL
            else -> BlueprintConfirmationAction.NONE
        }
    }

    private fun containerTitle(screen: Any?): String? =
        (screen as? ContainerScreen)?.title?.string?.uppercase(Locale.ROOT)

    private fun isMaterialMarket(screen: Any?): Boolean = containerTitle(screen)?.contains("MATERIAL MARKET") == true

    private fun isFusionForge(screen: Any?): Boolean = containerTitle(screen)?.contains("FUSION FORGE") == true

    private fun isBlueprintRelatedScreen(screen: Any?): Boolean {
        val title = containerTitle(screen) ?: return false
        return title.contains("INFINITEBAG") || title.contains("INFINIBAG") || title.contains("BLUEPRINT ASSEMBLER")
    }

    private fun isScannableInventory(screen: ContainerScreen): Boolean {
        val title = screen.title.string.uppercase(Locale.ROOT)
        if (title.contains("MATERIAL MARKET")) return false
        return listOf("RECIPE", "INFINITEBAG", "INFINIBAG", "BLUEPRINT ASSEMBLER", "FUSION FORGE", "CRAFT")
            .any(title::contains)
    }

    private fun isEnabled(): Boolean = McciTweaksConfigStore.get().materialMarket.enabled

    internal fun usesTraditionalInventory(): Boolean =
        McciTweaksConfigStore.get().materialMarket.usesTraditionalInventory()

    private fun isOnMccServer(): Boolean =
        Minecraft.getInstance().currentServer?.ip?.lowercase(Locale.ROOT)?.contains("mccisland.net") == true

    private fun materialInventory(): Map<String, MaterialMarketInventoryStore.MaterialInventoryEntry> {
        val playerId = currentPlayerId() ?: return emptyMap()
        return MaterialMarketInventoryStore.inventoryFor(playerId)
    }

    private fun currentPlayerId(): String? = Minecraft.getInstance().gameProfile?.id()?.toString()

    private fun findEntry(name: String): MaterialMarketInventoryStore.MaterialInventoryEntry? {
        val inventory = materialInventory()
        val normalized = normalize(name)
        inventory[normalized]?.let { return it }

        val compact = normalized.replace(" ", "")
        return inventory.entries.firstOrNull { (assetName, _) ->
            val assetCompact = assetName.replace(" ", "")
            assetName.contains(normalized) ||
                normalized.contains(assetName) ||
                assetCompact.contains(compact) ||
                compact.contains(assetCompact)
        }?.value
    }

    private fun upsert(materials: List<ScannedMaterial>) {
        val playerId = currentPlayerId() ?: return
        val now = Instant.now().toEpochMilli()
        val inventory = MaterialMarketInventoryStore.inventoryFor(playerId)
        var changed = false

        for (material in materials) {
            val amount = maxOf(0L, material.amount)
            val existing = inventory[material.key]
            if (existing != null && existing.displayName == material.displayName && existing.amount == amount) continue

            inventory[material.key] = MaterialMarketInventoryStore.MaterialInventoryEntry(material.displayName, amount, now)
            changed = true
        }

        if (changed) MaterialMarketInventoryStore.save()
    }

    private fun adjustAmount(key: String, displayName: String, delta: Long) {
        val playerId = currentPlayerId() ?: return
        val inventory = MaterialMarketInventoryStore.inventoryFor(playerId)
        val existing = inventory[key]
        inventory[key] = MaterialMarketInventoryStore.MaterialInventoryEntry(
            existing?.displayName ?: displayName,
            maxOf(0L, (existing?.amount ?: 0L) + delta),
            Instant.now().toEpochMilli()
        )
    }

    private fun applyConfirmedBlueprintCraft(materials: List<BlueprintCraftTransaction.Material>) {
        val playerId = currentPlayerId() ?: return
        val now = Instant.now().toEpochMilli()
        val inventory = MaterialMarketInventoryStore.inventoryFor(playerId)
        var changed = false
        for (material in materials) {
            val amount = maxOf(0L, material.owned - material.required)
            val existing = inventory[material.key]
            val displayName = existing?.displayName ?: material.displayName
            if (existing != null && existing.amount == amount && existing.displayName == displayName) continue
            inventory[material.key] = MaterialMarketInventoryStore.MaterialInventoryEntry(displayName, amount, now)
            changed = true
        }
        if (changed) MaterialMarketInventoryStore.save()
    }

    private fun parseLong(value: String): Long = value.replace(",", "").toLongOrNull() ?: 0L

    private fun normalize(value: String): String = MaterialMarketApiClient.normalize(value)

    private fun firstNonBlank(first: String?, second: String): String =
        if (!first.isNullOrBlank()) first else second

    private fun format(value: Long): String = "%,d".format(value)

    private data class Cost(val availableBalance: Long, val unitCost: Long)

    private data class ScannedMaterial(val key: String, val displayName: String, val amount: Long)

    internal enum class BlueprintConfirmationAction {
        CONFIRM,
        CANCEL,
        NONE
    }
}
