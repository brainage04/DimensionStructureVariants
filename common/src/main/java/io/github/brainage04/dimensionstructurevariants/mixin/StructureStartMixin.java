package io.github.brainage04.dimensionstructurevariants.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.brainage04.dimensionstructurevariants.StructurePalettes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(StructureStart.class)
abstract class StructureStartMixin {
	@Shadow
	public abstract Structure getStructure();

	@WrapMethod(method = "placeInChunk")
	private void dimensionstructurevariants$placeWithPalette(WorldGenLevel level, StructureManager structures,
			ChunkGenerator generator, RandomSource random, BoundingBox bounds, ChunkPos chunk, Operation<Void> original) {
		Map<BlockState, BlockState> previous = StructurePalettes.enter(level, getStructure());
		try {
			original.call(level, structures, generator, random, bounds, chunk);
		} finally {
			StructurePalettes.restore(previous);
		}
	}
}
