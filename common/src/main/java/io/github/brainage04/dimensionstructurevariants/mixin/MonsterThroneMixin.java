package io.github.brainage04.dimensionstructurevariants.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.brainage04.dimensionstructurevariants.StructureMobs;
import java.util.Optional;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.levelgen.structure.structures.NetherFortressPieces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The fortress throne room's spawner holds a blaze ({@code NetherFortressPieces.java:1106-1107});
 * in a variant that takes its mobs from the biome it holds a monster of that biome, and where the
 * biome has none the room has no spawner.
 */
@Mixin(NetherFortressPieces.MonsterThrone.class)
abstract class MonsterThroneMixin {
    @WrapOperation(
            method = "postProcess",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/block/entity/SpawnerBlockEntity;"
                                            + "setEntityId(Lnet/minecraft/world/entity/EntityType;"
                                            + "Lnet/minecraft/util/RandomSource;)V"))
    private void dimensionstructurevariants$biomeMonster(
            SpawnerBlockEntity spawner,
            EntityType<?> type,
            RandomSource random,
            Operation<Void> original,
            @Local(argsOnly = true) WorldGenLevel level) {
        if (!StructureMobs.replaces()) {
            original.call(spawner, type, random);
            return;
        }
        Optional<EntityType<?>> monster = StructureMobs.monsterAt(level, spawner.getBlockPos());
        if (monster.isPresent()) {
            original.call(spawner, monster.get(), random);
        } else {
            level.setBlock(spawner.getBlockPos(), Blocks.AIR.defaultBlockState(), 2);
        }
    }
}
