package io.github.brainage04.dimensionstructurevariants;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Shared by both loaders: End island variants must never start over the void. */
public final class SolidGroundGameTests {
	private static final List<ResourceKey<Structure>> END_ISLAND_VARIANTS = List.of(
			ResourceKey.create(Registries.STRUCTURE, DimensionStructureVariants.of("village_end")),
			ResourceKey.create(Registries.STRUCTURE, DimensionStructureVariants.of("bastion_end"))
	);

	private SolidGroundGameTests() {
	}

	/**
	 * Probes start chunks in the empty ring around the main island (always void) and across the outer
	 * islands. Every accepted start must sit above the dimension floor, and the outer islands must still
	 * accept some starts so the variants keep generating.
	 */
	public static void endIslandVariantsOnlyStartOnSolidGround(GameTestHelper helper) {
		ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
		helper.assertTrue(end != null, "The End must be loaded");
		ChunkGenerator generator = end.getChunkSource().getGenerator();
		var registries = end.registryAccess();
		var structures = registries.lookupOrThrow(Registries.STRUCTURE);
		int floor = end.getMinY();
		for (ResourceKey<Structure> key : END_ISLAND_VARIANTS) {
			Structure structure = structures.getOrThrow(key).value();
			int acceptedOnIslands = 0;
			for (ChunkPos chunk : sampleChunks()) {
				Structure.GenerationContext context = new Structure.GenerationContext(registries, generator,
						generator.getBiomeSource(), end.getChunkSource().randomState(),
						end.getServer().getStructureManager(), end.getSeed(), chunk, end, structure.biomes()::contains);
				Optional<Structure.GenerationStub> stub = structure.findValidGenerationPoint(context);
				if (stub.isEmpty()) {
					continue;
				}
				int startY = stub.get().position().getY();
				helper.assertTrue(startY > floor,
						key.identifier() + " started over the void at chunk " + chunk + " (y=" + startY + ")");
				acceptedOnIslands++;
			}
			helper.assertTrue(acceptedOnIslands > 0, key.identifier() + " no longer starts on any sampled End island");
		}
		helper.succeed();
	}

	private static List<ChunkPos> sampleChunks() {
		List<ChunkPos> chunks = new ArrayList<>();
		// Void ring between the main island and the outer islands (chunk radius < 64).
		for (int radius = 16; radius <= 56; radius += 8) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx != 0 || dz != 0) {
						chunks.add(new ChunkPos(dx * radius, dz * radius));
					}
				}
			}
		}
		// Outer islands and the gaps between them.
		for (int x = -128; x <= 128; x += 12) {
			for (int z = -128; z <= 128; z += 12) {
				if ((long) x * x + (long) z * z > 72L * 72L) {
					chunks.add(new ChunkPos(x, z));
				}
			}
		}
		return chunks;
	}
}
