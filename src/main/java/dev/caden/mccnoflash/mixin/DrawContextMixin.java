package dev.caden.mccnoflash.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.caden.mccnoflash.MccNoFlashState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

@Mixin(GuiGraphicsExtractor.class)
public abstract class DrawContextMixin {
    @Unique
    private boolean mccNoFlash$overrodeInventoryModel;

    @Unique
    private boolean mccNoFlash$hadInventoryOriginalModel;

    @Unique
    private Identifier mccNoFlash$inventoryOriginalModel;

    @Inject(method = "item(Lnet/minecraft/world/item/ItemStack;II)V", at = @At("HEAD"))
    private void mccNoFlash$beforeItem(ItemStack stack, int x, int y, CallbackInfo ci) {
        mccNoFlash$beforeItemRender(null, stack);
    }

    @Inject(method = "item(Lnet/minecraft/world/item/ItemStack;III)V", at = @At("HEAD"))
    private void mccNoFlash$beforeItemSeed(ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        mccNoFlash$beforeItemRender(null, stack);
    }

    @Inject(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;III)V", at = @At("HEAD"))
    private void mccNoFlash$beforeEntityItem(LivingEntity entity, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        mccNoFlash$beforeItemRender(entity, stack);
    }

    @Inject(method = "item(Lnet/minecraft/world/item/ItemStack;II)V", at = @At("RETURN"))
    private void mccNoFlash$afterItem(ItemStack stack, int x, int y, CallbackInfo ci) {
        mccNoFlash$afterItemRender(stack);
    }

    @Inject(method = "item(Lnet/minecraft/world/item/ItemStack;III)V", at = @At("RETURN"))
    private void mccNoFlash$afterItemSeed(ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        mccNoFlash$afterItemRender(stack);
    }

    @Inject(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;III)V", at = @At("RETURN"))
    private void mccNoFlash$afterEntityItem(LivingEntity entity, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        mccNoFlash$afterItemRender(stack);
    }

    @Unique
    private void mccNoFlash$beforeItemRender(LivingEntity entity, ItemStack stack) {
        this.mccNoFlash$overrodeInventoryModel = false;
        this.mccNoFlash$hadInventoryOriginalModel = false;
        this.mccNoFlash$inventoryOriginalModel = null;

        if (stack == null || stack.isEmpty()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        MccNoFlashState state = MccNoFlashState.get();

        if (state.isSupportedInventoryScreen(client.gui.screen())) {
            state.resolveInventoryOverrideItemModel(stack, client).ifPresent(modelId -> mccNoFlash$applyOverride(stack, modelId));
            return;
        }

        Player player = entity instanceof Player p ? p : client.player;
        int slot = mccNoFlash$resolveHotbarSlot(player, stack);
        if (slot < 0) {
            return;
        }

        if (state.isOnMccServer(client)) {
            state.captureRender(slot, stack);
        }

        state.resolveOverrideItemModel(slot, stack, client).ifPresent(modelId -> mccNoFlash$applyOverride(stack, modelId));
    }

    @Unique
    private void mccNoFlash$applyOverride(ItemStack stack, Identifier modelId) {
        Identifier previous = stack.get(DataComponents.ITEM_MODEL);
        this.mccNoFlash$hadInventoryOriginalModel = previous != null;
        this.mccNoFlash$inventoryOriginalModel = previous;
        stack.set(DataComponents.ITEM_MODEL, modelId);
        this.mccNoFlash$overrodeInventoryModel = true;
    }

    @Unique
    private void mccNoFlash$afterItemRender(ItemStack stack) {
        if (!this.mccNoFlash$overrodeInventoryModel || stack == null || stack.isEmpty()) {
            return;
        }

        if (this.mccNoFlash$hadInventoryOriginalModel) {
            stack.set(DataComponents.ITEM_MODEL, this.mccNoFlash$inventoryOriginalModel);
        } else {
            stack.remove(DataComponents.ITEM_MODEL);
        }

        this.mccNoFlash$overrodeInventoryModel = false;
        this.mccNoFlash$hadInventoryOriginalModel = false;
        this.mccNoFlash$inventoryOriginalModel = null;
    }

    @Unique
    private int mccNoFlash$resolveHotbarSlot(Player player, ItemStack target) {
        if (player == null || player.getInventory() == null) {
            return -1;
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack == target) {
                return i;
            }
        }

        return -1;
    }
}

