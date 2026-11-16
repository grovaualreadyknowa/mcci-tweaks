package dev.caden.mccnoflash

import java.util.Locale
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack

class MccNoFlashState private constructor() {
    private enum class ItemType {
        JOURNAL,
        POCKET,
        NAVIGATOR,
        ANGLR_PANEL,
        PROFILE,
        UNKNOWN
    }

    private val captures: MutableMap<Int, SlotCapture> = ConcurrentHashMap()
    private val lastLogBySlot = HashMap<Int, Long>()
    private val lastDecisionBySlot: MutableMap<Int, RenderDecision> = ConcurrentHashMap()
    private val lastDecisionLogBySlot: MutableMap<Int, Long> = ConcurrentHashMap()

    private var config = MccNoFlashConfig()
    private var debugSession = false

    fun loadConfig() {
        config = MccNoFlashConfig.load()
    }

    fun config(): MccNoFlashConfig = config

    fun saveConfig() {
        config.save()
    }

    fun isFeatureEnabled(): Boolean = config.enabled

    fun isDebugEnabled(): Boolean = config.debugLogging || debugSession

    fun isDebugSessionEnabled(): Boolean = debugSession

    fun setDebugSession(enabled: Boolean) {
        debugSession = enabled
    }

    private fun effective(mode: FlashMode?): FlashMode =
        if (config.globalMode != FlashMode.DEFAULT) config.globalMode else mode ?: FlashMode.DEFAULT

    fun getJournalMode(): FlashMode = effective(config.journalMode)

    fun getPocketMode(): FlashMode = effective(config.pocketMode)

    fun getNavigatorMode(): FlashMode = effective(config.navigatorMode)

    fun getAnglrPanelMode(): FlashMode = effective(config.anglrPanelMode)

    fun getProfileMode(): FlashMode = effective(config.profileMode)

    fun isOnMccServer(client: Minecraft?): Boolean {
        val ip = client?.currentServer?.ip ?: return false
        return normalize(ip).contains("mccisland.net")
    }

    private fun determineItemType(stack: ItemStack): ItemType {
        val directType = determineItemTypeWithoutCaptures(stack)
        if (directType != ItemType.UNKNOWN) return directType

        val pocketCapture = captures[POCKET_SLOT]
        if (pocketCapture != null && pocketCapture.matches(stack)) return ItemType.POCKET

        return ItemType.UNKNOWN
    }

    private fun modeFor(type: ItemType): FlashMode = when (type) {
        ItemType.JOURNAL -> getJournalMode()
        ItemType.POCKET -> getPocketMode()
        ItemType.NAVIGATOR -> getNavigatorMode()
        ItemType.ANGLR_PANEL -> getAnglrPanelMode()
        ItemType.PROFILE -> getProfileMode()
        ItemType.UNKNOWN -> FlashMode.DEFAULT
    }

    fun captureRender(slot: Int, stack: ItemStack) {
        if (!isFeatureEnabled() || !shouldCaptureSlot(slot, stack)) return

        val capture = SlotCapture.from(slot, stack)
        val previous = captures.put(slot, capture)

        if (isDebugEnabled()) {
            val now = System.currentTimeMillis()
            val last = lastLogBySlot.getOrDefault(slot, 0L)
            if (capture != previous || now - last >= LOG_COOLDOWN_MS) {
                lastLogBySlot[slot] = now
                MccNoFlashMod.LOGGER.info("Captured slot {} signature: {}", slot + 1, capture.toLogLine())
            }
        }
    }

    fun resolveOverrideItemModel(slot: Int, stack: ItemStack, client: Minecraft?): Optional<Identifier> {
        if (!isFeatureEnabled() || !isOnMccServer(client)) return Optional.empty()

        val currentModel = stack.get(DataComponents.ITEM_MODEL)
        val type = determineItemType(stack)
        if (type == ItemType.UNKNOWN) {
            rememberDecision(
                slot,
                RenderDecision.noOverride(slot, FlashMode.DEFAULT, "<unknown item type>", currentModel, null, null, false)
            )
            return Optional.empty()
        }

        val mode = modeFor(type)
        if (mode == FlashMode.DEFAULT) {
            rememberDecision(slot, RenderDecision.noOverride(slot, mode, "mode is DEFAULT", currentModel, null, null, true))
            return Optional.empty()
        }

        if (!matchesExpectedItem(type, stack)) {
            rememberDecision(
                slot,
                RenderDecision.noOverride(slot, mode, "item signature mismatch for $type", currentModel, null, null, true)
            )
            return Optional.empty()
        }

        val decision = when (mode) {
            FlashMode.STATIC_GLOW -> RenderDecision.override(
                slot, mode, "STATIC_GLOW selected", currentModel, null, staticGlowModelForType(type), true
            )
            FlashMode.NORMAL_NO_FLASH -> resolveNormalOrStatic(slot, type, stack, client)
            else -> RenderDecision.noOverride(slot, mode, "unhandled mode", currentModel, null, null, true)
        }

        rememberDecision(slot, decision)
        return decision.toOptional()
    }

