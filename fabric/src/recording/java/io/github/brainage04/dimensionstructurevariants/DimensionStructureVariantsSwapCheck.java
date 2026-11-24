package io.github.brainage04.dimensionstructurevariants;

import io.github.brainage04.minecraftmodconventions.ClientGameTestRecordingSession;
import io.github.brainage04.minecraftmodconventions.ClientGameTestServers;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;

/**
 * Reproduces A-1402: an End city in the Nether kept vanilla purpur on its base floor, because the
 * film's swap box started one block above the ground and the base floor stands at the ground's own
 * height. The test places the city, checks it holds no purpur, runs the film's swap, and checks
 * that the floor the vanilla re-place wrote into is the palette's again.
 *
 * <p>Runs in the client GameTest lane beside the film, so CI and {@code verify-done} run it.
 */
@SuppressWarnings("UnstableApiUsage")
public final class DimensionStructureVariantsSwapCheck implements FabricClientGameTest {
    private static final ResourceKey<Structure> END_CITY_NETHER =
            ResourceKey.create(
                    Registries.STRUCTURE, DimensionStructureVariants.of("end_city_nether"));

    private static final ResourceKey<Structure> VILLAGE_NETHER =
            ResourceKey.create(
                    Registries.STRUCTURE, DimensionStructureVariants.of("village_nether"));

    @Override
    public void runTest(ClientGameTestContext context) {
        // withDedicatedServer starts a recording session, and the film's is the one a recording run
        // has: a second start throws (ClientGameTestRecordingSession.start). The check runs in the
        // plain client lane, where no session is enabled.
        if (ClientGameTestRecordingSession.isEnabled()) {
            System.out.println(
                    "[DSV] swap check skipped: the film owns this run's recording session");
            return;
        }
        Properties properties = ClientGameTestServers.flatServerProperties();
        properties.setProperty("max-tick-time", "-1");
        ClientGameTestServers.withDedicatedServer(
                context,
                properties,
                "DimensionStructureVariants swap check",
                server ->
                        server.runOnServer(
                                minecraftServer -> {
                                    ServerLevel nether = minecraftServer.getLevel(Level.NETHER);
                                    if (nether == null) throw new AssertionError("No Nether");
                                    check(nether);
                                    checkVillage(nether);
                                }));
    }

    /**
     * Reproduces A-2316: the farms of a Nether village held obsidian where the palette has lava.
     * The village's own placement holds none (this check counts it). The film's swap puts the
     * vanilla village over it, whose farms hold water, and then writes the palette back block by
     * block: a lava cell set beside a water block that is not yet replaced becomes obsidian, or
     * cobblestone if it is not a source ({@code LiquidBlock.shouldSpreadLiquid}, called from {@code
     * LiquidBlock.onPlace}).
     */
    private static void checkVillage(ServerLevel level) {
        Holder<Structure> village =
                level.registryAccess()
                        .lookupOrThrow(Registries.STRUCTURE)
                        .getOrThrow(VILLAGE_NETHER);
        StructureStart start = null;
        for (int ring = 0; ring < 12 && start == null; ring++) {
            for (int dx = -ring; dx <= ring && start == null; dx++) {
                for (int dz = -ring; dz <= ring && start == null; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    StructureStart candidate =
                            generate(level, village, new ChunkPos(900 + dx * 8, 900 + dz * 8));
                    if (candidate.isValid()) start = candidate;
                }
            }
        }
        if (start == null) throw new AssertionError("No village start in the Nether");
        BoundingBox box = start.getBoundingBox();
        List<ChunkPos> chunks = chunks(box);
        for (ChunkPos chunk : chunks) {
            level.setChunkForced(chunk.x(), chunk.z(), true);
            level.getChunk(chunk.x(), chunk.z());
        }
        for (ChunkPos chunk : chunks) place(level, start, chunk);
        BoundingBox swap = DimensionStructureVariantsFilm.swapBox(level, true, box, box.minY());
        BlockState[] snapshot = read(level, swap);
        int lava = count(snapshot, Blocks.LAVA);
        int placedSolid = count(snapshot, Blocks.OBSIDIAN) + count(snapshot, Blocks.COBBLESTONE);
        Structure vanilla =
                level.registryAccess()
                        .lookupOrThrow(Registries.STRUCTURE)
                        .getValue(
                                DimensionStructureVariantsFilm.vanillaStructure("village_nether"));
        StructureStart plain =
                new StructureStart(
                        vanilla, start.getChunkPos(), 0, new PiecesContainer(start.getPieces()));
        for (ChunkPos chunk : chunks) place(level, plain, chunk);
        long writeStart = System.nanoTime();
        DimensionStructureVariantsFilm.writeBack(level, swap, snapshot);
        long writeMillis = (System.nanoTime() - writeStart) / 1_000_000L;
        BlockState[] after = read(level, swap);
        int obsidian = count(after, Blocks.OBSIDIAN);
        int cobblestone = count(after, Blocks.COBBLESTONE);
        int lavaAfter = count(after, Blocks.LAVA);
        for (ChunkPos chunk : chunks) level.setChunkForced(chunk.x(), chunk.z(), false);
        System.out.println(
                "[DSV] village swap check: box "
                        + swap
                        + " ("
                        + snapshot.length
                        + " blocks), palette holds "
                        + lava
                        + " lava and "
                        + placedSolid
                        + " obsidian or cobblestone; writeBack took "
                        + writeMillis
                        + " ms; after the swap "
                        + lavaAfter
                        + " lava, "
                        + obsidian
                        + " obsidian, "
                        + cobblestone
                        + " cobblestone");
        if (lava == 0 || placedSolid != 0) {
            throw new AssertionError(
                    "The Nether village's own placement must hold lava and no obsidian, got "
                            + lava
                            + " lava, "
                            + placedSolid
                            + " obsidian or cobblestone");
        }
        if (obsidian != 0 || cobblestone != 0 || lavaAfter != lava) {
            throw new AssertionError(
                    "The swap must put the farm lava back as lava: "
                            + lava
                            + " lava before, "
                            + lavaAfter
                            + " lava, "
                            + obsidian
                            + " obsidian and "
                            + cobblestone
                            + " cobblestone after");
        }
    }

