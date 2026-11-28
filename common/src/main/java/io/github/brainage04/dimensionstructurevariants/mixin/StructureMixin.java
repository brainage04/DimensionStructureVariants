package io.github.brainage04.dimensionstructurevariants.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.brainage04.dimensionstructurevariants.SolidGround;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Rejects a start whose island does not reach up to it, after the pieces are assembled, because
 * only then is the footprint known ({@code Structure.java:96-121}).
 */
@Mixin(Structure.class)
abstract class StructureMixin {
    @WrapMethod(method = "generate")
    private StructureStart dimensionstructurevariants$requireLevelGround(
            Holder<Structure> selected,
            ResourceKey<Level> dimension,
            RegistryAccess registryAccess,
            ChunkGenerator chunkGenerator,
            BiomeSource biomeSource,
            Climate.Sampler climateSampler,
            RandomState randomState,
            StructureTemplateManager structureTemplateManager,
            long seed,
            ChunkPos sourceChunkPos,
            int references,
            LevelHeightAccessor heightAccessor,
            Predicate<Holder<Biome>> validBiome,
            Operation<StructureStart> original) {
        StructureStart start =
                original.call(
                        selected,
                        dimension,
                        registryAccess,
                        chunkGenerator,
                        biomeSource,
                        climateSampler,
                        randomState,
                        structureTemplateManager,
                        seed,
                        sourceChunkPos,
                        references,
                        heightAccessor,
                        validBiome);
        if (start.isValid()
                && selected.is(SolidGround.REQUIRES_ISLAND_UNDER_FOOTPRINT)
                && !SolidGround.isLevelUnder(start, chunkGenerator, heightAccessor, randomState)) {
            return StructureStart.INVALID_START;
        }
        return start;
    }
}
