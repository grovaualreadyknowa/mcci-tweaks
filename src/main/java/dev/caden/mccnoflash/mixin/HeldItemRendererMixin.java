package dev.caden.mccnoflash.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.caden.mccnoflash.MccNoFlashState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemRendererMixin {
    @Unique
    private boolean mccNoFlash$overrodeModel;

    @Unique
    private boolean mccNoFlash$hadOriginalModel;

    @Unique
    private Identifier mccNoFlash$originalModel;

    @Inject(
        method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
        at = @At("HEAD")
    )
    private void mccNoFlash$beforeRenderItem(
        LivingEntity entity,
        ItemStack item,
        ItemDisplayContext displayContext,
        PoseStack poseStack,
        SubmitNodeCollector collector,
        int light,
        CallbackInfo ci
    ) {
        this.mccNoFlash$overrodeModel = false;
        this.mccNoFlash$hadOriginalModel = false;
        this.mccNoFlash$originalModel = null;

        if (item == null || item.isEmpty() || !displayContext.firstPerson()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        MccNoFlashState state = MccNoFlashState.get();
        int slot = mccNoFlash$resolveHandSlot(client.player, displayContext, item);
        if (slot < 0) {
            return;
        }

        if (state.isOnMccServer(client)) {
            state.captureRender(slot, item);
        }

        state.resolveOverrideItemModel(slot, item, client).ifPresent(modelId -> {
            Identifier previous = item.get(DataComponents.ITEM_MODEL);
            this.mccNoFlash$hadOriginalModel = previous != null;
            this.mccNoFlash$originalModel = previous;
            item.set(DataComponents.ITEM_MODEL, modelId);
            this.mccNoFlash$overrodeModel = true;
        });
    }

    @Inject(
        method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
        at = @At("RETURN")
    )
    private void mccNoFlash$afterRenderItem(
        LivingEntity entity,
        ItemStack item,
        ItemDisplayContext displayContext,
        PoseStack poseStack,
        SubmitNodeCollector collector,
        int light,
        CallbackInfo ci
    ) {
        if (!this.mccNoFlash$overrodeModel || item == null || item.isEmpty()) {
            return;
        }

        if (this.mccNoFlash$hadOriginalModel) {
            item.set(DataComponents.ITEM_MODEL, this.mccNoFlash$originalModel);
        } else {
            item.remove(DataComponents.ITEM_MODEL);
        }

        this.mccNoFlash$overrodeModel = false;
        this.mccNoFlash$hadOriginalModel = false;
        this.mccNoFlash$originalModel = null;
    }

    @Unique
    private int mccNoFlash$resolveHandSlot(LocalPlayer player, ItemDisplayContext displayContext, ItemStack item) {
        if (player == null || displayContext == null || item == null) {
            return -1;
        }

        if (displayContext.leftHand()) {
            return 40;
        }

        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i) == item) {
                return i;
            }
        }

        return -1;
    }
}
