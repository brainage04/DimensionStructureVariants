package io.github.brainage04.dimensionstructurevariants;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jspecify.annotations.Nullable;

/** Shared by both loaders: structures placed the way natural generation places them. */
public final class NaturalGenerationGameTests {
    private static final ResourceKey<Structure> FORTRESS_OVERWORLD =
            ResourceKey.create(
                    Registries.STRUCTURE, DimensionStructureVariants.of("fortress_overworld"));

    /** Far from every other test's structure, so the placed pieces disturb none of them. */
    private static final ChunkPos VANILLA_START = new ChunkPos(1000, 1000);

    private static final ChunkPos VARIANT_START = new ChunkPos(1000, 1040);

    private static final ResourceKey<Structure> VILLAGE_NETHER =
            ResourceKey.create(Registries.STRUCTURE, DimensionStructureVariants.of("village_nether"));

    private static final ChunkPos VILLAGE_START = new ChunkPos(1000, 1080);

    private static final long SEED = 0L;

    private NaturalGenerationGameTests() {}

    /**
     * Natural generation places a start's pieces chunk by chunk, each through the region of the
     * FEATURES step that decorates the chunk ({@code ChunkGenerator.java:376-388}). That region
     * hands out its neighbours only up to {@code structure_starts} beyond one chunk ({@code
     * ChunkPyramid.java:24-29}) and throws for anything more ({@code WorldGenRegion.java:131-158}),
     * so a start whose centre lies two or more chunks away must be placed without reading its
     * centre's chunk. A GameTest world makes no structure starts ({@code GameTestServer.java:92}),
     * so the test builds the region as the FEATURES step does and places a vanilla Nether fortress
     * and the Overworld fortress variant in their chunk farthest from their centre: the vanilla one
     * keeps its nether bricks, the variant wears the palette of the biome at its centre (plains,
     * the flat Overworld's only biome).
     */
    public static void structuresPlaceFromAChunkAwayFromTheirCentre(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState netherBricks = Blocks.NETHER_BRICKS.defaultBlockState();

        StructureStart vanilla = start(helper, level, BuiltinStructures.FORTRESS, VANILLA_START);
        ChunkAccess vanillaChunk = placeFarFromCentre(helper, level, vanilla);
        helper.assertTrue(
                count(level, vanillaChunk, netherBricks::equals) > 0,
                "The vanilla fortress must keep its nether bricks");

        StructureStart variant = start(helper, level, FORTRESS_OVERWORLD, VARIANT_START);
        ChunkAccess variantChunk = placeFarFromCentre(helper, level, variant);
        StructurePalettes.Choice bricks =
                StructurePalettes.palette(
                                FORTRESS_OVERWORLD.identifier(),
                                level.registryAccess()
                                        .lookupOrThrow(Registries.BIOME)
                                        .getOrThrow(Biomes.PLAINS))
                        .choiceFor(netherBricks);
        helper.assertTrue(
                count(level, variantChunk, netherBricks::equals) == 0,
                "The fortress variant must not leave vanilla nether bricks");
        helper.assertTrue(
                count(level, variantChunk, state -> bricks.states().contains(state)) > 0,
                "The fortress variant must build its walls from the plains palette");
        helper.succeed();
    }

    /**
     * The Nether village stands one block above the first free block its generator reports, so a
     * Nether whose terrain a datapack moves keeps the village on its surface. The test level's
     * flat Overworld puts that block far below the vanilla Nether roof, where the village used to
     * start whatever the terrain.
     */
    public static void theNetherVillageStandsOnItsGeneratorsSurface(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureStart start = start(helper, level, VILLAGE_NETHER, VILLAGE_START);
        PoolElementStructurePiece centre =
                (PoolElementStructurePiece) start.getPieces().getFirst();
        BoundingBox box = centre.getBoundingBox();
        int surface =
                level.getChunkSource()
                        .getGenerator()
                        .getFirstFreeHeight(
                                (box.minX() + box.maxX()) / 2,
                                (box.minZ() + box.maxZ()) / 2,
                                Heightmap.Types.WORLD_SURFACE_WG,
                                level,
                                level.getChunkSource().randomState());
        helper.assertValueEqual(
                box.minY() + centre.getGroundLevelDelta(),
                surface + 1,
                "the Nether village's floor height");
        helper.succeed();
    }

    private static StructureStart start(
            GameTestHelper helper, ServerLevel level, ResourceKey<Structure> key, ChunkPos chunk) {
        Holder<Structure> structure =
                level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(key);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();
        StructureStart start =
                structure
                        .value()
                        .generate(
                                structure,
                                level.dimension(),
                                level.registryAccess(),
                                generator,
                                generator.getBiomeSource(),
                                randomState.createClimateSampler(
                                        SamplerContext.builder().enableCaches().build()),
                                randomState,
                                level.getStructureTemplateManager(),
                                SEED,
                                chunk,
                                0,
                                level,
                                biome -> true);
        helper.assertTrue(start.isValid(), key.identifier() + " generated no pieces");
        return start;
    }

