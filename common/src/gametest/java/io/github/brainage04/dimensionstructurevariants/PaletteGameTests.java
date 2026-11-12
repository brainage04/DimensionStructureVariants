package io.github.brainage04.dimensionstructurevariants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallBlock;
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
                        Biomes.OCEAN,
                        Biomes.FROZEN_OCEAN));
        helper.assertTrue(
                palette(helper, "fortress_overworld", Biomes.WARM_OCEAN)
                        == palette(helper, "fortress_overworld", Biomes.OCEAN),
                "The warm ocean fortress must wear the bastion's ocean prismarine, like the"
                        + " temperate ocean fortress");
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
                    !palette(helper, "fortress_overworld", biome)
                            .targets()
                            .contains(Blocks.NETHER_BRICKS.defaultBlockState()),
                    "The Overworld fortress must not keep vanilla nether brick in "
                            + biome.identifier());
        }
        assertMain(helper, "fortress_overworld", Biomes.PLAINS, Blocks.STONE_BRICKS);
        assertMain(helper, "fortress_overworld", Biomes.DESERT, Blocks.SANDSTONE);
        assertMain(helper, "fortress_overworld", Biomes.OCEAN, Blocks.PRISMARINE_BRICKS);
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
                StructurePalettes.palette(
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
                    brickChoice(helper).states().contains(level.getBlockState(pos)),
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
        StructurePalettes.Palette previous =
                StructurePalettes.push(
                        StructurePalettes.palette(
                                DimensionStructureVariants.of("fortress_overworld"),
                                biomes.getOrThrow(Biomes.PLAINS)));
        try {
            BlockState source = Blocks.NETHER_BRICKS.defaultBlockState();
            BlockPos pos = BlockPos.ZERO;
            AtomicReference<BlockState> workerResult = new AtomicReference<>();
            Thread worker =
                    Thread.ofPlatform()
                            .unstarted(
                                    () -> workerResult.set(StructurePalettes.remap(source, pos)));
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
            StructurePalettes.Palette outer =
                    StructurePalettes.enter(
                            helper.getLevel(),
                            structure(helper, Identifier.withDefaultNamespace("bastion_remnant")),
                            BlockPos.ZERO);
            try {
                helper.assertTrue(
                        StructurePalettes.remap(source, pos) == source,
                        "Nested vanilla structures must retain vanilla blocks");
            } finally {
                StructurePalettes.restore(outer);
            }
            helper.assertTrue(
                    brickChoice(helper).states().contains(StructurePalettes.remap(source, pos)),
                    "The enclosing palette must resume after nested placement");
        } finally {
            StructurePalettes.restore(previous);
        }
        helper.succeed();
    }

    /** One biome per Overworld fortress palette. */
    private static final List<ResourceKey<Biome>> FORTRESS_BIOMES =
            List.of(
                    Biomes.PLAINS,
                    Biomes.DESERT,
                    Biomes.BADLANDS,
                    Biomes.JUNGLE,
                    Biomes.SWAMP,
                    Biomes.SNOWY_PLAINS,
                    Biomes.MUSHROOM_FIELDS,
                    Biomes.OCEAN,
                    Biomes.FROZEN_OCEAN);

    /**
     * Every Overworld fortress palette textures its walls with several blocks, picked from the
     * position alone, and fills the windows the vanilla fortress fences with a block the eye passes
     * through.
     */
    public static void fortressWallsAreTexturedAndWindowsSeeThrough(GameTestHelper helper) {
        BlockState brick = Blocks.NETHER_BRICKS.defaultBlockState();
        BlockState fence =
                Blocks.NETHER_BRICK_FENCE
                        .defaultBlockState()
                        .setValue(BlockStateProperties.EAST, true)
                        .setValue(BlockStateProperties.WEST, true);
        for (ResourceKey<Biome> biome : FORTRESS_BIOMES) {
            StructurePalettes.Palette palette = palette(helper, "fortress_overworld", biome);
            Set<BlockState> walls = new HashSet<>();
            for (BlockPos pos : BlockPos.betweenClosed(0, 64, 0, 15, 67, 15)) {
                BlockState block = palette.remap(brick, pos);
                helper.assertTrue(
                        block == palette.remap(brick, pos.immutable()),
                        "fortress_overworld in "
                                + biome.identifier()
                                + " must pick the same block every time at "
                                + pos);
                walls.add(block);
            }
            helper.assertTrue(
                    walls.size() >= 3,
                    "fortress_overworld in "
                            + biome.identifier()
                            + " must texture its walls with at least 3 blocks, got "
                            + walls);
            BlockState window = palette.remap(fence, BlockPos.ZERO);
            helper.assertTrue(
                    !(window.getBlock() instanceof WallBlock) && !window.isSolidRender(),
                    "fortress_overworld in "
                            + biome.identifier()
                            + " must fill its windows with a see-through block, got "
                            + window);
        }
        helper.succeed();
    }

    /**
     * A Nether village lays its biome's ground as a patch whose edge wanders around the pieces,
     * mixes the biome's blocks in it, covers every column under the pieces, and lays the same patch
     * at the same place every time.
     */
    public static void villageGroundIsANaturalPatch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructurePalettes.Palette palette =
                palette(helper, "village_nether", Biomes.CRIMSON_FOREST);
        BlockPos corner = helper.absolutePos(BlockPos.ZERO).offset(4096, 0, 4096);
        int y = 200;
        BoundingBox pieceBox =
                new BoundingBox(
                        corner.getX(),
                        y,
                        corner.getZ(),
                        corner.getX() + 15,
                        y + 3,
                        corner.getZ() + 8);
        BoundingBox area = pieceBox.inflatedBy(16, 0, 16);
        List<StructurePiece> pieces = List.of(new BoxPiece(pieceBox));
        List<BlockState> first = layGround(level, palette, pieces, area, y);
        List<BlockState> second = layGround(level, palette, pieces, area, y);
        clearGround(level, area, y);
        helper.assertTrue(first.equals(second), "The village must lay the same ground every time");

        Set<BlockState> blocks = new HashSet<>();
        int outside = 0;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int index = 0;
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                BlockState ground = first.get(index++);
                boolean under = pieceBox.isInside(new BlockPos(x, y, z));
                if (ground.isAir()) {
                    helper.assertTrue(!under, "No ground under the piece at " + x + " " + z);
                    continue;
                }
                blocks.add(ground);
                if (!under) outside++;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
        }
        int laid = (int) first.stream().filter(state -> !state.isAir()).count();
        helper.assertTrue(outside > 0, "The ground must reach past the pieces");
        helper.assertTrue(
                laid < (maxX - minX + 1) * (maxZ - minZ + 1),
                "The ground must not fill a rectangle");
        helper.assertTrue(
                blocks.contains(Blocks.CRIMSON_NYLIUM.defaultBlockState())
                        && blocks.contains(Blocks.NETHERRACK.defaultBlockState()),
                "Crimson forest ground must mix nylium and netherrack, got " + blocks);
        helper.succeed();
    }

    /** Lays the ground over a stone floor in {@code area} and returns the ground row, x then z. */
    private static List<BlockState> layGround(
            ServerLevel level,
            StructurePalettes.Palette palette,
            List<StructurePiece> pieces,
            BoundingBox area,
            int y) {
        clearGround(level, area, y);
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                level.setBlock(new BlockPos(x, y - 1, z), Blocks.STONE.defaultBlockState(), 2);
            }
        }
        ChunkPos.rangeClosed(
                        ChunkPos.containing(new BlockPos(area.minX(), y, area.minZ())),
                        ChunkPos.containing(new BlockPos(area.maxX(), y, area.maxZ())))
                .forEach(
                        chunk ->
                                StructureGround.lay(
                                        level,
                                        level.getChunkSource().getGenerator(),
                                        palette,
                                        pieces,
                                        chunk));
        List<BlockState> row = new ArrayList<>();
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                row.add(level.getBlockState(new BlockPos(x, y, z)));
            }
        }
        return row;
    }

    /** Clears the floor, the ground and anything grown on it, chunk margin included. */
    private static void clearGround(ServerLevel level, BoundingBox area, int y) {
        BoundingBox chunks =
                new BoundingBox(
                        SectionPos.blockToSectionCoord(area.minX()) * 16 - 16,
                        y - 1,
                        SectionPos.blockToSectionCoord(area.minZ()) * 16 - 16,
                        SectionPos.blockToSectionCoord(area.maxX()) * 16 + 31,
                        y + 32,
                        SectionPos.blockToSectionCoord(area.maxZ()) * 16 + 31);
        for (BlockPos pos :
                BlockPos.betweenClosed(
                        chunks.minX(),
                        chunks.minY(),
                        chunks.minZ(),
                        chunks.maxX(),
                        chunks.maxY(),
                        chunks.maxZ())) {
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
        }
    }

    /** The main block the fortress puts in place of nether bricks in this biome. */
    private static void assertMain(
            GameTestHelper helper, String id, ResourceKey<Biome> biome, Block expected) {
        StructurePalettes.Choice choice =
                palette(helper, id, biome).choiceFor(Blocks.NETHER_BRICKS.defaultBlockState());
        helper.assertTrue(
                choice != null && choice.main().is(expected),
                id + " in " + biome.identifier() + " must build mainly of " + expected);
    }

    /** What the plains fortress puts in place of nether bricks. */
    private static StructurePalettes.Choice brickChoice(GameTestHelper helper) {
        return palette(helper, "fortress_overworld", Biomes.PLAINS)
                .choiceFor(Blocks.NETHER_BRICKS.defaultBlockState());
    }

    /** Asserts the ground block the variant lays under its footprint in this biome. */
    private static void assertGround(
            GameTestHelper helper, String id, ResourceKey<Biome> biome, Block expected) {
        var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        StructurePalettes.Choice ground =
                StructurePalettes.palette(
                                DimensionStructureVariants.of(id), biomes.getOrThrow(biome))
                        .ground();
        helper.assertTrue(
                ground != null && ground.main().is(expected),
                id + " must lay " + expected + " as the ground in " + biome.identifier());
    }

    /** Asserts that every named biome answers with a palette of its own. */
    private static void assertDistinctPalettes(
            GameTestHelper helper, String id, List<ResourceKey<Biome>> biomes) {
        Set<Set<BlockState>> seen = new HashSet<>();
        for (ResourceKey<Biome> biome : biomes) {
            Set<BlockState> targets = palette(helper, id, biome).targets();
            helper.assertTrue(
                    seen.add(targets),
                    id
                            + " uses the same palette in "
                            + biome.identifier()
                            + " as in another biome");
        }
    }

    private static BlockState remap(
            GameTestHelper helper, String id, ResourceKey<Biome> biome, BlockState source) {
        StructurePalettes.Palette previous = StructurePalettes.push(palette(helper, id, biome));
        try {
            return StructurePalettes.remap(source, BlockPos.ZERO);
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

    private static StructurePalettes.Palette palette(
            GameTestHelper helper, String id, ResourceKey<Biome> biome) {
        Holder<Biome> holder =
                helper.getLevel()
                        .registryAccess()
                        .lookupOrThrow(Registries.BIOME)
                        .getOrThrow(biome);
        StructurePalettes.Palette palette =
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

    /** A piece that only has a footprint, for the ground around it. */
    private static final class BoxPiece extends StructurePiece {
        private BoxPiece(BoundingBox box) {
            super(StructurePieceType.NETHER_FORTRESS_BRIDGE_STRAIGHT, 0, box);
        }

        @Override
        protected void addAdditionalSaveData(
                StructurePieceSerializationContext context, CompoundTag tag) {
            throw new UnsupportedOperationException("The ground probe is never serialized");
        }

        @Override
        public void postProcess(
                WorldGenLevel level,
                StructureManager structures,
                ChunkGenerator generator,
                RandomSource random,
                BoundingBox bounds,
                ChunkPos chunk,
                BlockPos pivot) {}
    }
}
