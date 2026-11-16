package dev.caden.mccitweaks.materialmarket.mixin;

import dev.caden.mccitweaks.materialmarket.MaterialMarketInventory;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Watches the labeled controls in Minecraft's server-driven dialog screen. */
@Mixin(Button.class)
public abstract class MaterialMarketDialogButtonMixin {
    @Inject(method = "onPress", at = @At("HEAD"))
    private void mcciTweaks$blueprintDialogPressed(InputWithModifiers input, CallbackInfo ci) {
        MaterialMarketInventory.onBlueprintDialogButton(((Button) (Object) this).getMessage());
    }
}
