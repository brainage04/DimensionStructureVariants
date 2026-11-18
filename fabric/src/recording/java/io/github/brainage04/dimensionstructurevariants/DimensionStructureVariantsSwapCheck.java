package io.github.brainage04.dimensionstructurevariants;

import io.github.brainage04.minecraftmodconventions.ClientGameTestServers;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
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
 * height. The test places the city, runs the film's swap, and checks that the floor the vanilla
 * re-place wrote into is the palette's again.
 *
 * <p>Runs only when {@code DSV_SWAP_CHECK} is set, so the film's own client test is unaffected.
 */
@SuppressWarnings("UnstableApiUsage")
public final class DimensionStructureVariantsSwapCheck implements FabricClientGameTest {
    private static final ResourceKey<Structure> END_CITY_NETHER =
            ResourceKey.create(
                    Registries.STRUCTURE, DimensionStructureVariants.of("end_city_nether"));

    @Override
    public void runTest(ClientGameTestContext context) {
        if (System.getenv("DSV_SWAP_CHECK") == null) {
            System.out.println("[DSV] swap check skipped: DSV_SWAP_CHECK is not set");
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
                                }));
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
                        .getValue(Identifier.withDefaultNamespace("end_city"));
        StructureStart plain =
                new StructureStart(
                        vanilla, start.getChunkPos(), 0, new PiecesContainer(start.getPieces()));
        BoundingBox swap = DimensionStructureVariantsFilm.swapBox(level, true, box, groundY);
        // The film's swap, step for step: snapshot the box, put the vanilla pieces in, then put
        // the snapshot back over the box. Anything the vanilla re-place wrote outside the box
        // stays vanilla, which is the bug.
        BlockState[] before = read(level, box);
        BlockState[] snapshot = read(level, swap);
        for (ChunkPos chunk : chunks) place(level, plain, chunk);
        write(level, swap, snapshot);
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
                        + (differing.isEmpty() ? "" : ": " + differing.subList(0, Math.min(8, differing.size()))));
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

    /** Puts {@code states} back over {@code box}, as the film's second half does. */
    private static void write(ServerLevel level, BoundingBox box, BlockState[] states) {
        int flags =
                net.minecraft.world.level.block.Block.UPDATE_CLIENTS
                        | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE
                        | net.minecraft.world.level.block.Block.UPDATE_SUPPRESS_DROPS
                        | net.minecraft.world.level.block.Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int index = 0;
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    BlockState wanted = states[index++];
                    if (level.getBlockState(cursor.set(x, y, z)) == wanted) continue;
                    level.setBlock(cursor, wanted, flags);
                }
            }
        }
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
