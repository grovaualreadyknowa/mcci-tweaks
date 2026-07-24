package dev.caden.mccitweaks.materialmarket;

import dev.caden.mccitweaks.McciTweaksConfigStore;
import dev.caden.mccitweaks.McciTweaksMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MaterialMarketInventory {
    private static final Pattern COST_PATTERN = Pattern.compile("Cost:\\s*([\\d,]+)/([\\d,]+)\\s+\\[(.+)]");
    private static final Pattern RECEIVE_PATTERN = Pattern.compile("You receive:\\s*(?:\\[(.+?)]|(.+?))(?:\\s*x([\\d,]+))?\\s*$");
    private static final Pattern MATERIAL_COUNT_PATTERN = Pattern.compile("\\[(.+?)]\\s*\\[([\\d,]+)/[\\d,]+]");

    private static final int SCAN_BATCH_SIZE = 12;
    private static final int SCAN_TICKS_AFTER_OPEN = 80;

    private static boolean pendingPurchase;
    private static ContainerScreen activeScanScreen;
    private static int scanTicksRemaining;
    private static int scanCursor;

    private MaterialMarketInventory() {
    }

    public static void register() {
        MaterialMarketInventoryStore.load();
        MaterialMarketApiSync.register();
        ClientTickEvents.END_CLIENT_TICK.register(MaterialMarketInventory::handleTick);
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            handleChatMessage(message.getString());
            return true;
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> MaterialMarketInventoryStore.save());
    }

    public static void onScreenOpened(ContainerScreen screen) {
        if (!isEnabled() || !isOnMccServer()) {
            return;
        }

        if (McciTweaksConfigStore.get().materialMarket.usesApiInventory()) {
            if (isMaterialMarket(screen)) {
                MaterialMarketApiSync.requestAutomatic(Minecraft.getInstance());
            }
            return;
        }

        if (!isScannableInventory(screen)) {
            return;
        }
        syncFromScreen(screen);
        activeScanScreen = screen;
        scanTicksRemaining = SCAN_TICKS_AFTER_OPEN;
        scanCursor = 0;
    }

    public static void onScreenClosed(ContainerScreen screen) {
        if (activeScanScreen == screen) {
            stopBatchedScan();
        }
    }

    public static void onScreenClick(ContainerScreen screen, Slot clickedSlot, MouseButtonEvent event) {
        if (!isEnabled() || !usesTraditionalInventory() || !isOnMccServer() || !isMaterialMarket(screen)) {
            return;
        }
        if (event.button() != 0 && event.button() != 1) {
            return;
        }

        ItemStack item = clickedSlot == null ? null : clickedSlot.getItem();
        Cost cost = item == null ? null : parseCost(item);
        if (item == null || cost == null) {
            if (pendingPurchase && isCancelClick(clickedSlot, item, event)) {
                pendingPurchase = false;
            }
            return;
        }

        if (cost.unitCost > 0 && cost.availableBalance >= cost.unitCost) {
            pendingPurchase = true;
        }
    }

    public static void modifyTooltip(ItemStack item, Consumer<Component> consumer) {
        if (!isEnabled() || !isOnMccServer()) {
            return;
        }

        if (usesTraditionalInventory()) {
            syncFromFusionForgeTooltip(item);
        }
        if (!isMaterialMarket(Minecraft.getInstance().screen) || parseCost(item) == null) {
            return;
        }

        consumer.accept(Component.empty());
        String boughtName = item.getHoverName().getString();
        MaterialMarketInventoryStore.MaterialInventoryEntry owned = findEntry(boughtName);
        if (owned == null) {
            String guidance = usesTraditionalInventory()
                ? "MCCI Tweaks: open the Recipe Book or Fusion Forge to scan material counts."
                : "MCCI Tweaks: no API count cached; open settings and sync.";
            consumer.accept(Component.literal(guidance).withStyle(ChatFormatting.GRAY));
            return;
        }

        consumer.accept(
            Component.literal("Owned: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(format(owned.amount) + " " + boughtName).withStyle(ChatFormatting.WHITE))
        );
    }

    public static void clearLocalData() {
        String playerId = currentPlayerId();
        if (playerId != null) {
            MaterialMarketInventoryStore.clear(playerId);
        }
        pendingPurchase = false;
    }

    public static int trackedMaterialCount() {
        String playerId = currentPlayerId();
        return playerId == null ? 0 : materialInventory(playerId).size();
    }

    static void handleChatMessage(String message) {
        if (!isEnabled() || !usesTraditionalInventory() || !isOnMccServer()) {
            return;
        }

        Matcher match = RECEIVE_PATTERN.matcher(message);
        if (!match.find()) {
            return;
        }

        String receivedName = firstNonBlank(match.group(1), match.group(2));
        long receivedQuantity = match.group(3) == null || match.group(3).isBlank() ? 1L : parseLong(match.group(3));
        if (receivedQuantity > 0) {
            McciTweaksMod.LOGGER.debug("Material Market inventory received {} x{}", receivedName, receivedQuantity);
            adjustAmount(normalize(receivedName), receivedName, receivedQuantity);
            MaterialMarketInventoryStore.save();
        }
        pendingPurchase = false;
    }

    private static void handleTick(Minecraft client) {
        if (!isEnabled() || !usesTraditionalInventory()) {
            stopBatchedScan();
            return;
        }

        ContainerScreen screen = activeScanScreen;
        if (screen == null) {
            return;
        }
        if (client.screen != screen || !isOnMccServer() || !isScannableInventory(screen)) {
            stopBatchedScan();
            return;
        }
        if (scanTicksRemaining-- <= 0) {
            stopBatchedScan();
            return;
        }

        List<Slot> slots = screen.getMenu().slots;
        if (slots.isEmpty()) {
            return;
        }

        List<ScannedMaterial> scanned = new ArrayList<>();
        int count = Math.min(SCAN_BATCH_SIZE, slots.size());
        for (int index = 0; index < count; index++) {
            ItemStack item = slots.get(scanCursor % slots.size()).getItem();
            scanCursor = (scanCursor + 1) % slots.size();
            if (!item.isEmpty()) {
                scanned.addAll(scanItem(item));
            }
        }
        if (!scanned.isEmpty()) {
            upsert(scanned);
        }
    }

    private static void stopBatchedScan() {
        activeScanScreen = null;
        scanTicksRemaining = 0;
        scanCursor = 0;
    }

    private static void syncFromScreen(ContainerScreen screen) {
        List<ScannedMaterial> scanned = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            ItemStack item = slot.getItem();
            if (!item.isEmpty()) {
                scanned.addAll(scanItem(item));
            }
        }
        if (!scanned.isEmpty()) {
            upsert(scanned);
        }
    }

    private static void syncFromFusionForgeTooltip(ItemStack item) {
        if (!isFusionForge(Minecraft.getInstance().screen)) {
            return;
        }
        List<ScannedMaterial> scanned = scanMaterialCountLore(item);
        if (!scanned.isEmpty()) {
            upsert(scanned);
        }
    }

    private static List<ScannedMaterial> scanItem(ItemStack item) {
        List<ScannedMaterial> result = new ArrayList<>();
        ScannedMaterial customAmount = scanCustomAmount(item);
        if (customAmount != null) {
            result.add(customAmount);
        }
        result.addAll(scanMaterialCountLore(item));
        return result;
    }

    private static ScannedMaterial scanCustomAmount(ItemStack item) {
        var customData = item.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }

        Long amount = parseCustomAmount(customData.copyTag());
        String displayName = item.getHoverName().getString();
        if (amount == null || displayName.isBlank()) {
            return null;
        }
        return new ScannedMaterial(normalize(displayName), displayName, amount);
    }

    private static Long parseCustomAmount(CompoundTag tag) {
        CompoundTag values = tag.getCompound("PublicBukkitValues").orElse(null);
        if (values == null) {
            return null;
        }
        Integer amount = values.getInt("mcc:amount").orElse(null);
        return amount == null ? null : amount.longValue();
    }

    private static List<ScannedMaterial> scanMaterialCountLore(ItemStack item) {
        ItemLore lore = item.get(DataComponents.LORE);
        if (lore == null) {
            return List.of();
        }

        List<ScannedMaterial> result = new ArrayList<>();
        for (Component line : lore.styledLines()) {
            Matcher match = MATERIAL_COUNT_PATTERN.matcher(line.getString());
            if (match.find()) {
                String displayName = match.group(1);
                result.add(new ScannedMaterial(normalize(displayName), displayName, parseLong(match.group(2))));
            }
        }
        return result;
    }

    private static Cost parseCost(ItemStack item) {
        ItemLore lore = item.get(DataComponents.LORE);
        if (lore == null) {
            return null;
        }

        for (Component line : lore.styledLines()) {
            Matcher match = COST_PATTERN.matcher(line.getString());
            if (match.find()) {
                return new Cost(parseLong(match.group(1)), parseLong(match.group(2)));
            }
        }
        return null;
    }

    private static boolean isCancelClick(Slot clickedSlot, ItemStack clickedItem, MouseButtonEvent event) {
        if (clickedItem != null && clickedItem.getHoverName().getString().toUpperCase(Locale.ROOT).contains("CANCEL")) {
            return true;
        }
        if (clickedSlot != null) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        int panelLeft = (client.getWindow().getGuiScaledWidth() - 350) / 2;
        int panelTop = (client.getWindow().getGuiScaledHeight() - 188) / 2;
        int buttonTop = panelTop + 118;
        int buttonBottom = panelTop + 160;
        return event.y() >= buttonTop
            && event.y() <= buttonBottom
            && event.x() >= panelLeft + 193
            && event.x() <= panelLeft + 303;
    }

    private static boolean isMaterialMarket(Object screen) {
        return screen instanceof ContainerScreen container
            && container.getTitle().getString().toUpperCase(Locale.ROOT).contains("MATERIAL MARKET");
    }

    private static boolean isFusionForge(Object screen) {
        return screen instanceof ContainerScreen container
            && container.getTitle().getString().toUpperCase(Locale.ROOT).contains("FUSION FORGE");
    }

    private static boolean isScannableInventory(ContainerScreen screen) {
        String title = screen.getTitle().getString().toUpperCase(Locale.ROOT);
        if (title.contains("MATERIAL MARKET")) {
            return false;
        }
        return title.contains("RECIPE")
            || title.contains("BLUEPRINT ASSEMBLER")
            || title.contains("FUSION FORGE")
            || title.contains("CRAFT");
    }

    private static boolean isEnabled() {
        return McciTweaksConfigStore.get().materialMarket.enabled;
    }

    static boolean usesTraditionalInventory() {
        return McciTweaksConfigStore.get().materialMarket.usesTraditionalInventory();
    }

    private static boolean isOnMccServer() {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        return server != null
            && server.ip != null
            && server.ip.toLowerCase(Locale.ROOT).contains("mccisland.net");
    }

    private static Map<String, MaterialMarketInventoryStore.MaterialInventoryEntry> materialInventory(String playerId) {
        return MaterialMarketInventoryStore.inventoryFor(playerId);
    }

    private static Map<String, MaterialMarketInventoryStore.MaterialInventoryEntry> materialInventory() {
        String playerId = currentPlayerId();
        return playerId == null ? Map.of() : materialInventory(playerId);
    }

    private static String currentPlayerId() {
        Minecraft client = Minecraft.getInstance();
        return client.getGameProfile() == null ? null : client.getGameProfile().id().toString();
    }

    private static MaterialMarketInventoryStore.MaterialInventoryEntry findEntry(String name) {
        Map<String, MaterialMarketInventoryStore.MaterialInventoryEntry> inventory = materialInventory();
        String normalized = normalize(name);
        MaterialMarketInventoryStore.MaterialInventoryEntry direct = inventory.get(normalized);
        if (direct != null) {
            return direct;
        }

        String compact = normalized.replace(" ", "");
        for (Map.Entry<String, MaterialMarketInventoryStore.MaterialInventoryEntry> entry : inventory.entrySet()) {
            String assetName = entry.getKey();
            String assetCompact = assetName.replace(" ", "");
            if (assetName.contains(normalized)
                || normalized.contains(assetName)
                || assetCompact.contains(compact)
                || compact.contains(assetCompact)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static void upsert(List<ScannedMaterial> materials) {
        String playerId = currentPlayerId();
        if (playerId == null) {
            return;
        }

        long now = Instant.now().toEpochMilli();
        Map<String, MaterialMarketInventoryStore.MaterialInventoryEntry> inventory = materialInventory(playerId);
        boolean changed = false;

        for (ScannedMaterial material : materials) {
            long amount = Math.max(0L, material.amount);
            MaterialMarketInventoryStore.MaterialInventoryEntry existing = inventory.get(material.key);
            if (existing != null
                && existing.displayName.equals(material.displayName)
                && existing.amount == amount) {
                continue;
            }

            inventory.put(
                material.key,
                new MaterialMarketInventoryStore.MaterialInventoryEntry(material.displayName, amount, now)
            );
            changed = true;
        }

        if (changed) {
            MaterialMarketInventoryStore.save();
        }
    }

    private static void adjustAmount(String key, String displayName, long delta) {
        String playerId = currentPlayerId();
        if (playerId == null) {
            return;
        }

        Map<String, MaterialMarketInventoryStore.MaterialInventoryEntry> inventory = materialInventory(playerId);
        MaterialMarketInventoryStore.MaterialInventoryEntry existing = inventory.get(key);
        long newAmount = Math.max(0L, (existing == null ? 0L : existing.amount) + delta);
        String effectiveName = existing == null ? displayName : existing.displayName;
        inventory.put(
            key,
            new MaterialMarketInventoryStore.MaterialInventoryEntry(
                effectiveName,
                newAmount,
                Instant.now().toEpochMilli()
            )
        );
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.replace(",", ""));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static String normalize(String value) {
        return MaterialMarketApiClient.normalize(value);
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static String format(long value) {
        return String.format("%,d", value);
    }

    private record Cost(long availableBalance, long unitCost) {
    }

    private record ScannedMaterial(String key, String displayName, long amount) {
    }
}