    fun resolveInventoryOverrideItemModel(stack: ItemStack, client: Minecraft?): Optional<Identifier> {
        if (client == null || !isFeatureEnabled() || !isOnMccServer(client)) return Optional.empty()

        val screen = client.gui.screen()
        if (!isSupportedInventoryScreen(screen)) return Optional.empty()

        val type = determineItemType(stack)
        if (type == ItemType.UNKNOWN || !matchesExpectedItem(type, stack)) return Optional.empty()
        if (!isSupportedInventoryTarget(screen, type)) return Optional.empty()

        val mode = modeFor(type)
        if (mode == FlashMode.DEFAULT) return Optional.empty()

        val decisionSlot = inventoryDecisionSlot(type)
        val decision = when (mode) {
            FlashMode.STATIC_GLOW -> RenderDecision.override(
                decisionSlot,
                mode,
                "inventory override: STATIC_GLOW selected",
                stack.get(DataComponents.ITEM_MODEL),
                null,
                staticGlowModelForType(type),
                true
            )
            FlashMode.NORMAL_NO_FLASH -> resolveNormalOrStatic(decisionSlot, type, stack, client)
            else -> RenderDecision.noOverride(
                decisionSlot, mode, "inventory mode default", stack.get(DataComponents.ITEM_MODEL), null, null, true
            )
        }

        rememberDecision(decisionSlot, decision)
        return decision.toOptional()
    }

    fun isSupportedInventoryScreen(screen: Screen?): Boolean {
        if (screen == null) return false
        if (screen is InventoryScreen) return true
        return normalize(screen.title.string).contains("pocket menu")
    }

    private fun isSupportedInventoryTarget(screen: Screen?, type: ItemType): Boolean {
        if (screen is InventoryScreen) {
            return type != ItemType.UNKNOWN
        }
        if (!isSupportedInventoryScreen(screen)) return false
        return type == ItemType.JOURNAL ||
            type == ItemType.NAVIGATOR ||
            type == ItemType.ANGLR_PANEL ||
            type == ItemType.PROFILE
    }

    private fun resolveNormalOrStatic(slot: Int, type: ItemType, stack: ItemStack, client: Minecraft?): RenderDecision {
        val current = stack.get(DataComponents.ITEM_MODEL)
        val derivedNormal = deriveNormalFromGlow(current)

        if (current == null) {
            return RenderDecision.noOverride(
                slot,
                FlashMode.NORMAL_NO_FLASH,
                "NORMAL_NO_FLASH skipped: item has no ITEM_MODEL component",
                null,
                null,
                null,
                true
            )
        }

        val customNormal = preferredNormalModelForType(type, current, derivedNormal)
        if (customNormal != null) {
            return RenderDecision.override(
                slot,
                FlashMode.NORMAL_NO_FLASH,
                "NORMAL_NO_FLASH using custom static normal model",
                current,
                derivedNormal,
                customNormal,
                true
            )
        }

        // If the server already gave us a non-glow model, that's exactly what NORMAL_NO_FLASH wants.
        if (derivedNormal == null) {
            return RenderDecision.override(
                slot, FlashMode.NORMAL_NO_FLASH, "NORMAL_NO_FLASH using current non-glow model", current, null, current, true
            )
        }

        if (!itemModelExists(client, derivedNormal)) {
            val reason = "NORMAL_NO_FLASH skipped: missing item model json items/${derivedNormal.path}.json"
            return RenderDecision.noOverride(slot, FlashMode.NORMAL_NO_FLASH, reason, current, derivedNormal, null, true)
        }

        return RenderDecision.override(
            slot, FlashMode.NORMAL_NO_FLASH, "NORMAL_NO_FLASH using derived model", current, derivedNormal, derivedNormal, true
        )
    }

