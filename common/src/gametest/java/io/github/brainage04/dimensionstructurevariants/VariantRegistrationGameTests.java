package io.github.brainage04.dimensionstructurevariants;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Every variant is registered for its target dimension and generates through a structure set. */
public final class VariantRegistrationGameTests {
    private VariantRegistrationGameTests() {}

    public static void allVariantsAreRegisteredForTheirTargetDimensions(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var structures = registries.lookupOrThrow(Registries.STRUCTURE);
        var biomes = registries.lookupOrThrow(Registries.BIOME);
        for (StructureVariantCatalog.Variant variant : StructureVariantCatalog.VARIANTS) {
            var structure = structures.getOrThrow(variant.structure());
            helper.assertTrue(
                    structure.value().biomes().contains(biomes.getOrThrow(variant.targetBiome())),
                    variant.structure().identifier() + " must target its intended dimension");
            helper.assertTrue(
                    !structure
                            .value()
                            .biomes()
                            .contains(biomes.getOrThrow(variant.excludedBiome())),
                    variant.structure().identifier() + " must not target its source dimension");
        }
        var structureSets = registries.lookupOrThrow(Registries.STRUCTURE_SET);
        for (var structureSet : StructureVariantCatalog.STRUCTURE_SETS) {
            helper.assertTrue(
                    structureSets.get(structureSet).isPresent(),
                    structureSet.identifier() + " must be registered");
        }
        helper.succeed();
    }

    public static void allVariantsBelongToNaturalStructureSets(GameTestHelper helper) {
        var structureSets =
                helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        Set<ResourceKey<Structure>> placedStructures = new HashSet<>();
        for (var structureSet : StructureVariantCatalog.STRUCTURE_SETS) {
            structureSets
                    .getOrThrow(structureSet)
                    .value()
                    .structures()
                    .forEach(
                            entry ->
                                    entry.structure().unwrapKey().ifPresent(placedStructures::add));
        }
        for (StructureVariantCatalog.Variant variant : StructureVariantCatalog.VARIANTS) {
            helper.assertTrue(
                    placedStructures.contains(variant.structure()),
                    variant.structure().identifier() + " must belong to a natural structure set");
        }
        helper.succeed();
    }
}
