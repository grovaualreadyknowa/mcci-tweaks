package dev.caden.mccnoflash;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.Identifier;

public final class MccNoFlashState {
    private static final Pattern WORD_BREAKS = Pattern.compile("[^a-z0-9]+");
    private static final long LOG_COOLDOWN_MS = 2_000L;
    private static final long DECISION_LOG_COOLDOWN_MS = 3_000L;

    public static final int JOURNAL_SLOT = 2;
    public static final int JOURNAL_ALT_SLOT = 3;
    public static final int POCKET_SLOT = 8;

    private static final Identifier JOURNAL_STATIC_MODEL = Identifier.fromNamespaceAndPath(MccNoFlashMod.MOD_ID, "static/journal_glow");
    private static final Identifier POCKET_STATIC_MODEL = Identifier.fromNamespaceAndPath(MccNoFlashMod.MOD_ID, "static/pocket_glow");
    private static final Identifier NAVIGATOR_NORMAL_MODEL = Identifier.fromNamespaceAndPath(MccNoFlashMod.MOD_ID, "static/navigator");
    private static final Identifier NAVIGATOR_STATIC_MODEL = Identifier.fromNamespaceAndPath(MccNoFlashMod.MOD_ID, "static/navigator_glow");
    private static final Identifier ANGLR_PANEL_STATIC_MODEL = Identifier.fromNamespaceAndPath(MccNoFlashMod.MOD_ID, "static/anglr_panel_glow");
    private static final Identifier PROFILE_NORMAL_MODEL = Identifier.fromNamespaceAndPath(MccNoFlashMod.MOD_ID, "static/profile");
    private static final Identifier PROFILE_STATIC_GLOW_MODEL = Identifier.fromNamespaceAndPath(MccNoFlashMod.MOD_ID, "static/profile_glow");
    private enum ItemType {
        JOURNAL,
        POCKET,
        NAVIGATOR,
        ANGLR_PANEL,
        PROFILE,
        UNKNOWN
    }

    private static final MccNoFlashState INSTANCE = new MccNoFlashState();

    private final Map<Integer, SlotCapture> captures = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastLogBySlot = new HashMap<>();
    private final Map<Integer, RenderDecision> lastDecisionBySlot = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastDecisionLogBySlot = new ConcurrentHashMap<>();

    private MccNoFlashConfig config = new MccNoFlashConfig();
    private boolean debugSession;

    private MccNoFlashState() {
    }

    public static MccNoFlashState get() {
        return INSTANCE;
    }

    public void loadConfig() {
        this.config = MccNoFlashConfig.load();
    }

    public MccNoFlashConfig config() {
        return this.config;
    }

    public void saveConfig() {
        this.config.save();
    }

    public boolean isFeatureEnabled() {
        return this.config.enabled;
    }

    public boolean isDebugEnabled() {
        return this.config.debugLogging || this.debugSession;
    }

    public boolean isDebugSessionEnabled() {
        return this.debugSession;
    }

    public void setDebugSession(boolean enabled) {
        this.debugSession = enabled;
    }

    public FlashMode getJournalMode() {
        return this.config.globalMode != FlashMode.DEFAULT ? this.config.globalMode : this.config.journalMode;
    }

    public FlashMode getPocketMode() {
        return this.config.globalMode != FlashMode.DEFAULT ? this.config.globalMode : this.config.pocketMode;
    }

    public FlashMode getNavigatorMode() {
        return this.config.globalMode != FlashMode.DEFAULT ? this.config.globalMode : this.config.navigatorMode;
    }

    public FlashMode getAnglrPanelMode() {
        return this.config.globalMode != FlashMode.DEFAULT ? this.config.globalMode : this.config.anglrPanelMode;
    }

    public FlashMode getProfileMode() {
        return this.config.globalMode != FlashMode.DEFAULT ? this.config.globalMode : this.config.profileMode;
    }

    public boolean isOnMccServer(Minecraft client) {
        if (client == null) {
            return false;
        }

        ServerData entry = client.getCurrentServer();
        if (entry == null || entry.ip == null) {
            return false;
        }

        return normalize(entry.ip).contains("mccisland.net");
    }

