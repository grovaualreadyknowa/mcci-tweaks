package dev.caden.mccitweaks.materialmarket.mixin;

import dev.caden.mccitweaks.materialmarket.MaterialMarketInventory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ItemStack.class)
public abstract class MaterialMarketItemStackMixin {
    @Inject(
        method = "addDetailsToTooltip",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/TooltipFlag;isAdvanced()Z",
            shift = At.Shift.AFTER
        )
    )
    private void mcciTweaks$addMaterialCount(
        Item.TooltipContext tooltipContext,
        TooltipDisplay tooltipDisplay,
        @Nullable Player player,
        TooltipFlag tooltipFlag,
        Consumer<Component> consumer,
        CallbackInfo ci
    ) {
        MaterialMarketInventory.modifyTooltip((ItemStack) (Object) this, consumer);
    }
}