    private static int count(BlockState[] states, net.minecraft.world.level.block.Block block) {
        int count = 0;
        for (BlockState state : states) {
            if (state.is(block)) count++;
        }
        return count;
    }

    private static void check(ServerLevel level) {
        Holder<Structure> city =
                level.registryAccess()
                        .lookupOrThrow(Registries.STRUCTURE)
                        .getOrThrow(END_CITY_NETHER);
        StructureStart start = null;
        for (int ring = 0; ring < 12 && start == null; ring++) {
            for (int dx = -ring; dx <= ring && start == null; dx++) {
                for (int dz = -ring; dz <= ring && start == null; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    StructureStart candidate =
                            generate(level, city, new ChunkPos(300 + dx * 6, 300 + dz * 6));
                    if (candidate.isValid()) start = candidate;
                }
            }
        }
        if (start == null) throw new AssertionError("No End city start in the Nether");
        BoundingBox box = start.getBoundingBox();
        List<ChunkPos> chunks = chunks(box);
        for (ChunkPos chunk : chunks) {
            level.setChunkForced(chunk.x(), chunk.z(), true);
            level.getChunk(chunk.x(), chunk.z());
        }
        for (ChunkPos chunk : chunks) place(level, start, chunk);
        int groundY = box.minY();
        // The film's swap: the vanilla structure over the same pieces, then the palette back.
        Structure vanilla =
                level.registryAccess()
                        .lookupOrThrow(Registries.STRUCTURE)
                        .getValue(
                                DimensionStructureVariantsFilm.vanillaStructure("end_city_nether"));
        StructureStart plain =
                new StructureStart(
                        vanilla, start.getChunkPos(), 0, new PiecesContainer(start.getPieces()));
        BoundingBox swap = DimensionStructureVariantsFilm.swapBox(level, true, box, groundY);
        // The film's swap over the box: snapshot it, put the vanilla pieces in, then put the
        // snapshot back. Anything the vanilla re-place wrote outside the box stays vanilla, which
        // is the bug. The film's Nether sweep and foundation clearing are left out: they only
        // remove blocks, so they cannot hide a block the swap failed to restore.
        BlockState[] before = read(level, box);
        // The owner's ask: no purpur may remain in a Nether End city. The swap below only compares
        // the world with itself, so a placement path that skipped the remap would pass it; this
        // reads the placed city instead.
        List<String> purpur = new ArrayList<>();
        BlockPos.MutableBlockPos purpurCursor = new BlockPos.MutableBlockPos();
        int purpurIndex = 0;
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    // The four purpur blocks vanilla registers (Blocks.java:3625-3643); there is no
                    // purpur block tag to ask instead.
                    if (before[purpurIndex].is(Blocks.PURPUR_BLOCK)
                            || before[purpurIndex].is(Blocks.PURPUR_PILLAR)
                            || before[purpurIndex].is(Blocks.PURPUR_STAIRS)
                            || before[purpurIndex].is(Blocks.PURPUR_SLAB)) {
                        purpur.add(
                                purpurCursor.set(x, y, z).toShortString()
                                        + " "
                                        + before[purpurIndex]);
                    }
                    purpurIndex++;
                }
            }
        }
        if (!purpur.isEmpty()) {
            throw new AssertionError(
                    "A Nether End city must hold no purpur, "
                            + purpur.size()
                            + " blocks: "
                            + purpur.subList(0, Math.min(8, purpur.size())));
        }
        BlockState[] snapshot = read(level, swap);
        for (ChunkPos chunk : chunks) place(level, plain, chunk);
        DimensionStructureVariantsFilm.writeBack(level, swap, snapshot);
        BlockState[] after = read(level, box);
        List<String> differing = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int index = 0;
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    if (before[index] != after[index]) {
                        differing.add(
                                cursor.set(x, y, z).toShortString()
                                        + " "
                                        + before[index]
                                        + " -> "
                                        + after[index]);
                    }
                    index++;
                }
            }
        }
        for (ChunkPos chunk : chunks) level.setChunkForced(chunk.x(), chunk.z(), false);
        System.out.println(
                "[DSV] swap check: box "
                        + swap
                        + " ground "
                        + groundY
                        + ", "
                        + differing.size()
                        + " blocks differ after the swap"
                        + (differing.isEmpty()
                                ? ""
                                : ": " + differing.subList(0, Math.min(8, differing.size()))));
        if (!differing.isEmpty()) {
            throw new AssertionError(
                    "The swap must put the palette back, "
                            + differing.size()
                            + " blocks differ: "
                            + differing.subList(0, Math.min(8, differing.size())));
        }
    }

    private static StructureStart generate(
            ServerLevel level, Holder<Structure> structure, ChunkPos chunk) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        var randomState = level.getChunkSource().randomState();
        return structure
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
                        level.getSeed(),
                        chunk,
                        0,
                        level,
                        biome -> true);
    }

    private static BlockState[] read(ServerLevel level, BoundingBox box) {
        BlockState[] states = new BlockState[box.getXSpan() * box.getYSpan() * box.getZSpan()];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int index = 0;
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    states[index++] = level.getBlockState(cursor.set(x, y, z));
                }
            }
        }
        return states;
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
}