    private ItemType determineItemType(ItemStack stack) {
        ItemType directType = determineItemTypeWithoutCaptures(stack);
        if (directType != ItemType.UNKNOWN) {
            return directType;
        }

        SlotCapture pocketCapture = this.captures.get(POCKET_SLOT);
        if (pocketCapture != null && pocketCapture.matches(stack)) {
            return ItemType.POCKET;
        }

        return ItemType.UNKNOWN;
    }

    private static ItemType determineItemTypeWithoutCaptures(ItemStack stack) {
        Identifier modelId = stack.get(DataComponents.ITEM_MODEL);
        String modelPath = modelId == null ? "" : normalize(modelId.toString());
        Set<String> nameWords = splitWords(stack.getHoverName().getString());
        String normalizedName = normalize(stack.getHoverName().getString());
        String compactName = compactNormalize(stack.getHoverName().getString());

        if (modelPath.contains("quest_log/icon") || modelPath.contains("journal") || nameWords.contains("journal") || nameWords.contains("quest")) {
            return ItemType.JOURNAL;
        }

        if (modelPath.contains("misc/pocket") || nameWords.contains("pocket")) {
            return ItemType.POCKET;
        }

        if (!isIslandExchangeItem(modelPath, nameWords, normalizedName)
            && (modelPath.contains("island_interface/navigator/island")
                || (modelPath.contains("compass_00") && nameWords.contains("navigator"))
                || nameWords.contains("navigator"))) {
            return ItemType.NAVIGATOR;
        }

        if (modelPath.contains("island_interface/fishing/anglr_panel") || compactName.contains("anglr")) {
            return ItemType.ANGLR_PANEL;
        }

        if (modelPath.contains("island_interface/personal_menu/icon")
            || compactName.contains("myprofile")
            || (nameWords.contains("profile") && (nameWords.contains("my") || nameWords.contains("personal")))) {
            return ItemType.PROFILE;
        }

        return ItemType.UNKNOWN;
    }

    public void captureRender(int slot, ItemStack stack) {
        if (!isFeatureEnabled()) {
            return;
        }

        if (!shouldCaptureSlot(slot, stack)) {
            return;
        }

        SlotCapture capture = SlotCapture.from(slot, stack);
        SlotCapture previous = this.captures.put(slot, capture);

        if (isDebugEnabled()) {
            long now = System.currentTimeMillis();
            long last = this.lastLogBySlot.getOrDefault(slot, 0L);
            if (!capture.equals(previous) || now - last >= LOG_COOLDOWN_MS) {
                this.lastLogBySlot.put(slot, now);
                MccNoFlashMod.LOGGER.info("Captured slot {} signature: {}", slot + 1, capture.toLogLine());
            }
        }
    }

    public Optional<Identifier> resolveOverrideItemModel(int slot, ItemStack stack, Minecraft client) {
        if (!isFeatureEnabled() || !isOnMccServer(client)) {
            return Optional.empty();
        }

        Identifier currentModel = stack.get(DataComponents.ITEM_MODEL);
        ItemType type = determineItemType(stack);
        if (type == ItemType.UNKNOWN) {
            rememberDecision(slot, RenderDecision.noOverride(slot, FlashMode.DEFAULT, "<unknown item type>", currentModel, null, null, false));
            return Optional.empty();
        }

        FlashMode mode = switch (type) {
            case JOURNAL -> getJournalMode();
            case POCKET -> getPocketMode();
            case NAVIGATOR -> getNavigatorMode();
            case ANGLR_PANEL -> getAnglrPanelMode();
            case PROFILE -> getProfileMode();
            default -> FlashMode.DEFAULT;
        };

        if (mode == FlashMode.DEFAULT) {
            rememberDecision(slot, RenderDecision.noOverride(slot, mode, "mode is DEFAULT", currentModel, null, null, true));
            return Optional.empty();
        }

        if (!matchesExpectedItem(type, stack)) {
            rememberDecision(slot, RenderDecision.noOverride(slot, mode, "item signature mismatch for " + type, currentModel, null, null, true));
            return Optional.empty();
        }

        RenderDecision decision = switch (mode) {
            case STATIC_GLOW -> RenderDecision.override(slot, mode, "STATIC_GLOW selected", currentModel, null, staticGlowModelForType(type), true);
            case NORMAL_NO_FLASH -> resolveNormalOrStatic(slot, type, stack, client);
            default -> RenderDecision.noOverride(slot, mode, "unhandled mode", currentModel, null, null, true);
        };

        rememberDecision(slot, decision);
        return decision.shouldOverride() ? Optional.of(decision.chosenModel()) : Optional.empty();
    }

