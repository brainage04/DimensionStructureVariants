package io.github.brainage04.dimensionstructurevariants.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.brainage04.dimensionstructurevariants.CaveFloor;
import io.github.brainage04.dimensionstructurevariants.SolidGround;
import java.util.Optional;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rejects void starts before jigsaw assembly and moves cave-floor starts onto a floor; covers
 * natural generation and {@code /locate}.
 */
@Mixin(JigsawStructure.class)
abstract class JigsawStructureMixin {
    @Inject(method = "findGenerationPoint", at = @At("HEAD"), cancellable = true)
    private void dimensionstructurevariants$requireSolidGround(
            Structure.GenerationContext context,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        Structure structure = (Structure) (Object) this;
        if (SolidGround.isRequiredBy(structure, context) && !SolidGround.isPresent(context)) {
            cir.setReturnValue(Optional.empty());
        }
    }

    @WrapOperation(
            method = "findGenerationPoint",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/levelgen/heightproviders/HeightProvider;sample(Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;)I"))
    private int dimensionstructurevariants$startOnCaveFloor(
            HeightProvider provider,
            RandomSource random,
            WorldGenerationContext heightContext,
            Operation<Integer> original,
            Structure.GenerationContext context) {
        int height = original.call(provider, random, heightContext);
        Structure structure = (Structure) (Object) this;
        return CaveFloor.isUsedBy(structure, context) ? CaveFloor.find(context, height) : height;
    }
}
