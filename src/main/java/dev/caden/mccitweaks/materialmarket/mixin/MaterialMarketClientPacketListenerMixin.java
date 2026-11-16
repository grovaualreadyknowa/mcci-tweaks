package dev.caden.mccitweaks.materialmarket.mixin;

import dev.caden.mccitweaks.materialmarket.MaterialMarketInventory;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Distinguishes a confirmed server inventory update from the client-side Confirm click. */
@Mixin(ClientPacketListener.class)
public abstract class MaterialMarketClientPacketListenerMixin {
    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void mcciTweaks$blueprintSlotUpdated(
        ClientboundContainerSetSlotPacket packet,
        CallbackInfo ci
    ) {
        MaterialMarketInventory.onServerContainerUpdated(packet.getContainerId());
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void mcciTweaks$blueprintContentsUpdated(
        ClientboundContainerSetContentPacket packet,
        CallbackInfo ci
    ) {
        MaterialMarketInventory.onServerContainerUpdated(packet.containerId());
    }
}
