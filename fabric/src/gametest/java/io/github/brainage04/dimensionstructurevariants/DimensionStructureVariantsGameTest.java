package io.github.brainage04.dimensionstructurevariants;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class DimensionStructureVariantsGameTest {
    @GameTest
    public void allVariantsAreRegisteredForTheirTargetDimensions(GameTestHelper helper) {
        VariantRegistrationGameTests.allVariantsAreRegisteredForTheirTargetDimensions(helper);
    }

    @GameTest
    public void allVariantsBelongToNaturalStructureSets(GameTestHelper helper) {
        VariantRegistrationGameTests.allVariantsBelongToNaturalStructureSets(helper);
    }

    @GameTest(maxTicks = 200)
    public void endIslandVariantsOnlyStartOnSolidGround(GameTestHelper helper) {
        SolidGroundGameTests.endIslandVariantsOnlyStartOnSolidGround(helper);
    }

    @GameTest(maxTicks = 400)
    public void endVillageFarmWaterStaysInItsFarm(GameTestHelper helper) {
        EndVillageGameTests.endVillageFarmWaterStaysInItsFarm(helper);
    }

    @GameTest
    public void dimensionMaterialsAndProperties(GameTestHelper helper) {
        PaletteGameTests.dimensionMaterialsAndProperties(helper);
    }

    @GameTest
    public void biomeGroupsGetTheirOwnPalette(GameTestHelper helper) {
        PaletteGameTests.biomeGroupsGetTheirOwnPalette(helper);
    }

    @GameTest
    public void everyTargetedBiomeResolvesAPalette(GameTestHelper helper) {
        PaletteGameTests.everyTargetedBiomeResolvesAPalette(helper);
    }

    @GameTest
    public void placementRestoresContextAfterSuccessAndFailure(GameTestHelper helper) {
        PaletteGameTests.placementRestoresContextAfterSuccessAndFailure(helper);
    }

    @GameTest
    public void nestedAndWorkerContextsStayIsolated(GameTestHelper helper) {
        PaletteGameTests.nestedAndWorkerContextsStayIsolated(helper);
    }

    @GameTest
    public void oceanAndIslandVariantsStayInTheirBiomes(GameTestHelper helper) {
        BiomeRestrictionGameTests.oceanAndIslandVariantsStayInTheirBiomes(helper);
    }

    @GameTest
    public void fenceConnectionsSurviveWallConversion(GameTestHelper helper) {
        PaletteGameTests.fenceConnectionsSurviveWallConversion(helper);
    }

    @GameTest
    public void fortressWallsAreTexturedAndWindowsSeeThrough(GameTestHelper helper) {
        PaletteGameTests.fortressWallsAreTexturedAndWindowsSeeThrough(helper);
    }

    @GameTest
    public void villageGroundIsANaturalPatch(GameTestHelper helper) {
        PaletteGameTests.villageGroundIsANaturalPatch(helper);
    }

    @GameTest
    public void structuresPlaceFromAChunkAwayFromTheirCentre(GameTestHelper helper) {
        NaturalGenerationGameTests.structuresPlaceFromAChunkAwayFromTheirCentre(helper);
    }
}
