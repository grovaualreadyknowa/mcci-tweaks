package dev.caden.mccitweaks.chatmixin;

import dev.caden.mccitweaks.chat.ChatTabs;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {
    @Inject(method = "onDisconnected", at = @At("HEAD"))
    private void mcciTweaks$resetSelectedTab(CallbackInfo callbackInfo) {
        ChatTabs.resetSelection();
    }
}
