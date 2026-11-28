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
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;

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

    /** Structures refused unless the island under a generated start holds their footprint. */
    public static final TagKey<Structure> REQUIRES_ISLAND_UNDER_FOOTPRINT =
            TagKey.create(
                    Registries.STRUCTURE,
                    DimensionStructureVariants.of("requires_island_under_footprint"));

    /**
     * Half-width of the sampled square around the jigsaw start (the start chunk's minimum corner).
     */
    private static final int FOOTPRINT_RADIUS = 32;

    private static final int SAMPLE_STEP = 16;

    /** One column in this many blocks is sampled across each piece's footprint. */
    private static final int PIECE_SAMPLE_STEP = 8;

    /** How far terrain adaptation reaches out from a piece ({@code Structure.java:81}). */
    private static final int ADAPTATION_REACH = 12;

    /**
     * The share of a structure's footprint that may lie over the void when terrain adaptation does
     * not fill it. Measured on 8 End fortresses (28 Nov 2026): 4 had 29 to 37 percent of their
     * footprint over the void, the other 4 had 44 to 71 percent.
     */
    static final double MAX_OVERHANG_WITHOUT_ADAPTATION = 0.4D;

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
     * Whether the island under a generated start holds its footprint. Terrain adaptation grows
     * terrain within 12 blocks of every piece ({@code Structure.java:80-82}), which stretches an
     * island that ends under the structure, so a start whose adaptation would reach into the void
     * is refused. A structure without adaptation may overhang by {@link
     * #MAX_OVERHANG_WITHOUT_ADAPTATION} of its footprint.
     */
    public static boolean isLevelUnder(
            StructureStart start,
            ChunkGenerator generator,
            LevelHeightAccessor heightAccessor,
            RandomState randomState) {
        boolean adapted = start.getStructure().terrainAdaptation() != TerrainAdjustment.NONE;
        int reach = adapted ? ADAPTATION_REACH : 0;
        int floor = heightAccessor.getMinY();
        int sampled = 0;
        int overVoid = 0;
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox box = piece.getBoundingBox().inflatedBy(reach, 0, reach);
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
                    if (surface <= floor) overVoid++;
                }
            }
        }
        return overVoid <= sampled * (adapted ? 0.0D : MAX_OVERHANG_WITHOUT_ADAPTATION);
    }
}
