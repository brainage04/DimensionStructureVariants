package io.github.brainage04.dimensionstructurevariants.recording.mixin;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the recording's camera player connected while the test holds the client. A client GameTest
 * pauses the client while a long server task runs (loading and placing the next stage can take 45
 * seconds on a loaded test host), so it cannot answer the server's keepalive, which vanilla drops
 * after 15 seconds. Only the client GameTest loads this.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
abstract class ServerCommonPacketListenerImplMixin {
    @Inject(method = "keepConnectionAlive", at = @At("HEAD"), cancellable = true)
    private void dimensionstructurevariants$skipKeepAlive(CallbackInfo ci) {
        ci.cancel();
    }

    /**
     * Drops the hurt flash of a mob hit by a spear. Minecraft 26.3 registers the spear damage type
     * in code but the copied registry the connection encodes with does not carry it, so the packet
     * fails to encode and vanilla disconnects the camera player: one parched or piglin hitting
     * another mob ended the film. The flash is the only thing lost.
     */
    @Inject(
            method =
                    "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void dimensionstructurevariants$skipDamageEvents(
            Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
        if (packet instanceof ClientboundDamageEventPacket) ci.cancel();
    }
}
