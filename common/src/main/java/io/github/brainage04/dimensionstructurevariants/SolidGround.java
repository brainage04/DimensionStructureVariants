package io.github.brainage04.dimensionstructurevariants;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Keeps floating-island variants off the void. Jigsaw structures project their start onto the
 * surface heightmap, which over the End's void is the dimension floor, so a start there would build
 * at the bottom of the world. Structures in {@link #REQUIRED_BY} only start where terrain exists
 * under the core of their footprint.
 */
public final class SolidGround {
    public static final TagKey<Structure> REQUIRED_BY =
            TagKey.create(
                    Registries.STRUCTURE, DimensionStructureVariants.of("requires_solid_ground"));

    /**
     * Half-width of the sampled square around the jigsaw start (the start chunk's minimum corner).
     */
    private static final int FOOTPRINT_RADIUS = 32;

    private static final int SAMPLE_STEP = 16;

    /** One column in this many blocks is sampled across each piece's footprint. */
    private static final int PIECE_SAMPLE_STEP = 8;

    /**
     * How far below the structure's lowest block the island's surface may lie in a column before
     * the column counts as low. Terrain adaptation fills a low column up to the structure, which is
     * what stretches a small island to hold a large structure. Measured on 14 End bastions
     * generated with the mod's earlier rule (28 Nov 2026): 12 blocks separates the 7 that sat on
     * their island (0 to 6 percent of the footprint lower) from the 7 that perched on it (54 to 84
     * percent lower); every End village and the larger fortresses fall under the share below.
     */
    static final int MAX_DROP = 12;

    /** The share of sampled footprint columns that may be low. */
    static final double MAX_LOW_SHARE = 0.35D;

    private SolidGround() {}

    public static boolean isRequiredBy(Structure structure, Structure.GenerationContext context) {
        return context.registryAccess()
                .lookupOrThrow(Registries.STRUCTURE)
                .wrapAsHolder(structure)
                .is(REQUIRED_BY);
    }

    public static boolean isPresent(Structure.GenerationContext context) {
        ChunkGenerator generator = context.chunkGenerator();
        LevelHeightAccessor heightAccessor = context.heightAccessor();
        RandomState randomState = context.randomState();
        ChunkPos chunk = context.chunkPos();
        int startX = chunk.getMinBlockX();
        int startZ = chunk.getMinBlockZ();
        int floor = heightAccessor.getMinY();
        for (int dx = -FOOTPRINT_RADIUS; dx <= FOOTPRINT_RADIUS; dx += SAMPLE_STEP) {
            for (int dz = -FOOTPRINT_RADIUS; dz <= FOOTPRINT_RADIUS; dz += SAMPLE_STEP) {
                int surface =
                        generator.getFirstOccupiedHeight(
                                startX + dx,
                                startZ + dz,
                                Heightmap.Types.WORLD_SURFACE_WG,
                                heightAccessor,
                                randomState);
                if (surface <= floor) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Whether the island under a generated start reaches up to the structure over all but {@link
     * #MAX_LOW_SHARE} of its footprint, so terrain adaptation has no island to stretch.
     */
    public static boolean isLevelUnder(
            StructureStart start,
            ChunkGenerator generator,
            LevelHeightAccessor heightAccessor,
            RandomState randomState) {
        return lowShare(start, generator, heightAccessor, randomState) <= MAX_LOW_SHARE;
    }

    /**
     * The share of sampled footprint columns whose surface lies more than {@link #MAX_DROP} under
     * the start.
     */
    static double lowShare(
            StructureStart start,
            ChunkGenerator generator,
            LevelHeightAccessor heightAccessor,
            RandomState randomState) {
        int base = start.getBoundingBox().minY();
        int sampled = 0;
        int low = 0;
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            for (int x = box.minX(); x <= box.maxX(); x += PIECE_SAMPLE_STEP) {
                for (int z = box.minZ(); z <= box.maxZ(); z += PIECE_SAMPLE_STEP) {
                    sampled++;
                    int surface =
                            generator.getFirstOccupiedHeight(
                                    x,
                                    z,
                                    Heightmap.Types.WORLD_SURFACE_WG,
                                    heightAccessor,
                                    randomState);
                    if (surface < base - MAX_DROP) low++;
                }
            }
        }
        return sampled == 0 ? 0.0D : (double) low / sampled;
    }
}
