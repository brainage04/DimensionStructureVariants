package io.github.brainage04.dimensionstructurevariants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
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
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/** Runs the production palette tables and placement mixins on both loaders. */
public final class PaletteGameTests {
    private static final int SNOWY_SNOW_PERCENT = 2;
    private static final int OCEAN_DARK_PERCENT = 3;

    /** Fewer vines than this on a whole jungle fortress would not be "here and there". */
    private static final int MIN_JUNGLE_VINES = 20;

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
                Blocks.COMPOSTER.defaultBlockState());
        assertRemap(
                helper,
                "village_nether",
                Biomes.NETHER_WASTES,
                Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7),
                Blocks.SOUL_SAND.defaultBlockState());
        assertRemap(
                helper,
                "village_nether",
                Biomes.NETHER_WASTES,
                Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, 7),
                Blocks.NETHER_WART.defaultBlockState().setValue(BlockStateProperties.AGE_3, 3));
        assertRemap(
                helper,
                "village_nether",
                Biomes.NETHER_WASTES,
                Blocks.CARROTS.defaultBlockState(),
                Blocks.NETHER_WART.defaultBlockState());
        assertRemap(
                helper,
                "village_end",
                Biomes.END_HIGHLANDS,
                Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7),
                Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
        assertRemap(
                helper,
                "village_end",
                Biomes.END_HIGHLANDS,
                Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, 7),
                Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, 7));
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

    /**
     * Every vanilla biome a variant can start in has a rule of its own. The default palette is for
     * what no rule foresees (a datapack's biome, or a centre outside the variant's biomes), so a
     * vanilla biome that reaches it, for example one a new game version adds to {@code
     * #minecraft:is_overworld}, needs a rule written for it.
     */
    public static void everyTargetedBiomeResolvesAPalette(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var structures = registries.lookupOrThrow(Registries.STRUCTURE);
        var biomes = registries.lookupOrThrow(Registries.BIOME);
        for (StructureVariantCatalog.Variant variant : StructureVariantCatalog.VARIANTS) {
            Identifier id = variant.structure().identifier();
            helper.assertTrue(
                    StructurePalettes.palette(id, biomes.getOrThrow(variant.targetBiome())) != null,
                    id + " has no palette");
            List<String> missing = new ArrayList<>();
            for (Holder<Biome> biome :
                    structures.getOrThrow(variant.structure()).value().biomes()) {
                Identifier biomeId = biome.unwrapKey().orElseThrow().identifier();
                if (biomeId.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)
                        && !StructurePalettes.hasRule(id, biome)) {
                    missing.add(biomeId.toString());
                }
            }
            helper.assertTrue(missing.isEmpty(), id + " has no rule for " + missing);
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
                    StructurePalettes.push(
                            StructurePalettes.palette(
                                    helper.getLevel(),
                                    structure(
                                            helper,
                                            Identifier.withDefaultNamespace("bastion_remnant")),
                                    BlockPos.ZERO));
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
     * The Overworld fortress palettes that texture their walls with several blocks, picked from the
     * position alone. The snowy and mushroom palettes are not among them: they follow the ocean
     * monument's design, one main block and one accent block in named pieces ({@code
     * OceanMonumentPieces.java:1417-1419}), which {@link #accentPiecesWearTheAccentBlock} checks.
     */
    private static final List<ResourceKey<Biome>> TEXTURED_FORTRESS_BIOMES =
            List.of(
                    Biomes.PLAINS,
                    Biomes.DESERT,
                    Biomes.BADLANDS,
                    Biomes.JUNGLE,
                    Biomes.SWAMP,
                    Biomes.OCEAN,
                    Biomes.FROZEN_OCEAN);

    /**
     * Every textured Overworld fortress palette textures its walls with several blocks, picked from
     * the position alone, and fills the windows the vanilla fortress fences with a block the eye
     * passes through.
     */
    public static void fortressWallsAreTexturedAndWindowsSeeThrough(GameTestHelper helper) {
        BlockState brick = Blocks.NETHER_BRICKS.defaultBlockState();
        BlockState fence =
                Blocks.NETHER_BRICK_FENCE
                        .defaultBlockState()
                        .setValue(BlockStateProperties.EAST, true)
                        .setValue(BlockStateProperties.WEST, true);
        for (ResourceKey<Biome> biome : TEXTURED_FORTRESS_BIOMES) {
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
        }
        for (ResourceKey<Biome> biome : FORTRESS_BIOMES) {
            StructurePalettes.Palette palette = palette(helper, "fortress_overworld", biome);
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
     * The snowy and mushroom fortresses follow the ocean monument's design: one main block for the
     * walls, and one accent block in the pieces the palette names. The monument gives its three
     * prismarines one role each ({@code OceanMonumentPieces.java:1417-1419}) and uses dark
     * prismarine as trim in about half its room types rather than in a share of every wall.
     */
    public static void accentPiecesWearTheAccentBlock(GameTestHelper helper) {
        BlockState brick = Blocks.NETHER_BRICKS.defaultBlockState();
        for (ResourceKey<Biome> biome : List.of(Biomes.SNOWY_PLAINS, Biomes.MUSHROOM_FIELDS)) {
            boolean snowy = biome == Biomes.SNOWY_PLAINS;
            Block expectedMain = snowy ? Blocks.PACKED_ICE : Blocks.RED_MUSHROOM_BLOCK;
            Block expectedAccent = snowy ? Blocks.BLUE_ICE : Blocks.MUSHROOM_STEM;
            StructurePalettes.Palette palette = palette(helper, "fortress_overworld", biome);
            StructurePalettes.Accent accent = palette.accent();
            helper.assertTrue(
                    accent != null,
                    "fortress_overworld in " + biome.identifier() + " must name an accent");
            BlockState main = palette.choiceFor(brick).main();
            helper.assertTrue(
                    main.is(expectedMain),
                    "fortress_overworld in "
                            + biome.identifier()
                            + " must build its walls of "
                            + expectedMain
                            + ", got "
                            + main);
            BlockState accentBlock = null;
            for (Identifier piece : accent.pieces()) {
                Identifier previous = StructurePalettes.pushPiece(piece);
                try {
                    BlockState block = palette.choiceFor(brick).main();
                    if (accentBlock == null) accentBlock = block;
                    helper.assertTrue(
                            block == accentBlock,
                            "fortress_overworld in "
                                    + biome.identifier()
                                    + " must give every accent piece the same block, got "
                                    + block
                                    + " in "
                                    + piece
                                    + " and "
                                    + accentBlock);
                } finally {
                    StructurePalettes.restorePiece(previous);
                }
            }
            helper.assertTrue(
                    accentBlock != null && accentBlock.is(expectedAccent),
                    "fortress_overworld in "
                            + biome.identifier()
                            + " must give its accent pieces "
                            + expectedAccent
                            + ", got "
                            + accentBlock);
            BlockState fence = Blocks.NETHER_BRICK_FENCE.defaultBlockState();
            Block expectedWindow = snowy ? Blocks.ICE : Blocks.STAINED_GLASS_PANE.red();
            Block expectedRail = snowy ? Blocks.PACKED_ICE : Blocks.STAINED_GLASS_PANE.white();
            helper.assertTrue(
                    palette.remap(fence, BlockPos.ZERO).is(expectedWindow),
                    "fortress_overworld in "
                            + biome.identifier()
                            + " must fill its windows with "
                            + expectedWindow);
            Identifier railPiece = accent.pieces().iterator().next();
            Identifier beforeRail = StructurePalettes.pushPiece(railPiece);
            try {
                helper.assertTrue(
                        palette.remap(fence, BlockPos.ZERO).is(expectedRail),
                        "fortress_overworld in "
                                + biome.identifier()
                                + " must put "
                                + expectedRail
                                + " in the rails of "
                                + railPiece);
            } finally {
                StructurePalettes.restorePiece(beforeRail);
            }
            Identifier previous =
                    StructurePalettes.pushPiece(Identifier.withDefaultNamespace("neccs"));
            try {
                helper.assertTrue(
                        palette.choiceFor(brick).main() == main,
                        "fortress_overworld in "
                                + biome.identifier()
                                + " must keep the main block in a piece it does not name");
            } finally {
                StructurePalettes.restorePiece(previous);
            }
        }
        helper.succeed();
    }

    /**
     * Each role that carries a rare block gives it its share: a seeded sample over 16384 positions
     * stays within one percentage point of {@link #SNOWY_SNOW_PERCENT} for the snowy fortress's
     * snow (walls, fill and bridge pieces) and of {@link #OCEAN_DARK_PERCENT} for the ocean
     * fortress's dark prismarine (blocks, stairs, slabs and fill).
     */
    public static void rareBlocksKeepTheirShare(GameTestHelper helper) {
        StructurePalettes.Palette snowy =
                palette(helper, "fortress_overworld", Biomes.SNOWY_PLAINS);
        StructurePalettes.Palette ocean = palette(helper, "fortress_overworld", Biomes.OCEAN);
        BlockState brick = Blocks.NETHER_BRICKS.defaultBlockState();
        BlockState fill = Blocks.NETHERRACK.defaultBlockState();
        assertRareShare(helper, "snowy walls", snowy, brick, Blocks.SNOW_BLOCK, SNOWY_SNOW_PERCENT);
        assertRareShare(helper, "snowy fill", snowy, fill, Blocks.SNOW_BLOCK, SNOWY_SNOW_PERCENT);
        Identifier previous = StructurePalettes.pushPiece(Identifier.withDefaultNamespace("nebs"));
        try {
            assertRareShare(
                    helper, "snowy bridges", snowy, brick, Blocks.SNOW_BLOCK, SNOWY_SNOW_PERCENT);
        } finally {
            StructurePalettes.restorePiece(previous);
        }
        assertRareShare(
                helper, "ocean walls", ocean, brick, Blocks.DARK_PRISMARINE, OCEAN_DARK_PERCENT);
        assertRareShare(
                helper,
                "ocean stairs",
                ocean,
                Blocks.NETHER_BRICK_STAIRS.defaultBlockState(),
                Blocks.DARK_PRISMARINE_STAIRS,
                OCEAN_DARK_PERCENT);
        assertRareShare(
                helper,
                "ocean slabs",
                ocean,
                Blocks.NETHER_BRICK_SLAB.defaultBlockState(),
                Blocks.DARK_PRISMARINE_SLAB,
                OCEAN_DARK_PERCENT);
        assertRareShare(
                helper, "ocean fill", ocean, fill, Blocks.DARK_PRISMARINE, OCEAN_DARK_PERCENT);
        helper.succeed();
    }

    private static void assertRareShare(
            GameTestHelper helper,
            String what,
            StructurePalettes.Palette palette,
            BlockState source,
            Block rare,
            int percent) {
        int hits = 0;
        int sampled = 0;
        for (BlockPos pos : BlockPos.betweenClosed(0, 64, 0, 63, 67, 63)) {
            sampled++;
            if (palette.remap(source, pos).is(rare)) hits++;
        }
        helper.assertTrue(
                hits * 100 >= sampled * (percent - 1) && hits * 100 <= sampled * (percent + 1),
                what
                        + " must hold "
                        + rare
                        + " in about "
                        + percent
                        + " blocks in 100, got "
                        + hits
                        + " of "
                        + sampled);
    }

    /**
     * The snowy fortress builds its walls of packed ice, its bridges of blue ice, and puts about
     * {@link #SNOWY_SNOW_PERCENT} blocks in 100 of snow among them; its windows are ice, the one of
     * the four that the game draws translucent (its texture has an alpha of 190, the others 255;
     * {@code SpriteContents.java:82}, {@code BakedQuad.java:76}). The counts come from a fortress
     * placed in a snowy plains, not from the palette alone.
     */
    public static void snowyFortressWeightsBlocksByStrength(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos chunk = new ChunkPos(1200, 1200);
        StructureStart start = start(helper, level, "fortress_overworld", chunk);
        BoundingBox box = start.getBoundingBox();
        List<ChunkPos> chunks = chunks(box);
        for (ChunkPos each : chunks) {
            level.setChunkForced(each.x(), each.z(), true);
            level.getChunk(each.x(), each.z());
        }
        fillBiome(helper, level, box, Biomes.SNOWY_PLAINS);
        for (ChunkPos each : chunks) place(level, start, each);
        Map<Block, Integer> counts = new LinkedHashMap<>();
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox pieceBox = piece.getBoundingBox();
            for (BlockPos pos :
                    BlockPos.betweenClosed(
                            pieceBox.minX(),
                            pieceBox.minY(),
                            pieceBox.minZ(),
                            pieceBox.maxX(),
                            pieceBox.maxY(),
                            pieceBox.maxZ())) {
                BlockState state = level.getBlockState(pos);
                if (state.is(Blocks.PACKED_ICE)
                        || state.is(Blocks.BLUE_ICE)
                        || state.is(Blocks.ICE)
                        || state.is(Blocks.SNOW_BLOCK)
                        || state.is(Blocks.GLASS_PANE)) {
                    counts.merge(state.getBlock(), 1, Integer::sum);
                }
            }
        }
        for (ChunkPos each : chunks) level.setChunkForced(each.x(), each.z(), false);
        int packed = counts.getOrDefault(Blocks.PACKED_ICE, 0);
        int blue = counts.getOrDefault(Blocks.BLUE_ICE, 0);
        int ice = counts.getOrDefault(Blocks.ICE, 0);
        int snow = counts.getOrDefault(Blocks.SNOW_BLOCK, 0);
        int walls = packed + blue + snow;
        System.out.println(
                "[DSV] Snowy fortress: "
                        + packed
                        + " packed ice, "
                        + blue
                        + " blue ice, "
                        + ice
                        + " ice, "
                        + snow
                        + " snow of "
                        + walls
                        + " wall blocks");
        helper.assertTrue(walls > 100, "The snowy fortress must build walls, got " + counts);
        helper.assertTrue(
                packed > 0 && blue > 0,
                "The snowy fortress must hold packed ice and blue ice, got " + counts);
        helper.assertTrue(
                snow * 100 >= walls * (SNOWY_SNOW_PERCENT - 1)
                        && snow * 100 <= walls * (SNOWY_SNOW_PERCENT + 1),
                "Snow must stay at about "
                        + SNOWY_SNOW_PERCENT
                        + " blocks in 100 of the snowy fortress, got "
                        + snow
                        + " of "
                        + walls
                        + " wall blocks");
        helper.assertTrue(
                ice > 0, "The snowy fortress must fill its windows with ice, got " + counts);
        helper.assertTrue(
                !counts.containsKey(Blocks.GLASS_PANE),
                "The snowy fortress must hold no glass pane, got " + counts);
        helper.succeed();
    }

    /**
     * A placed snowy fortress wears the accent in the pieces the palette names and nowhere else.
     * The test reads the world, not the palette, so a piece id the game does not know, or a mixin
     * that names the wrong piece, fails here.
     */
    public static void placedFortressWearsTheAccentInItsPieces(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos chunk = new ChunkPos(1300, 1300);
        StructureStart start = start(helper, level, "fortress_overworld", chunk);
        BoundingBox box = start.getBoundingBox();
        List<ChunkPos> chunks = chunks(box);
        for (ChunkPos each : chunks) {
            level.setChunkForced(each.x(), each.z(), true);
            level.getChunk(each.x(), each.z());
        }
        fillBiome(helper, level, box, Biomes.SNOWY_PLAINS);
        for (ChunkPos each : chunks) place(level, start, each);
        StructurePalettes.Palette palette =
                palette(helper, "fortress_overworld", Biomes.SNOWY_PLAINS);
        StructurePalettes.Accent accent = palette.accent();
        helper.assertTrue(accent != null, "The snowy fortress must name an accent");
        Set<Identifier> named = accent.pieces();
        Map<Identifier, Integer> accentIn = new LinkedHashMap<>();
        Map<Identifier, Integer> accentOut = new LinkedHashMap<>();
        for (StructurePiece piece : start.getPieces()) {
            Identifier id = BuiltInRegistries.STRUCTURE_PIECE.getKey(piece.getType());
            BoundingBox pieceBox = piece.getBoundingBox();
            for (BlockPos pos :
                    BlockPos.betweenClosed(
                            pieceBox.minX(),
                            pieceBox.minY(),
                            pieceBox.minZ(),
                            pieceBox.maxX(),
                            pieceBox.maxY(),
                            pieceBox.maxZ())) {
                if (!level.getBlockState(pos).is(Blocks.BLUE_ICE)) continue;
                if (named.contains(id)) {
                    accentIn.merge(id, 1, Integer::sum);
                } else {
                    accentOut.merge(id, 1, Integer::sum);
                }
            }
        }
        for (ChunkPos each : chunks) level.setChunkForced(each.x(), each.z(), false);
        System.out.println(
                "[DSV] Snowy fortress accent: blue ice in "
                        + accentIn
                        + ", elsewhere "
                        + accentOut);
        helper.assertTrue(
                accentOut.isEmpty(),
                "The accent must stay in the pieces the palette names, found blue ice in "
                        + accentOut);
        for (Identifier id : named) {
            helper.assertTrue(
                    accentIn.containsKey(id),
                    "The accent piece " + id + " must wear the accent, found " + accentIn);
        }
        helper.succeed();
    }

    /**
     * The badlands fortress builds of the red sandstone family alone, as the desert fortress builds
     * of sandstone: terracotta of any colour would add a second colour to the walls (owner, 28 Nov
     * 2026).
     */
    public static void badlandsFortressUsesNoTerracotta(GameTestHelper helper) {
        StructurePalettes.Palette palette = palette(helper, "fortress_overworld", Biomes.BADLANDS);
        for (BlockState target : palette.targets()) {
            helper.assertTrue(
                    !target.is(BlockTags.TERRACOTTA),
                    "The badlands fortress must place no terracotta, found " + target);
        }
        StructurePalettes.Choice walls =
                palette.choiceFor(Blocks.NETHER_BRICKS.defaultBlockState());
        for (BlockState wall : walls.states()) {
            helper.assertTrue(
                    BuiltInRegistries.BLOCK.getKey(wall.getBlock()).getPath().contains("sandstone"),
                    "The badlands fortress must build its walls of sandstone, found " + wall);
        }
        helper.succeed();
    }

    /**
     * The soul sand valley village lays soul soil and soul sand and no bone block, and the basalt
     * deltas village lays no gilded blackstone, among the blocks its ground still mixes (owner, 28
     * Nov 2026).
     */
    public static void villageGroundHoldsNoBoneOrGildedBlocks(GameTestHelper helper) {
        assertGroundBlocks(
                helper,
                Biomes.SOUL_SAND_VALLEY,
                List.of(Blocks.SOUL_SOIL, Blocks.SOUL_SAND),
                List.of(Blocks.BONE_BLOCK));
        assertGroundBlocks(
                helper,
                Biomes.BASALT_DELTAS,
                List.of(Blocks.BLACKSTONE, Blocks.BASALT),
                List.of(Blocks.GILDED_BLACKSTONE));
        helper.succeed();
    }

    private static void assertGroundBlocks(
            GameTestHelper helper,
            ResourceKey<Biome> biome,
            List<Block> expected,
            List<Block> forbidden) {
        StructurePalettes.Palette palette = palette(helper, "village_nether", biome);
        Set<BlockState> placed = new HashSet<>(palette.targets());
        placed.addAll(palette.ground().states());
        for (Block block : expected) {
            helper.assertTrue(
                    palette.ground().states().contains(block.defaultBlockState()),
                    "The village in " + biome.identifier() + " must lay " + block);
        }
        for (Block block : forbidden) {
            helper.assertTrue(
                    placed.stream().noneMatch(state -> state.is(block)),
                    "The village in " + biome.identifier() + " must place no " + block);
        }
    }

    /**
     * A fortress placed in a jungle grows vines on its walls, each one supported where the game's
     * own vine rule keeps it; the swamp and plains fortresses grow none (owner, 28 Nov 2026: "just
     * for the jungle ones").
     */
    public static void jungleFortressesGrowVines(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int x = 1500;
        for (ResourceKey<Biome> biome :
                List.<ResourceKey<Biome>>of(
                        Biomes.JUNGLE, Biomes.SWAMP, Biomes.PLAINS, Biomes.BADLANDS)) {
            ChunkPos chunk = new ChunkPos(x, 1500);
            x += 40;
            StructureStart start = start(helper, level, "fortress_overworld", chunk);
            BoundingBox box = start.getBoundingBox().inflatedBy(1);
            List<ChunkPos> chunks = chunks(box);
            for (ChunkPos each : chunks) {
                level.setChunkForced(each.x(), each.z(), true);
                level.getChunk(each.x(), each.z());
            }
            fillBiome(helper, level, box, biome);
            for (ChunkPos each : chunks) place(level, start, each);
            int vines = 0;
            for (BlockPos pos :
                    BlockPos.betweenClosed(
                            box.minX(),
                            box.minY(),
                            box.minZ(),
                            box.maxX(),
                            box.maxY(),
                            box.maxZ())) {
                BlockState state = level.getBlockState(pos);
                if (!state.is(Blocks.VINE)) continue;
                vines++;
                helper.assertTrue(
                        state.canSurvive(level, pos), "The vine at " + pos + " must stay attached");
            }
            for (ChunkPos each : chunks) level.setChunkForced(each.x(), each.z(), false);
            System.out.println("[DSV] Fortress vines in " + biome.identifier() + ": " + vines);
            if (biome == Biomes.JUNGLE) {
                helper.assertTrue(
                        vines >= MIN_JUNGLE_VINES,
                        "The jungle fortress must grow at least "
                                + MIN_JUNGLE_VINES
                                + " vines, got "
                                + vines);
            } else {
                helper.assertTrue(
                        vines == 0,
                        "The fortress in "
                                + biome.identifier()
                                + " must grow no vines, got "
                                + vines);
            }
        }
        helper.succeed();
    }

    /**
     * A start of variant {@code id} in {@code chunk}, assembled as {@code Structure.generate} does
     * ({@code Structure.java:99-107}) without the island check of {@link SolidGround}, which a flat
     * test world cannot satisfy.
     */
    static StructureStart start(
            GameTestHelper helper, ServerLevel level, String id, ChunkPos chunk) {
        Structure structure =
                level.registryAccess()
                        .lookupOrThrow(Registries.STRUCTURE)
                        .getOrThrow(
                                ResourceKey.create(
                                        Registries.STRUCTURE, DimensionStructureVariants.of(id)))
                        .value();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        var randomState = level.getChunkSource().randomState();
        Structure.GenerationContext context =
                new Structure.GenerationContext(
                        level.registryAccess(),
                        generator,
                        generator.getBiomeSource(),
                        randomState.createClimateSampler(
                                SamplerContext.builder().enableCaches().build()),
                        randomState,
                        level.getStructureTemplateManager(),
                        level.getSeed(),
                        chunk,
                        level,
                        biome -> true);
        StructureStart start =
                structure
                        .findValidGenerationPoint(context)
                        .map(
                                stub ->
                                        new StructureStart(
                                                structure,
                                                chunk,
                                                0,
                                                stub.getPiecesBuilder().build()))
                        .orElse(StructureStart.INVALID_START);
        helper.assertTrue(start.isValid(), id + " generated no pieces");
        return start;
    }

    /**
     * Fills the biome of {@code box} with {@code biome}, so the palette picks it. The command
     * refuses a volume above the {@code max_block_modifications} game rule ({@code
     * FillBiomeCommand.java:127-130}) and rounds each corner down to a quart boundary ({@code
     * :83-89}), so the box goes in cubes small enough to stay under the limit once rounded.
     */
    static void fillBiome(
            GameTestHelper helper, ServerLevel level, BoundingBox box, ResourceKey<Biome> biome) {
        Holder<Biome> holder =
                level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(biome);
        int limit = level.getGameRules().get(GameRules.MAX_BLOCK_MODIFICATIONS);
        int side = 1;
        while ((side + 3) * (side + 3) * (side + 3) <= limit) side++;
        side--;
        for (int x = box.minX(); x <= box.maxX(); x += side) {
            for (int y = box.minY(); y <= box.maxY(); y += side) {
                for (int z = box.minZ(); z <= box.maxZ(); z += side) {
                    var result =
                            FillBiomeCommand.fill(
                                    level,
                                    new BlockPos(x, y, z),
                                    new BlockPos(
                                            Math.min(box.maxX(), x + side - 1),
                                            Math.min(box.maxY(), y + side - 1),
                                            Math.min(box.maxZ(), z + side - 1)),
                                    holder);
                    result.ifRight(
                            error -> {
                                if (error.getType() == FillBiomeCommand.ERROR_NO_BIOMES_SET) {
                                    return;
                                }
                                throw new AssertionError("Could not fill the biome", error);
                            });
                }
            }
        }
    }

    static List<ChunkPos> chunks(BoundingBox box) {
        List<ChunkPos> chunks = new ArrayList<>();
        ChunkPos.rangeClosed(
                        ChunkPos.containing(new BlockPos(box.minX(), 0, box.minZ())),
                        ChunkPos.containing(new BlockPos(box.maxX(), 0, box.maxZ())))
                .forEach(chunks::add);
        return chunks;
    }

    static void place(ServerLevel level, StructureStart start, ChunkPos chunk) {
        start.placeInChunk(
                level,
                level.structureManager(),
                level.getChunkSource().getGenerator(),
                level.getRandom(),
                new BoundingBox(
                        chunk.getMinBlockX(),
                        level.getMinY(),
                        chunk.getMinBlockZ(),
                        chunk.getMaxBlockX(),
                        level.getMaxY() + 1,
                        chunk.getMaxBlockZ()),
                chunk);
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