    private fun matchesExpectedItem(type: ItemType, stack: ItemStack): Boolean {
        val modelId = stack.get(DataComponents.ITEM_MODEL)
        val modelPath = if (modelId == null) "" else normalize(modelId.toString())
        val hoverName = stack.hoverName.string
        val normalizedName = normalize(hoverName)
        val nameWords = splitWords(hoverName)
        val compactName = compactNormalize(hoverName)

        return when (type) {
            ItemType.JOURNAL ->
                modelPath.contains("quest_log/icon") ||
                    modelPath.contains("journal") ||
                    captures[JOURNAL_SLOT]?.matches(stack) == true ||
                    captures[JOURNAL_ALT_SLOT]?.matches(stack) == true ||
                    "journal" in nameWords ||
                    "quest" in nameWords

            ItemType.POCKET ->
                modelPath.contains("misc/pocket") ||
                    captures[POCKET_SLOT]?.matches(stack) == true ||
                    normalizedName.contains("pocket")

            ItemType.NAVIGATOR ->
                !isIslandExchangeItem(modelPath, nameWords, normalizedName) &&
                    (modelPath.contains("island_interface/navigator/island") ||
                        (modelPath.contains("compass_00") && "navigator" in nameWords) ||
                        "navigator" in nameWords)

            ItemType.ANGLR_PANEL ->
                modelPath.contains("island_interface/fishing/anglr_panel") || compactName.contains("anglr")

            ItemType.PROFILE -> isProfile(modelPath, compactName, nameWords)

            ItemType.UNKNOWN -> false
        }
    }

    private fun rememberDecision(slot: Int, decision: RenderDecision) {
        if (!shouldTrackDecision(slot, decision)) return

        val previous = lastDecisionBySlot.put(slot, decision)
        if (!isDebugEnabled()) return

        val now = System.currentTimeMillis()
        val last = lastDecisionLogBySlot.getOrDefault(slot, 0L)
        if (previous != decision || now - last >= DECISION_LOG_COOLDOWN_MS) {
            lastDecisionLogBySlot[slot] = now
            MccNoFlashMod.LOGGER.info("Decision slot {}: {}", slot + 1, decision.toLogLine())
        }
    }

    fun getDecisionSummary(): String =
        summarizeSlots(lastDecisionBySlot.keys) { slot -> lastDecisionBySlot[slot]?.toLogLine() ?: "<none>" }

    fun getCaptureSummary(): String =
        summarizeSlots(captures.keys) { slot -> captures[slot]?.toLogLine() ?: "<none>" }

    private fun summarizeSlots(slots: Collection<Int>, formatter: (Int) -> String): String {
        val orderedSlots = slots.sorted().ifEmpty { listOf(JOURNAL_SLOT, JOURNAL_ALT_SLOT, POCKET_SLOT) }
        return orderedSlots.joinToString(" | ") { slot -> "slot${slot + 1}=${formatter(slot)}" }
    }

    private fun shouldCaptureSlot(slot: Int, stack: ItemStack): Boolean {
        val directType = determineItemTypeWithoutCaptures(stack)
        return when (slot) {
            JOURNAL_SLOT, JOURNAL_ALT_SLOT -> directType == ItemType.JOURNAL
            POCKET_SLOT -> directType == ItemType.POCKET
            else -> directType != ItemType.UNKNOWN
        }
    }

    private fun shouldTrackDecision(slot: Int, decision: RenderDecision): Boolean =
        slot == JOURNAL_SLOT ||
            slot == JOURNAL_ALT_SLOT ||
            slot == POCKET_SLOT ||
            decision.matchedTarget ||
            captures.containsKey(slot)

    fun discoveryKeywords(): Set<String> {
        val keywords = mutableSetOf("journal", "quest", "pocket", "menu", "icon_glow", "profile", "personal_menu")
        for (capture in captures.values) {
            keywords += splitWords(capture.itemId)
            keywords += splitWords(capture.displayName)
            keywords += splitWords(capture.components)
            keywords += splitWords(capture.itemModel)
        }
        keywords.removeIf { it.length < 4 }
        return keywords
    }

    fun sortByRelevance(values: Collection<String>): List<String> {
        val keywords = discoveryKeywords()
        return values.sortedByDescending { relevanceScore(it, keywords) }
    }

    fun getCaptureForSlot(slot: Int): Optional<SlotCapture> = Optional.ofNullable(captures[slot])

