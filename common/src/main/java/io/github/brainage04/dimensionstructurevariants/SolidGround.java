package io.github.brainage04.dimensionstructurevariants;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Keeps floating-island variants off the void. Jigsaw structures project their start onto the
 * surface heightmap, which over the End's void is the dimension floor, so a start there would build
 * at the bottom of the world. Structures in {@link #REQUIRED_BY} only start where terrain exists under
 * the core of their footprint.
 */
public final class SolidGround {
	public static final TagKey<Structure> REQUIRED_BY =
			TagKey.create(Registries.STRUCTURE, DimensionStructureVariants.of("requires_solid_ground"));

	/** Half-width of the sampled square around the jigsaw start (the start chunk's minimum corner). */
	private static final int FOOTPRINT_RADIUS = 32;
	private static final int SAMPLE_STEP = 16;

	private SolidGround() {
	}

	public static boolean isRequiredBy(Structure structure, Structure.GenerationContext context) {
		return context.registryAccess().lookupOrThrow(Registries.STRUCTURE).wrapAsHolder(structure).is(REQUIRED_BY);
	}

	public static boolean isPresent(Structure.GenerationContext context) {
		ChunkGenerator generator = context.chunkGenerator();
		LevelHeightAccessor heightAccessor = context.heightAccessor();
		RandomState randomState = context.randomState();
		ChunkPos chunk = context.chunkPos();
		int startX = chunk.getMinBlockX();
		int startZ = chunk.getMinBlockZ();
		int floor = heightAccessor.getMinY();
		for (int dx = -FOOTPRINT_RADIUS; dx <= FOOTPRINT_RADIUS; dx += SAMPLE_STEP) {
			for (int dz = -FOOTPRINT_RADIUS; dz <= FOOTPRINT_RADIUS; dz += SAMPLE_STEP) {
				int surface = generator.getFirstOccupiedHeight(startX + dx, startZ + dz,
						Heightmap.Types.WORLD_SURFACE_WG, heightAccessor, randomState);
				if (surface <= floor) {
					return false;
				}
			}
		}
		return true;
	}
}
