package io.github.brainage04.dimensionstructurevariants;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.List;
import java.util.Set;

public final class BiomeRestrictionGameTests {
	private BiomeRestrictionGameTests() {
	}

	public static void oceanAndIslandVariantsStayInTheirBiomes(GameTestHelper helper) {
		var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
		var structures = helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Set<ResourceKey<Biome>> coldOceans = Set.of(Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN, Biomes.COLD_OCEAN, Biomes.DEEP_COLD_OCEAN);
		Set<ResourceKey<Biome>> islands = Set.of(Biomes.END_HIGHLANDS, Biomes.END_MIDLANDS);
		for (String id : List.of("bastion_overworld", "end_city_overworld", "village_end", "bastion_end")) {
			var structure = structures.getValue(DimensionStructureVariants.of(id));
			for (var biome : biomes.listElements().toList()) {
				boolean expected = switch (id) {
					case "bastion_overworld" -> biome.is(BiomeTags.IS_OCEAN);
					case "end_city_overworld" -> coldOceans.contains(biome.key());
					default -> islands.contains(biome.key());
				};
				helper.assertTrue(structure.biomes().contains(biome) == expected,
						id + " has the wrong generation eligibility for " + biome.key().identifier());
			}
		}
		helper.succeed();
	}
}