    public Optional<Identifier> resolveInventoryOverrideItemModel(ItemStack stack, Minecraft client) {
        if (!isFeatureEnabled() || !isOnMccServer(client) || client == null) {
            return Optional.empty();
        }

        Screen screen = client.screen;
        if (!isSupportedInventoryScreen(screen)) {
            return Optional.empty();
        }

        ItemType type = determineItemType(stack);
        if (type == ItemType.UNKNOWN || !matchesExpectedItem(type, stack)) {
            return Optional.empty();
        }

        if (!isSupportedInventoryTarget(screen, type)) {
            return Optional.empty();
        }

        FlashMode mode = switch (type) {
            case JOURNAL -> getJournalMode();
            case POCKET -> getPocketMode();
            case NAVIGATOR -> getNavigatorMode();
            case ANGLR_PANEL -> getAnglrPanelMode();
            case PROFILE -> getProfileMode();
            default -> FlashMode.DEFAULT;
        };

        if (mode == FlashMode.DEFAULT) {
            return Optional.empty();
        }

        RenderDecision decision = switch (mode) {
            case STATIC_GLOW -> RenderDecision.override(
                inventoryDecisionSlot(type),
                mode,
                "inventory override: STATIC_GLOW selected",
                stack.get(DataComponents.ITEM_MODEL),
                null,
                staticGlowModelForType(type),
                true
            );
            case NORMAL_NO_FLASH -> resolveNormalOrStatic(inventoryDecisionSlot(type), type, stack, client);
            default -> RenderDecision.noOverride(inventoryDecisionSlot(type), mode, "inventory mode default", stack.get(DataComponents.ITEM_MODEL), null, null, true);
        };

        rememberDecision(inventoryDecisionSlot(type), decision);
        return decision.shouldOverride() ? Optional.of(decision.chosenModel()) : Optional.empty();
    }

    public boolean isSupportedInventoryScreen(Screen screen) {
        if (screen == null) {
            return false;
        }

        if (screen instanceof InventoryScreen) {
            return true;
        }

        String normalizedTitle = normalize(screen.getTitle().getString());
        return normalizedTitle.contains("pocket menu");
    }

    private boolean isSupportedInventoryTarget(Screen screen, ItemType type) {
        if (screen instanceof InventoryScreen) {
            return type == ItemType.JOURNAL
                || type == ItemType.POCKET
                || type == ItemType.NAVIGATOR
                || type == ItemType.ANGLR_PANEL
                || type == ItemType.PROFILE;
        }

        if (!isSupportedInventoryScreen(screen)) {
            return false;
        }

        return type == ItemType.JOURNAL
            || type == ItemType.NAVIGATOR
            || type == ItemType.ANGLR_PANEL
            || type == ItemType.PROFILE;
    }

    private RenderDecision resolveNormalOrStatic(int slot, ItemType type, ItemStack stack, Minecraft client) {
        Identifier current = stack.get(DataComponents.ITEM_MODEL);
        Identifier derivedNormal = deriveNormalModel(current);

        if (current == null) {
            return RenderDecision.noOverride(slot, FlashMode.NORMAL_NO_FLASH, "NORMAL_NO_FLASH skipped: item has no ITEM_MODEL component", null, null, null, true);
        }

        Identifier customNormal = preferredNormalModelForType(type, current, derivedNormal);
        if (customNormal != null) {
            return RenderDecision.override(
                slot,
                FlashMode.NORMAL_NO_FLASH,
                "NORMAL_NO_FLASH using custom static normal model",
                current,
                derivedNormal,
                customNormal,
                true
            );
        }

        // If the server already gave us a non-glow model, that's exactly what NORMAL_NO_FLASH wants.
        if (derivedNormal == null) {
            return RenderDecision.override(
                slot,
                FlashMode.NORMAL_NO_FLASH,
                "NORMAL_NO_FLASH using current non-glow model",
                current,
                null,
                current,
                true
            );
        }

        if (!itemModelExists(client, derivedNormal)) {
            String reason = "NORMAL_NO_FLASH skipped: missing item model json items/" + derivedNormal.getPath() + ".json";
            return RenderDecision.noOverride(slot, FlashMode.NORMAL_NO_FLASH, reason, current, derivedNormal, null, true);
        }

        return RenderDecision.override(slot, FlashMode.NORMAL_NO_FLASH, "NORMAL_NO_FLASH using derived model", current, derivedNormal, derivedNormal, true);
    }