    /**
     * Places {@code start} in the chunk its pieces fill most among those farthest from its centre,
     * through a FEATURES region around that chunk, and returns the chunk.
     */
    private static ChunkAccess placeFarFromCentre(
            GameTestHelper helper, ServerLevel level, StructureStart start) {
        ChunkPos centre = ChunkPos.containing(start.getBoundingBox().getCenter());
        ChunkPos target = null;
        int farthest = -1;
        long filled = 0L;
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            for (ChunkPos chunk :
                    ChunkPos.rangeClosed(
                                    ChunkPos.containing(new BlockPos(box.minX(), 0, box.minZ())),
                                    ChunkPos.containing(new BlockPos(box.maxX(), 0, box.maxZ())))
                            .toList()) {
                int distance = centre.getChessboardDistance(chunk);
                long volume = volumeIn(start, chunk);
                if (distance > farthest || (distance == farthest && volume > filled)) {
                    target = chunk;
                    farthest = distance;
                    filled = volume;
                }
            }
        }
        helper.assertTrue(
                target != null && farthest >= 2,
                "The structure must reach two chunks past its centre, not " + farthest);

        ChunkStep step = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES);
        ChunkPos decorated = target;
        StaticCache2D<GenerationChunkHolder> cache =
                StaticCache2D.create(
                        decorated.x(),
                        decorated.z(),
                        step.directDependencies().size() - 1,
                        (x, z) ->
                                new LoadedChunk(new ChunkPos(x, z), chunk(level, decorated, x, z)));
        ChunkAccess chunk = level.getChunk(decorated.x(), decorated.z());
        WorldGenRegion region = new WorldGenRegion(level, cache, step, chunk);
        start.placeInChunk(
                region,
                level.structureManager().forWorldGenRegion(region),
                level.getChunkSource().getGenerator(),
                RandomSource.create(SEED),
                new BoundingBox(
                        decorated.getMinBlockX(),
                        level.getMinY() + 1,
                        decorated.getMinBlockZ(),
                        decorated.getMaxBlockX(),
                        level.getMaxY(),
                        decorated.getMaxBlockZ()),
                decorated);
        return chunk;
    }

    /**
     * The chunks a FEATURES region holds: the decorated chunk and its neighbours whole, so the
     * pieces' writes land in the level, and the rest as far as {@code structure_starts}.
     */
    private static ChunkAccess chunk(ServerLevel level, ChunkPos decorated, int x, int z) {
        if (decorated.getChessboardDistance(x, z) <= 1) return level.getChunk(x, z);
        return level.getChunkSource().getChunk(x, z, ChunkStatus.STRUCTURE_STARTS, true);
    }

    private static long volumeIn(StructureStart start, ChunkPos chunk) {
        long volume = 0L;
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            long x =
                    Math.min(box.maxX(), chunk.getMaxBlockX())
                            - Math.max(box.minX(), chunk.getMinBlockX())
                            + 1L;
            long z =
                    Math.min(box.maxZ(), chunk.getMaxBlockZ())
                            - Math.max(box.minZ(), chunk.getMinBlockZ())
                            + 1L;
            if (x > 0L && z > 0L) volume += x * z * box.getYSpan();
        }
        return volume;
    }

    private static int count(ServerLevel level, ChunkAccess chunk, Predicate<BlockState> wanted) {
        ChunkPos pos = chunk.getPos();
        int count = 0;
        for (BlockPos block :
                BlockPos.betweenClosed(
                        pos.getMinBlockX(),
                        level.getMinY(),
                        pos.getMinBlockZ(),
                        pos.getMaxBlockX(),
                        level.getMaxY(),
                        pos.getMaxBlockZ())) {
            if (wanted.test(chunk.getBlockState(block))) count++;
        }
        return count;
    }

    /** A chunk the test loaded itself, handed to the region as the chunk map's holder would. */
    private static final class LoadedChunk extends GenerationChunkHolder {
        private final ChunkAccess chunk;

        private LoadedChunk(ChunkPos pos, ChunkAccess chunk) {
            super(pos);
            this.chunk = chunk;
        }

        @Override
        public @Nullable ChunkAccess getChunkIfPresentUnchecked(ChunkStatus status) {
            return chunk;
        }

        @Override
        protected void addSaveDependency(CompletableFuture<?> sync) {}

        @Override
        public int getTicketLevel() {
            return 0;
        }

        @Override
        public int getQueueLevel() {
            return 0;
        }
    }
}
