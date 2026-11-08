package io.github.brainage04.dimensionstructurevariants.gametest.mixin;

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
}
