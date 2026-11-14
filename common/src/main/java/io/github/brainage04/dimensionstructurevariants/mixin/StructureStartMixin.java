package io.github.brainage04.dimensionstructurevariants.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.brainage04.dimensionstructurevariants.StructureGround;
import io.github.brainage04.dimensionstructurevariants.StructurePalettes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
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
        StructurePalettes.Palette palette =
                StructurePalettes.palette(level, getStructure(), centre);
        StructurePalettes.Palette previous = StructurePalettes.push(palette);
        try {
            if (palette != null) StructureGround.lay(level, generator, palette, getPieces(), chunk);
            original.call(level, structures, generator, random, bounds, chunk);
        } finally {
            StructurePalettes.restore(previous);
        }
    }
}
