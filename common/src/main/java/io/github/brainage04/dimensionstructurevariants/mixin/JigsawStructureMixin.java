package io.github.brainage04.dimensionstructurevariants.mixin;

import io.github.brainage04.dimensionstructurevariants.SolidGround;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** Rejects void starts before jigsaw assembly; covers natural generation and {@code /locate}. */
@Mixin(JigsawStructure.class)
abstract class JigsawStructureMixin {
	@Inject(method = "findGenerationPoint", at = @At("HEAD"), cancellable = true)
	private void dimensionstructurevariants$requireSolidGround(Structure.GenerationContext context,
			CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
		Structure structure = (Structure) (Object) this;
		if (SolidGround.isRequiredBy(structure, context) && !SolidGround.isPresent(context)) {
			cir.setReturnValue(Optional.empty());
		}
	}
}