    private boolean matchesExpectedItem(ItemType type, ItemStack stack) {
        Identifier modelId = stack.get(DataComponents.ITEM_MODEL);
        String modelPath = modelId == null ? "" : normalize(modelId.toString());
        String normalizedName = normalize(stack.getHoverName().getString());
        Set<String> nameWords = splitWords(stack.getHoverName().getString());
        String compactName = compactNormalize(stack.getHoverName().getString());

        if (type == ItemType.JOURNAL) {
            if (modelPath.contains("quest_log/icon") || modelPath.contains("journal")) {
                return true;
            }

            // Check captures for journal slots (2 and 3)
            SlotCapture capture2 = this.captures.get(JOURNAL_SLOT);
            if (capture2 != null && capture2.matches(stack)) {
                return true;
            }
            SlotCapture capture3 = this.captures.get(JOURNAL_ALT_SLOT);
            if (capture3 != null && capture3.matches(stack)) {
                return true;
            }

            return nameWords.contains("journal") || nameWords.contains("quest");
        }

        if (type == ItemType.POCKET) {
            if (modelPath.contains("misc/pocket")) {
                return true;
            }

            SlotCapture capture = this.captures.get(POCKET_SLOT);
            if (capture != null && capture.matches(stack)) {
                return true;
            }

            return normalizedName.contains("pocket");
        }

        if (type == ItemType.NAVIGATOR) {
            if (isIslandExchangeItem(modelPath, nameWords, normalizedName)) {
                return false;
            }

            return modelPath.contains("island_interface/navigator/island")
                || (modelPath.contains("compass_00") && nameWords.contains("navigator"))
                || nameWords.contains("navigator");
        }

        if (type == ItemType.ANGLR_PANEL) {
            return modelPath.contains("island_interface/fishing/anglr_panel") || compactName.contains("anglr");
        }

        if (type == ItemType.PROFILE) {
            return modelPath.contains("island_interface/personal_menu/icon")
                || compactName.contains("myprofile")
                || (nameWords.contains("profile") && (nameWords.contains("my") || nameWords.contains("personal")));
        }

        return false;
    }

    private static Identifier deriveNormalFromGlow(Identifier glowModel) {
        if (glowModel == null) {
            return null;
        }

        String path = glowModel.getPath();
        if (!path.endsWith("_glow")) {
            return null;
        }

        return glowModel.withPath(path.substring(0, path.length() - "_glow".length()));
    }

    private static boolean isIslandExchangeItem(String modelPath, Set<String> nameWords, String normalizedName) {
        if (modelPath.contains("island_exchange")) {
            return true;
        }

        if (normalizedName.contains("island exchange")) {
            return true;
        }

        return nameWords.contains("exchange") && nameWords.contains("island");
    }

    private static Identifier staticModelForType(ItemType type) {
        return switch (type) {
            case JOURNAL -> JOURNAL_STATIC_MODEL;
            case POCKET -> POCKET_STATIC_MODEL;
            case NAVIGATOR -> NAVIGATOR_STATIC_MODEL;
            case ANGLR_PANEL -> ANGLR_PANEL_STATIC_MODEL;
            case PROFILE -> PROFILE_NORMAL_MODEL;
            default -> POCKET_STATIC_MODEL;
        };
    }

    private static Identifier staticGlowModelForType(ItemType type) {
        return switch (type) {
            case NAVIGATOR -> NAVIGATOR_STATIC_MODEL;
            case ANGLR_PANEL -> ANGLR_PANEL_STATIC_MODEL;
            case PROFILE -> PROFILE_STATIC_GLOW_MODEL;
            default -> staticModelForType(type);
        };
    }

    private static Identifier preferredNormalModelForType(ItemType type, Identifier currentModel, Identifier derivedNormal) {
        if (type == ItemType.NAVIGATOR) {
            return NAVIGATOR_NORMAL_MODEL;
        }

        if (type == ItemType.PROFILE) {
            String currentPath = currentModel == null ? "" : normalize(currentModel.toString());
            String derivedPath = derivedNormal == null ? "" : normalize(derivedNormal.toString());
            if (currentPath.contains("island_interface/personal_menu/icon") || derivedPath.contains("island_interface/personal_menu/icon")) {
                return PROFILE_NORMAL_MODEL;
            }
        }

        return null;
    }

