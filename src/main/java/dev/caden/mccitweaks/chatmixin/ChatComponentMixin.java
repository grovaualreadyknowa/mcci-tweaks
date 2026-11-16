package dev.caden.mccitweaks.chatmixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import dev.caden.mccitweaks.chat.ChatSeparator;
import dev.caden.mccitweaks.chat.ChatTabBar;
import dev.caden.mccitweaks.chat.ChatTabs;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    /** Keep every message in history so switching tabs can show messages that were filtered out earlier. */
    @ModifyExpressionValue(
        method = "addMessage",
        at = @At(value = "INVOKE", target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z")
    )
    private boolean mcciTweaks$storeEveryMessage(boolean original) {
        return ChatSeparator.isEnabled() || original;
    }

    @WrapWithCondition(
        method = "addMessage",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessageToDisplayQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V"
        )
    )
    private boolean mcciTweaks$displaySelectedMessage(ChatComponent chat, GuiMessage message) {
        return ChatTabs.isVisible(message);
    }

    @ModifyConstant(
        method = {"addMessageToDisplayQueue", "addMessageToQueue"},
        constant = @Constant(intValue = 100)
    )
    private int mcciTweaks$keepMoreMessages(int original) {
        return ChatSeparator.isEnabled() ? ChatTabs.HISTORY_LIMIT : original;
    }

    @Inject(
        method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
        at = @At("RETURN")
    )
    private void mcciTweaks$renderTabs(
        GuiGraphicsExtractor graphics,
        Font font,
        int ticks,
        int mouseX,
        int mouseY,
        ChatComponent.DisplayMode displayMode,
        boolean changeCursorOnInsertions,
        CallbackInfo callbackInfo
    ) {
        ChatTabBar.render(graphics, mouseX, mouseY, displayMode);
    }
}
