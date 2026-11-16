package dev.caden.mccnoflash.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caden.mccnoflash.MccNoFlashState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonHandsAndItemsMixin {
    @WrapOperation(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemModelResolver;updateForTopItem(Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/ItemOwner;I)V"
        )
    )
    private void mccNoFlash$resolveHeldItem(
        ItemModelResolver resolver,
        ItemStackRenderState renderState,
        ItemStack item,
        ItemDisplayContext displayContext,
        Level level,
        ItemOwner owner,
        int seed,
        Operation<Void> original
    ) {
        ItemStack renderedItem = item;
        Minecraft client = Minecraft.getInstance();
        if (!item.isEmpty() && displayContext.firstPerson() && owner instanceof LocalPlayer player && player == client.player) {
            int slot = mccNoFlash$resolveHandSlot(player, item);
            if (slot >= 0) {
                MccNoFlashState state = MccNoFlashState.get();
                if (state.isOnMccServer(client)) {
                    state.captureRender(slot, item);
                }
                Identifier modelId = state.resolveOverrideItemModel(slot, item, client).orElse(null);
                if (modelId != null) {
                    // Resolve the replacement while extracting render state, without changing the inventory stack.
                    renderedItem = item.copy();
                    renderedItem.set(DataComponents.ITEM_MODEL, modelId);
                }
            }
        }
        original.call(resolver, renderState, renderedItem, displayContext, level, owner, seed);
    }

    @Unique
    private int mccNoFlash$resolveHandSlot(LocalPlayer player, ItemStack item) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i) == item) {
                return i;
            }
        }
        return player.getOffhandItem() == item ? 40 : -1;
    }
}
