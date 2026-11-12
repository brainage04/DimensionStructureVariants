package io.github.brainage04.dimensionstructurevariants.gametest.mixin;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.client.resources.sounds.BiomeAmbientSoundsHandler;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restarts a biome ambient loop the sound engine dropped, for the recording only (A-0375).
 *
 * <p>A biome's loop voice is streamed ({@code assets/minecraft/sounds.json} gives {@code
 * ambient.<biome>.loop} {@code "stream": true}), so the engine queues four one-second buffers
 * ({@code Channel.java:123-149}) and refills them only from {@code ChannelAccess.scheduleTick()}
 * ({@code ChannelAccess.java:47-60}), which {@code SoundEngine.tick} reaches once per frame ({@code
 * SoundEngine.java:227}). Placing the next stage's village blocks the client for 8 to 10 seconds
 * ({@code Can't keep up! ... 9791ms} in the audit of 2026-11-12), long enough for those buffers to
 * run dry: the source reaches AL_STOPPED, the engine releases the channel and forgets the instance,
 * and this handler keeps its {@code loopSounds} entry, so vanilla never replays the loop while the
 * loop event stays the same ({@code BiomeAmbientSoundsHandler.java:48-61}). Two stages of one biome
 * in a row then give a whole silent shot. Forget the dropped instance and the handler's own logic
 * recreates and fades it in on its next tick.
 */
@Mixin(BiomeAmbientSoundsHandler.class)
abstract class AmbientLoopKeeperMixin {
    private static final int DEAD_TICKS_BEFORE_RESTART = 5;

    @Shadow
    private Object2ObjectArrayMap<Holder<SoundEvent>, BiomeAmbientSoundsHandler.LoopSoundInstance>
            loopSounds;

    @Shadow private Holder<SoundEvent> previousLoopSound;

    @Shadow private SoundManager soundManager;

    private int dimensionstructurevariants$deadTicks;

    @Inject(method = "tick", at = @At("TAIL"))
    private void dimensionstructurevariants$restartDroppedLoop(CallbackInfo ci) {
        BiomeAmbientSoundsHandler.LoopSoundInstance instance =
                this.previousLoopSound == null ? null : this.loopSounds.get(this.previousLoopSound);
        if (instance == null || instance.isStopped() || this.soundManager.isActive(instance)) {
            this.dimensionstructurevariants$deadTicks = 0;
            return;
        }
        if (++this.dimensionstructurevariants$deadTicks < DEAD_TICKS_BEFORE_RESTART) return;
        this.dimensionstructurevariants$deadTicks = 0;
        this.loopSounds.remove(this.previousLoopSound);
        System.out.println(
                "[DSV] the sound engine dropped this stage's ambient loop ("
                        + this.previousLoopSound.getRegisteredName()
                        + "), restarted it");
        this.previousLoopSound = null;
    }
}
