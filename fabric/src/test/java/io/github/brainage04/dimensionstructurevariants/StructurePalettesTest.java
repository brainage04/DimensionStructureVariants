package io.github.brainage04.dimensionstructurevariants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Lifecycle;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.jqwik.api.Example;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
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

    /** A biome that takes the default has no rule; one a rule names has. */
    @Example
    void onlyABiomeARuleNamesHasARule() {
        assertTrue(hasRule("village_nether", Biomes.NETHER_WASTES));
        assertTrue(hasRule("bastion_overworld", Biomes.WARM_OCEAN));
        assertFalse(hasRule("village_nether", DATAPACK_NETHER_BIOME));
        assertFalse(hasRule("bastion_overworld", Biomes.BEACH));
        assertFalse(
                StructurePalettes.hasRule(
                        Identifier.withDefaultNamespace("bastion_remnant"),
                        BIOMES.get(Biomes.NETHER_WASTES)));
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

    /**
     * A piece accent naming a piece the game does not know fails the table, so a misspelled id
     * cannot quietly leave a part of the structure in the main block.
     */
    @Example
    void aMisspelledAccentPieceFailsTheTable() {
        IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () -> StructurePalettes.parse(accentTable("minecraft:nebc")));
        assertTrue(
                failure.getMessage().startsWith("Palette stone names the unknown piece "),
                failure.getMessage());
    }

    @Example
    void aRealAccentPiecePassesTheTable() {
        assertNotNull(StructurePalettes.parse(accentTable("minecraft:nebcr")));
    }

    private static JsonObject accentTable(String piece) {
        return accentTable(piece, "\"minecraft:blue_ice\"");
    }

    private static JsonObject accentTable(String piece, String accentBlock) {
        return JsonParser.parseString(
                        """
                        {
                          "role_sets": {"fortress": {"minecraft:nether_bricks": "wall"}},
                          "palettes": {
                            "stone": {
                              "blocks": {"wall": "minecraft:stone_bricks"},
                              "piece_accent": {
                                "blocks": {"wall": %s},
                                "pieces": ["%s"]
                              }
                            }
                          },
                          "structures": {
                            "fortress_test": {
                              "roles": "fortress",
                              "default": "stone",
                              "biomes": []
                            }
                          }
                        }
                        """
                                .formatted(accentBlock, piece))
                .getAsJsonObject();
    }

    /**
     * A role left null inside a piece accent has no meaning (leaving the role out keeps the main
     * block), so it fails the table with a message that names the palette and the role.
     */
    @Example
    void aNullAccentBlockFailsTheTable() {
        IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () -> StructurePalettes.parse(accentTable("minecraft:nebcr", "null")));
        assertTrue(
                failure.getMessage()
                        .startsWith("Palette stone gives its accent role wall no block"),
                failure.getMessage());
    }

    /**
     * Blocks a palette may never replace: gold, gilded blackstone and the rest of the set the game
     * itself guards with piglins or builds beacons and copper from, read from the vanilla block
     * tags, and every {@code _ore}. The few valuable blocks no tag names are listed by hand.
     */
    private static final List<String> VALUABLE_TAGS =
            List.of("guarded_by_piglins", "beacon_base_blocks", "copper", "ores");

    private static final Set<String> VALUABLE_BLOCKS = valuableBlocks();

    private static Set<String> valuableBlocks() {
        Set<String> blocks =
                new TreeSet<>(
                        Set.of(
                                "minecraft:ancient_debris",
                                "minecraft:lapis_block",
                                "minecraft:raw_iron_block",
                                "minecraft:raw_copper_block",
                                "minecraft:beacon",
                                "minecraft:spawner",
                                "minecraft:trial_spawner",
                                "minecraft:vault"));
        for (String tag : VALUABLE_TAGS) addTag(blocks, tag);
        return blocks;
    }

    private static void addTag(Set<String> blocks, String tag) {
        String path = "/data/minecraft/tags/block/" + tag + ".json";
        try (InputStream stream = Block.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            JsonObject json =
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                            .getAsJsonObject();
            for (JsonElement value : json.getAsJsonArray("values")) {
                String id = value.getAsString();
                if (id.startsWith(TAG_PREFIX)) {
                    addTag(blocks, id.substring(TAG_PREFIX.length()));
                } else {
                    blocks.add(id);
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(path, exception);
        }
    }

    private static final String TAG_PREFIX = "#minecraft:";

    /**
     * Replacements of a valuable or block-entity block that stay for now: a barrel is a container,
     * and the village palettes turn it into purpur pillar or stripped crimson hyphae, a block with
     * no block entity, which drops the template's contents and loot table. Each entry reads {@code
     * source role -> targets in palette name}, the palette included, so a new palette that does the
     * same is not covered. The test fails when an entry no longer matches the shipped table, so the
     * list shrinks as the replacements go.
     */
    private static final Set<String> OPEN_REPLACEMENTS =
            Set.of(
                    "minecraft:barrel storage -> [minecraft:purpur_pillar] in palette village_end",
                    "minecraft:barrel storage -> [minecraft:stripped_crimson_hyphae] in palette village_nether",
                    "minecraft:barrel storage -> [minecraft:stripped_crimson_hyphae] in palette village_nether_basalt",
                    "minecraft:barrel storage -> [minecraft:stripped_crimson_hyphae] in palette village_nether_crimson",
                    "minecraft:barrel storage -> [minecraft:stripped_crimson_hyphae] in palette village_nether_soul",
                    "minecraft:barrel storage -> [minecraft:stripped_crimson_hyphae] in palette village_nether_warped");

    private static boolean valuable(Block block) {
        String id = BuiltInRegistries.BLOCK.getKey(block).toString();
        return VALUABLE_BLOCKS.contains(id) || id.endsWith("_ore");
    }

    private static StructurePalettes.Data shippedTable() throws Exception {
        try (Reader reader =
                new InputStreamReader(
                        StructurePalettes.class.getResourceAsStream(
                                "/dimensionstructurevariants/palettes.json"),
                        StandardCharsets.UTF_8)) {
            return StructurePalettes.parse(JsonParser.parseReader(reader).getAsJsonObject());
        }
    }

    /**
     * Every palette a structure uses, resolved: structure to palette to the replacements each of
     * its roles makes of a source block that {@code matches}. A role set maps source blocks to
     * roles and a palette maps roles to blocks, so a role that a palette leaves null, or maps to
     * the source block itself, replaces nothing.
     */
    private static Set<String> replacements(
            StructurePalettes.Data data, java.util.function.Predicate<Block> matches) {
        Set<String> found = new TreeSet<>();
        for (Map.Entry<String, StructurePalettes.Rules> structure : data.structures().entrySet()) {
            Map<String, String> roles = data.roleSets().get(structure.getValue().roles());
            Set<String> palettes = new LinkedHashSet<>();
            palettes.add(structure.getValue().fallback());
            for (StructurePalettes.Rule rule : structure.getValue().biomes()) {
                palettes.add(rule.palette());
            }
            for (String name : palettes) {
                StructurePalettes.RawPalette palette = data.palettes().get(name);
                for (Map.Entry<String, String> role : roles.entrySet()) {
                    Block source =
                            BuiltInRegistries.BLOCK.getValue(Identifier.parse(role.getKey()));
                    if (!matches.test(source)) continue;
                    note(found, name, role, palette.blocks().get(role.getValue()), "");
                    if (palette.accent() != null) {
                        note(
                                found,
                                name,
                                role,
                                palette.accent().blocks().get(role.getValue()),
                                " (piece_accent)");
                    }
                }
            }
        }
        return found;
    }

    private static void note(
            Set<String> found,
            String palette,
            Map.Entry<String, String> role,
            Map<String, Integer> targets,
            String where) {
        if (targets == null || targets.keySet().equals(Set.of(role.getKey()))) return;
        found.add(
                role.getKey()
                        + " "
                        + role.getValue()
                        + " -> "
                        + new TreeSet<>(targets.keySet())
                        + " in palette "
                        + palette
                        + where);
    }

    private static Set<String> withoutOpen(Set<String> replacements) {
        Set<String> rest = new TreeSet<>(replacements);
        rest.removeAll(OPEN_REPLACEMENTS);
        return rest;
    }

    /**
     * A block the lists above name that the registry does not know is a typo or a removed block.
     */
    @Example
    void everyNamedValuableBlockExists() {
        for (String id : VALUABLE_BLOCKS) {
            assertTrue(BuiltInRegistries.BLOCK.containsKey(Identifier.parse(id)), id);
        }
    }

    /** The tags reach the blocks a hand-written list missed: copper chests and shulker boxes. */
    @Example
    void theValuableSetHoldsCopperChestsAndShulkerBoxes() {
        assertTrue(VALUABLE_BLOCKS.contains("minecraft:copper_chest"));
        assertTrue(VALUABLE_BLOCKS.contains("minecraft:waxed_oxidized_copper_chest"));
        assertTrue(VALUABLE_BLOCKS.contains("minecraft:shulker_box"));
        assertTrue(VALUABLE_BLOCKS.contains("minecraft:gold_block"));
        assertTrue(VALUABLE_BLOCKS.contains("minecraft:raw_gold_block"));
    }

    /**
     * A palette keeps gold blocks, gilded blackstone, ores, metal blocks, beacons, containers and
     * spawners as vanilla places them. The bastion palettes used to turn gold and gilded blackstone
     * into sea lanterns and purpur.
     */
    @Example
    void noPaletteReplacesAValuableBlock() throws Exception {
        Set<String> replaced =
                withoutOpen(replacements(shippedTable(), StructurePalettesTest::valuable));
        assertTrue(replaced.isEmpty(), "Palettes replace valuable blocks: " + replaced);
    }

    /**
     * The open replacements are exactly what the shipped table still does, so a decision that
     * removes one is not left behind in the list.
     */
    @Example
    void everyOpenReplacementIsStillInTheTable() throws Exception {
        Set<String> stillThere = new TreeSet<>();
        for (String replacement : replacements(shippedTable(), StructurePalettesTest::valuable)) {
            stillThere.add(replacement);
        }
        assertEquals(new TreeSet<>(OPEN_REPLACEMENTS), stillThere);
    }

    /**
     * The scan reports what it is meant to: an ore and a copper chest, one through the palette's
     * own block and one through a piece accent, and nothing for a block it does not guard.
     */
    @Example
    void theScanReportsAValuableBlockAPaletteReplaces() {
        StructurePalettes.Data data =
                StructurePalettes.parse(
                        JsonParser.parseString(
                                        """
                                        {
                                          "role_sets": {"test": {
                                            "minecraft:diamond_ore": "ore",
                                            "minecraft:copper_chest": "chest",
                                            "minecraft:stone_bricks": "wall"}},
                                          "palettes": {"p": {
                                            "blocks": {
                                              "ore": "minecraft:stone",
                                              "chest": "minecraft:copper_chest",
                                              "wall": "minecraft:cobblestone"},
                                            "piece_accent": {
                                              "blocks": {"chest": "minecraft:glass"},
                                              "pieces": ["minecraft:nebcr"]}}},
                                          "structures": {"s": {
                                            "roles": "test", "default": "p", "biomes": []}}
                                        }
                                        """)
                                .getAsJsonObject());
        assertEquals(
                Set.of(
                        "minecraft:diamond_ore ore -> [minecraft:stone] in palette p",
                        "minecraft:copper_chest chest -> [minecraft:glass] in palette p (piece_accent)"),
                replacements(data, StructurePalettesTest::valuable));
    }

    /**
     * An explicit null keeps the vanilla block when a palette is compiled and a block is remapped:
     * the four bastion palettes keep their gold block and gilded blackstone, and still change
     * crying obsidian, so the check can fail.
     */
    @Example
    void aBastionKeepsItsGoldAndChangesItsCryingObsidian() {
        List<StructurePalettes.Palette> bastions =
                List.of(
                        palette("bastion_overworld", Biomes.OCEAN),
                        palette("bastion_overworld", Biomes.WARM_OCEAN),
                        palette("bastion_overworld", Biomes.FROZEN_OCEAN),
                        palette("bastion_end", Biomes.THE_END));
        assertEquals(bastions.size(), Set.copyOf(bastions).size());
        BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
        BlockState gilded = Blocks.GILDED_BLACKSTONE.defaultBlockState();
        BlockState crying = Blocks.CRYING_OBSIDIAN.defaultBlockState();
        for (StructurePalettes.Palette bastion : bastions) {
            assertSame(gold, bastion.remap(gold, BlockPos.ZERO));
            assertSame(gilded, bastion.remap(gilded, BlockPos.ZERO));
            assertFalse(bastion.remap(crying, BlockPos.ZERO).is(Blocks.CRYING_OBSIDIAN));
        }
    }

    /**
     * A block with a block entity that a palette turns into one without loses its loot table and
     * contents: {@code StructureTemplate} sets the replacement block and then loads the template's
     * NBT into whatever block entity is there ({@code StructureTemplate.java:280-296}), so no block
     * entity means the NBT, loot table included, is dropped.
     */
    @Example
    void noPaletteTurnsABlockEntityBlockIntoOneWithoutAnEntity() throws Exception {
        StructurePalettes.Data data = shippedTable();
        Set<String> lost = new TreeSet<>();
        for (String replacement : replacements(data, source -> source instanceof EntityBlock)) {
            String head = replacement.substring(0, replacement.indexOf(" in palette "));
            String targets = head.substring(head.indexOf("-> [") + 4, head.length() - 1);
            for (String target : targets.split(", ")) {
                Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(target));
                if (!(block instanceof EntityBlock)) lost.add(replacement);
            }
        }
        assertTrue(
                withoutOpen(lost).isEmpty(),
                "Palettes drop the block entity of: " + withoutOpen(lost));
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

    private static boolean hasRule(String variant, ResourceKey<Biome> biome) {
        return StructurePalettes.hasRule(DimensionStructureVariants.of(variant), BIOMES.get(biome));
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
                        Biomes.FROZEN_OCEAN,
                        Biomes.THE_END,
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
