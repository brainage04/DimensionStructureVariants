package io.github.brainage04.dimensionstructurevariants;

import it.unimi.dsi.fastutil.HashCommon;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

/**
 * The vines a palette's {@code vines} chance grows on a structure's walls, after its pieces are
 * placed. A wall block grows one on a face it shares with air, as a ruined portal does ({@code
 * RuinedPortalPiece.java:195-206}), and the vine hangs a few blocks down. Every choice follows from
 * the position alone, and a vine only grows where both the wall and the air stand in the chunk
 * being placed, so a structure looks the same whichever of its chunks generates first.
 */
public final class StructureVines {
    /** A vine hangs at most this many blocks, its first included. */
    private static final int MAX_HANG = 3;

    private static final long SALT = 0x71E5B1A5L;

    private static final int ROLL_BITS = 24;

    private static final int DIRECTION_SHIFT = ROLL_BITS;

    private static final int HANG_SHIFT = ROLL_BITS + 2;

    private static final int FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private StructureVines() {}

    public static void grow(
            WorldGenLevel level,
            StructurePalettes.Palette palette,
            List<StructurePiece> pieces,
            ChunkPos chunk) {
        float chance = palette.vines();
        if (chance <= 0.0F) return;
        BoundingBox bounds =
                new BoundingBox(
                        chunk.getMinBlockX(),
                        level.getMinY(),
                        chunk.getMinBlockZ(),
                        chunk.getMaxBlockX(),
                        level.getMaxY(),
                        chunk.getMaxBlockZ());
        BlockPos.MutableBlockPos wall = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos air = new BlockPos.MutableBlockPos();
        for (StructurePiece piece : pieces) {
            BoundingBox box = piece.getBoundingBox();
            for (int x = Math.max(box.minX(), bounds.minX());
                    x <= Math.min(box.maxX(), bounds.maxX());
                    x++) {
                for (int y = Math.max(box.minY(), bounds.minY());
                        y <= Math.min(box.maxY(), bounds.maxY());
                        y++) {
                    for (int z = Math.max(box.minZ(), bounds.minZ());
                            z <= Math.min(box.maxZ(), bounds.maxZ());
                            z++) {
                        long hash = HashCommon.mix(BlockPos.asLong(x, y, z) ^ SALT);
                        if ((hash & ((1L << ROLL_BITS) - 1)) >= chance * (1L << ROLL_BITS)) {
                            continue;
                        }
                        wall.set(x, y, z);
                        Direction side =
                                Direction.from2DDataValue((int) (hash >>> DIRECTION_SHIFT) & 3);
                        air.setWithOffset(wall, side);
                        if (!bounds.isInside(air)) continue;
                        BlockState state = level.getBlockState(wall);
                        if (state.isAir() || !level.getBlockState(air).isAir()) continue;
                        if (!Block.isFaceFull(state.getCollisionShape(level, wall), side)) {
                            continue;
                        }
                        hang(
                                level,
                                air,
                                VineBlock.PROPERTY_BY_DIRECTION.get(side.getOpposite()),
                                1 + (int) ((hash >>> HANG_SHIFT) % MAX_HANG));
                    }
                }
            }
        }
    }

    /** A vine on {@code face} at {@code top} and as many more below it as there is air for. */
    private static void hang(WorldGenLevel level, BlockPos top, BooleanProperty face, int length) {
        BlockPos.MutableBlockPos pos = top.mutable();
        for (int i = 0; i < length && level.getBlockState(pos).isAir(); i++) {
            level.setBlock(pos, Blocks.VINE.defaultBlockState().setValue(face, true), FLAGS);
            pos.move(Direction.DOWN);
        }
    }
}