    private static Identifier deriveNormalModel(Identifier currentModel) {
        return deriveNormalFromGlow(currentModel);
    }

    private static boolean itemModelExists(Minecraft client, Identifier itemModelId) {
        if (client == null || client.getResourceManager() == null) {
            return false;
        }

        ResourceManager resourceManager = client.getResourceManager();
        Identifier itemModelJson = itemModelId.withPrefix("items/").withSuffix(".json");
        return resourceManager.getResource(itemModelJson).isPresent();
    }

    private void rememberDecision(int slot, RenderDecision decision) {
        if (!shouldTrackDecision(slot, decision)) {
            return;
        }

        RenderDecision previous = this.lastDecisionBySlot.put(slot, decision);
        if (!isDebugEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        long last = this.lastDecisionLogBySlot.getOrDefault(slot, 0L);
        if (!Objects.equals(previous, decision) || now - last >= DECISION_LOG_COOLDOWN_MS) {
            this.lastDecisionLogBySlot.put(slot, now);
            MccNoFlashMod.LOGGER.info("Decision slot {}: {}", slot + 1, decision.toLogLine());
        }
    }

    public String getDecisionSummary() {
        return summarizeSlots(this.lastDecisionBySlot.keySet(), this::formatDecision);
    }

    private String formatDecision(int slot) {
        RenderDecision decision = this.lastDecisionBySlot.get(slot);
        return decision == null ? "<none>" : decision.toLogLine();
    }

    public String getCaptureSummary() {
        return summarizeSlots(this.captures.keySet(), this::formatCapture);
    }

    private String formatCapture(int slot) {
        SlotCapture capture = this.captures.get(slot);
        return capture == null ? "<none>" : capture.toLogLine();
    }

    private String summarizeSlots(Collection<Integer> slots, java.util.function.IntFunction<String> formatter) {
        List<Integer> orderedSlots = trackedSlots(slots);
        if (orderedSlots.isEmpty()) {
            return "<none>";
        }

        StringJoiner joiner = new StringJoiner(" | ");
        for (int slot : orderedSlots) {
            joiner.add("slot" + (slot + 1) + "=" + formatter.apply(slot));
        }
        return joiner.toString();
    }

    private List<Integer> trackedSlots(Collection<Integer> slots) {
        List<Integer> ordered = new ArrayList<>(slots);
        ordered.sort(Integer::compareTo);
        if (!ordered.isEmpty()) {
            return ordered;
        }

        return List.of(JOURNAL_SLOT, JOURNAL_ALT_SLOT, POCKET_SLOT);
    }

    private boolean shouldCaptureSlot(int slot, ItemStack stack) {
        ItemType directType = determineItemTypeWithoutCaptures(stack);

        if (slot == JOURNAL_SLOT || slot == JOURNAL_ALT_SLOT) {
            return directType == ItemType.JOURNAL;
        }

        if (slot == POCKET_SLOT) {
            return directType == ItemType.POCKET;
        }

        return directType != ItemType.UNKNOWN;
    }

    private boolean shouldTrackDecision(int slot, RenderDecision decision) {
        return slot == JOURNAL_SLOT
            || slot == JOURNAL_ALT_SLOT
            || slot == POCKET_SLOT
            || decision.matchedTarget()
            || this.captures.containsKey(slot);
    }

    private static int inventoryDecisionSlot(ItemType type) {
        return switch (type) {
            case JOURNAL -> JOURNAL_SLOT;
            case POCKET -> POCKET_SLOT;
            case NAVIGATOR -> 0;
            case ANGLR_PANEL -> 1;
            case PROFILE -> 4;
            default -> JOURNAL_SLOT;
        };
    }

    public Set<String> discoveryKeywords() {
        Set<String> keywords = new HashSet<>();
        keywords.add("journal");
        keywords.add("quest");
        keywords.add("pocket");
        keywords.add("menu");
        keywords.add("icon_glow");
        keywords.add("profile");
        keywords.add("personal_menu");

        for (SlotCapture capture : this.captures.values()) {
            keywords.addAll(splitWords(capture.itemId()));
            keywords.addAll(splitWords(capture.displayName()));
            keywords.addAll(splitWords(capture.components()));
            keywords.addAll(splitWords(capture.itemModel()));
        }

        keywords.removeIf(word -> word.length() < 4);
        return keywords;
    }

    public List<String> sortByRelevance(Collection<String> values) {
        List<String> ordered = new ArrayList<>(values);
        Set<String> keywords = discoveryKeywords();

        ordered.sort(Comparator.comparingInt((String value) -> relevanceScore(value, keywords)).reversed());
        return ordered;
    }

    private static int relevanceScore(String value, Set<String> keywords) {
        int score = 0;
        String normalized = normalize(value);
        for (String keyword : keywords) {
            if (normalized.contains(keyword)) {
                score += keyword.length();
            }
        }

        return score;
    }

    private static String normalize(String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT);
    }

