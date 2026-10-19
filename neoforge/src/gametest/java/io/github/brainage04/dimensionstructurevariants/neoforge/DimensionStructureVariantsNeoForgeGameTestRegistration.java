package io.github.brainage04.dimensionstructurevariants.neoforge;

import io.github.brainage04.dimensionstructurevariants.DimensionStructureVariants;
import io.github.brainage04.dimensionstructurevariants.DimensionStructureVariantsNeoForgeGameTests;
import io.github.brainage04.dimensionstructurevariants.SolidGroundGameTests;
import io.github.brainage04.dimensionstructurevariants.PaletteGameTests;
import io.github.brainage04.dimensionstructurevariants.BiomeRestrictionGameTests;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = DimensionStructureVariants.MOD_ID)
public final class DimensionStructureVariantsNeoForgeGameTestRegistration {
	private DimensionStructureVariantsNeoForgeGameTestRegistration() {
	}

	@SubscribeEvent
	public static void registerTestFunctions(RegisterEvent event) {
		DimensionStructureVariantsNeoForgeGameTests tests = new DimensionStructureVariantsNeoForgeGameTests();
		event.register(
				BuiltInRegistries.TEST_FUNCTION.key(),
				DimensionStructureVariants.of("all_variants_are_registered_for_their_target_dimensions"),
				() -> tests::allVariantsAreRegisteredForTheirTargetDimensions
		);
		event.register(
				BuiltInRegistries.TEST_FUNCTION.key(),
				DimensionStructureVariants.of("all_variants_belong_to_natural_structure_sets"),
				() -> tests::allVariantsBelongToNaturalStructureSets
		);
		event.register(
				BuiltInRegistries.TEST_FUNCTION.key(),
				DimensionStructureVariants.of("end_island_variants_only_start_on_solid_ground"),
				() -> SolidGroundGameTests::endIslandVariantsOnlyStartOnSolidGround
		);
		event.register(
				BuiltInRegistries.TEST_FUNCTION.key(),
				DimensionStructureVariants.of("dimension_materials_and_properties"),
				() -> PaletteGameTests::dimensionMaterialsAndProperties
		);
		event.register(
				BuiltInRegistries.TEST_FUNCTION.key(),
				DimensionStructureVariants.of("placement_restores_context_after_success_and_failure"),
				() -> PaletteGameTests::placementRestoresContextAfterSuccessAndFailure
		);
		event.register(
				BuiltInRegistries.TEST_FUNCTION.key(),
				DimensionStructureVariants.of("nested_and_worker_contexts_stay_isolated"),
				() -> PaletteGameTests::nestedAndWorkerContextsStayIsolated
		);
		event.register(
				BuiltInRegistries.TEST_FUNCTION.key(),
				DimensionStructureVariants.of("ocean_and_island_variants_stay_in_their_biomes"),
				() -> BiomeRestrictionGameTests::oceanAndIslandVariantsStayInTheirBiomes
		);
	}
}
