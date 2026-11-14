package io.github.brainage04.dimensionstructurevariants.recording.mixin;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.TimeoutException;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ignores the 30-second read timeout on both ends of the recording's connection. While a long
 * server task holds the paused GameTest client, neither side sends anything, and vanilla would drop
 * the camera player. Only the client GameTest loads this.
 */
@Mixin(Connection.class)
abstract class ConnectionMixin {
    @Inject(method = "exceptionCaught", at = @At("HEAD"), cancellable = true)
    private void dimensionstructurevariants$ignoreReadTimeout(
            ChannelHandlerContext ctx, Throwable cause, CallbackInfo ci) {
        if (cause instanceof TimeoutException) ci.cancel();
    }
}
