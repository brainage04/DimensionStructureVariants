package io.github.brainage04.dimensionstructurevariants.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.brainage04.dimensionstructurevariants.StructurePalettes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Covers templates, jigsaw pieces, procedural fortresses and downward foundations. */
@Mixin({WorldGenRegion.class, Level.class})
abstract class StructureBlockMixin {
    @ModifyVariable(
            method =
                    "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"),
            argsOnly = true)
    private BlockState dimensionstructurevariants$remap(
            BlockState state, @Local(argsOnly = true) BlockPos pos) {
        return StructurePalettes.remap(state, pos);
    }
}
