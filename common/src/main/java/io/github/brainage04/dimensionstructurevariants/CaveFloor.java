package io.github.brainage04.dimensionstructurevariants;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Starts structures in {@link #USED_BY} on a cave floor instead of at their fixed start height. In
 * the Nether every column reports its first free block just above the ceiling, so heightmap
 * projection would put the start on the roof. Instead the raw terrain columns around the start are
 * searched for the floor height shared by most of them: solid ground (not the lava sea) with
 * headroom above it.
 */
public final class CaveFloor {
    public static final TagKey<Structure> USED_BY =
            TagKey.create(
                    Registries.STRUCTURE, DimensionStructureVariants.of("starts_on_cave_floor"));

    /** Half-width of the sampled square around the start; the sample spacing is one chunk. */
    private static final int RADIUS = 48;

    private static final int STEP = 16;

    /** Air blocks a floor needs above it to count; a village street needs walking room. */
    private static final int HEADROOM = 4;

    /** A sampled column supports a candidate floor when it has a floor within this distance. */
    private static final int TOLERANCE = 4;

    /** Lowest and highest floor considered: above the Nether lava sea, below the bedrock roof. */
    private static final int MIN_FLOOR = 33;

    private static final int MAX_FLOOR = 112;

    private CaveFloor() {}

    public static boolean isUsedBy(Structure structure, Structure.GenerationContext context) {
        return context.registryAccess()
                .lookupOrThrow(Registries.STRUCTURE)
                .wrapAsHolder(structure)
                .is(USED_BY);
    }

    /** The floor height most sampled columns share, or {@code fallback} when none has a floor. */
    public static int find(Structure.GenerationContext context, int fallback) {
        ChunkGenerator generator = context.chunkGenerator();
        LevelHeightAccessor heightAccessor = context.heightAccessor();
        RandomState randomState = context.randomState();
        ChunkPos chunk = context.chunkPos();
        int centreX = chunk.getMinBlockX();
        int centreZ = chunk.getMinBlockZ();
        int side = RADIUS * 2 / STEP + 1;
        boolean[][] floors = new boolean[side * side][];
        int minY = Math.max(MIN_FLOOR, heightAccessor.getMinY() + 1);
        int maxY = Math.min(MAX_FLOOR, heightAccessor.getMaxY() - HEADROOM);
        int index = 0;
        for (int dx = -RADIUS; dx <= RADIUS; dx += STEP) {
            for (int dz = -RADIUS; dz <= RADIUS; dz += STEP) {
                NoiseColumn column =
                        generator.getBaseColumn(
                                centreX + dx, centreZ + dz, heightAccessor, randomState);
                boolean[] columnFloors = new boolean[maxY + 1];
                for (int y = minY; y <= maxY; y++) {
                    columnFloors[y] = isFloor(column, y);
                }
                floors[index++] = columnFloors;
            }
        }
        int best = fallback;
        int bestScore = 0;
        for (int candidate = minY; candidate <= maxY; candidate++) {
            int score = 0;
            for (boolean[] columnFloors : floors) {
                for (int y = Math.max(minY, candidate - TOLERANCE);
                        y <= Math.min(maxY, candidate + TOLERANCE);
                        y++) {
                    if (columnFloors[y]) {
                        score++;
                        break;
                    }
                }
            }
            if (score > bestScore
                    || (score == bestScore
                            && score > 0
                            && Math.abs(candidate - fallback) < Math.abs(best - fallback))) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    /** Solid, non-liquid ground at {@code y - 1} with {@link #HEADROOM} air blocks from {@code y}. */
    private static boolean isFloor(NoiseColumn column, int y) {
        BlockState ground = column.getBlock(y - 1);
        if (ground.isAir() || !ground.getFluidState().isEmpty()) return false;
        for (int up = 0; up < HEADROOM; up++) {
            if (!column.getBlock(y + up).isAir()) return false;
        }
        return true;
    }
}