    private static String compactNormalize(String input) {
        return WORD_BREAKS.matcher(normalize(input)).replaceAll("");
    }

    private static Set<String> splitWords(String input) {
        if (input == null || input.isBlank()) {
            return Collections.emptySet();
        }

        String[] parts = WORD_BREAKS.split(normalize(input));
        Set<String> output = new HashSet<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                output.add(part);
            }
        }

        return output;
    }

    public record RenderDecision(
        int slot,
        FlashMode mode,
        String reason,
        Identifier currentModel,
        Identifier derivedModel,
        Identifier chosenModel,
        boolean matchedTarget,
        boolean shouldOverride
    ) {
        public static RenderDecision noOverride(
            int slot,
            FlashMode mode,
            String reason,
            Identifier currentModel,
            Identifier derivedModel,
            Identifier chosenModel,
            boolean matchedTarget
        ) {
            return new RenderDecision(slot, mode, reason, currentModel, derivedModel, chosenModel, matchedTarget, false);
        }

        public static RenderDecision override(
            int slot,
            FlashMode mode,
            String reason,
            Identifier currentModel,
            Identifier derivedModel,
            Identifier chosenModel,
            boolean matchedTarget
        ) {
            return new RenderDecision(slot, mode, reason, currentModel, derivedModel, chosenModel, matchedTarget, true);
        }

        public String toLogLine() {
            return "{slot=" + (this.slot + 1)
                + ", mode=" + this.mode
                + ", override=" + this.shouldOverride
                + ", matched=" + this.matchedTarget
                + ", current=" + (this.currentModel == null ? "<none>" : this.currentModel)
                + ", derived=" + (this.derivedModel == null ? "<none>" : this.derivedModel)
                + ", chosen=" + (this.chosenModel == null ? "<none>" : this.chosenModel)
                + ", reason='" + this.reason + "'}";
        }
    }

    public record SlotCapture(int slot, String itemId, String displayName, String components, String itemModel, int count) {
        public static SlotCapture from(int slot, ItemStack stack) {
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            String displayName = stack.getHoverName().getString();
            String components;
            String itemModel;

            try {
                components = String.valueOf(stack.immutableComponents());
            } catch (Exception ignored) {
                components = "<unavailable>";
            }

            Identifier itemModelId = stack.get(DataComponents.ITEM_MODEL);
            itemModel = itemModelId == null ? "<none>" : itemModelId.toString();

            return new SlotCapture(slot, itemId, displayName, components, itemModel, stack.getCount());
        }

        public boolean matches(ItemStack stack) {
            String otherId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!Objects.equals(this.itemId, otherId)) {
                return false;
            }

            String normalizedExpectedName = normalize(this.displayName);
            if (!normalizedExpectedName.isBlank()) {
                String normalizedActual = normalize(stack.getHoverName().getString());
                if (!normalizedActual.equals(normalizedExpectedName)) {
                    return false;
                }
            }

            return true;
        }

        public String toLogLine() {
            return "{slot=" + (this.slot + 1)
                + ", itemId=" + this.itemId
                + ", model=" + this.itemModel
                + ", name='" + this.displayName + "'"
                + ", count=" + this.count
                + ", components=" + this.components
                + "}";
        }
    }

    public Optional<SlotCapture> getCaptureForSlot(int slot) {
        return Optional.ofNullable(this.captures.get(slot));
    }
}


