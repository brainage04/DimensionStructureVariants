package io.github.brainage04.dimensionstructurevariants;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.Fluids;

/** Shared by both loaders: End villages on the islands natural generation gives them. */
public final class EndVillageGameTests {
    private static final ResourceKey<Structure> VILLAGE_END =
            ResourceKey.create(Registries.STRUCTURE, DimensionStructureVariants.of("village_end"));

    private static final int VILLAGES = 4;

    /**
     * The share of the columns under rigid pieces that may hold air. The terrain adaptation leaves
     * the odd corner column open (4 of 4576 in the run of 12 Nov). Terrain generated without it
     * left 520 of the same 4576 open.
     */
    private static final double MAX_FLOATING_SHARE = 0.01D;

    /** Ticks the water gets to flow before the area around the villages is checked. */
    private static final int FLOW_TICKS = 60;

    private EndVillageGameTests() {}

    /**
     * A farm's water lies in the farm's bottom layer with nothing of the farm under it ({@code
     * village/plains/houses/plains_small_farm_1.nbt}), so it stays in the farm only where the
     * terrain under the farm was raised for it: the village's {@code beard_thin} terrain
     * adaptation, which the terrain gets while it generates from the starts its chunks know ({@code
     * NoiseBasedChunkGenerator.java:102}, {@code Beardifier.java:46-70}). End villages placed as
     * natural generation places them must stand their rigid pieces on ground, leave no water open
     * to the air below or beside it, and once every water block has had its fluid tick, as a
     * neighbour's update gives it in play, no water outside their pieces.
     *
     * <p>The villages also keep every composter and barrel their pieces' templates place: the
     * composters are the farmers' workstations, and the palette leaves both blocks as they are.
     *
     * <p>A GameTest world creates no structure starts of its own ({@code GameTestServer.java:92}
     * turns structures off, which {@code ChunkStatusTasks.java:48} and {@code
     * ChunkGenerator.java:378} check), so the test gives each start to its chunk before the terrain
     * around it generates, as natural generation does, and places the pieces once the chunks are
     * generated.
     */
    public static void endVillagesKeepFarmWaterAndWorkstations(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "The End must be loaded");
        Holder<Structure> village =
                end.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(VILLAGE_END);
        List<StructureStart> starts = islandStarts(end, village);
        helper.assertTrue(
                starts.size() == VILLAGES,
                "Expected " + VILLAGES + " End village starts, found " + starts.size());
        List<ChunkPos> forced = new ArrayList<>();
        for (StructureStart start : starts) {
            List<ChunkPos> chunks = chunks(start.getBoundingBox());
            for (ChunkPos chunk : chunks) {
                end.setChunkForced(chunk.x(), chunk.z(), true);
                forced.add(chunk);
                end.getChunk(chunk.x(), chunk.z());
            }
            for (ChunkPos chunk : chunks) place(end, start, chunk);
            wakeWater(end, start);
        }

