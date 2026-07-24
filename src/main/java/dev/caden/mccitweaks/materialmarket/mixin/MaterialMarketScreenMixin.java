package dev.caden.mccitweaks.materialmarket.mixin;

import dev.caden.mccitweaks.materialmarket.MaterialMarketInventory;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class MaterialMarketScreenMixin extends Screen {
    @Shadow
    protected Slot hoveredSlot;

    protected MaterialMarketScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mcciTweaks$materialMarketOpened(CallbackInfo ci) {
        if ((Object) this instanceof ContainerScreen screen) {
            MaterialMarketInventory.onScreenOpened(screen);
        }
    }

    @Inject(method = "onClose", at = @At("HEAD"))
    private void mcciTweaks$materialMarketClosed(CallbackInfo ci) {
        if ((Object) this instanceof ContainerScreen screen) {
            MaterialMarketInventory.onScreenClosed(screen);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void mcciTweaks$materialMarketClicked(
        MouseButtonEvent event,
        boolean doubleClick,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if ((Object) this instanceof ContainerScreen screen) {
            MaterialMarketInventory.onScreenClick(screen, this.hoveredSlot, event);
        }
    }
}
