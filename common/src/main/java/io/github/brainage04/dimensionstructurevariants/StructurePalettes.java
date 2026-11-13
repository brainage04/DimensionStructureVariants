package io.github.brainage04.dimensionstructurevariants;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.HashCommon;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.Nullable;

/**
 * Block substitution for the variants, one palette per structure and biome.
 *
 * <p>The tables live in {@code data/dimensionstructurevariants/palette/palettes.json}: a role set
 * maps the blocks a vanilla structure places to roles, a palette gives each role the block of the
 * biome it stands in, and a structure names the palette each of its biomes uses. A role may name
 * several blocks with weights; the block at a position is then picked from the position alone, so a
 * structure looks the same every time the world generates it. Every target is a vanilla block, so a
 * client needs neither this mod nor a resource pack.
 */
public final class StructurePalettes {
    private static final String DATA =
            "/data/" + DimensionStructureVariants.MOD_ID + "/palette/palettes.json";

    private static final ThreadLocal<Palette> ACTIVE = new ThreadLocal<>();
    private static final Map<String, Palette> COMPILED = new ConcurrentHashMap<>();
    private static volatile Data data;

    private StructurePalettes() {}

    /**
     * The palette of a structure start at {@code pos}, or null when the structure keeps its vanilla
     * blocks. The position picks the biome; the start's own centre is the natural choice.
     */
    public static @Nullable Palette palette(
            WorldGenLevel level, Structure structure, BlockPos pos) {
        Identifier id =
                level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(structure);
        if (id == null) return null;
        return palette(id, level.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2));
    }

    /** The palette of variant {@code id} in {@code biome}, or null when it has none there. */
    public static @Nullable Palette palette(Identifier id, Holder<Biome> biome) {
        if (!id.getNamespace().equals(DimensionStructureVariants.MOD_ID)) return null;
        Rules rules = data().structures.get(id.getPath());
        if (rules == null) return null;
        String palette = rules.paletteFor(biome);
        if (palette == null) return null;
        return COMPILED.computeIfAbsent(
                id.getPath() + "|" + palette, key -> compile(rules.roles(), palette));
    }

    /** Makes {@code palette} the active one and returns the palette the caller must restore. */
    public static @Nullable Palette push(@Nullable Palette palette) {
        Palette previous = ACTIVE.get();
        restore(palette);
        return previous;
    }

    public static void restore(@Nullable Palette palette) {
        if (palette == null) {
            ACTIVE.remove();
        } else {
            ACTIVE.set(palette);
        }
    }

    /** The block the active palette puts at {@code pos} in place of {@code state}. */
    public static BlockState remap(BlockState state, BlockPos pos) {
        Palette palette = ACTIVE.get();
        return palette == null ? state : palette.remap(state, pos);
    }

    /** A compiled palette: what it puts in place of each vanilla state, and its ground. */
    public static final class Palette {
        private final Map<BlockState, Choice> states;
        private final @Nullable Choice ground;
        private final @Nullable ResourceKey<Feature> groundFeature;
        private final float groundFeatureChance;

        private Palette(
                Map<BlockState, Choice> states,
                @Nullable Choice ground,
                @Nullable ResourceKey<Feature> groundFeature,
                float groundFeatureChance) {
            this.states = states;
            this.ground = ground;
            this.groundFeature = groundFeature;
            this.groundFeatureChance = groundFeatureChance;
        }

        public BlockState remap(BlockState state, BlockPos pos) {
            Choice choice = states.get(state);
            return choice == null ? state : choice.at(pos.getX(), pos.getY(), pos.getZ());
        }

        /** The choice this palette makes for {@code state}, or null when it keeps it. */
        public @Nullable Choice choiceFor(BlockState state) {
            return states.get(state);
        }

        /** Every block state the palette can place in place of a vanilla one. */
        public Set<BlockState> targets() {
            Set<BlockState> targets = new LinkedHashSet<>();
            for (Choice choice : states.values()) targets.addAll(choice.states());
            return targets;
        }

        /** The ground the structure lays around its pieces, or null where it lays none. */
        public @Nullable Choice ground() {
            return ground;
        }

        /** The feature that now and then grows on that ground, or null. */
        public @Nullable ResourceKey<Feature> groundFeature() {
            return groundFeature;
        }

        /** The chance that a column of ground clear of the pieces grows the feature. */
        public float groundFeatureChance() {
            return groundFeatureChance;
        }
    }

    /** One block, or several picked by weight from the position alone. */
    public static final class Choice {
        private final BlockState[] states;
        private final int[] upTo;

        private Choice(BlockState[] states, int[] weights) {
            this.states = states;
            this.upTo = new int[weights.length];
            int total = 0;
            for (int i = 0; i < weights.length; i++) {
                total += weights[i];
                upTo[i] = total;
            }
        }

        public BlockState at(int x, int y, int z) {
            if (states.length == 1) return states[0];
            return pick(BlockPos.asLong(x, y, z));
        }

        /** The state a roll of {@code seed} lands on, each state as often as its weight says. */
        public BlockState pick(long seed) {
            int roll = (int) Math.floorMod(HashCommon.mix(seed), (long) upTo[upTo.length - 1]);
            for (int i = 0; i < upTo.length; i++) {
                if (roll < upTo[i]) return states[i];
            }
            return states[states.length - 1];
        }

        public List<BlockState> states() {
            return List.of(states);
        }

        /** The state with the largest weight. */
        public BlockState main() {
            int best = 0;
            for (int i = 1; i < states.length; i++) {
                if (weight(i) > weight(best)) best = i;
            }
            return states[best];
        }

        private int weight(int index) {
            return upTo[index] - (index == 0 ? 0 : upTo[index - 1]);
        }
    }

    // ------------------------------------------------------------------------------------------
    // The table
    // ------------------------------------------------------------------------------------------

    private static final class Matcher {
        private final ResourceKey<Biome> biome;
        private final TagKey<Biome> tag;

        private Matcher(ResourceKey<Biome> biome, TagKey<Biome> tag) {
            this.biome = biome;
            this.tag = tag;
        }

        boolean matches(Holder<Biome> candidate) {
            return biome != null ? candidate.is(biome) : candidate.is(tag);
        }
    }

    private record Rule(List<Matcher> match, String palette) {}

    private record Rules(String roles, List<Rule> biomes) {
        String paletteFor(Holder<Biome> biome) {
            for (Rule rule : biomes) {
                for (Matcher matcher : rule.match()) {
                    if (matcher.matches(biome)) return rule.palette();
                }
            }
            return null;
        }
    }

    /**
     * One palette as written: role to weighted blocks, where null keeps the source block as it is,
     * and the ground it lays with the feature that grows on it.
     */
    private record RawPalette(
            Map<String, Map<String, Integer>> blocks,
            Map<String, Integer> ground,
            String groundFeature,
            float groundFeatureChance) {}

    private record Data(
            Map<String, Map<String, String>> roleSets,
            Map<String, RawPalette> palettes,
            Map<String, Rules> structures) {}

    private static Data data() {
        Data loaded = data;
        if (loaded != null) return loaded;
        synchronized (StructurePalettes.class) {
            if (data == null) data = read();
            return data;
        }
    }

    private static Data read() {
        JsonObject root;
        try (InputStream stream = StructurePalettes.class.getResourceAsStream(DATA)) {
            if (stream == null) throw new IllegalStateException("Missing palette table " + DATA);
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unreadable palette table " + DATA, exception);
        }

        Map<String, Map<String, String>> roleSets = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> set : root.getAsJsonObject("role_sets").entrySet()) {
            Map<String, String> roles = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry :
                    set.getValue().getAsJsonObject().entrySet()) {
                block(entry.getKey());
                roles.put(entry.getKey(), entry.getValue().getAsString());
            }
            roleSets.put(set.getKey(), roles);
        }

        Map<String, JsonObject> raw = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("palettes").entrySet()) {
            raw.put(entry.getKey(), entry.getValue().getAsJsonObject());
        }
        Map<String, RawPalette> palettes = new LinkedHashMap<>();
        for (String name : raw.keySet()) {
            palettes.put(name, resolvePalette(name, raw, new ArrayList<>()));
        }

        Map<String, Rules> structures = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("structures").entrySet()) {
            JsonObject object = entry.getValue().getAsJsonObject();
            String roles = object.get("roles").getAsString();
            if (!roleSets.containsKey(roles)) {
                throw new IllegalStateException(
                        entry.getKey() + " names the unknown role set " + roles);
            }
            List<Rule> biomes = new ArrayList<>();
            for (JsonElement element : object.getAsJsonArray("biomes")) {
                JsonObject rule = element.getAsJsonObject();
                List<Matcher> match = new ArrayList<>();
                for (JsonElement biome : rule.getAsJsonArray("match")) {
                    match.add(matcher(biome.getAsString()));
                }
                String palette = rule.get("palette").getAsString();
                RawPalette resolved = palettes.get(palette);
                if (resolved == null) {
                    throw new IllegalStateException(
                            entry.getKey() + " names the unknown palette " + palette);
                }
                checkCoverage(entry.getKey(), roleSets.get(roles), palette, resolved);
                biomes.add(new Rule(List.copyOf(match), palette));
            }
            structures.put(entry.getKey(), new Rules(roles, List.copyOf(biomes)));
        }
        return new Data(roleSets, palettes, structures);
    }

    private static RawPalette resolvePalette(
            String name, Map<String, JsonObject> raw, List<String> path) {
        JsonObject object = raw.get(name);
        if (object == null) throw new IllegalStateException("Unknown palette " + name);
        if (path.contains(name)) {
            throw new IllegalStateException("Palette " + name + " extends itself through " + path);
        }
        path.add(name);
        Map<String, Map<String, Integer>> blocks = new LinkedHashMap<>();
        Map<String, Integer> ground = null;
        String feature = null;
        float chance = 0.0F;
        if (object.has("extends")) {
            RawPalette parent = resolvePalette(object.get("extends").getAsString(), raw, path);
            blocks.putAll(parent.blocks());
            ground = parent.ground();
            feature = parent.groundFeature();
            chance = parent.groundFeatureChance();
        }
        if (object.has("blocks")) {
            for (Map.Entry<String, JsonElement> entry :
                    object.getAsJsonObject("blocks").entrySet()) {
                blocks.put(entry.getKey(), weights(name, entry.getValue()));
            }
        }
        if (object.has("ground")) ground = weights(name, object.get("ground"));
        if (object.has("ground_feature")) {
            JsonObject spec = object.getAsJsonObject("ground_feature");
            feature = spec.get("feature").getAsString();
            Identifier.parse(feature);
            chance = spec.get("chance").getAsFloat();
            if (chance <= 0.0F || chance > 1.0F) {
                throw new IllegalStateException(
                        "Palette " + name + " gives its ground feature the chance " + chance);
            }
        }
        path.remove(path.size() - 1);
        return new RawPalette(Collections.unmodifiableMap(blocks), ground, feature, chance);
    }

    /**
     * A target as written: one block id, an object of block ids to weights, or null for "keep the
     * vanilla block" (returned as null).
     */
    private static Map<String, Integer> weights(String palette, JsonElement target) {
        if (target.isJsonNull()) return null;
        Map<String, Integer> weights = new LinkedHashMap<>();
        if (target.isJsonPrimitive()) {
            weights.put(target.getAsString(), 1);
        } else {
            for (Map.Entry<String, JsonElement> entry : target.getAsJsonObject().entrySet()) {
                int weight = entry.getValue().getAsInt();
                if (weight <= 0) {
                    throw new IllegalStateException(
                            "Palette "
                                    + palette
                                    + " gives "
                                    + entry.getKey()
                                    + " the weight "
                                    + weight);
                }
                weights.put(entry.getKey(), weight);
            }
            if (weights.isEmpty()) {
                throw new IllegalStateException("Palette " + palette + " names an empty choice");
            }
        }
        for (String block : weights.keySet()) block(block);
        return Collections.unmodifiableMap(weights);
    }

    private static Matcher matcher(String value) {
        if (value.startsWith("#")) {
            return new Matcher(
                    null, TagKey.create(Registries.BIOME, Identifier.parse(value.substring(1))));
        }
        return new Matcher(ResourceKey.create(Registries.BIOME, Identifier.parse(value)), null);
    }

    /**
     * Every role a structure's role set names must be answered by each palette the structure uses,
     * with a block or an explicit null for "keep the vanilla block", so a role name no palette
     * knows fails here instead of quietly leaving blocks vanilla.
     */
    private static void checkCoverage(
            String structure, Map<String, String> roles, String palette, RawPalette resolved) {
        List<String> used = new ArrayList<>(roles.values());
        for (String role : used) {
            if (!resolved.blocks().containsKey(role)) {
                throw new IllegalStateException(
                        "Palette "
                                + palette
                                + " for "
                                + structure
                                + " has no block for role "
                                + role);
            }
        }
        for (String role : resolved.blocks().keySet()) {
            if (!used.contains(role)) {
                throw new IllegalStateException(
                        "Palette "
                                + palette
                                + " sets role "
                                + role
                                + ", which "
                                + structure
                                + " never uses");
            }
        }
    }

    private static Palette compile(String rolesName, String paletteName) {
        Data loaded = data();
        Map<String, String> roles = loaded.roleSets.get(rolesName);
        RawPalette palette = loaded.palettes.get(paletteName);
        Map<BlockState, Choice> states = new IdentityHashMap<>();
        boolean lava = false;
        for (Map.Entry<String, String> entry : roles.entrySet()) {
            Map<String, Integer> target = palette.blocks().get(entry.getValue());
            if (target == null) continue;
            for (String block : target.keySet()) lava |= block(block) == Blocks.LAVA;
            addMappings(states, block(entry.getKey()), target);
        }
        if (lava) {
            // Lava cannot fill a block, so nothing beside it may hold water.
            states.replaceAll((source, choice) -> withoutWater(choice));
        }
        Choice ground = null;
        if (palette.ground() != null) {
            List<BlockState> blocks = new ArrayList<>();
            for (String block : palette.ground().keySet()) {
                blocks.add(block(block).defaultBlockState());
            }
            ground = choice(blocks, palette.ground());
        }
        ResourceKey<Feature> feature =
                palette.groundFeature() == null
                        ? null
                        : ResourceKey.create(
                                Registries.FEATURE, Identifier.parse(palette.groundFeature()));
        return new Palette(
                Collections.unmodifiableMap(states),
                ground,
                feature,
                palette.groundFeatureChance());
    }

    private static Choice withoutWater(Choice choice) {
        BlockState[] states = choice.states.clone();
        for (int i = 0; i < states.length; i++) {
            if (states[i].hasProperty(BlockStateProperties.WATERLOGGED)) {
                states[i] = states[i].setValue(BlockStateProperties.WATERLOGGED, false);
            }
        }
        int[] weights = new int[states.length];
        for (int i = 0; i < weights.length; i++) weights[i] = choice.weight(i);
        return new Choice(states, weights);
    }

    private static Choice choice(List<BlockState> states, Map<String, Integer> weights) {
        int[] values = new int[states.size()];
        int i = 0;
        for (int weight : weights.values()) values[i++] = weight;
        return new Choice(states.toArray(BlockState[]::new), values);
    }

    private static Block block(String id) {
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
        if (block == null) throw new IllegalStateException("Unknown block " + id + " in " + DATA);
        return block;
    }

    private static void addMappings(
            Map<BlockState, Choice> states, Block source, Map<String, Integer> targets) {
        for (BlockState state : source.getStateDefinition().getPossibleStates()) {
            List<BlockState> replacements = new ArrayList<>(targets.size());
            for (String id : targets.keySet()) replacements.add(convert(state, block(id)));
            states.put(state, choice(replacements, targets));
        }
    }

    private static BlockState convert(BlockState state, Block target) {
        BlockState replacement = target.withPropertiesOf(state);
        for (Property<?> property : state.getProperties()) {
            Property<?> targetProperty =
                    target.getStateDefinition().getProperty(property.getName());
            if (targetProperty != null && !replacement.hasProperty(property)) {
                replacement = copyCompatibleValue(state, replacement, property, targetProperty);
            }
        }
        return replacement;
    }

    /** Retains facing values and converts fence connections into low wall sides. */
    private static <T extends Comparable<T>, U extends Comparable<U>>
            BlockState copyCompatibleValue(
                    BlockState source,
                    BlockState target,
                    Property<T> sourceProperty,
                    Property<U> targetProperty) {
        String value = sourceProperty.getName(source.getValue(sourceProperty));
        if (sourceProperty instanceof BooleanProperty
                && WallSide.class.equals(targetProperty.getValueClass())) {
            value = source.getValue(sourceProperty).equals(Boolean.TRUE) ? "low" : "none";
        }
        return targetProperty
                .getValue(value)
                .map(parsed -> target.setValue(targetProperty, parsed))
                .orElse(target);
    }
}
