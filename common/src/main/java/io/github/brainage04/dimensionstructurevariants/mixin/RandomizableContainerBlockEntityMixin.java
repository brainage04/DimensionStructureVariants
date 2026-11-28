package io.github.brainage04.dimensionstructurevariants.mixin;

import io.github.brainage04.dimensionstructurevariants.StructurePalettes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Every chest a structure writes names its loot table here: a piece's own code through {@code
 * StructurePiece.createChest} ({@code StructurePiece.java:451-469}), a template's chest through the
 * block entity's saved data ({@code RandomizableContainer.java:55-60}).
 */
@Mixin(RandomizableContainerBlockEntity.class)
abstract class RandomizableContainerBlockEntityMixin {
    @ModifyVariable(
            method = "setLootTable(Lnet/minecraft/resources/ResourceKey;)V",
            at = @At("HEAD"),
            argsOnly = true)
    private ResourceKey<LootTable> dimensionstructurevariants$swapLootTable(
            ResourceKey<LootTable> table) {
        return StructurePalettes.lootTable(table);
    }
}
