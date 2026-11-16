package dev.caden.mccitweaks.chatmixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.caden.mccitweaks.chat.ChatTabBar;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void mcciTweaks$handleTabClick(
        MouseButtonEvent event,
        boolean doubleClick,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && ChatTabBar.handleClick(event.x(), event.y())) {
            callbackInfo.setReturnValue(true);
        }
    }
}
