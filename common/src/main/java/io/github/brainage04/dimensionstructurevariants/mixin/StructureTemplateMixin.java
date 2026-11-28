package io.github.brainage04.dimensionstructurevariants.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.brainage04.dimensionstructurevariants.StructureMobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The mobs a template places. A bastion's piglins, brutes and hoglins are template entities ({@code
 * StructureTemplate.java:473-513}) and its lava basin's magma cube spawner is a template block
 * entity, both of which a variant that takes its mobs from the biome swaps for a monster of that
 * biome.
 */
@Mixin(StructureTemplate.class)
abstract class StructureTemplateMixin {
    @WrapMethod(method = "createEntityIgnoreException")
    private static Optional<Entity> dimensionstructurevariants$biomeMob(
            ProblemReporter reporter,
            ServerLevelAccessor level,
            CompoundTag tag,
            Operation<Optional<Entity>> original) {
        if (!StructureMobs.replaces()) return original.call(reporter, level, tag);
        Optional<Vec3> at = tag.read("Pos", Vec3.CODEC);
        if (at.isEmpty()) return original.call(reporter, level, tag);
        return StructureMobs.biomeMob(level, BlockPos.containing(at.get()), tag)
                .flatMap(mob -> original.call(reporter, level, mob));
    }

    /**
     * The template's block entities after the processors ran. {@code placeInWorld} calls this
     * overload in vanilla, and NeoForge's patched {@code placeInWorld} calls the one below, which
     * the vanilla overload also reaches there, so wrapping both swaps a spawner once on each loader
     * ({@code biomeSpawner} gives the same answer for a spawner it already swapped).
     */
    @WrapMethod(
            method =
                    "processBlockInfos(Lnet/minecraft/world/level/ServerLevelAccessor;"
                            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;"
                            + "Ljava/util/List;)Ljava/util/List;")
    private static List<StructureBlockInfo> dimensionstructurevariants$biomeSpawners(
            ServerLevelAccessor level,
            BlockPos position,
            BlockPos referencePos,
            StructurePlaceSettings settings,
            List<StructureBlockInfo> infos,
            Operation<List<StructureBlockInfo>> original) {
        return swapSpawners(level, original.call(level, position, referencePos, settings, infos));
    }

    @WrapMethod(
            method =
                    "processBlockInfos(Lnet/minecraft/world/level/ServerLevelAccessor;"
                            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;"
                            + "Ljava/util/List;"
                            + "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)"
                            + "Ljava/util/List;",
            require = 0)
    private static List<StructureBlockInfo> dimensionstructurevariants$biomeSpawnersNeoForge(
            ServerLevelAccessor level,
            BlockPos position,
            BlockPos referencePos,
            StructurePlaceSettings settings,
            List<StructureBlockInfo> infos,
            StructureTemplate template,
            Operation<List<StructureBlockInfo>> original) {
        return swapSpawners(
                level, original.call(level, position, referencePos, settings, infos, template));
    }

    private static List<StructureBlockInfo> swapSpawners(
            ServerLevelAccessor level, List<StructureBlockInfo> processed) {
        if (!StructureMobs.replaces()) return processed;
        List<StructureBlockInfo> swapped = new ArrayList<>(processed.size());
        for (StructureBlockInfo info : processed) {
            swapped.add(StructureMobs.biomeSpawner(level, info));
        }
        return swapped;
    }
}
