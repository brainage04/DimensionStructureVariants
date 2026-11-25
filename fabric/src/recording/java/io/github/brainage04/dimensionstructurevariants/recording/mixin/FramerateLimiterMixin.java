package io.github.brainage04.dimensionstructurevariants.recording.mixin;

import io.github.brainage04.dimensionstructurevariants.ShowcaseCamera;
import net.minecraft.client.FramerateLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spaces the frames of a shot a capture slot apart: the game calls the limiter right after it
 * presented a frame ({@code Minecraft.runTick}, after {@code windowSurface.present()}).
 */
@Mixin(FramerateLimiter.class)
abstract class FramerateLimiterMixin {
    @Inject(method = "limitDisplayFPS", at = @At("HEAD"))
    private static void dimensionstructurevariants$spaceFrames(
            int framerateLimit, CallbackInfo ci) {
        ShowcaseCamera.spaceFrames();
    }
}
