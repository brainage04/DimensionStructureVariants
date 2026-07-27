package io.github.brainage04.dimensionstructurevariants.fabric;

import io.github.brainage04.dimensionstructurevariants.DimensionStructureVariants;
import net.fabricmc.api.ModInitializer;

public final class DimensionStructureVariantsFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		DimensionStructureVariants.initialize();
	}
}
