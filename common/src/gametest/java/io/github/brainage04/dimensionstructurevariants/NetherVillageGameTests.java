package io.github.brainage04.dimensionstructurevariants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/** Shared by both loaders: what the pieces of a natural Nether village hold. */
public final class NetherVillageGameTests {
    private static final ResourceKey<Structure> VILLAGE_NETHER =
            ResourceKey.create(
                    Registries.STRUCTURE, DimensionStructureVariants.of("village_nether"));

    /** How many villages the test generates; every one of them is checked. */
    private static final int VILLAGES = 4;

    private NetherVillageGameTests() {}

    /**
     * A Nether village keeps its composters as composters, turns the farms of the plains pieces it
     * is built from into soul sand with nether wart, and leaves neither farmland nor a crop
     * anywhere, because nothing in the Nether grows wheat: water is lava there, and the palette
     * maps every farm block (palettes.json, village_nether).
     *
     * <p>The same test reports how many of those composters stand within the reach of the lava the
     * farm water became. Lava's random tick lights a flammable block up to two blocks above itself
     * and two blocks to either side ({@code LavaFluid.java:83-113}), and a composter burns ({@code
     * Blocks.java:4741}, {@code ignitedByLava()}). Thomas was shown the count on 14 Nov and chose
     * to keep the composters unguarded rather than have a block added beside them, so the test
     * prints the count and the composters it names and only asserts that the farms held lava to
     * measure it against; nothing keeps a composter from burning.
     *
     * <p>A GameTest world creates no structure starts of its own ({@code GameTestServer.java:92}
     * turns structures off), so the test gives each start to its chunk before the terrain around it
     * generates, as natural generation does, and places the pieces once the chunks are generated.
     */
    public static void netherVillageFarmsUseSoulSandAndKeepComposters(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "The Nether must be loaded");
        Holder<Structure> village =
                nether.registryAccess()
                        .lookupOrThrow(Registries.STRUCTURE)
                        .getOrThrow(VILLAGE_NETHER);
        List<StructureStart> starts = roofStarts(nether, village);
        helper.assertTrue(
                starts.size() == VILLAGES,
                "Expected " + VILLAGES + " Nether village starts, found " + starts.size());

        List<ChunkPos> forced = new ArrayList<>();
        for (StructureStart start : starts) {
            List<ChunkPos> chunks = chunks(start.getBoundingBox());
            for (ChunkPos chunk : chunks) {
                nether.setChunkForced(chunk.x(), chunk.z(), true);
                forced.add(chunk);
                nether.getChunk(chunk.x(), chunk.z());
            }
            for (ChunkPos chunk : chunks) place(nether, start, chunk);
        }

        Counts counts = new Counts();
        Set<BlockPos> composters = new HashSet<>();
        Set<BlockPos> lava = new LinkedHashSet<>();
        List<String> farmland = new ArrayList<>();
        List<String> crops = new ArrayList<>();
        for (StructureStart start : starts) {
            count(nether, start, counts, composters, lava, farmland, crops);
        }
        Set<BlockPos> withinReach = withinLavaReach(nether, lava, composters);
        for (ChunkPos chunk : forced) nether.setChunkForced(chunk.x(), chunk.z(), false);

