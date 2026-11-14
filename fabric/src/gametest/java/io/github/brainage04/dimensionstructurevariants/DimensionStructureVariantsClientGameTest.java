package io.github.brainage04.dimensionstructurevariants;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Films every shipped variant; the film itself is {@link DimensionStructureVariantsFilm}. */
@SuppressWarnings("UnstableApiUsage")
public final class DimensionStructureVariantsClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        DimensionStructureVariantsFilm.run(
                context,
                StructureVariantCatalog.VARIANTS.stream()
                        .map(StructureVariantCatalog.Variant::structure)
                        .toList());
    }
}
