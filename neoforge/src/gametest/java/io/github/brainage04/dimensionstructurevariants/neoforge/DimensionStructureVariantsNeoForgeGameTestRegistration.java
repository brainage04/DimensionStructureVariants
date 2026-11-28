package io.github.brainage04.dimensionstructurevariants.neoforge;

import io.github.brainage04.dimensionstructurevariants.BiomeRestrictionGameTests;
import io.github.brainage04.dimensionstructurevariants.DimensionStructureVariants;
import io.github.brainage04.dimensionstructurevariants.EndCityNetherGameTests;
import io.github.brainage04.dimensionstructurevariants.EndVillageGameTests;
import io.github.brainage04.dimensionstructurevariants.LootAndMobGameTests;
import io.github.brainage04.dimensionstructurevariants.NaturalGenerationGameTests;
import io.github.brainage04.dimensionstructurevariants.NetherVillageGameTests;
import io.github.brainage04.dimensionstructurevariants.PaletteGameTests;
import io.github.brainage04.dimensionstructurevariants.SolidGroundGameTests;
import io.github.brainage04.dimensionstructurevariants.VariantRegistrationGameTests;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = DimensionStructureVariants.MOD_ID)
public final class DimensionStructureVariantsNeoForgeGameTestRegistration {
    private DimensionStructureVariantsNeoForgeGameTestRegistration() {}

    @SubscribeEvent
    public static void registerTestFunctions(RegisterEvent event) {
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of(
                        "all_variants_are_registered_for_their_target_dimensions"),
                () ->
                        VariantRegistrationGameTests
                                ::allVariantsAreRegisteredForTheirTargetDimensions);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of("all_variants_belong_to_natural_structure_sets"),
                () -> VariantRegistrationGameTests::allVariantsBelongToNaturalStructureSets);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of("end_island_variants_only_start_on_solid_ground"),
                () -> SolidGroundGameTests::endIslandVariantsOnlyStartOnSolidGround);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of("dimension_materials_and_properties"),
                () -> PaletteGameTests::dimensionMaterialsAndProperties);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of("biome_groups_get_their_own_palette"),
                () -> PaletteGameTests::biomeGroupsGetTheirOwnPalette);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of("every_targeted_biome_resolves_a_palette"),
                () -> PaletteGameTests::everyTargetedBiomeResolvesAPalette);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of(
                        "placement_restores_context_after_success_and_failure"),
                () -> PaletteGameTests::placementRestoresContextAfterSuccessAndFailure);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of("nested_and_worker_contexts_stay_isolated"),
                () -> PaletteGameTests::nestedAndWorkerContextsStayIsolated);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of("ocean_and_island_variants_stay_in_their_biomes"),
                () -> BiomeRestrictionGameTests::oceanAndIslandVariantsStayInTheirBiomes);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of("fence_connections_survive_wall_conversion"),
                () -> PaletteGameTests::fenceConnectionsSurviveWallConversion);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of(
                        "fortress_walls_are_textured_and_windows_see_through"),
                () -> PaletteGameTests::fortressWallsAreTexturedAndWindowsSeeThrough);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of("village_ground_is_a_natural_patch"),
                () -> PaletteGameTests::villageGroundIsANaturalPatch);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of("end_village_farm_water_stays_in_its_farm"),
                () -> EndVillageGameTests::endVillageFarmWaterStaysInItsFarm);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of(
                        "structures_place_from_a_chunk_away_from_their_centre"),
                () -> NaturalGenerationGameTests::structuresPlaceFromAChunkAwayFromTheirCentre);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of(
                        "the_nether_village_stands_on_its_generators_surface"),
                () -> NaturalGenerationGameTests::theNetherVillageStandsOnItsGeneratorsSurface);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of("accent_pieces_wear_the_accent_block"),
                () -> PaletteGameTests::accentPiecesWearTheAccentBlock);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of("placed_fortress_wears_the_accent_in_its_pieces"),
                () -> PaletteGameTests::placedFortressWearsTheAccentInItsPieces);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of("snowy_fortress_weights_blocks_by_strength"),
                () -> PaletteGameTests::snowyFortressWeightsBlocksByStrength);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of("rare_blocks_keep_their_share"),
                () -> PaletteGameTests::rareBlocksKeepTheirShare);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of("end_city_in_the_nether_leaves_no_purpur"),
                () -> EndCityNetherGameTests::endCityInTheNetherLeavesNoPurpur);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of(
                        "nether_village_farms_use_soul_sand_and_keep_composters"),
                () -> NetherVillageGameTests::netherVillageFarmsUseSoulSandAndKeepComposters);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of("badlands_fortress_uses_no_terracotta"),
                () -> PaletteGameTests::badlandsFortressUsesNoTerracotta);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of("village_ground_holds_no_bone_or_gilded_blocks"),
                () -> PaletteGameTests::villageGroundHoldsNoBoneOrGildedBlocks);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of("jungle_fortresses_grow_vines"),
                () -> PaletteGameTests::jungleFortressesGrowVines);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of("every_moved_structure_names_its_own_loot_table"),
                () -> LootAndMobGameTests::everyMovedStructureNamesItsOwnLootTable);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of("moved_structures_place_their_own_loot"),
                () -> LootAndMobGameTests::movedStructuresPlaceTheirOwnLoot);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of(
                        "fortress_throne_spawner_takes_a_monster_of_its_biome"),
                () -> LootAndMobGameTests::fortressThroneSpawnerTakesAMonsterOfItsBiome);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of("bastion_mobs_are_the_biomes_monsters"),
                () -> LootAndMobGameTests::bastionMobsAreTheBiomesMonsters);
        event.register(
                BuiltInRegistries.TEST_FUNCTION.key(),
                DimensionStructureVariants.of("placed_bastions_hold_no_nether_mob"),
                () -> LootAndMobGameTests::placedBastionsHoldNoNetherMob);
    }
}
