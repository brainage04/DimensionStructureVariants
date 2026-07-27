package io.github.brainage04.dimensionstructurevariants;

import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.List;

/** Registry keys and expected dimension-biome targeting for every shipped variant. */
public final class StructureVariantCatalog {
	public static final List<Variant> VARIANTS = List.of(
			variant("village_nether", Biomes.NETHER_WASTES, Biomes.PLAINS),
			variant("village_end", Biomes.END_HIGHLANDS, Biomes.PLAINS),
			variant("fortress_overworld", Biomes.PLAINS, Biomes.NETHER_WASTES),
			variant("fortress_end", Biomes.END_HIGHLANDS, Biomes.NETHER_WASTES),
			variant("bastion_overworld", Biomes.PLAINS, Biomes.NETHER_WASTES),
			variant("bastion_end", Biomes.END_HIGHLANDS, Biomes.NETHER_WASTES),
			variant("end_city_overworld", Biomes.PLAINS, Biomes.END_HIGHLANDS),
			variant("end_city_nether", Biomes.NETHER_WASTES, Biomes.END_HIGHLANDS)
	);

	public static final List<ResourceKey<StructureSet>> STRUCTURE_SETS = List.of(
			structureSet("cross_dimension_villages"),
			structureSet("cross_dimension_nether_complexes"),
			structureSet("cross_dimension_end_cities")
	);

	private StructureVariantCatalog() {
	}

	private static Variant variant(String id, ResourceKey<Biome> targetBiome, ResourceKey<Biome> excludedBiome) {
		return new Variant(ResourceKey.create(Registries.STRUCTURE, DimensionStructureVariants.of(id)), targetBiome, excludedBiome);
	}

	private static ResourceKey<StructureSet> structureSet(String id) {
		return ResourceKey.create(Registries.STRUCTURE_SET, DimensionStructureVariants.of(id));
	}

	public record Variant(
			ResourceKey<Structure> structure,
			ResourceKey<Biome> targetBiome,
			ResourceKey<Biome> excludedBiome
	) {
	}
}
