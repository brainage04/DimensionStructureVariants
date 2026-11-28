package io.github.brainage04.dimensionstructurevariants;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/** Shared by both loaders: End island variants must never start over the void. */
public final class SolidGroundGameTests {
    private static final List<ResourceKey<Structure>> END_ISLAND_VARIANTS =
            List.of(
                    ResourceKey.create(
                            Registries.STRUCTURE, DimensionStructureVariants.of("village_end")),
                    ResourceKey.create(
                            Registries.STRUCTURE, DimensionStructureVariants.of("bastion_end")));

    private static final List<ResourceKey<Structure>> LEVEL_GROUND_VARIANTS =
            List.of(
                    END_ISLAND_VARIANTS.get(0),
                    END_ISLAND_VARIANTS.get(1),
                    ResourceKey.create(
                            Registries.STRUCTURE, DimensionStructureVariants.of("fortress_end")));

    /** Chunks between the sampled starts across the outer islands. */
    private static final int OUTER_SAMPLE_STEP = 5;

    private SolidGroundGameTests() {}

    /**
     * Probes start chunks in the empty ring around the main island (always void) and across the
     * outer islands. Every accepted start must sit above the dimension floor, and the outer islands
     * must still accept some starts so the variants keep generating.
     */
    public static void endIslandVariantsOnlyStartOnSolidGround(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "The End must be loaded");
        ChunkGenerator generator = end.getChunkSource().getGenerator();
        var registries = end.registryAccess();
        var structures = registries.lookupOrThrow(Registries.STRUCTURE);
        int floor = end.getMinY();
        RandomState randomState = end.getChunkSource().randomState();
        for (ResourceKey<Structure> key : END_ISLAND_VARIANTS) {
            Structure structure = structures.getOrThrow(key).value();
            int acceptedOnIslands = 0;
            for (ChunkPos chunk : sampleChunks()) {
                Structure.GenerationContext context =
                        new Structure.GenerationContext(
                                registries,
                                generator,
                                generator.getBiomeSource(),
                                randomState.createClimateSampler(
                                        SamplerContext.builder().enableCaches().build()),
                                randomState,
                                end.getServer().getStructureTemplateManager(),
                                end.getSeed(),
                                chunk,
                                end,
                                structure.biomes()::contains);
                Optional<Structure.GenerationStub> stub =
                        structure.findValidGenerationPoint(context);
                if (stub.isEmpty()) {
                    continue;
                }
                int startY = stub.get().position().getY();
                helper.assertTrue(
                        startY > floor,
                        key.identifier()
                                + " started over the void at chunk "
                                + chunk
                                + " (y="
                                + startY
                                + ")");
                for (int dx = -32; dx <= 32; dx += 16) {
                    for (int dz = -32; dz <= 32; dz += 16) {
                        int surface =
                                generator.getFirstOccupiedHeight(
                                        chunk.getMinBlockX() + dx,
                                        chunk.getMinBlockZ() + dz,
                                        net.minecraft.world.level.levelgen.Heightmap.Types
                                                .WORLD_SURFACE_WG,
                                        end,
                                        end.getChunkSource().randomState());
                        helper.assertTrue(
                                surface > floor,
                                key.identifier()
                                        + " accepted an unsupported footprint at chunk "
                                        + chunk);
                    }
                }
                acceptedOnIslands++;
            }
            helper.assertTrue(
                    acceptedOnIslands > 0,
                    key.identifier() + " no longer starts on any sampled End island");
        }
        helper.succeed();
    }

    /**
     * A generated End island start keeps the island's surface within reach of the structure over
     * all but {@code SolidGround.MAX_LOW_SHARE} of its footprint, so terrain adaptation has no
     * island to stretch (owner, 28 Nov 2026: the End bastion "had to extend the island for all of
     * it to fit"). The sampled chunks hold fortress starts over the void that the footprint check
     * rejects: the start point alone accepts them.
     */
    public static void endIslandStartsSitOnAnIslandThatReachesThem(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "The End must be loaded");
        ChunkGenerator generator = end.getChunkSource().getGenerator();
        var registries = end.registryAccess();
        var structures = registries.lookupOrThrow(Registries.STRUCTURE);
        RandomState randomState = end.getChunkSource().randomState();
        for (ResourceKey<Structure> key : LEVEL_GROUND_VARIANTS) {
            Holder<Structure> structure = structures.getOrThrow(key);
            int reaching = 0;
            int perched = 0;
            double maxShare = 0.0D;
            for (ChunkPos chunk : sampleChunks()) {
                Optional<Structure.GenerationStub> stub =
                        structure.value().findValidGenerationPoint(context(end, chunk));
                if (stub.isEmpty()) {
                    continue;
                }
                StructureStart assembled =
                        new StructureStart(
                                structure.value(), chunk, 0, stub.get().getPiecesBuilder().build());
                if (!assembled.isValid()) {
                    continue;
                }
                boolean reaches = SolidGround.isLevelUnder(assembled, generator, end, randomState);
                StructureStart generated =
                        structure
                                .value()
                                .generate(
                                        structure,
                                        end.dimension(),
                                        registries,
                                        generator,
                                        generator.getBiomeSource(),
                                        randomState.createClimateSampler(
                                                SamplerContext.builder().enableCaches().build()),
                                        randomState,
                                        end.getStructureTemplateManager(),
                                        end.getSeed(),
                                        chunk,
                                        0,
                                        end,
                                        biome -> true);
                helper.assertTrue(
                        generated.isValid() == reaches,
                        key.identifier()
                                + " at "
                                + chunk
                                + (reaches
                                        ? " has an island that reaches it but was refused"
                                        : " has an island that does not reach it but was accepted"));
                maxShare =
                        Math.max(
                                maxShare,
                                SolidGround.lowShare(assembled, generator, end, randomState));
                if (reaches) {
                    reaching++;
                } else {
                    perched++;
                }
            }
            helper.assertTrue(
                    reaching > 0, key.identifier() + " must still start on some sampled island");
            if (key.identifier().getPath().equals("fortress_end")) {
                helper.assertTrue(
                        perched > 0,
                        key.identifier()
                                + " must have a sampled start on an island that does not reach it,"
                                + " reaching "
                                + reaching
                                + ", largest low share "
                                + maxShare);
            }
        }
        helper.succeed();
    }

    private static Structure.GenerationContext context(ServerLevel end, ChunkPos chunk) {
        ChunkGenerator generator = end.getChunkSource().getGenerator();
        RandomState randomState = end.getChunkSource().randomState();
        return new Structure.GenerationContext(
                end.registryAccess(),
                generator,
                generator.getBiomeSource(),
                randomState.createClimateSampler(SamplerContext.builder().enableCaches().build()),
                randomState,
                end.getStructureTemplateManager(),
                end.getSeed(),
                chunk,
                end,
                biome -> true);
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
        for (int x = -128; x <= 128; x += OUTER_SAMPLE_STEP) {
            for (int z = -128; z <= 128; z += OUTER_SAMPLE_STEP) {
                if ((long) x * x + (long) z * z > 72L * 72L) {
                    chunks.add(new ChunkPos(x, z));
                }
            }
        }
        return chunks;
    }
}