    data class RenderDecision(
        val slot: Int,
        val mode: FlashMode,
        val reason: String,
        val currentModel: Identifier?,
        val derivedModel: Identifier?,
        val chosenModel: Identifier?,
        val matchedTarget: Boolean,
        val shouldOverride: Boolean
    ) {
        fun toOptional(): Optional<Identifier> =
            if (shouldOverride && chosenModel != null) Optional.of(chosenModel) else Optional.empty()

        fun toLogLine(): String =
            "{slot=${slot + 1}, mode=$mode, override=$shouldOverride, matched=$matchedTarget" +
                ", current=${currentModel ?: "<none>"}, derived=${derivedModel ?: "<none>"}" +
                ", chosen=${chosenModel ?: "<none>"}, reason='$reason'}"

        companion object {
            @JvmStatic
            fun noOverride(
                slot: Int,
                mode: FlashMode,
                reason: String,
                currentModel: Identifier?,
                derivedModel: Identifier?,
                chosenModel: Identifier?,
                matchedTarget: Boolean
            ) = RenderDecision(slot, mode, reason, currentModel, derivedModel, chosenModel, matchedTarget, false)

            @JvmStatic
            fun override(
                slot: Int,
                mode: FlashMode,
                reason: String,
                currentModel: Identifier?,
                derivedModel: Identifier?,
                chosenModel: Identifier?,
                matchedTarget: Boolean
            ) = RenderDecision(slot, mode, reason, currentModel, derivedModel, chosenModel, matchedTarget, true)
        }
    }

    data class SlotCapture(
        val slot: Int,
        val itemId: String,
        val displayName: String,
        val components: String,
        val itemModel: String,
        val count: Int
    ) {
        fun matches(stack: ItemStack): Boolean {
            val otherId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
            if (itemId != otherId) return false

            val expectedName = normalize(displayName)
            return expectedName.isBlank() || normalize(stack.hoverName.string) == expectedName
        }

        fun toLogLine(): String =
            "{slot=${slot + 1}, itemId=$itemId, model=$itemModel, name='$displayName', count=$count, components=$components}"

        companion object {
            @JvmStatic
            fun from(slot: Int, stack: ItemStack): SlotCapture {
                val components = try {
                    stack.immutableComponents().toString()
                } catch (_: Exception) {
                    "<unavailable>"
                }
                return SlotCapture(
                    slot = slot,
                    itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString(),
                    displayName = stack.hoverName.string,
                    components = components,
                    itemModel = stack.get(DataComponents.ITEM_MODEL)?.toString() ?: "<none>",
                    count = stack.count
                )
            }
        }
    }

