package io.github.brainage04.dimensionstructurevariants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Lifecycle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Example;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.NetherFortressStructure;

/**
 * The palette table and the palette lookup, against the bundled {@code palettes.json} and the
 * vanilla classes, without a server: the biomes and structures are registered here, with values the
 * lookup never reads.
 */
class StructurePalettesTest {
    private static final ResourceKey<Biome> DATAPACK_NETHER_BIOME =
            ResourceKey.create(
                    Registries.BIOME, Identifier.fromNamespaceAndPath("dsvtest", "ashen_wastes"));

    private static final Map<ResourceKey<Biome>, Holder<Biome>> BIOMES;

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BIOMES = biomes();
    }

    /**
     * A bastion that started in an ocean but whose centre lies on a beach keeps the ocean palette
     * instead of its vanilla blackstone and lava.
     */
    @Example
    void aBastionCentredOnABeachKeepsAPalette() {
        StructurePalettes.Palette beach = palette("bastion_overworld", Biomes.BEACH);
        assertNotNull(beach);
        assertSame(palette("bastion_overworld", Biomes.OCEAN), beach);
    }

    @Example
    void anEndCityCentredInAWarmOceanKeepsAPalette() {
        StructurePalettes.Palette warm = palette("end_city_overworld", Biomes.WARM_OCEAN);
        assertNotNull(warm);
        assertSame(palette("end_city_overworld", Biomes.OCEAN), warm);
    }

    /**
     * A biome a datapack adds to {@code #minecraft:is_nether}, which the village's rules never
     * name.
     */
    @Example
    void aNetherVillageInADatapackBiomeKeepsAPalette() {
        StructurePalettes.Palette datapack = palette("village_nether", DATAPACK_NETHER_BIOME);
        assertNotNull(datapack);
        assertSame(palette("village_nether", Biomes.NETHER_WASTES), datapack);
    }

    /**
     * During natural generation a start is placed through the FEATURES step's region, which throws
     * for a biome read more than one chunk from the chunk it decorates. A vanilla structure must
     * not read the biome at all, and a variant must read it from the generator's biome source.
     */
    @Example
    void structuresReadNoChunkTheFeaturesStepWithholds() throws Exception {
        MappedRegistry<Structure> structures =
                new MappedRegistry<>(Registries.STRUCTURE, Lifecycle.stable());
        Structure vanilla = allocate(NetherFortressStructure.class);
        Structure variant = allocate(NetherFortressStructure.class);
        structures.register(BuiltinStructures.FORTRESS, vanilla, RegistrationInfo.BUILT_IN);
        structures.register(
                ResourceKey.create(
                        Registries.STRUCTURE, DimensionStructureVariants.of("fortress_overworld")),
                variant,
                RegistrationInfo.BUILT_IN);
        structures.freeze();
        Holder<Biome> plains = BIOMES.get(Biomes.PLAINS);
        WorldGenRegion region =
                featuresRegion(
                        new RegistryAccess.ImmutableRegistryAccess(List.of(structures)),
                        (x, y, z) -> plains);
        BlockPos centre = new BlockPos(2 * 16 + 8, 64, 8);

        assertThrows(
                ReportedException.class,
                () ->
                        region.getNoiseBiome(
                                QuartPos.fromBlock(centre.getX()),
                                QuartPos.fromBlock(centre.getY()),
                                QuartPos.fromBlock(centre.getZ())));
        assertNull(StructurePalettes.palette(region, vanilla, centre));
        StructurePalettes.Palette placed = StructurePalettes.palette(region, variant, centre);
        assertNotNull(placed);
        assertSame(palette("fortress_overworld", Biomes.PLAINS), placed);
    }

    @Example
    void aMisspelledBlockFailsTheTable() {
        IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () -> StructurePalettes.parse(table("minecraft:stone_brickz", true)));
        assertTrue(
                failure.getMessage().startsWith("Unknown block minecraft:stone_brickz "),
                failure.getMessage());
    }

    @Example
    void aStructureWithoutADefaultPaletteFailsTheTable() {
        IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () -> StructurePalettes.parse(table("minecraft:stone_bricks", false)));
        assertEquals("fortress_test names no default palette", failure.getMessage());
    }

    private static StructurePalettes.Palette palette(String variant, ResourceKey<Biome> biome) {
        return StructurePalettes.palette(DimensionStructureVariants.of(variant), BIOMES.get(biome));
    }

    private static JsonObject table(String brick, boolean withDefault) {
        String fallback = withDefault ? "\"default\": \"stone\"," : "";
        return JsonParser.parseString(
                        """
                        {
                          "role_sets": {"fortress": {"minecraft:nether_bricks": "wall"}},
                          "palettes": {"stone": {"blocks": {"wall": "%s"}}},
                          "structures": {
                            "fortress_test": {"roles": "fortress", %s "biomes": []}
                          }
                        }
                        """
                                .formatted(brick, fallback))
                .getAsJsonObject();
    }

    private static Map<ResourceKey<Biome>, Holder<Biome>> biomes() {
        MappedRegistry<Biome> registry = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        Map<ResourceKey<Biome>, Holder<Biome>> holders = new HashMap<>();
        for (ResourceKey<Biome> key :
                List.of(
                        Biomes.PLAINS,
                        Biomes.BEACH,
                        Biomes.OCEAN,
                        Biomes.WARM_OCEAN,
                        Biomes.NETHER_WASTES,
                        DATAPACK_NETHER_BIOME)) {
            holders.put(
                    key, registry.register(key, allocate(Biome.class), RegistrationInfo.BUILT_IN));
        }
        registry.bindTags(
                Map.of(
                        BiomeTags.IS_OCEAN,
                        List.of(holders.get(Biomes.OCEAN), holders.get(Biomes.WARM_OCEAN)),
                        BiomeTags.IS_NETHER,
                        List.of(
                                holders.get(Biomes.NETHER_WASTES),
                                holders.get(DATAPACK_NETHER_BIOME))));
        registry.freeze();
        return holders;
    }

    /**
     * The region the FEATURES step decorates chunk 0, 0 through, as {@code WorldGenRegion}'s
     * constructor would build it from a server level, with the chunks it holds left empty.
     */
    private static WorldGenRegion featuresRegion(RegistryAccess registries, BiomeResolver biomes)
            throws ReflectiveOperationException {
        ServerLevel level = allocate(ServerLevel.class);
        set(Level.class, level, "registryAccess", registries);
        ChunkStep step = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES);
        StaticCache2D<ChunkAccess> cache =
                StaticCache2D.create(
                        0,
                        0,
                        step.directDependencies().size() - 1,
                        (x, z) -> chunk(new ChunkPos(x, z)));
        WorldGenRegion region = allocate(WorldGenRegion.class);
        set(WorldGenRegion.class, region, "level", level);
        set(WorldGenRegion.class, region, "center", chunk(ChunkPos.ZERO));
        set(WorldGenRegion.class, region, "generatingStep", step);
        set(WorldGenRegion.class, region, "cache", cache);
        set(WorldGenRegion.class, region, "uncachedBiomeResolver", biomes);
        return region;
    }

    private static ProtoChunk chunk(ChunkPos pos) {
        ProtoChunk chunk = allocate(ProtoChunk.class);
        try {
            set(ChunkAccess.class, chunk, "chunkPos", pos);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
        return chunk;
    }

    private static void set(Class<?> owner, Object target, String name, Object value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /** An instance whose constructor never ran: the lookup reads none of its fields. */
    private static <T> T allocate(Class<T> type) {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field field = unsafeClass.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
            return type.cast(allocateInstance.invoke(field.get(null), type));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
