package io.github.brainage04.dimensionstructurevariants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/** Runs the production palette tables and placement mixins on both loaders. */
public final class PaletteGameTests {
    private PaletteGameTests() {}

    public static void fenceConnectionsSurviveWallConversion(GameTestHelper helper) {
        for (int mask = 0; mask < 16; mask++) {
            boolean north = (mask & 1) != 0;
            boolean east = (mask & 2) != 0;
            boolean south = (mask & 4) != 0;
            boolean west = (mask & 8) != 0;
            BlockState expected =
                    Blocks.END_STONE_BRICK_WALL
                            .defaultBlockState()
                            .setValue(
                                    BlockStateProperties.NORTH_WALL,
                                    north ? WallSide.LOW : WallSide.NONE)
                            .setValue(
                                    BlockStateProperties.EAST_WALL,
                                    east ? WallSide.LOW : WallSide.NONE)
                            .setValue(
                                    BlockStateProperties.SOUTH_WALL,
                                    south ? WallSide.LOW : WallSide.NONE)
                            .setValue(
                                    BlockStateProperties.WEST_WALL,
                                    west ? WallSide.LOW : WallSide.NONE);
            BlockState fence =
                    Blocks.OAK_FENCE
                            .defaultBlockState()
                            .setValue(BlockStateProperties.NORTH, north)
                            .setValue(BlockStateProperties.EAST, east)
                            .setValue(BlockStateProperties.SOUTH, south)
                            .setValue(BlockStateProperties.WEST, west)
                            .setValue(BlockStateProperties.WATERLOGGED, true);
            assertRemap(
                    helper,
                    "village_end",
                    Biomes.END_HIGHLANDS,
                    fence,
                    expected.setValue(BlockStateProperties.WATERLOGGED, true));
            BlockState netherFence =
                    Blocks.NETHER_BRICK_FENCE
                            .defaultBlockState()
                            .setValue(BlockStateProperties.NORTH, north)
                            .setValue(BlockStateProperties.EAST, east)
                            .setValue(BlockStateProperties.SOUTH, south)
                            .setValue(BlockStateProperties.WEST, west);
            assertRemap(helper, "fortress_end", Biomes.END_HIGHLANDS, netherFence, expected);
        }
        helper.succeed();
    }