    companion object {
        private val WORD_BREAKS = Regex("[^a-z0-9]+")
        private const val LOG_COOLDOWN_MS = 2_000L
        private const val DECISION_LOG_COOLDOWN_MS = 3_000L

        const val JOURNAL_SLOT = 2
        const val JOURNAL_ALT_SLOT = 3
        const val POCKET_SLOT = 8

        private fun model(path: String): Identifier = Identifier.fromNamespaceAndPath(MccNoFlashMod.MOD_ID, path)

        private val JOURNAL_STATIC_MODEL = model("static/journal_glow")
        private val POCKET_STATIC_MODEL = model("static/pocket_glow")
        private val NAVIGATOR_NORMAL_MODEL = model("static/navigator")
        private val NAVIGATOR_STATIC_MODEL = model("static/navigator_glow")
        private val ANGLR_PANEL_STATIC_MODEL = model("static/anglr_panel_glow")
        private val PROFILE_NORMAL_MODEL = model("static/profile")
        private val PROFILE_STATIC_GLOW_MODEL = model("static/profile_glow")

        private val INSTANCE = MccNoFlashState()

        @JvmStatic
        fun get(): MccNoFlashState = INSTANCE

        private fun determineItemTypeWithoutCaptures(stack: ItemStack): ItemType {
            val modelId = stack.get(DataComponents.ITEM_MODEL)
            val modelPath = if (modelId == null) "" else normalize(modelId.toString())
            val hoverName = stack.hoverName.string
            val nameWords = splitWords(hoverName)
            val normalizedName = normalize(hoverName)
            val compactName = compactNormalize(hoverName)

            return when {
                modelPath.contains("quest_log/icon") || modelPath.contains("journal") ||
                    "journal" in nameWords || "quest" in nameWords -> ItemType.JOURNAL

                modelPath.contains("misc/pocket") || "pocket" in nameWords -> ItemType.POCKET

                !isIslandExchangeItem(modelPath, nameWords, normalizedName) &&
                    (modelPath.contains("island_interface/navigator/island") ||
                        (modelPath.contains("compass_00") && "navigator" in nameWords) ||
                        "navigator" in nameWords) -> ItemType.NAVIGATOR

                modelPath.contains("island_interface/fishing/anglr_panel") || compactName.contains("anglr") ->
                    ItemType.ANGLR_PANEL

                isProfile(modelPath, compactName, nameWords) -> ItemType.PROFILE

                else -> ItemType.UNKNOWN
            }
        }

        private fun isProfile(modelPath: String, compactName: String, nameWords: Set<String>): Boolean =
            modelPath.contains("island_interface/personal_menu/icon") ||
                compactName.contains("myprofile") ||
                ("profile" in nameWords && ("my" in nameWords || "personal" in nameWords))

        private fun deriveNormalFromGlow(glowModel: Identifier?): Identifier? {
            val path = glowModel?.path ?: return null
            if (!path.endsWith("_glow")) return null
            return glowModel.withPath(path.removeSuffix("_glow"))
        }

        private fun isIslandExchangeItem(modelPath: String, nameWords: Set<String>, normalizedName: String): Boolean =
            modelPath.contains("island_exchange") ||
                normalizedName.contains("island exchange") ||
                ("exchange" in nameWords && "island" in nameWords)

        private fun staticModelForType(type: ItemType): Identifier = when (type) {
            ItemType.JOURNAL -> JOURNAL_STATIC_MODEL
            ItemType.POCKET -> POCKET_STATIC_MODEL
            ItemType.NAVIGATOR -> NAVIGATOR_STATIC_MODEL
            ItemType.ANGLR_PANEL -> ANGLR_PANEL_STATIC_MODEL
            ItemType.PROFILE -> PROFILE_NORMAL_MODEL
            ItemType.UNKNOWN -> POCKET_STATIC_MODEL
        }

        private fun staticGlowModelForType(type: ItemType): Identifier = when (type) {
            ItemType.NAVIGATOR -> NAVIGATOR_STATIC_MODEL
            ItemType.ANGLR_PANEL -> ANGLR_PANEL_STATIC_MODEL
            ItemType.PROFILE -> PROFILE_STATIC_GLOW_MODEL
            else -> staticModelForType(type)
        }

        private fun preferredNormalModelForType(type: ItemType, currentModel: Identifier?, derivedNormal: Identifier?): Identifier? {
            if (type == ItemType.NAVIGATOR) return NAVIGATOR_NORMAL_MODEL
            if (type == ItemType.PROFILE) {
                val currentPath = normalize(currentModel?.toString())
                val derivedPath = normalize(derivedNormal?.toString())
                if (currentPath.contains("island_interface/personal_menu/icon") ||
                    derivedPath.contains("island_interface/personal_menu/icon")
                ) {
                    return PROFILE_NORMAL_MODEL
                }
            }
            return null
        }

        private fun itemModelExists(client: Minecraft?, itemModelId: Identifier): Boolean {
            val resourceManager = client?.resourceManager ?: return false
            val itemModelJson = itemModelId.withPrefix("items/").withSuffix(".json")
            return resourceManager.getResource(itemModelJson).isPresent
        }

        private fun inventoryDecisionSlot(type: ItemType): Int = when (type) {
            ItemType.JOURNAL -> JOURNAL_SLOT
            ItemType.POCKET -> POCKET_SLOT
            ItemType.NAVIGATOR -> 0
            ItemType.ANGLR_PANEL -> 1
            ItemType.PROFILE -> 4
            ItemType.UNKNOWN -> JOURNAL_SLOT
        }

        private fun relevanceScore(value: String, keywords: Set<String>): Int {
            val normalized = normalize(value)
            return keywords.filter { normalized.contains(it) }.sumOf { it.length }
        }

        private fun normalize(input: String?): String = input?.lowercase(Locale.ROOT) ?: ""

        private fun compactNormalize(input: String?): String = WORD_BREAKS.replace(normalize(input), "")

        private fun splitWords(input: String?): Set<String> {
            if (input.isNullOrBlank()) return emptySet()
            return WORD_BREAKS.split(normalize(input)).filter { it.isNotBlank() }.toSet()
        }
    }
}
