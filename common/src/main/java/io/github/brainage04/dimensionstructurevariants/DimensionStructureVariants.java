package io.github.brainage04.dimensionstructurevariants;

import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared identity and lifecycle for the data-driven structure variants. */
public final class DimensionStructureVariants {
	public static final String MOD_ID = "dimensionstructurevariants";
	public static final String MOD_NAME = "DimensionStructureVariants";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

	private DimensionStructureVariants() {
	}

	public static void initialize() {
		LOGGER.info("{} initialized.", MOD_NAME);
	}

	public static Identifier of(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
