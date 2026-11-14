package io.github.brainage04.dimensionstructurevariants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.HashCommon;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.GenerationMode;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * Properties of the palette lookup and the weighted block pick, against the bundled {@code
 * palettes.json}, vanilla's biome tags and vanilla's {@code WeightedList}.
 */
class StructurePalettesPropertyTest {
    private static final String TABLE = "/dimensionstructurevariants/palettes.json";

    private static final String STRUCTURES = "/data/dimensionstructurevariants/worldgen/structure/";

    /** A biome no rule and no vanilla tag names, as a datapack adds one. */
    private static final ResourceKey<Biome> UNNAMED =
            ResourceKey.create(
                    Registries.BIOME, Identifier.fromNamespaceAndPath("dsvtest", "none"));

    private static final JsonObject PALETTES;

    /** Every vanilla biome and {@link #UNNAMED}, bound to the vanilla tags the tables name. */
    private static final Map<ResourceKey<Biome>, Holder<Biome>> VANILLA_BIOMES;

    private static final Map<TagKey<Biome>, Set<ResourceKey<Biome>>> VANILLA_TAGS;

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        PALETTES = json(TABLE);
        VANILLA_TAGS = new LinkedHashMap<>();
        VANILLA_BIOMES = vanillaBiomes();
    }

    /**
     * PIT swaps each mutant into a JVM where an earlier mutant may already have loaded the table
     * and compiled the palettes, which would keep the mutated code from running, and it credits the
     * loading code only to the property that ran it first.
     */
    @BeforeProperty
    void forgetTheLoadedTable() throws ReflectiveOperationException {
        Field data = StructurePalettes.class.getDeclaredField("data");
        data.setAccessible(true);
        data.set(null, null);
        Field compiled = StructurePalettes.class.getDeclaredField("COMPILED");
        compiled.setAccessible(true);
        ((Map<?, ?>) compiled.get(null)).clear();
    }

    // ---------------------------------------------------------------------------------------
    // Rule order, against generated tables
    // ---------------------------------------------------------------------------------------

    private static final List<String> TEST_BIOMES = List.of("a", "b", "c", "d", "e");

    private static final Map<String, List<String>> TEST_TAGS =
            Map.of("warm", List.of("a", "b"), "wet", List.of("b", "c", "d"));

    private static final Map<String, Holder<Biome>> TEST_HOLDERS = testBiomes();

    record RuleSpec(List<String> match, int palette) {}

    @Provide
    Arbitrary<List<RuleSpec>> rules() {
        List<String> matchers = new ArrayList<>();
        for (String biome : TEST_BIOMES) matchers.add("dsvtest:" + biome);
        for (String tag : TEST_TAGS.keySet()) matchers.add("#dsvtest:" + tag);
        // A biome id no registry holds matches nothing.
        matchers.add("dsvtest:missing");
        Arbitrary<RuleSpec> rule =
                Combinators.combine(
                                Arbitraries.of(matchers).list().ofMaxSize(3),
                                Arbitraries.integers().between(0, 3))
                        .as(RuleSpec::new);
        return rule.list().ofMaxSize(5);
    }

    /**
     * A biome takes the palette of the first rule, in file order, with any matcher naming it or a
     * tag holding it, and the structure's default when none does.
     */
    @Property
    void theFirstMatchingRuleChoosesThePalette(
            @ForAll("rules") List<RuleSpec> rules,
            @ForAll @IntRange(min = 0, max = 3) int fallback,
            @ForAll("testBiome") String biome) {
        StructurePalettes.Rules parsed =
                StructurePalettes.parse(table(rules, fallback)).structures().get("test");
        String expected = null;
        search:
        for (RuleSpec rule : rules) {
            for (String matcher : rule.match()) {
                if (names(matcher, biome)) {
                    expected = "p" + rule.palette();
                    break search;
                }
            }
        }
        Holder<Biome> holder = TEST_HOLDERS.get(biome);
        assertEquals(expected, parsed.ruleFor(holder));
        assertEquals(expected == null ? "p" + fallback : expected, parsed.paletteFor(holder));
    }

    @Provide
    Arbitrary<String> testBiome() {
        return Arbitraries.of(TEST_BIOMES);
    }

    private static boolean names(String matcher, String biome) {
        if (matcher.startsWith("#dsvtest:")) {
            return TEST_TAGS.get(matcher.substring("#dsvtest:".length())).contains(biome);
        }
        return matcher.equals("dsvtest:" + biome);
    }

    private static JsonObject table(List<RuleSpec> rules, int fallback) {
        JsonObject palettes = new JsonObject();
        for (int i = 0; i <= 3; i++) {
            JsonObject blocks = new JsonObject();
            blocks.addProperty("wall", "minecraft:stone");
            JsonObject palette = new JsonObject();
            palette.add("blocks", blocks);
            palettes.add("p" + i, palette);
        }
        JsonArray biomes = new JsonArray();
        for (RuleSpec rule : rules) {
            JsonArray match = new JsonArray();
            rule.match().forEach(match::add);
            JsonObject object = new JsonObject();
            object.add("match", match);
            object.addProperty("palette", "p" + rule.palette());
            biomes.add(object);
        }
        JsonObject structure = new JsonObject();
        structure.addProperty("roles", "test");
        structure.addProperty("default", "p" + fallback);
        structure.add("biomes", biomes);
        JsonObject structures = new JsonObject();
        structures.add("test", structure);
        JsonObject roles = new JsonObject();
        roles.addProperty("minecraft:stone_bricks", "wall");
        JsonObject roleSets = new JsonObject();
        roleSets.add("test", roles);
        JsonObject root = new JsonObject();
        root.add("role_sets", roleSets);
        root.add("palettes", palettes);
        root.add("structures", structures);
        return root;
    }

    // ---------------------------------------------------------------------------------------
    // What a table must say, against generated tables
    // ---------------------------------------------------------------------------------------

    /** A block's weight in a choice must be at least 1. */
    @Property
    void aWeightBelowOneFailsTheTable(@ForAll @IntRange(min = -1000, max = 1000) int weight) {
        JsonObject root = table(List.of(), 0);
        JsonObject choice = new JsonObject();
        choice.addProperty("minecraft:stone", 3);
        choice.addProperty("minecraft:cobblestone", weight);
        blocks(root, "p2").add("wall", choice);
        if (weight >= 1) {
            StructurePalettes.parse(root);
        } else {
            assertEquals(
                    "Palette p2 gives minecraft:cobblestone the weight " + weight,
                    assertThrows(IllegalStateException.class, () -> StructurePalettes.parse(root))
                            .getMessage());
        }
    }

    @Example
    void aChoiceOfNoBlocksFailsTheTable() {
        JsonObject root = table(List.of(), 0);
        blocks(root, "p1").add("wall", new JsonObject());
        assertEquals(
                "Palette p1 names an empty choice",
                assertThrows(IllegalStateException.class, () -> StructurePalettes.parse(root))
                        .getMessage());
    }

    /** A ground feature's chance must lie above 0 and at most 1. */
    @Property(generation = GenerationMode.EXHAUSTIVE)
    void aGroundFeatureChanceOutsideZeroToOneFailsTheTable(
            @ForAll @IntRange(min = -3, max = 1003) int thousandths) {
        float chance = thousandths / 1000.0F;
        JsonObject root = table(List.of(), 0);
        JsonObject feature = new JsonObject();
        feature.addProperty("feature", "minecraft:oak");
        feature.addProperty("chance", chance);
        root.getAsJsonObject("palettes").getAsJsonObject("p3").add("ground_feature", feature);
        if (chance > 0.0F && chance <= 1.0F) {
            StructurePalettes.parse(root);
        } else {
            assertThrows(IllegalStateException.class, () -> StructurePalettes.parse(root));
        }
    }

    /**
     * Every palette a structure uses answers each role of its role set, with a block or an explicit
     * null, and no other role.
     */
    @Property(generation = GenerationMode.EXHAUSTIVE)
    void aPaletteAnswersExactlyTheRolesOfItsStructure(
            @ForAll @IntRange(min = 0, max = 2) int wall,
            @ForAll @IntRange(min = 0, max = 2) int roof,
            @ForAll @IntRange(min = 0, max = 2) int floor,
            @ForAll boolean asDefault) {
        JsonObject root =
                table(asDefault ? List.of() : List.of(new RuleSpec(List.of("dsvtest:a"), 1)), 0);
        root.getAsJsonObject("role_sets")
                .getAsJsonObject("test")
                .addProperty("minecraft:bricks", "roof");
        for (int i = 0; i <= 3; i++) blocks(root, "p" + i).addProperty("roof", "minecraft:stone");
        String used = asDefault ? "p0" : "p1";
        JsonObject blocks = blocks(root, used);
        answer(blocks, "wall", wall);
        answer(blocks, "roof", roof);
        answer(blocks, "floor", floor);
        if (wall > 0 && roof > 0 && floor == 0) {
            StructurePalettes.parse(root);
        } else {
            assertThrows(IllegalStateException.class, () -> StructurePalettes.parse(root));
        }
    }

    /** 0 leaves the role out, 1 answers it with a block and 2 with an explicit null. */
    private static void answer(JsonObject blocks, String role, int how) {
        blocks.remove(role);
        if (how == 1) blocks.addProperty(role, "minecraft:stone");
        if (how == 2) blocks.add(role, com.google.gson.JsonNull.INSTANCE);
    }

    @Property
    void aRuleOrDefaultNamingAnUnknownPaletteFailsTheTable(@ForAll boolean inARule) {
        JsonObject root = table(List.of(new RuleSpec(List.of("dsvtest:a"), 1)), 0);
        JsonObject structure = root.getAsJsonObject("structures").getAsJsonObject("test");
        if (inARule) {
            structure
                    .getAsJsonArray("biomes")
                    .get(0)
                    .getAsJsonObject()
                    .addProperty("palette", "nowhere");
        } else {
            structure.addProperty("default", "nowhere");
        }
        assertEquals(
                "test names the unknown palette nowhere",
                assertThrows(IllegalStateException.class, () -> StructurePalettes.parse(root))
                        .getMessage());
    }

    /**
     * A palette that extends another takes every block it does not set itself from it, through any
     * number of steps; a palette that extends itself, directly or through others, fails.
     */
    @Property
    void aPaletteTakesTheBlocksOfThePalettesItExtends(
            @ForAll @IntRange(min = 1, max = 6) int steps, @ForAll boolean cycle) {
        JsonObject root = table(List.of(), 0);
        JsonObject palettes = root.getAsJsonObject("palettes");
        for (int i = 1; i <= steps; i++) {
            JsonObject palette = new JsonObject();
            palette.addProperty("extends", "c" + (i - 1));
            palettes.add("c" + i, palette);
        }
        JsonObject first = new JsonObject();
        first.add("blocks", blocks(root, "p0").deepCopy());
        if (cycle) first.addProperty("extends", "c" + steps);
        palettes.add("c0", first);
        root.getAsJsonObject("structures")
                .getAsJsonObject("test")
                .addProperty("default", "c" + steps);
        if (cycle) {
            assertTrue(
                    assertThrows(IllegalStateException.class, () -> StructurePalettes.parse(root))
                            .getMessage()
                            .contains(" extends itself through "));
        } else {
            StructurePalettes.parse(root);
        }
    }

    private static JsonObject blocks(JsonObject root, String palette) {
        return root.getAsJsonObject("palettes").getAsJsonObject(palette).getAsJsonObject("blocks");
    }

    // ---------------------------------------------------------------------------------------
    // The bundled table against vanilla's biomes
    // ---------------------------------------------------------------------------------------

    record StartBiome(String variant, ResourceKey<Biome> biome) {}

    /**
     * Every vanilla biome a variant's structure may start in has a rule of its own, so none of them
     * falls back to the default by accident. A biome a later Minecraft adds to those tags fails
     * here until the table names it.
     */
    @Property(generation = GenerationMode.EXHAUSTIVE)
    void everyVanillaStartBiomeHasARule(@ForAll("startBiomes") StartBiome start) {
        assertTrue(
                StructurePalettes.hasRule(
                        DimensionStructureVariants.of(start.variant()),
                        VANILLA_BIOMES.get(start.biome())),
                start.variant() + " has no rule for " + start.biome().identifier());
    }

    @Provide
    Arbitrary<StartBiome> startBiomes() {
        List<StartBiome> starts = new ArrayList<>();
        for (String variant : PALETTES.getAsJsonObject("structures").keySet()) {
            JsonElement biomes = json(STRUCTURES + variant + ".json").get("biomes");
            for (ResourceKey<Biome> biome : resolve(biomes))
                starts.add(new StartBiome(variant, biome));
        }
        assertFalse(starts.isEmpty());
        return Arbitraries.of(starts);
    }

    /** A matcher naming a biome or tag vanilla does not have would never match. */
    @Example
    void everyMatcherNamesAVanillaBiomeOrTag() {
        List<String> unknown = new ArrayList<>();
        for (Map.Entry<String, JsonElement> structure :
                PALETTES.getAsJsonObject("structures").entrySet()) {
            for (JsonElement rule :
                    structure.getValue().getAsJsonObject().getAsJsonArray("biomes")) {
                for (JsonElement matcher : rule.getAsJsonObject().getAsJsonArray("match")) {
                    String value = matcher.getAsString();
                    boolean known =
                            value.startsWith("#")
                                    ? !tagMembers(Identifier.parse(value.substring(1))).isEmpty()
                                    : VANILLA_BIOMES.containsKey(
                                            ResourceKey.create(
                                                    Registries.BIOME, Identifier.parse(value)));
                    if (!known) unknown.add(structure.getKey() + ": " + value);
                }
            }
        }
        assertTrue(unknown.isEmpty(), "Unknown biomes or tags " + unknown);
    }

    /** A biome two rules of one structure match takes the first; the second is dead for it. */
    @Property(generation = GenerationMode.EXHAUSTIVE)
    void noVanillaBiomeIsMatchedByTwoRules(@ForAll("anyVariantAndBiome") StartBiome pair) {
        JsonObject structure =
                PALETTES.getAsJsonObject("structures").getAsJsonObject(pair.variant());
        int matched = 0;
        for (JsonElement rule : structure.getAsJsonArray("biomes")) {
            if (resolve(rule.getAsJsonObject().get("match")).contains(pair.biome())) matched++;
        }
        assertTrue(matched <= 1, pair + " is matched by " + matched + " rules");
    }

    @Provide
    Arbitrary<StartBiome> anyVariantAndBiome() {
        List<StartBiome> pairs = new ArrayList<>();
        for (String variant : PALETTES.getAsJsonObject("structures").keySet()) {
            for (ResourceKey<Biome> biome : VANILLA_BIOMES.keySet()) {
                pairs.add(new StartBiome(variant, biome));
            }
        }
        return Arbitraries.of(pairs);
    }

    // ---------------------------------------------------------------------------------------
    // Compiled palettes
    // ---------------------------------------------------------------------------------------

    record PaletteCase(String variant, ResourceKey<Biome> biome, List<BlockState> sources) {
        StructurePalettes.Palette palette() {
            StructurePalettes.Palette palette =
                    StructurePalettes.palette(
                            DimensionStructureVariants.of(variant), VANILLA_BIOMES.get(biome));
            assertNotNull(palette, variant + " in " + biome.identifier());
            return palette;
        }
    }

    /** Each variant once per vanilla biome its rules name, and once in a biome they do not. */
    @Provide
    Arbitrary<PaletteCase> palettes() {
        List<PaletteCase> cases = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry :
                PALETTES.getAsJsonObject("structures").entrySet()) {
            JsonObject structure = entry.getValue().getAsJsonObject();
            List<BlockState> sources = new ArrayList<>();
            for (String block :
                    PALETTES.getAsJsonObject("role_sets")
                            .getAsJsonObject(structure.get("roles").getAsString())
                            .keySet()) {
                sources.addAll(
                        BuiltInRegistries.BLOCK
                                .getValue(Identifier.parse(block))
                                .getStateDefinition()
                                .getPossibleStates());
            }
            Set<ResourceKey<Biome>> biomes = new LinkedHashSet<>();
            for (JsonElement rule : structure.getAsJsonArray("biomes")) {
                biomes.addAll(resolve(rule.getAsJsonObject().get("match")));
            }
            biomes.add(UNNAMED);
            for (ResourceKey<Biome> biome : biomes) {
                cases.add(new PaletteCase(entry.getKey(), biome, List.copyOf(sources)));
            }
        }
        return Arbitraries.of(cases);
    }

    /**
     * A palette puts one of its role's blocks in place of a mapped state, the same one every time
     * at one position, and keeps every state it does not map.
     */
    @Property(tries = 2000)
    void aPositionAlwaysGetsTheSameBlockOfItsRole(
            @ForAll("palettes") PaletteCase palette,
            @ForAll @IntRange(min = 0, max = Integer.MAX_VALUE) int source,
            @ForAll @IntRange(min = -30_000_000, max = 30_000_000) int x,
            @ForAll @IntRange(min = -64, max = 319) int y,
            @ForAll @IntRange(min = -30_000_000, max = 30_000_000) int z) {
        StructurePalettes.Palette compiled = palette.palette();
        BlockState state = palette.sources().get(source % palette.sources().size());
        BlockPos pos = new BlockPos(x, y, z);
        BlockState placed = compiled.remap(state, pos);
        StructurePalettes.Choice choice = compiled.choiceFor(state);
        if (choice == null) {
            assertSame(state, placed);
            return;
        }
        assertTrue(choice.states().contains(placed), placed + " is not among " + choice.states());
        assertSame(placed, choice.at(x, y, z));
        assertSame(placed, compiled.remap(state, new BlockPos(x, y, z)));
        if (choice.states().size() > 1) {
            assertSame(placed, choice.pick(BlockPos.asLong(x, y, z)));
        }
    }

    /**
     * A compiled palette replaces every state of a role's block with the blocks the table gives
     * that role, through the palettes it extends, its main block being the first of the largest
     * weight; a role the table answers with null keeps its block. Its ground and ground feature are
     * the table's.
     */
    @Property(tries = 1000)
    void aCompiledPaletteHoldsWhatTheTableSays(
            @ForAll("palettes") PaletteCase palette,
            @ForAll @IntRange(min = 0, max = Integer.MAX_VALUE) int source) {
        StructurePalettes.Palette compiled = palette.palette();
        JsonObject structure =
                PALETTES.getAsJsonObject("structures").getAsJsonObject(palette.variant());
        String name = structure.get("default").getAsString();
        for (JsonElement rule : structure.getAsJsonArray("biomes")) {
            if (resolve(rule.getAsJsonObject().get("match")).contains(palette.biome())) {
                name = rule.getAsJsonObject().get("palette").getAsString();
                break;
            }
        }
        JsonObject resolved = resolvedPalette(name);
        BlockState state = palette.sources().get(source % palette.sources().size());
        String role =
                PALETTES.getAsJsonObject("role_sets")
                        .getAsJsonObject(structure.get("roles").getAsString())
                        .get(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())
                        .getAsString();
        JsonElement target = resolved.getAsJsonObject("blocks").get(role);
        StructurePalettes.Choice choice = compiled.choiceFor(state);
        if (target.isJsonNull()) {
            assertNull(choice, state + " has the null role " + role + " in " + name);
        } else {
            assertNotNull(choice, state + " has the role " + role + " in " + name);
            assertChoice(weights(target), choice, role + " in " + name);
        }
        if (resolved.has("ground")) {
            assertNotNull(compiled.ground(), name);
            assertChoice(weights(resolved.get("ground")), compiled.ground(), "ground of " + name);
        } else {
            assertNull(compiled.ground(), name);
        }
        if (resolved.has("ground_feature")) {
            JsonObject feature = resolved.getAsJsonObject("ground_feature");
            assertEquals(
                    feature.get("feature").getAsString(),
                    compiled.groundFeature().identifier().toString());
            assertEquals(feature.get("chance").getAsFloat(), compiled.groundFeatureChance());
        } else {
            assertNull(compiled.groundFeature(), name);
            assertEquals(0.0F, compiled.groundFeatureChance());
        }
    }

    private static void assertChoice(
            Map<String, Integer> weights, StructurePalettes.Choice choice, String what) {
        List<String> blocks = new ArrayList<>();
        for (BlockState placed : choice.states()) {
            blocks.add(BuiltInRegistries.BLOCK.getKey(placed.getBlock()).toString());
        }
        assertEquals(List.copyOf(weights.keySet()), blocks, what);
        String main = null;
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            if (main == null || entry.getValue() > weights.get(main)) main = entry.getKey();
        }
        assertEquals(
                main, BuiltInRegistries.BLOCK.getKey(choice.main().getBlock()).toString(), what);
    }

    /** A target as the table writes it: one block of weight 1, or blocks with their weights. */
    private static Map<String, Integer> weights(JsonElement target) {
        Map<String, Integer> weights = new LinkedHashMap<>();
        if (target.isJsonPrimitive()) {
            weights.put(target.getAsString(), 1);
        } else {
            target.getAsJsonObject().asMap().forEach((id, w) -> weights.put(id, w.getAsInt()));
        }
        return weights;
    }

    /** A palette with what it takes from the palettes it extends, as one object. */
    private static JsonObject resolvedPalette(String name) {
        JsonObject raw = PALETTES.getAsJsonObject("palettes").getAsJsonObject(name);
        JsonObject resolved =
                raw.has("extends")
                        ? resolvedPalette(raw.get("extends").getAsString())
                        : new JsonObject();
        JsonObject blocks =
                resolved.has("blocks") ? resolved.getAsJsonObject("blocks") : new JsonObject();
        if (raw.has("blocks")) {
            raw.getAsJsonObject("blocks").asMap().forEach(blocks::add);
        }
        resolved.add("blocks", blocks);
        if (raw.has("ground")) resolved.add("ground", raw.get("ground"));
        if (raw.has("ground_feature")) resolved.add("ground_feature", raw.get("ground_feature"));
        return resolved;
    }

    /**
     * A replacement keeps every property value of the vanilla state its block can hold: a number
     * above the target's largest becomes that largest, and a fence connection becomes a low wall
     * side. Where lava is among the palette's blocks nothing is waterlogged.
     */
    @Property(tries = 2000)
    void aReplacementKeepsThePropertiesItsBlockCanHold(
            @ForAll("palettes") PaletteCase palette,
            @ForAll @IntRange(min = 0, max = Integer.MAX_VALUE) int source) {
        StructurePalettes.Palette compiled = palette.palette();
        boolean lava = false;
        for (BlockState target : compiled.targets()) lava |= target.is(Blocks.LAVA);
        BlockState state = palette.sources().get(source % palette.sources().size());
        StructurePalettes.Choice choice = compiled.choiceFor(state);
        if (choice == null) return;
        for (BlockState target : choice.states()) {
            if (lava && target.hasProperty(BlockStateProperties.WATERLOGGED)) {
                assertFalse(target.getValue(BlockStateProperties.WATERLOGGED), target.toString());
            }
            for (net.minecraft.world.level.block.state.properties.Property<?> property :
                    state.getProperties()) {
                var targetProperty =
                        target.getBlock().getStateDefinition().getProperty(property.getName());
                if (targetProperty == null
                        || lava && targetProperty == BlockStateProperties.WATERLOGGED) {
                    continue;
                }
                String expected = expectedValue(state, property, targetProperty);
                if (expected == null) continue;
                assertEquals(
                        expected,
                        valueName(target, targetProperty),
                        property.getName() + " of " + state + " placed as " + target);
            }
        }
    }

    /** The value a target property must hold, or null where the source's value has no match. */
    private static <T extends Comparable<T>> String expectedValue(
            BlockState state,
            net.minecraft.world.level.block.state.properties.Property<T> property,
            net.minecraft.world.level.block.state.properties.Property<?> target) {
        T value = state.getValue(property);
        if (property instanceof BooleanProperty && WallSide.class.equals(target.getValueClass())) {
            return Boolean.TRUE.equals(value) ? "low" : "none";
        }
        if (property instanceof IntegerProperty && target instanceof IntegerProperty limit) {
            int largest = Collections.max(limit.getPossibleValues());
            String capped = String.valueOf(Math.min((Integer) value, largest));
            return target.getValue(capped).isPresent() ? capped : null;
        }
        String name = property.getName(value);
        return target.getValue(name).isPresent() ? name : null;
    }

    private static <T extends Comparable<T>> String valueName(
            BlockState state,
            net.minecraft.world.level.block.state.properties.Property<T> property) {
        return property.getName(state.getValue(property));
    }

    /**
     * While a palette is active, every block written goes through it; a nested push of no palette
     * keeps blocks as they are until the caller restores the palette it got back.
     */
    @Property(tries = 300)
    void theActivePaletteRemapsUntilItIsRestored(
            @ForAll("palettes") PaletteCase palette,
            @ForAll @IntRange(min = 0, max = Integer.MAX_VALUE) int source,
            @ForAll @IntRange(min = -30_000_000, max = 30_000_000) int x,
            @ForAll @IntRange(min = -30_000_000, max = 30_000_000) int z) {
        StructurePalettes.Palette compiled = palette.palette();
        BlockState state = palette.sources().get(source % palette.sources().size());
        BlockPos pos = new BlockPos(x, 64, z);
        assertSame(state, StructurePalettes.remap(state, pos));
        assertNull(StructurePalettes.push(compiled));
        try {
            assertSame(compiled.remap(state, pos), StructurePalettes.remap(state, pos));
            StructurePalettes.Palette outer = StructurePalettes.push(null);
            assertSame(compiled, outer);
            assertSame(state, StructurePalettes.remap(state, pos));
            StructurePalettes.restore(outer);
            assertSame(compiled.remap(state, pos), StructurePalettes.remap(state, pos));
        } finally {
            StructurePalettes.restore(null);
        }
        assertSame(state, StructurePalettes.remap(state, pos));
    }

    // ---------------------------------------------------------------------------------------
    // The weighted pick against vanilla's WeightedList
    // ---------------------------------------------------------------------------------------

    /**
     * Given the roll {@code floorMod(mix(seed), total)} as its {@code RandomSource#nextInt(total)},
     * vanilla's {@code WeightedList#getRandomOrThrow} ({@code WeightedList.java:85-92}) picks the
     * option the palette picks, with its flat table below a total of 64 and its walk above.
     */
    @Property
    void picksWhatVanillasWeightedListPicksForTheSameRoll(
            @ForAll @Size(min = 1, max = 8) List<@IntRange(min = 1, max = 1000) Integer> weights,
            @ForAll long seed) {
        int[] array = weights.stream().mapToInt(Integer::intValue).toArray();
        WeightedList.Builder<Integer> builder = WeightedList.builder();
        for (int i = 0; i < array.length; i++) builder.add(i, array[i]);
        assertEquals(
                builder.build().getRandomOrThrow(rollOf(seed)).intValue(),
                new WeightedPick(array).index(seed));
    }

    /**
     * A {@link RandomSource} whose bounded {@code nextInt} returns the pick's roll of {@code seed}.
     */
    private static RandomSource rollOf(long seed) {
        return (RandomSource)
                Proxy.newProxyInstance(
                        RandomSource.class.getClassLoader(),
                        new Class<?>[] {RandomSource.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("nextInt")
                                    && method.getParameterCount() == 1) {
                                int bound = (Integer) args[0];
                                return (int) Math.floorMod(HashCommon.mix(seed), (long) bound);
                            }
                            throw new UnsupportedOperationException(method.toString());
                        });
    }

    // ---------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------

    /** The biomes a "biomes" value of a structure or a rule names, tags resolved. */
    private static Set<ResourceKey<Biome>> resolve(JsonElement biomes) {
        Set<ResourceKey<Biome>> resolved = new LinkedHashSet<>();
        List<JsonElement> values = new ArrayList<>();
        if (biomes.isJsonArray()) {
            biomes.getAsJsonArray().forEach(values::add);
        } else {
            values.add(biomes);
        }
        for (JsonElement value : values) {
            String name = value.getAsString();
            if (name.startsWith("#")) {
                resolved.addAll(tagMembers(Identifier.parse(name.substring(1))));
            } else {
                resolved.add(ResourceKey.create(Registries.BIOME, Identifier.parse(name)));
            }
        }
        return resolved;
    }

    /** A biome tag's members, read from the tag files on the classpath, nested tags included. */
    private static Set<ResourceKey<Biome>> tagMembers(Identifier tag) {
        TagKey<Biome> key = TagKey.create(Registries.BIOME, tag);
        Set<ResourceKey<Biome>> cached = VANILLA_TAGS.get(key);
        if (cached != null) return cached;
        Set<ResourceKey<Biome>> members = new LinkedHashSet<>();
        String path =
                "/data/" + tag.getNamespace() + "/tags/worldgen/biome/" + tag.getPath() + ".json";
        if (StructurePalettesPropertyTest.class.getResource(path) != null) {
            for (JsonElement entry : json(path).getAsJsonArray("values")) {
                String id =
                        entry.isJsonObject()
                                ? entry.getAsJsonObject().get("id").getAsString()
                                : entry.getAsString();
                if (id.startsWith("#")) {
                    members.addAll(tagMembers(Identifier.parse(id.substring(1))));
                } else {
                    members.add(ResourceKey.create(Registries.BIOME, Identifier.parse(id)));
                }
            }
        }
        VANILLA_TAGS.put(key, members);
        return members;
    }

    private static Map<ResourceKey<Biome>, Holder<Biome>> vanillaBiomes() {
        List<ResourceKey<Biome>> keys = new ArrayList<>();
        for (Field field : Biomes.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == ResourceKey.class) {
                try {
                    @SuppressWarnings("unchecked")
                    ResourceKey<Biome> key = (ResourceKey<Biome>) field.get(null);
                    keys.add(key);
                } catch (IllegalAccessException exception) {
                    throw new IllegalStateException(exception);
                }
            }
        }
        keys.add(UNNAMED);
        // Load every tag a table or a structure names before the registry binds them.
        for (JsonElement structure : PALETTES.getAsJsonObject("structures").asMap().values()) {
            for (JsonElement rule : structure.getAsJsonObject().getAsJsonArray("biomes")) {
                resolve(rule.getAsJsonObject().get("match"));
            }
        }
        MappedRegistry<Biome> registry = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        Map<ResourceKey<Biome>, Holder<Biome>> holders = new LinkedHashMap<>();
        for (ResourceKey<Biome> key : keys) {
            holders.put(
                    key, registry.register(key, allocate(Biome.class), RegistrationInfo.BUILT_IN));
        }
        Map<TagKey<Biome>, List<Holder<Biome>>> tags = new HashMap<>();
        VANILLA_TAGS.forEach(
                (tag, members) -> {
                    List<Holder<Biome>> bound = new ArrayList<>();
                    for (ResourceKey<Biome> member : members) {
                        Holder<Biome> holder = holders.get(member);
                        assertNotNull(holder, tag + " names the unknown biome " + member);
                        bound.add(holder);
                    }
                    tags.put(tag, bound);
                });
        registry.bindTags(tags);
        registry.freeze();
        return holders;
    }

    private static Map<String, Holder<Biome>> testBiomes() {
        MappedRegistry<Biome> registry = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        Map<String, Holder<Biome>> holders = new HashMap<>();
        for (String biome : TEST_BIOMES) {
            holders.put(
                    biome,
                    registry.register(
                            ResourceKey.create(
                                    Registries.BIOME,
                                    Identifier.fromNamespaceAndPath("dsvtest", biome)),
                            allocate(Biome.class),
                            RegistrationInfo.BUILT_IN));
        }
        Map<TagKey<Biome>, List<Holder<Biome>>> tags = new HashMap<>();
        TEST_TAGS.forEach(
                (tag, members) ->
                        tags.put(
                                TagKey.create(
                                        Registries.BIOME,
                                        Identifier.fromNamespaceAndPath("dsvtest", tag)),
                                members.stream().map(holders::get).toList()));
        registry.bindTags(tags);
        registry.freeze();
        return holders;
    }

    private static JsonObject json(String path) {
        try (InputStream in = StructurePalettesPropertyTest.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
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

    @Example
    void anUnnamedBiomeTakesTheDefaultPalette() {
        for (Map.Entry<String, JsonElement> entry :
                PALETTES.getAsJsonObject("structures").entrySet()) {
            assertFalse(
                    StructurePalettes.hasRule(
                            DimensionStructureVariants.of(entry.getKey()),
                            VANILLA_BIOMES.get(UNNAMED)));
        }
        assertNull(
                StructurePalettes.palette(
                        Identifier.withDefaultNamespace("fortress"), VANILLA_BIOMES.get(UNNAMED)));
    }
}
