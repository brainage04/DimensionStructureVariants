package io.github.brainage04.dimensionstructurevariants;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.Nullable;

/**
 * Block substitution for the variants, one palette per structure and biome.
 *
 * <p>The tables live in {@code dimensionstructurevariants/palettes.json}, bundled in the mod's jar:
 * a role set maps the blocks a vanilla structure places to roles, a palette gives each role the
 * block of the biome it stands in, and a structure names the palette each of its biomes uses and a
 * default palette for every other biome. A role may name several blocks with weights; the block at
 * a position is then picked from the position alone, so a structure looks the same every time the
 * world generates it. Every target is a vanilla block, so a client needs neither this mod nor a
 * resource pack.
 */
public final class StructurePalettes {
    private static final String DATA = "/" + DimensionStructureVariants.MOD_ID + "/palettes.json";

    private static final ThreadLocal<Palette> ACTIVE = new ThreadLocal<>();

    /**
     * The piece being placed, so a palette can give one role a different block in the pieces it
     * names. Set around a piece's own placement, and null outside one.
     */
    private static final ThreadLocal<Identifier> PIECE = new ThreadLocal<>();

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
        if (id == null || !id.getNamespace().equals(DimensionStructureVariants.MOD_ID)) {
            return null;
        }
        return palette(id, biome(level, pos));
    }

    /**
     * The biome at {@code pos}. During natural generation the level is a {@code WorldGenRegion},
     * which throws for a chunk more than one chunk from the one it decorates ({@code
     * WorldGenRegion.java:131-158}), so there the biome comes from the generator's biome source:
     * the noise biome a newly generated chunk stores ({@code ChunkGenerator.java:134-150}), except
     * where the chunk is blended with chunks from before 1.18 ({@code
     * NoiseBasedChunkGenerator.java:94-97}). Placed through a server level ({@code /place}, the
     * GameTests, the film), the chunk's stored biome counts, so a biome filled before placement
     * picks the palette.
     */
    private static Holder<Biome> biome(WorldGenLevel level, BlockPos pos) {
        int x = QuartPos.fromBlock(pos.getX());
        int y = QuartPos.fromBlock(pos.getY());
        int z = QuartPos.fromBlock(pos.getZ());
        return level instanceof ServerLevel
                ? level.getNoiseBiome(x, y, z)
                : level.getUncachedNoiseBiome(x, y, z);
    }

    /**
     * The palette of variant {@code id} in {@code biome}, or null when {@code id} is no variant.
     */
    public static @Nullable Palette palette(Identifier id, Holder<Biome> biome) {
        if (!id.getNamespace().equals(DimensionStructureVariants.MOD_ID)) return null;
        Rules rules = data().structures.get(id.getPath());
        if (rules == null) return null;
        String palette = rules.paletteFor(biome);
        return COMPILED.computeIfAbsent(
                id.getPath() + "|" + palette, key -> compile(rules.roles(), palette));
    }

    /**
     * Whether a rule of variant {@code id} names {@code biome}, so that it does not fall back to
     * the variant's default palette there.
     */
    public static boolean hasRule(Identifier id, Holder<Biome> biome) {
        if (!id.getNamespace().equals(DimensionStructureVariants.MOD_ID)) return false;
        Rules rules = data().structures.get(id.getPath());
        return rules != null && rules.ruleFor(biome) != null;
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

    /**
     * Makes {@code piece} the piece being placed and returns the one the caller must restore, so a
     * palette can give one role a different block in the pieces it names.
     */
    public static @Nullable Identifier pushPiece(@Nullable Identifier piece) {
        Identifier previous = PIECE.get();
        if (piece == null) {
            PIECE.remove();
        } else {
            PIECE.set(piece);
        }
        return previous;
    }

    public static void restorePiece(@Nullable Identifier piece) {
        if (piece == null) {
            PIECE.remove();
        } else {
            PIECE.set(piece);
        }
    }

    /** The block the active palette puts at {@code pos} in place of {@code state}. */
    public static BlockState remap(BlockState state, BlockPos pos) {
        Palette palette = ACTIVE.get();
        return palette == null ? state : palette.remap(state, pos);
    }

    /**
     * A compiled palette: what it puts in place of each vanilla state, its ground, and the accent
     * it gives one role in the pieces it names.
     */
    public static final class Palette {
        private final Map<BlockState, Choice> states;
        private final @Nullable Choice ground;
        private final @Nullable ResourceKey<Feature> groundFeature;
        private final float groundFeatureChance;
        private final @Nullable Accent accent;

        private Palette(
                Map<BlockState, Choice> states,
                @Nullable Choice ground,
                @Nullable ResourceKey<Feature> groundFeature,
                float groundFeatureChance,
                @Nullable Accent accent) {
            this.states = states;
            this.ground = ground;
            this.groundFeature = groundFeature;
            this.groundFeatureChance = groundFeatureChance;
            this.accent = accent;
        }

        public BlockState remap(BlockState state, BlockPos pos) {
            Choice choice = choiceFor(state);
            return choice == null ? state : choice.at(pos.getX(), pos.getY(), pos.getZ());
        }

        /**
         * The choice this palette makes for {@code state} in the piece being placed, or null when
         * it keeps it. A piece the palette names as an accent piece takes the accent's block for
         * the accent's role; every other piece takes the palette's own.
         */
        public @Nullable Choice choiceFor(BlockState state) {
            if (accent != null && accent.appliesTo(PIECE.get())) {
                Choice choice = accent.states().get(state);
                if (choice != null) return choice;
            }
            return states.get(state);
        }

        /** Every block state the palette can place in place of a vanilla one. */
        public Set<BlockState> targets() {
            Set<BlockState> targets = new LinkedHashSet<>();
            for (Choice choice : states.values()) targets.addAll(choice.states());
            if (accent != null) {
                for (Choice choice : accent.states().values()) targets.addAll(choice.states());
            }
            return targets;
        }

        /** The accent this palette gives the pieces it names, or null where it gives none. */
        public @Nullable Accent accent() {
            return accent;
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

    /** One role's block in the pieces a palette names, so a part of the structure reads as its own. */
    public record Accent(Set<Identifier> pieces, Map<BlockState, Choice> states) {
        boolean appliesTo(@Nullable Identifier piece) {
            return piece != null && pieces.contains(piece);
        }
    }

    /** One block, or several picked by weight from the position alone. */
    public static final class Choice {
        private final BlockState[] states;
        private final WeightedPick pick;

        private Choice(BlockState[] states, int[] weights) {
            this.states = states;
            this.pick = new WeightedPick(weights);
        }

        public BlockState at(int x, int y, int z) {
            if (states.length == 1) return states[0];
            return pick(BlockPos.asLong(x, y, z));
        }

        /** The state a roll of {@code seed} lands on, each state as often as its weight says. */
        public BlockState pick(long seed) {
            return states[pick.index(seed)];
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
            return pick.weight(index);
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

    /** The palette of the first rule that matches a biome, else the structure's default. */
    private record Rules(String roles, List<Rule> biomes, String fallback) {
        String paletteFor(Holder<Biome> biome) {
            String palette = ruleFor(biome);
            return palette == null ? fallback : palette;
        }

        @Nullable String ruleFor(Holder<Biome> biome) {
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
     * the ground it lays with the feature that grows on it, and the accent it gives one role in
     * the pieces it names.
     */
    private record RawPalette(
            Map<String, Map<String, Integer>> blocks,
            Map<String, Integer> ground,
            String groundFeature,
            float groundFeatureChance,
            @Nullable PieceAccent accent) {}

    /** One role's block in the named pieces, so a part of the structure reads as its own. */
    private record PieceAccent(String role, Set<Identifier> pieces, Map<String, Integer> block) {}

    record Data(
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
        try (InputStream stream = StructurePalettes.class.getResourceAsStream(DATA)) {
            if (stream == null) throw new IllegalStateException("Missing palette table " + DATA);
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return parse(JsonParser.parseReader(reader).getAsJsonObject());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unreadable palette table " + DATA, exception);
        }
    }

    static Data parse(JsonObject root) {
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
                checkPalette(entry.getKey(), roleSets.get(roles), palette, palettes);
                biomes.add(new Rule(List.copyOf(match), palette));
            }
            if (!object.has("default")) {
                throw new IllegalStateException(entry.getKey() + " names no default palette");
            }
            String fallback = object.get("default").getAsString();
            checkPalette(entry.getKey(), roleSets.get(roles), fallback, palettes);
            structures.put(entry.getKey(), new Rules(roles, List.copyOf(biomes), fallback));
        }
        return new Data(roleSets, palettes, structures);
    }

    private static void checkPalette(
            String structure,
            Map<String, String> roles,
            String palette,
            Map<String, RawPalette> palettes) {
        RawPalette resolved = palettes.get(palette);
        if (resolved == null) {
            throw new IllegalStateException(structure + " names the unknown palette " + palette);
        }
        checkCoverage(structure, roles, palette, resolved);
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
        PieceAccent accent = null;
        if (object.has("extends")) {
            RawPalette parent = resolvePalette(object.get("extends").getAsString(), raw, path);
            blocks.putAll(parent.blocks());
            ground = parent.ground();
            feature = parent.groundFeature();
            chance = parent.groundFeatureChance();
            accent = parent.accent();
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
        if (object.has("piece_accent")) {
            JsonObject spec = object.getAsJsonObject("piece_accent");
            String role = spec.get("role").getAsString();
            if (!blocks.containsKey(role)) {
                throw new IllegalStateException(
                        "Palette " + name + " gives its accent to the unknown role " + role);
            }
            Set<Identifier> pieces = new LinkedHashSet<>();
            for (JsonElement piece : spec.getAsJsonArray("pieces")) {
                pieces.add(Identifier.parse(piece.getAsString()));
            }
            if (pieces.isEmpty()) {
                throw new IllegalStateException("Palette " + name + " names no accent piece");
            }
            accent = new PieceAccent(role, Collections.unmodifiableSet(pieces), weights(name, spec.get("block")));
        }
        path.remove(path.size() - 1);
        return new RawPalette(
                Collections.unmodifiableMap(blocks), ground, feature, chance, accent);
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
        Accent accent = null;
        if (palette.accent() != null) {
            PieceAccent spec = palette.accent();
            Map<BlockState, Choice> accentStates = new IdentityHashMap<>();
            for (Map.Entry<String, String> entry : roles.entrySet()) {
                if (!entry.getValue().equals(spec.role())) continue;
                addMappings(accentStates, block(entry.getKey()), spec.block());
            }
            if (lava) {
                accentStates.replaceAll((source, choice) -> withoutWater(choice));
            }
            accent = new Accent(spec.pieces(), Collections.unmodifiableMap(accentStates));
        }
        return new Palette(
                Collections.unmodifiableMap(states),
                ground,
                feature,
                palette.groundFeatureChance(),
                accent);
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
        Identifier key = Identifier.parse(id);
        if (!BuiltInRegistries.BLOCK.containsKey(key)) {
            throw new IllegalStateException("Unknown block " + id + " in " + DATA);
        }
        return BuiltInRegistries.BLOCK.getValue(key);
    }

    private static void addMappings(
            Map<BlockState, Choice> states, Block source, Map<String, Integer> targets) {
        for (BlockState state : source.getStateDefinition().getPossibleStates()) {
            List<BlockState> replacements = new ArrayList<>(targets.size());
            for (String id : targets.keySet()) replacements.add(convert(state, block(id)));
            states.put(state, choice(replacements, targets));
        }
    }

    /**
     * Copies the source's properties onto the target block. A number the target cannot hold, such
     * as a wheat age of 7 on nether wart's 0 to 3 ({@code NetherWartBlock.java:20-21}), is cut down
     * to the target's largest first, so a grown crop becomes grown wart instead of a sprout.
     */
    private static BlockState convert(BlockState state, Block target) {
        BlockState capped = capToTarget(state, target);
        BlockState replacement = target.withPropertiesOf(capped);
        for (Property<?> property : capped.getProperties()) {
            Property<?> targetProperty =
                    target.getStateDefinition().getProperty(property.getName());
            if (targetProperty != null && !replacement.hasProperty(property)) {
                replacement = copyCompatibleValue(capped, replacement, property, targetProperty);
            }
        }
        return replacement;
    }

    /** {@code state} with each number above what {@code target} allows cut down to its largest. */
    private static BlockState capToTarget(BlockState state, Block target) {
        BlockState capped = state;
        for (Property<?> property : state.getProperties()) {
            Property<?> targetProperty =
                    target.getStateDefinition().getProperty(property.getName());
            if (!(property instanceof IntegerProperty source)
                    || !(targetProperty instanceof IntegerProperty limit)) {
                continue;
            }
            int largest = Collections.max(limit.getPossibleValues());
            int value = capped.getValue(source);
            if (value > largest) capped = capped.setValue(source, largest);
        }
        return capped;
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