        List<String> floating = new ArrayList<>();
        int[] columns = new int[1];
        List<String> open = new ArrayList<>();
        int[] workstations = new int[1];
        List<String> lost = new ArrayList<>();
        for (StructureStart start : starts) {
            floating.addAll(floatingColumns(end, start, columns));
            open.addAll(openFarmWater(end, start));
            lost.addAll(lostWorkstations(end, start, workstations));
        }
        System.out.println(
                "[DSV] End villages: "
                        + floating.size()
                        + " of "
                        + columns[0]
                        + " columns under rigid pieces hold air, "
                        + open.size()
                        + " water blocks open to the air, "
                        + lost.size()
                        + " of "
                        + workstations[0]
                        + " composters and barrels replaced");
        helper.runAfterDelay(
                FLOW_TICKS,
                () -> {
                    List<String> flowing = new ArrayList<>();
                    for (StructureStart start : starts) flowing.addAll(escapedWater(end, start));
                    for (ChunkPos chunk : forced) end.setChunkForced(chunk.x(), chunk.z(), false);
                    helper.assertTrue(
                            floating.size() <= MAX_FLOATING_SHARE * columns[0],
                            "Rigid End village pieces over air in "
                                    + floating.size()
                                    + " of "
                                    + columns[0]
                                    + " columns, e.g. "
                                    + floating.subList(0, Math.min(8, floating.size())));
                    helper.assertTrue(
                            open.isEmpty(), "Farm water open to the air in End villages: " + open);
                    helper.assertTrue(
                            workstations[0] > 0,
                            "The End villages hold no piece with a composter or a barrel to check");
                    helper.assertTrue(
                            lost.isEmpty(),
                            "End villages must keep their composters and barrels: "
                                    + lost.subList(0, Math.min(8, lost.size())));
                    helper.assertTrue(
                            flowing.isEmpty(), "Water left End villages' pieces: " + flowing);
                    helper.succeed();
                });
    }

    /**
     * The first {@link #VILLAGES} starts the placement grid gives End villages, ring by ring from
     * the outer islands, each handed to its chunk while that chunk has its structure starts and
     * nothing more, which is where natural generation keeps them.
     */
    private static List<StructureStart> islandStarts(ServerLevel end, Holder<Structure> village) {
        ChunkGeneratorStructureState state = end.getChunkSource().getGeneratorState();
        RandomSpreadStructurePlacement placement = null;
        for (StructurePlacement candidate : state.getPlacementsForStructure(village)) {
            if (candidate instanceof RandomSpreadStructurePlacement spread) placement = spread;
        }
        if (placement == null) throw new AssertionError("End villages need a random spread");
        var generator = end.getChunkSource().getGenerator();
        var randomState = end.getChunkSource().randomState();
        var sampler =
                randomState.createClimateSampler(SamplerContext.builder().enableCaches().build());
        List<StructureStart> starts = new ArrayList<>();
        int spacing = placement.spacing();
        for (int ring = 3; ring <= 24 && starts.size() < VILLAGES; ring++) {
            for (int rx = -ring; rx <= ring && starts.size() < VILLAGES; rx++) {
                for (int rz = -ring; rz <= ring && starts.size() < VILLAGES; rz++) {
                    if (Math.max(Math.abs(rx), Math.abs(rz)) != ring) continue;
                    ChunkPos chunk =
                            placement.getPotentialStructureChunk(
                                    state.getLevelSeed(), rx * spacing, rz * spacing);
                    if (!placement.isStructureChunk(state, chunk.x(), chunk.z())) continue;
                    StructureStart start =
                            village.value()
                                    .generate(
                                            village,
                                            end.dimension(),
                                            end.registryAccess(),
                                            generator,
                                            generator.getBiomeSource(),
                                            sampler,
                                            randomState,
                                            end.getStructureTemplateManager(),
                                            state.getLevelSeed(),
                                            chunk,
                                            0,
                                            end,
                                            biome -> village.value().biomes().contains(biome));
                    if (!start.isValid()) continue;
                    ChunkAccess origin =
                            end.getChunkSource()
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

    /**
     * The columns under a rigid piece's bottom layer that hold air; counts all in {@code columns}.
     */
    private static List<String> floatingColumns(
            ServerLevel level, StructureStart start, int[] columns) {
        List<String> floating = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (StructurePiece piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pool)
                    || pool.getElement().getProjection() != StructureTemplatePool.Projection.RIGID)
                continue;
            BoundingBox box = piece.getBoundingBox();
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    columns[0]++;
                    if (level.getBlockState(cursor.set(x, box.minY() - 1, z)).isAir()) {
                        floating.add(cursor.toShortString());
                    }
                }
            }
        }
        return floating;
    }

    /** Water sources in the village's pieces with air beside or below them. */
    private static List<String> openFarmWater(ServerLevel level, StructureStart start) {
        List<String> open = new ArrayList<>();
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
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
                if (!level.getFluidState(pos).isSourceOfType(Fluids.WATER)) continue;
                for (Direction direction : Direction.values()) {
                    if (direction == Direction.UP) continue;
                    if (level.getBlockState(neighbour.setWithOffset(pos, direction)).isAir()) {
                        open.add(pos.toShortString() + " " + direction.getName());
                        break;
                    }
                }
            }
        }
        return open;
    }

    /** Schedules every water block of the pieces for its fluid tick. */
    private static void wakeWater(ServerLevel level, StructureStart start) {
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
                var fluid = level.getFluidState(pos);
                if (fluid.is(Fluids.WATER)) {
                    level.scheduleTick(
                            pos.immutable(), fluid.getType(), fluid.getType().getTickDelay(level));
                }
            }
        }
    }

    /**
     * The composters and barrels the village's piece templates place that the placed village no
     * longer holds; counts every one the templates place in {@code workstations}.
     */
    private static List<String> lostWorkstations(
            ServerLevel level, StructureStart start, int[] workstations) {
        List<String> lost = new ArrayList<>();
        for (StructurePiece piece : start.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pool)
                    || !(pool.getElement() instanceof SinglePoolElement element)) continue;
            StructureTemplate template =
                    level.getStructureTemplateManager().getOrCreate(element.getTemplateLocation());
            StructurePlaceSettings settings =
                    new StructurePlaceSettings().setRotation(pool.getRotation());
            for (Block block : List.of(Blocks.COMPOSTER, Blocks.BARREL)) {
                for (StructureTemplate.StructureBlockInfo info :
                        template.filterBlocks(pool.getPosition(), settings, block)) {
                    workstations[0]++;
                    BlockState placed = level.getBlockState(info.pos());
                    if (!placed.is(block)) {
                        lost.add(info.pos().toShortString() + " holds " + placed);
                    }
                }
            }
        }
        return lost;
    }

    /** Water outside every piece, in the village's bounding box and the 16 blocks below it. */
    private static List<String> escapedWater(ServerLevel level, StructureStart start) {
        List<String> escaped = new ArrayList<>();
        BoundingBox box = start.getBoundingBox();
        for (BlockPos pos :
                BlockPos.betweenClosed(
                        box.minX(),
                        box.minY() - 16,
                        box.minZ(),
                        box.maxX(),
                        box.maxY(),
                        box.maxZ())) {
            if (!level.getFluidState(pos).is(Fluids.WATER)) continue;
            boolean inPiece = false;
            for (StructurePiece piece : start.getPieces()) {
                if (piece.getBoundingBox().isInside(pos)) {
                    inPiece = true;
                    break;
                }
            }
            if (inPiece) continue;
            escaped.add(pos.toShortString());
            if (escaped.size() >= 8) break;
        }
        return escaped;
    }
}