        System.out.println(
                "[DSV] Nether villages: "
                        + counts.composters
                        + " composters, "
                        + counts.wartOnSoulSand
                        + " nether wart on soul sand of "
                        + counts.wart
                        + " nether wart, "
                        + farmland.size()
                        + " farmland, "
                        + crops.size()
                        + " crops, "
                        + lava.size()
                        + " lava blocks; "
                        + withinReach.size()
                        + " of the composters sit within lava's reach: "
                        + withinReach);
        helper.assertTrue(
                counts.composters > 0,
                "Nether villages must keep their composters; the palette turned every one of them"
                        + " into a nether wart block");
        helper.assertTrue(
                counts.wartOnSoulSand > 0,
                "Nether village farms must grow nether wart on soul sand, found "
                        + counts.wart
                        + " nether wart of which none stood on soul sand");
        helper.assertTrue(
                farmland.isEmpty(), "Nether villages must hold no farmland: " + first(farmland));
        helper.assertTrue(crops.isEmpty(), "Nether villages must hold no crop: " + first(crops));
        helper.assertTrue(
                !lava.isEmpty(),
                "The Nether village farms must hold the lava their water became, or there is"
                        + " nothing to measure the composters against");
        helper.succeed();
    }

    /** What the pieces of the checked villages hold. */
    private static final class Counts {
        private int composters;
        private int wart;
        private int wartOnSoulSand;
    }

    /**
     * The first {@link #VILLAGES} starts the placement grid gives Nether villages, ring by ring
     * from the roof above the world origin, each handed to its chunk while that chunk has its
     * structure starts and nothing more, which is where natural generation keeps them.
     */
    private static List<StructureStart> roofStarts(ServerLevel nether, Holder<Structure> village) {
        ChunkGeneratorStructureState state = nether.getChunkSource().getGeneratorState();
        RandomSpreadStructurePlacement placement = null;
        for (StructurePlacement candidate : state.getPlacementsForStructure(village)) {
            if (candidate instanceof RandomSpreadStructurePlacement spread) placement = spread;
        }
        if (placement == null) throw new AssertionError("Nether villages need a random spread");
        var generator = nether.getChunkSource().getGenerator();
        var randomState = nether.getChunkSource().randomState();
        var sampler =
                randomState.createClimateSampler(SamplerContext.builder().enableCaches().build());
        List<StructureStart> starts = new ArrayList<>();
        int spacing = placement.spacing();
        for (int ring = 0; ring <= 24 && starts.size() < VILLAGES; ring++) {
            for (int rx = -ring; rx <= ring && starts.size() < VILLAGES; rx++) {
                for (int rz = -ring; rz <= ring && starts.size() < VILLAGES; rz++) {
                    if (Math.max(Math.abs(rx), Math.abs(rz)) != ring) continue;
                    ChunkPos chunk =
                            placement.getPotentialStructureChunk(
                                    state.getLevelSeed(), rx * spacing, rz * spacing);
                    if (!placement.isStructureChunk(state, chunk.x(), chunk.z())) continue;
                    // The set holds the End village too; in the Nether only this one generates.
                    StructureStart start =
                            village.value()
                                    .generate(
                                            village,
                                            nether.dimension(),
                                            nether.registryAccess(),
                                            generator,
                                            generator.getBiomeSource(),
                                            sampler,
                                            randomState,
                                            nether.getStructureTemplateManager(),
                                            state.getLevelSeed(),
                                            chunk,
                                            0,
                                            nether,
                                            biome -> village.value().biomes().contains(biome));
                    if (!start.isValid()) continue;
                    ChunkAccess origin =
                            nether.getChunkSource()
                                    .getChunk(
                                            chunk.x(),
                                            chunk.z(),
                                            ChunkStatus.STRUCTURE_STARTS,
                                            true);
                    if (origin == null
                            || origin.getPersistedStatus() != ChunkStatus.STRUCTURE_STARTS) {
                        continue;
                    }
                    origin.setStartForStructure(village.value(), start);
                    starts.add(start);
                }
            }
        }
        return starts;
    }

    private static List<ChunkPos> chunks(BoundingBox box) {
        List<ChunkPos> chunks = new ArrayList<>();
        ChunkPos.rangeClosed(
                        ChunkPos.containing(new BlockPos(box.minX(), 0, box.minZ())),
                        ChunkPos.containing(new BlockPos(box.maxX(), 0, box.maxZ())))
                .forEach(chunks::add);
        return chunks;
    }

    private static void place(ServerLevel level, StructureStart start, ChunkPos chunk) {
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

    /** Reads every block of every piece of {@code start} into the counts and the lists. */
    private static void count(
            ServerLevel level,
            StructureStart start,
            Counts counts,
            Set<BlockPos> composters,
            Set<BlockPos> lava,
            List<String> farmland,
            List<String> crops) {
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            for (BlockPos pos :
                    BlockPos.betweenClosed(
                            box.minX(),
                            box.minY(),
                            box.minZ(),
                            box.maxX(),
                            box.maxY(),
                            box.maxZ())) {
                BlockState state = level.getBlockState(pos);
                if (state.is(Blocks.COMPOSTER)) {
                    counts.composters++;
                    composters.add(pos.immutable());
                } else if (state.is(Blocks.NETHER_WART)) {
                    counts.wart++;
                    if (level.getBlockState(pos.below()).is(Blocks.SOUL_SAND)) {
                        counts.wartOnSoulSand++;
                    }
                } else if (state.is(Blocks.FARMLAND)) {
                    farmland.add(pos.toShortString());
                } else if (state.is(Blocks.WHEAT)
                        || state.is(Blocks.CARROTS)
                        || state.is(Blocks.POTATOES)
                        || state.is(Blocks.BEETROOTS)) {
                    crops.add(pos.toShortString() + " " + state);
                } else if (state.is(Blocks.LAVA)) {
                    lava.add(pos.immutable());
                }
            }
        }
    }

    /**
     * The composters a random tick of one of {@code lava} can set alight: a fire block stands at
     * most two blocks above the lava and two blocks to either side of it ({@code
     * LavaFluid.java:83-113}), and a fire lights every burnable block beside and above it. A player
     * within {@code FIRE_SPREAD_RADIUS_AROUND_PLAYER} of the lava is the remaining condition
     * ({@code ServerLevel.java:1941}), which is a question of who is nearby, not of the village.
     */
    private static Set<BlockPos> withinLavaReach(
            ServerLevel level, Set<BlockPos> lava, Set<BlockPos> composters) {
        Set<BlockPos> reach = new HashSet<>();
        for (BlockPos source : lava) {
            for (BlockPos fire : firePositions(source)) {
                if (!level.getBlockState(fire).isAir()) continue;
                for (Direction direction : Direction.values()) {
                    BlockPos neighbour = fire.relative(direction);
                    if (composters.contains(neighbour)) reach.add(neighbour);
                }
            }
        }
        return reach;
    }

    /**
     * Where lava's random tick can put fire: one block up and one to either side, the block above a
     * burnable block at its own level, and after a second step two blocks up and two to either
     * side.
     */
    private static List<BlockPos> firePositions(BlockPos lava) {
        List<BlockPos> positions = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                positions.add(lava.offset(dx, 1, dz));
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                positions.add(lava.offset(dx, 2, dz));
            }
        }
        return positions;
    }

    /** The first few entries of a list, for an assertion message that names positions. */
    private static List<String> first(List<String> positions) {
        return positions.subList(0, Math.min(8, positions.size()));
    }
}
