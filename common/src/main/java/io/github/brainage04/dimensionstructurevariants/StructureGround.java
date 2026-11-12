package io.github.brainage04.dimensionstructurevariants;

import it.unimi.dsi.fastutil.HashCommon;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * The biome ground a structure lays for itself: one block in every open column under its pieces,
 * and a margin around them whose edge wanders, so the ground reads as a patch of terrain instead of
 * the pieces' rectangles. The block in each column comes in clumps, and now and then a column clear
 * of the pieces grows the palette's ground feature. Every choice follows from the position alone.
 */
public final class StructureGround {
    /** Mean distance the ground reaches past the nearest piece. */
    private static final double EDGE = 4.0D;

    /**
     * How far the edge wanders either way. With {@link #EDGE} it stays inside the 12 blocks a
     * structure with terrain adaptation adds to its bounding box ({@code Structure.java:80}), the
     * chunks natural generation places the structure in.
     */
    private static final double EDGE_WOBBLE = 3.0D;

    private static final double EDGE_SCALE = 9.0D;

    /** Width of a clump of one ground block. */
    private static final double CLUMP = 4.0D;

    private static final double CLUMP_WARP = 3.0D;

    private static final double CLUMP_WARP_SCALE = 8.0D;

    /** A ground feature keeps this far from every piece, so its crown stays off the buildings. */
    private static final double FEATURE_CLEARANCE = 5.0D;

    private static final long FEATURE_SALT = 0x5D1C0FFEEL;

    private static final SimplexNoise NOISE = new SimplexNoise(new LegacyRandomSource(0x0D5BL));

    private static final int FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private StructureGround() {}

    public static void lay(
            WorldGenLevel level,
            ChunkGenerator generator,
            StructurePalettes.Palette palette,
            List<StructurePiece> pieces,
            ChunkPos chunk) {
        StructurePalettes.Choice ground = palette.ground();
        if (ground == null) return;
        Holder<Feature> feature =
                palette.groundFeature() == null
                        ? null
                        : level.registryAccess()
                                .lookupOrThrow(Registries.FEATURE)
                                .get(palette.groundFeature())
                                .orElseThrow(
                                        () ->
                                                new IllegalStateException(
                                                        "Unknown ground feature "
                                                                + palette.groundFeature()));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                double nearest = Double.MAX_VALUE;
                int nearestY = 0;
                boolean under = false;
                for (StructurePiece piece : pieces) {
                    BoundingBox box = piece.getBoundingBox();
                    int dx = Math.max(0, Math.max(box.minX() - x, x - box.maxX()));
                    int dz = Math.max(0, Math.max(box.minZ() - z, z - box.maxZ()));
                    if (dx == 0 && dz == 0) {
                        under = true;
                        put(level, ground, cursor.set(x, box.minY(), z));
                        continue;
                    }
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    if (distance < nearest) {
                        nearest = distance;
                        nearestY = box.minY();
                    }
                }
                if (under || nearest > edge(x, z)) continue;
                cursor.set(x, nearestY, z);
                if (!level.getBlockState(cursor).isAir()) continue;
                if (level.getBlockState(cursor.below()).isAir()) continue;
                put(level, ground, cursor);
                if (feature != null
                        && nearest >= FEATURE_CLEARANCE
                        && roll(x, nearestY, z) < palette.groundFeatureChance()) {
                    grow(level, generator, feature, cursor.above());
                }
            }
        }
    }

    private static void put(
            WorldGenLevel level, StructurePalettes.Choice ground, BlockPos.MutableBlockPos pos) {
        if (!level.getBlockState(pos).isAir()) return;
        level.setBlock(pos, clump(ground, pos.getX(), pos.getY(), pos.getZ()), FLAGS);
    }

    /**
     * Simplex noise held to -1..1. Its standard deviation is 0.425 ({@code SimplexNoise.java:9}).
     */
    private static double noise(double x, double z) {
        return Mth.clamp(NOISE.get(x, z), -1.0D, 1.0D);
    }

    /** How far past the nearest piece the ground reaches in this column. */
    private static double edge(int x, int z) {
        return EDGE + EDGE_WOBBLE * noise(x / EDGE_SCALE, z / EDGE_SCALE);
    }

    /** One block for a whole clump: the clumps are cells whose borders the noise bends. */
    private static BlockState clump(StructurePalettes.Choice ground, int x, int y, int z) {
        double warpedX = x + CLUMP_WARP * noise(x / CLUMP_WARP_SCALE, z / CLUMP_WARP_SCALE);
        double warpedZ =
                z + CLUMP_WARP * noise((x + 517) / CLUMP_WARP_SCALE, (z - 389) / CLUMP_WARP_SCALE);
        return ground.pick(
                BlockPos.asLong(Mth.floor(warpedX / CLUMP), y, Mth.floor(warpedZ / CLUMP)));
    }

    private static float roll(int x, int y, int z) {
        long mixed = HashCommon.mix(BlockPos.asLong(x, y, z) ^ FEATURE_SALT);
        return (mixed & 0xFFFFFF) / (float) 0x1000000;
    }

    /**
     * Grows the feature with no palette active, so its own blocks stay as the feature sets them.
     */
    private static void grow(
            WorldGenLevel level, ChunkGenerator generator, Holder<Feature> feature, BlockPos pos) {
        StructurePalettes.Palette previous = StructurePalettes.push(null);
        try {
            feature.value()
                    .place(
                            level,
                            generator,
                            RandomSource.create(HashCommon.mix(pos.asLong())),
                            pos);
        } finally {
            StructurePalettes.restore(previous);
        }
    }
}
