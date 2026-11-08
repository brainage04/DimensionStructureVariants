package io.github.brainage04.dimensionstructurevariants.gametest.mixin;

import io.github.brainage04.dimensionstructurevariants.ShowcaseCamera;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Puts the recording's camera where {@link ShowcaseCamera} says, every frame. */
@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "tick", at = @At("HEAD"))
    private void dimensionstructurevariants$countTick(CallbackInfo ci) {
        ShowcaseCamera.onTick();
    }

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void dimensionstructurevariants$fly(float partialTicks, CallbackInfo ci) {
        ShowcaseCamera.Pose pose = ShowcaseCamera.framePose(partialTicks);
        if (pose == null) return;
        setRotation(pose.yaw(), pose.pitch());
        setPosition(pose.x(), pose.y(), pose.z());
    }
}
