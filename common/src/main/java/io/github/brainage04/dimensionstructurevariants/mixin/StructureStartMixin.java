package io.github.brainage04.dimensionstructurevariants.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.brainage04.dimensionstructurevariants.StructureGround;
import io.github.brainage04.dimensionstructurevariants.StructurePalettes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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
import org.spongepowered.asm.mixin.injection.At;

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

    /**
     * Names the piece being placed, so a palette can give one role a different block in the pieces
     * it names. {@code placeInChunk} calls each piece's {@code postProcess} in turn ({@code
     * StructureStart.java:93-97}), which is where a piece writes its blocks.
     */
    @WrapOperation(
            method = "placeInChunk",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/levelgen/structure/StructurePiece;"
                                            + "postProcess(Lnet/minecraft/world/level/WorldGenLevel;"
                                            + "Lnet/minecraft/world/level/StructureManager;"
                                            + "Lnet/minecraft/world/level/chunk/ChunkGenerator;"
                                            + "Lnet/minecraft/util/RandomSource;"
                                            + "Lnet/minecraft/world/level/levelgen/structure/BoundingBox;"
                                            + "Lnet/minecraft/world/level/ChunkPos;"
                                            + "Lnet/minecraft/core/BlockPos;)V"))
    private void dimensionstructurevariants$placeWithPiece(
            StructurePiece piece,
            WorldGenLevel level,
            StructureManager structures,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox bounds,
            ChunkPos chunk,
            BlockPos pivot,
            Operation<Void> original) {
        Identifier id = BuiltInRegistries.STRUCTURE_PIECE.getKey(piece.getType());
        Identifier previous = StructurePalettes.pushPiece(id);
        try {
            original.call(piece, level, structures, generator, random, bounds, chunk, pivot);
        } finally {
            StructurePalettes.restorePiece(previous);
        }
    }
}
