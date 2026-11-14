package io.github.brainage04.dimensionstructurevariants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
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
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;

/**
 * The Nether village used to start at the fixed y 129, one block above the vanilla Nether roof. It
 * now projects its start onto a heightmap, so a Nether whose terrain a datapack moves gets the
 * village on its surface; in the vanilla Nether it must still stand at y 129, and where the terrain
 * leaves no room above it the village must not start at all.
 */
class NetherVillageHeightTest {
    private static final String STRUCTURE =
            "/data/dimensionstructurevariants/worldgen/structure/village_nether.json";

    private static final int OLD_START_HEIGHT = 129;

    private static final long[] SEEDS = {0L, 1L, 8_675_309L, -4_172_144_997_902_289_642L};

    private static final HolderLookup.Provider REGISTRIES;

    private static final Holder<NoiseGeneratorSettings> VANILLA;

    private static final LevelHeightAccessor NETHER;

    private static final JsonObject VILLAGE;

    private static final int TALLEST;

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        REGISTRIES = VanillaRegistries.createWorldLookup();
        VANILLA =
                REGISTRIES
                        .lookupOrThrow(Registries.NOISE_SETTINGS)
                        .getOrThrow(NoiseGeneratorSettings.NETHER);
        DimensionType nether =
                REGISTRIES
                        .lookupOrThrow(Registries.DIMENSION_TYPE)
                        .getOrThrow(BuiltinDimensionTypes.NETHER)
                        .value();
        NETHER = LevelHeightAccessor.create(nether.minY(), nether.height());
        VILLAGE = json(STRUCTURE);
        try {
            TALLEST = tallestTownCentre();
        } catch (IOException | URISyntaxException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Example
    void theVanillaNetherKeepsTheVillageAtItsOldHeight() throws Exception {
        assertTrue(
                VILLAGE.has("project_start_to_heightmap"),
                "The Nether village must follow the generator's surface");
        List<String> moved = new ArrayList<>();
        for (Floor floor : floors(VANILLA)) {
            if (floor.y() != OLD_START_HEIGHT) {
                moved.add(floor + ": moved to y " + floor.y());
            } else if (isDropped(centrePiece(floor, TALLEST))) {
                moved.add(floor + ": dropped for its height");
            }
        }
        assertTrue(moved.isEmpty(), "The village left y 129 in " + moved);
    }

    /**
     * A datapack Nether whose noise reaches the dimension's top puts the first free block above the
     * world, so a start there would lie wholly outside it, a village {@code /locate} finds but
     * nobody can see. The padding must drop every such start.
     */
    @Example
    void aNetherWhoseTerrainReachesTheTopGetsNoVillage() throws Exception {
        List<String> kept = new ArrayList<>();
        for (Floor floor : floors(toTheTop())) {
            if (!isDropped(centrePiece(floor, 1))) kept.add(floor + " at y " + floor.y());
        }
        assertTrue(kept.isEmpty(), "A village started above the world in " + kept);
    }

    /**
     * The same for any seed and any column of the world: the vanilla Nether's roof gives every
     * column one surface, so no seed or place moves the village off y 129 or drops it.
     */
    @Property(tries = 40)
    void anySeedAndColumnOfTheVanillaNetherKeepsTheVillageAtItsOldHeight(
            @ForAll long seed,
            @ForAll @IntRange(min = -29_999_984, max = 29_999_984) int x,
            @ForAll @IntRange(min = -29_999_984, max = 29_999_984) int z)
            throws Exception {
        Floor floor = floor(generator(VANILLA), randomState(VANILLA, seed), seed, x, z);
        assertEquals(OLD_START_HEIGHT, floor.y(), floor.toString());
        assertFalse(isDropped(centrePiece(floor, TALLEST)), floor + ": dropped for its height");
    }

    private record Floor(long seed, int x, int z, int y) {
        @Override
        public String toString() {
            return "seed " + seed + " at " + x + ", " + z;
        }
    }

    /** The floor height of a start in every sampled column of every seed. */
    private static List<Floor> floors(Holder<NoiseGeneratorSettings> settings) {
        ChunkGenerator generator = generator(settings);
        List<Floor> floors = new ArrayList<>();
        for (long seed : SEEDS) {
            RandomState random = randomState(settings, seed);
            for (int x = -6000; x <= 6000; x += 375) {
                for (int z = -6000; z <= 6000; z += 375) {
                    floors.add(floor(generator, random, seed, x, z));
                }
            }
        }
        return floors;
    }

    private static RandomState randomState(Holder<NoiseGeneratorSettings> settings, long seed) {
        return RandomState.create(
                REGISTRIES.lookupOrThrow(Registries.NOISE), seed, settings.value());
    }

    private static ChunkGenerator generator(Holder<NoiseGeneratorSettings> settings) {
        return new NoiseBasedChunkGenerator(
                new FixedBiomeSource(
                        REGISTRIES
                                .lookupOrThrow(Registries.BIOME)
                                .getOrThrow(Biomes.NETHER_WASTES)),
                settings);
    }

    /** The floor height of a start in one column. */
    private static Floor floor(
            ChunkGenerator generator, RandomState random, long seed, int x, int z) {
        Heightmap.Types heightmap =
                Heightmap.Types.CODEC
                        .parse(JsonOps.INSTANCE, VILLAGE.get("project_start_to_heightmap"))
                        .getOrThrow();
        int startHeight = VILLAGE.getAsJsonObject("start_height").get("absolute").getAsInt();
        int surface = generator.getFirstFreeHeight(x, z, heightmap, NETHER, random);
        return new Floor(seed, x, z, startHeight + surface);
    }

    /**
     * The vanilla Nether settings with the noise as tall as the dimension, written the way a
     * datapack would write them.
     */
    private static Holder<NoiseGeneratorSettings> toTheTop() {
        RegistryOps<JsonElement> ops = REGISTRIES.createSerializationContext(JsonOps.INSTANCE);
        JsonObject settings =
                NoiseGeneratorSettings.DIRECT_CODEC
                        .encodeStart(ops, VANILLA.value())
                        .getOrThrow()
                        .getAsJsonObject();
        JsonObject noise = settings.getAsJsonObject("noise");
        noise.addProperty("min_y", NETHER.getMinY());
        noise.addProperty("height", NETHER.getHeight());
        return Holder.direct(NoiseGeneratorSettings.DIRECT_CODEC.parse(ops, settings).getOrThrow());
    }

    /**
     * A centre piece {@code height} blocks tall on {@code floor}, placed as {@code
     * JigsawPlacement.java:122-123} places it: its lowest block one below the floor, by the ground
     * level delta of 1 every pool element has ({@code StructurePoolElement.java:92-94}).
     */
    private static BoundingBox centrePiece(Floor floor, int height) {
        int minY = floor.y() - 1;
        return new BoundingBox(floor.x(), minY, floor.z(), floor.x(), minY + height - 1, floor.z());
    }

    /** Whether the village's padding drops a start with this centre piece. */
    private static boolean isDropped(BoundingBox centre) throws ReflectiveOperationException {
        DimensionPadding padding =
                VILLAGE.has("dimension_padding")
                        ? DimensionPadding.CODEC
                                .parse(JsonOps.INSTANCE, VILLAGE.get("dimension_padding"))
                                .getOrThrow()
                        : DimensionPadding.ZERO;
        Method check =
                JigsawPlacement.class.getDeclaredMethod(
                        "isStartTooCloseToWorldHeightLimits",
                        LevelHeightAccessor.class,
                        DimensionPadding.class,
                        BoundingBox.class);
        check.setAccessible(true);
        return (boolean) check.invoke(null, NETHER, padding, centre);
    }

    /**
     * The height of the tallest template the start pool can open with. The plains pool keeps its
     * town centres, plain and zombie, in directories named after it under {@code village/plains}
     * ({@code PlainVillagePools.java:39-46}).
     */
    private static int tallestTownCentre() throws IOException, URISyntaxException {
        Identifier pool = Identifier.parse(VILLAGE.get("start_pool").getAsString());
        String poolPath = pool.getPath();
        String town = poolPath.substring(poolPath.lastIndexOf('/') + 1);
        String root =
                "data/"
                        + pool.getNamespace()
                        + "/structure/"
                        + poolPath.substring(0, poolPath.lastIndexOf('/'));
        URL url = NetherVillageHeightTest.class.getClassLoader().getResource(root);
        if (url == null) throw new IllegalStateException("Missing " + root);
        int tallest = 0;
        int templates = 0;
        try (Stream<Path> files = Files.walk(resource(url.toURI()))) {
            for (Path file : files.toList()) {
                if (!file.getFileName().toString().endsWith(".nbt")
                        || !file.getParent().getFileName().toString().equals(town)) {
                    continue;
                }
                try (InputStream in = Files.newInputStream(file)) {
                    int height =
                            NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap())
                                    .getListOrEmpty("size")
                                    .getIntOr(1, 0);
                    tallest = Math.max(tallest, height);
                    templates++;
                }
            }
        }
        assertTrue(templates > 0, "Found no town centre under " + root);
        return tallest;
    }

    /** The path of a resource, opening the game jar's file system unless it is already open. */
    private static Path resource(URI uri) throws IOException {
        try {
            return Path.of(uri);
        } catch (FileSystemNotFoundException notOpen) {
            FileSystems.newFileSystem(uri, Map.of());
            return Path.of(uri);
        }
    }

    private static JsonObject json(String path) {
        try (InputStream in = NetherVillageHeightTest.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
