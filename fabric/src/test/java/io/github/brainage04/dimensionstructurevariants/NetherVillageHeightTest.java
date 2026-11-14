package io.github.brainage04.dimensionstructurevariants;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Example;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * The Nether village used to start at the fixed y 129, one block above the vanilla Nether roof. It
 * now projects its start onto a heightmap, so a Nether whose terrain a datapack moves gets the
 * village on its surface; in the vanilla Nether it must still stand at y 129.
 */
class NetherVillageHeightTest {
    private static final String STRUCTURE =
            "/data/dimensionstructurevariants/worldgen/structure/village_nether.json";

    private static final int OLD_START_HEIGHT = 129;

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Example
    void theVanillaNetherKeepsTheVillageAtItsOldHeight() throws IOException {
        JsonObject structure = structure();
        assertTrue(
                structure.has("project_start_to_heightmap"),
                "The Nether village must follow the generator's surface");
        Heightmap.Types heightmap =
                Heightmap.Types.CODEC
                        .parse(JsonOps.INSTANCE, structure.get("project_start_to_heightmap"))
                        .getOrThrow();
        int startHeight =
                structure.getAsJsonObject("start_height").get("absolute").getAsInt();

        HolderLookup.Provider registries = VanillaRegistries.createWorldLookup();
        Holder<NoiseGeneratorSettings> settings =
                registries
                        .lookupOrThrow(Registries.NOISE_SETTINGS)
                        .getOrThrow(NoiseGeneratorSettings.NETHER);
        DimensionType nether =
                registries
                        .lookupOrThrow(Registries.DIMENSION_TYPE)
                        .getOrThrow(BuiltinDimensionTypes.NETHER)
                        .value();
        LevelHeightAccessor bounds = LevelHeightAccessor.create(nether.minY(), nether.height());
        ChunkGenerator generator =
                new NoiseBasedChunkGenerator(
                        new FixedBiomeSource(
                                registries
                                        .lookupOrThrow(Registries.BIOME)
                                        .getOrThrow(Biomes.NETHER_WASTES)),
                        settings);

        List<String> moved = new ArrayList<>();
        for (long seed : new long[] {0L, 1L, 8_675_309L, -4_172_144_997_902_289_642L}) {
            RandomState random =
                    RandomState.create(
                            registries.lookupOrThrow(Registries.NOISE), seed, settings.value());
            for (int x = -6000; x <= 6000; x += 375) {
                for (int z = -6000; z <= 6000; z += 375) {
                    int start =
                            startHeight
                                    + generator.getFirstFreeHeight(x, z, heightmap, bounds, random);
                    if (start != OLD_START_HEIGHT) {
                        moved.add("seed " + seed + " at " + x + ", " + z + ": y " + start);
                    }
                }
            }
        }
        assertTrue(moved.isEmpty(), "The village moved from y 129 in " + moved);
    }

    private static JsonObject structure() throws IOException {
        try (InputStream in = NetherVillageHeightTest.class.getResourceAsStream(STRUCTURE)) {
            if (in == null) throw new IllegalStateException("Missing " + STRUCTURE);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }
}
