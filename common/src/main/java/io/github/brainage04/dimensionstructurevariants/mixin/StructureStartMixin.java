package io.github.brainage04.dimensionstructurevariants.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.brainage04.dimensionstructurevariants.StructurePalettes;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(StructureStart.class)
abstract class StructureStartMixin {
    @Shadow
    public abstract Structure getStructure();

    @Shadow
    public abstract BoundingBox getBoundingBox();

    @Shadow
    public abstract java.util.List<StructurePiece> getPieces();

    @WrapMethod(method = "placeInChunk")
    private void dimensionstructurevariants$placeWithPalette(
            WorldGenLevel level,
            StructureManager structures,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox bounds,
            ChunkPos chunk,
            Operation<Void> original) {
        // The start's own centre picks the biome, so every chunk of one structure gets one palette.
        BlockPos centre = getBoundingBox().getCenter();
        StructurePalettes.Setting setting =
                StructurePalettes.setting(level, getStructure(), centre);
        Map<BlockState, BlockState> previous = StructurePalettes.push(setting.palette());
        try {
            if (setting.ground() != null) layGround(level, setting.ground(), chunk);
            original.call(level, structures, generator, random, bounds, chunk);
        } finally {
            StructurePalettes.restore(previous);
        }
    }

    /**
     * The biome ground layer under the structure's own footprint: one block in every open column
     * the pieces cover, so the structure stands on the biome's ground instead of on the roof or on
     * its foundations. The pieces are placed over it and replace it where they build.
     */
    private void layGround(WorldGenLevel level, BlockState ground, ChunkPos chunk) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        for (StructurePiece piece : getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            int y = box.minY();
            for (int x = Math.max(chunk.getMinBlockX(), box.minX());
                    x <= Math.min(chunk.getMaxBlockX(), box.maxX());
                    x++) {
                for (int z = Math.max(chunk.getMinBlockZ(), box.minZ());
                        z <= Math.min(chunk.getMaxBlockZ(), box.maxZ());
                        z++) {
                    if (!level.getBlockState(cursor.set(x, y, z)).isAir()) continue;
                    level.setBlock(cursor, ground, flags);
                }
            }
        }
    }
}
