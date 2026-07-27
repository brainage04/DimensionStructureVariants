package io.github.brainage04.dimensionstructurevariants;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class DimensionStructureVariantsGameTest {
	@GameTest
	public void allVariantsAreRegisteredForTheirTargetDimensions(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		var structures = registries.lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
		var biomes = registries.lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
		for (StructureVariantCatalog.Variant variant : StructureVariantCatalog.VARIANTS) {
			var structure = structures.getOrThrow(variant.structure());
			helper.assertTrue(
					structure.value().biomes().contains(biomes.getOrThrow(variant.targetBiome())),
					variant.structure().identifier() + " must target its intended dimension"
			);
			helper.assertTrue(
					!structure.value().biomes().contains(biomes.getOrThrow(variant.excludedBiome())),
					variant.structure().identifier() + " must not target its source dimension"
			);
		}
		var structureSets = registries.lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET);
		for (var structureSet : StructureVariantCatalog.STRUCTURE_SETS) {
			helper.assertTrue(structureSets.get(structureSet).isPresent(), structureSet.identifier() + " must be registered");
		}
		helper.succeed();
	}

	@GameTest
	public void allVariantsBelongToNaturalStructureSets(GameTestHelper helper) {
		var structureSets = helper.getLevel().registryAccess()
				.lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET);
		var placedStructures = new java.util.HashSet<net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.structure.Structure>>();
		for (var structureSet : StructureVariantCatalog.STRUCTURE_SETS) {
			structureSets.getOrThrow(structureSet).value().structures().forEach(entry ->
					entry.structure().unwrapKey().ifPresent(placedStructures::add));
		}
		for (StructureVariantCatalog.Variant variant : StructureVariantCatalog.VARIANTS) {
			helper.assertTrue(
					placedStructures.contains(variant.structure()),
					variant.structure().identifier() + " must belong to a natural structure set"
			);
		}
		helper.succeed();
	}
}