    public static void dimensionMaterialsAndProperties(GameTestHelper helper) {
        assertRemap(
                helper,
                "end_city_overworld",
                Biomes.FROZEN_OCEAN,
                Blocks.PURPUR_BLOCK.defaultBlockState(),
                Blocks.BLUE_ICE.defaultBlockState());
        assertRemap(
                helper,
                "bastion_overworld",
                Biomes.OCEAN,
                Blocks.LAVA.defaultBlockState().setValue(BlockStateProperties.LEVEL, 5),
                Blocks.WATER.defaultBlockState().setValue(BlockStateProperties.LEVEL, 5));
        assertRemap(
                helper,
                "bastion_end",
                Biomes.END_HIGHLANDS,
                Blocks.LAVA.defaultBlockState(),
                Blocks.PURPUR_BLOCK.defaultBlockState());
        assertRemap(
                helper,
                "fortress_end",
                Biomes.END_HIGHLANDS,
                Blocks.NETHER_BRICKS.defaultBlockState(),
                Blocks.END_STONE_BRICKS.defaultBlockState());
        assertRemap(
                helper,
                "village_end",
                Biomes.END_HIGHLANDS,
                Blocks.WALL_TORCH
                        .defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST),
                Blocks.END_ROD
                        .defaultBlockState()
                        .setValue(BlockStateProperties.FACING, Direction.WEST));
        assertRemap(
                helper,
                "village_nether",
                Biomes.NETHER_WASTES,
                Blocks.OAK_LOG
                        .defaultBlockState()
                        .setValue(BlockStateProperties.AXIS, Direction.Axis.X),
                Blocks.BASALT
                        .defaultBlockState()
                        .setValue(BlockStateProperties.AXIS, Direction.Axis.X));
        assertRemap(
                helper,
                "village_nether",
                Biomes.NETHER_WASTES,
                Blocks.WATER.defaultBlockState().setValue(BlockStateProperties.LEVEL, 8),
                Blocks.LAVA.defaultBlockState().setValue(BlockStateProperties.LEVEL, 8));
        assertRemap(
                helper,
                "village_nether",
                Biomes.NETHER_WASTES,
                Blocks.OAK_FENCE
                        .defaultBlockState()
                        .setValue(BlockStateProperties.EAST, true)
                        .setValue(BlockStateProperties.WATERLOGGED, true),
                Blocks.NETHER_BRICK_FENCE
                        .defaultBlockState()
                        .setValue(BlockStateProperties.EAST, true)
                        .setValue(BlockStateProperties.WATERLOGGED, false));
        assertRemap(
                helper,
                "village_nether",
                Biomes.NETHER_WASTES,
                Blocks.COMPOSTER.defaultBlockState(),
                Blocks.NETHER_WART_BLOCK.defaultBlockState());
        assertRemap(
                helper,
                "village_nether",
                Biomes.NETHER_WASTES,
                Blocks.BARREL.defaultBlockState(),
                Blocks.STRIPPED_CRIMSON_HYPHAE.defaultBlockState());
        assertRemap(
                helper,
                "village_end",
                Biomes.END_HIGHLANDS,
                Blocks.OAK_STAIRS
                        .defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                        .setValue(BlockStateProperties.HALF, Half.TOP)
                        .setValue(BlockStateProperties.WATERLOGGED, true),
                Blocks.PURPUR_STAIRS
                        .defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                        .setValue(BlockStateProperties.HALF, Half.TOP)
                        .setValue(BlockStateProperties.WATERLOGGED, true));
        for (StructureVariantCatalog.Variant variant : StructureVariantCatalog.VARIANTS) {
            assertRemap(
                    helper,
                    variant.structure().identifier().getPath(),
                    variant.targetBiome(),
                    Blocks.CHEST.defaultBlockState(),
                    Blocks.CHEST.defaultBlockState());
        }
        helper.succeed();
    }

    /**
     * Every variant that leaves its dimension wears the host biome's materials: the Overworld
     * fortress is no longer vanilla nether brick anywhere, and no two biome groups of one variant
     * share their palette.
     */
    public static void biomeGroupsGetTheirOwnPalette(GameTestHelper helper) {
        assertDistinctPalettes(
                helper,
                "fortress_overworld",
                List.of(
                        Biomes.PLAINS,
                        Biomes.DESERT,
                        Biomes.BADLANDS,
                        Biomes.JUNGLE,
                        Biomes.SWAMP,
                        Biomes.SNOWY_PLAINS,
                        Biomes.MUSHROOM_FIELDS,
                        Biomes.WARM_OCEAN,
                        Biomes.OCEAN,
                        Biomes.FROZEN_OCEAN));
        assertDistinctPalettes(
                helper,
                "village_nether",
                List.of(
                        Biomes.NETHER_WASTES,
                        Biomes.CRIMSON_FOREST,
                        Biomes.WARPED_FOREST,
                        Biomes.SOUL_SAND_VALLEY,
                        Biomes.BASALT_DELTAS));
        assertDistinctPalettes(
                helper,
                "end_city_nether",
                List.of(
                        Biomes.NETHER_WASTES,
                        Biomes.CRIMSON_FOREST,
                        Biomes.WARPED_FOREST,
                        Biomes.SOUL_SAND_VALLEY,
                        Biomes.BASALT_DELTAS));
        for (ResourceKey<Biome> biome :
                List.<ResourceKey<Biome>>of(
                        Biomes.PLAINS,
                        Biomes.DESERT,
                        Biomes.BADLANDS,
                        Biomes.JUNGLE,
                        Biomes.SWAMP,
                        Biomes.SNOWY_PLAINS,
                        Biomes.MUSHROOM_FIELDS,
                        Biomes.WARM_OCEAN,
                        Biomes.OCEAN,
                        Biomes.FROZEN_OCEAN)) {
            helper.assertTrue(
                    remap(helper, "fortress_overworld", biome, Blocks.NETHER_BRICKS.defaultBlockState())
                            != Blocks.NETHER_BRICKS.defaultBlockState(),
                    "The Overworld fortress must not keep vanilla nether brick in "
                            + biome.identifier());
        }
        assertRemap(
                helper,
                "fortress_overworld",
                Biomes.PLAINS,
                Blocks.NETHER_BRICKS.defaultBlockState(),
                Blocks.STONE_BRICKS.defaultBlockState());
        assertRemap(
                helper,
                "fortress_overworld",
                Biomes.DESERT,
                Blocks.NETHER_BRICKS.defaultBlockState(),
                Blocks.SANDSTONE.defaultBlockState());
        helper.succeed();
    }

    /** Every palette a structure can pick answers every block the variant has a role for. */
    public static void everyTargetedBiomeResolvesAPalette(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var structures = registries.lookupOrThrow(Registries.STRUCTURE);
        var biomes = registries.lookupOrThrow(Registries.BIOME);
        for (StructureVariantCatalog.Variant variant : StructureVariantCatalog.VARIANTS) {
            Identifier id = variant.structure().identifier();
            List<String> missing = new ArrayList<>();
            for (Holder<Biome> biome :
                    structures.getOrThrow(variant.structure()).value().biomes()) {
                if (StructurePalettes.palette(id, biome) == null) {
                    missing.add(biome.unwrapKey().orElseThrow().identifier().toString());
                }
            }
            helper.assertTrue(missing.isEmpty(), id + " has no palette in " + missing);
        }
        helper.assertTrue(
                StructurePalettes.palette(
                                Identifier.withDefaultNamespace("bastion_remnant"),
                                biomes.getOrThrow(Biomes.NETHER_WASTES))
                        == null,
                "Vanilla structures must keep their vanilla blocks");
        assertGround(helper, "village_nether", Biomes.NETHER_WASTES, Blocks.NETHERRACK);
        assertGround(helper, "village_nether", Biomes.CRIMSON_FOREST, Blocks.CRIMSON_NYLIUM);
        assertGround(helper, "village_nether", Biomes.WARPED_FOREST, Blocks.WARPED_NYLIUM);
        assertGround(helper, "village_nether", Biomes.SOUL_SAND_VALLEY, Blocks.SOUL_SOIL);
        assertGround(helper, "village_nether", Biomes.BASALT_DELTAS, Blocks.BLACKSTONE);
        helper.assertTrue(
                StructurePalettes.setting(
                                        DimensionStructureVariants.of("fortress_overworld"),
                                        biomes.getOrThrow(Biomes.PLAINS))
                                .ground()
                        == null,
                "Only the Nether village lays a ground layer of its own");
        helper.succeed();
    }

    public static void placementRestoresContextAfterSuccessAndFailure(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 2, 0));
        BoundingBox bounds = new BoundingBox(pos);
        Structure structure =
                structure(helper, DimensionStructureVariants.of("fortress_overworld"));
        for (boolean fail : new boolean[] {false, true}) {
            RuntimeException failure = new RuntimeException("Deliberate piece failure");
            StructureStart start =
                    new StructureStart(
                            structure,
                            ChunkPos.containing(pos),
                            0,
                            new PiecesContainer(
                                    List.of(new ProbePiece(pos, fail ? failure : null))));
            try {
                start.placeInChunk(
                        level,
                        level.structureManager(),
                        level.getChunkSource().getGenerator(),
                        RandomSource.create(0),
                        bounds,
                        ChunkPos.containing(pos));
                helper.assertTrue(!fail, "The failing piece must throw");
            } catch (RuntimeException exception) {
                if (exception != failure) throw exception;
            }
            helper.assertTrue(
                    level.getBlockState(pos).is(Blocks.STONE_BRICKS),
                    "Structure placement must apply the palette of the biome it stands in");
            level.setBlock(pos, Blocks.NETHER_BRICKS.defaultBlockState(), 2);
            helper.assertTrue(
                    level.getBlockState(pos).is(Blocks.NETHER_BRICKS),
                    "Later world writes must not inherit a structure palette");
        }
        helper.succeed();
    }

    public static void nestedAndWorkerContextsStayIsolated(GameTestHelper helper) {
        var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        Map<BlockState, BlockState> previous =
                StructurePalettes.push(
                        StructurePalettes.palette(
                                DimensionStructureVariants.of("fortress_overworld"),
                                biomes.getOrThrow(Biomes.PLAINS)));
        try {
            BlockState source = Blocks.NETHER_BRICKS.defaultBlockState();
            AtomicReference<BlockState> workerResult = new AtomicReference<>();
            Thread worker =
                    Thread.ofPlatform()
                            .unstarted(() -> workerResult.set(StructurePalettes.remap(source)));
            worker.start();
            try {
                worker.join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while checking palette isolation", exception);
            }
            helper.assertTrue(
                    workerResult.get() == source,
                    "A worker must not inherit another thread's palette");
            Map<BlockState, BlockState> outer =
                    StructurePalettes.enter(
                            helper.getLevel(),
                            structure(helper, Identifier.withDefaultNamespace("bastion_remnant")),
                            BlockPos.ZERO);
            try {
                helper.assertTrue(
                        StructurePalettes.remap(source) == source,
                        "Nested vanilla structures must retain vanilla blocks");
            } finally {
                StructurePalettes.restore(outer);
            }
            helper.assertTrue(
                    StructurePalettes.remap(source).is(Blocks.STONE_BRICKS),
                    "The enclosing palette must resume after nested placement");
        } finally {
            StructurePalettes.restore(previous);
        }
        helper.succeed();
    }

    /** Asserts the ground block the variant lays under its footprint in this biome. */
    private static void assertGround(
            GameTestHelper helper, String id, ResourceKey<Biome> biome, Block expected) {
        var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        BlockState ground =
                StructurePalettes.setting(
                                DimensionStructureVariants.of(id), biomes.getOrThrow(biome))
                        .ground();
        helper.assertTrue(
                ground != null && ground.is(expected),
                id + " must lay " + expected + " as the ground in " + biome.identifier());
    }

    /** Asserts that every named biome answers with a palette of its own. */
    private static void assertDistinctPalettes(
            GameTestHelper helper, String id, List<ResourceKey<Biome>> biomes) {
        Set<Set<BlockState>> seen = new HashSet<>();
        for (ResourceKey<Biome> biome : biomes) {
            Set<BlockState> targets = new HashSet<>(palette(helper, id, biome).values());
            helper.assertTrue(
                    seen.add(targets),
                    id + " uses the same palette in " + biome.identifier() + " as in another biome");
        }
    }

    private static BlockState remap(
            GameTestHelper helper, String id, ResourceKey<Biome> biome, BlockState source) {
        Map<BlockState, BlockState> previous = StructurePalettes.push(palette(helper, id, biome));
        try {
            return StructurePalettes.remap(source);
        } finally {
            StructurePalettes.restore(previous);
        }
    }

    private static void assertRemap(
            GameTestHelper helper,
            String id,
            ResourceKey<Biome> biome,
            BlockState source,
            BlockState expected) {
        BlockState actual = remap(helper, id, biome, source);
        helper.assertTrue(
                actual == expected,
                id
                        + " in "
                        + biome.identifier()
                        + ": expected "
                        + expected
                        + " for "
                        + source
                        + ", got "
                        + actual);
    }

    private static Map<BlockState, BlockState> palette(
            GameTestHelper helper, String id, ResourceKey<Biome> biome) {
        Holder<Biome> holder =
                helper.getLevel()
                        .registryAccess()
                        .lookupOrThrow(Registries.BIOME)
                        .getOrThrow(biome);
        Map<BlockState, BlockState> palette =
                StructurePalettes.palette(DimensionStructureVariants.of(id), holder);
        helper.assertTrue(palette != null, id + " has no palette in " + biome.identifier());
        return palette;
    }

    private static Structure structure(GameTestHelper helper, Identifier id) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(id);
    }

    /**
     * Minimal real piece: its write passes through StructureStart and Level, not a mock remapper.
     */
    private static final class ProbePiece extends StructurePiece {
        private final BlockPos pos;
        private final RuntimeException failure;

        private ProbePiece(BlockPos pos, RuntimeException failure) {
            super(StructurePieceType.NETHER_FORTRESS_BRIDGE_STRAIGHT, 0, new BoundingBox(pos));
            this.pos = pos;
            this.failure = failure;
        }

        @Override
        protected void addAdditionalSaveData(
                StructurePieceSerializationContext context, CompoundTag tag) {
            throw new UnsupportedOperationException("The placement probe is never serialized");
        }

        @Override
        public void postProcess(
                WorldGenLevel level,
                StructureManager structures,
                ChunkGenerator generator,
                RandomSource random,
                BoundingBox bounds,
                ChunkPos chunk,
                BlockPos pivot) {
            level.setBlock(pos, Blocks.NETHER_BRICKS.defaultBlockState(), 2);
            if (failure != null) throw failure;
        }
    }
}
