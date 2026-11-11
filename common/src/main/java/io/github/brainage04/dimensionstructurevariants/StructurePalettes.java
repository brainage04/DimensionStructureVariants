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
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Block substitution for the variants, one palette per structure and biome.
 *
 * <p>The tables live in {@code data/dimensionstructurevariants/palette/palettes.json}: a role set
 * maps the blocks a vanilla structure places to roles, a palette gives each role the block of the
 * biome it stands in, and a structure names the palette each of its biomes uses. Every target is a
 * vanilla block, so a client needs neither this mod nor a resource pack.
 */
public final class StructurePalettes {
    private static final String DATA =
            "/data/" + DimensionStructureVariants.MOD_ID + "/palette/palettes.json";

    private static final ThreadLocal<Map<BlockState, BlockState>> ACTIVE = new ThreadLocal<>();
    private static final Map<String, Map<BlockState, BlockState>> COMPILED =
            new ConcurrentHashMap<>();
    private static volatile Data data;

    private StructurePalettes() {}

    /**
     * The palette of a structure start at {@code pos}, or null when the structure keeps its vanilla
     * blocks. The position picks the biome; the start's own centre is the natural choice.
     */
    public static Map<BlockState, BlockState> palette(
            WorldGenLevel level, Structure structure, BlockPos pos) {
        Identifier id =
                level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(structure);
        if (id == null) return null;
        return palette(id, level.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2));
    }

    /** The palette of variant {@code id} in {@code biome}, or null when it has none there. */
    static Map<BlockState, BlockState> palette(Identifier id, Holder<Biome> biome) {
        if (!id.getNamespace().equals(DimensionStructureVariants.MOD_ID)) return null;
        Rules rules = data().structures.get(id.getPath());
        if (rules == null) return null;
        String palette = rules.paletteFor(biome);
        if (palette == null) return null;
        return COMPILED.computeIfAbsent(
                id.getPath() + "|" + palette, key -> compile(rules.roles(), palette));
    }

    /**
     * What a structure start does besides its palette: the ground block its biome lays under the
     * structure's own footprint, or null where the structure lays none.
     */
    public static Setting setting(WorldGenLevel level, Structure structure, BlockPos pos) {
        Identifier id =
                level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(structure);
        if (id == null) return Setting.NONE;
        return setting(id, level.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2));
    }

    /** The setting of variant {@code id} in {@code biome}; none when it has no palette there. */
    static Setting setting(Identifier id, Holder<Biome> biome) {
        Map<BlockState, BlockState> palette = palette(id, biome);
        if (palette == null) return Setting.NONE;
        return new Setting(palette, ground(id, biome));
    }

    public static Map<BlockState, BlockState> enter(
            WorldGenLevel level, Structure structure, BlockPos pos) {
        return push(palette(level, structure, pos));
    }

    /** Makes {@code palette} the active one and returns the palette the caller must restore. */
    public static Map<BlockState, BlockState> push(Map<BlockState, BlockState> palette) {
        Map<BlockState, BlockState> previous = ACTIVE.get();
        restore(palette);
        return previous;
    }

    public static void restore(Map<BlockState, BlockState> palette) {
        if (palette == null) {
            ACTIVE.remove();
        } else {
            ACTIVE.set(palette);
        }
    }

    public static BlockState remap(BlockState state) {
        Map<BlockState, BlockState> palette = ACTIVE.get();
        return palette == null ? state : palette.getOrDefault(state, state);
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

    /** One palette: role to block id, where a null block keeps the source block as it is. */
    private record Palette(Map<String, String> blocks, String ground) {}

    /** The palette of one structure start and the ground block it lays under its footprint. */
    public record Setting(Map<BlockState, BlockState> palette, BlockState ground) {
        public static final Setting NONE = new Setting(null, null);
    }

    private record Data(
            Map<String, Map<String, String>> roleSets,
            Map<String, Palette> palettes,
            Map<String, Rules> structures) {}

    /** The ground block variant {@code id} lays in {@code biome}, or null. */
    private static BlockState ground(Identifier id, Holder<Biome> biome) {
        Rules rules = data().structures.get(id.getPath());
        if (rules == null) return null;
        String palette = rules.paletteFor(biome);
        if (palette == null) return null;
        String ground = data().palettes.get(palette).ground();
        return ground == null ? null : block(ground).defaultBlockState();
    }

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
        Map<String, Palette> palettes = new LinkedHashMap<>();
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
                Palette resolved = palettes.get(palette);
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

    private static Palette resolvePalette(
            String name, Map<String, JsonObject> raw, List<String> path) {
        JsonObject object = raw.get(name);
        if (object == null) throw new IllegalStateException("Unknown palette " + name);
        if (path.contains(name)) {
            throw new IllegalStateException("Palette " + name + " extends itself through " + path);
        }
        path.add(name);
        Map<String, String> blocks = new LinkedHashMap<>();
        String ground = null;
        if (object.has("extends")) {
            Palette parent = resolvePalette(object.get("extends").getAsString(), raw, path);
            blocks.putAll(parent.blocks());
            ground = parent.ground();
        }
        if (object.has("blocks")) {
            for (Map.Entry<String, JsonElement> entry :
                    object.getAsJsonObject("blocks").entrySet()) {
                JsonElement target = entry.getValue();
                blocks.put(entry.getKey(), target.isJsonNull() ? null : target.getAsString());
            }
        }
        if (object.has("ground")) ground = object.get("ground").getAsString();
        for (String target : blocks.values()) {
            if (target != null) block(target);
        }
        if (ground != null) block(ground);
        path.remove(path.size() - 1);
        return new Palette(Collections.unmodifiableMap(blocks), ground);
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
            String structure, Map<String, String> roles, String palette, Palette resolved) {
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

    private static Map<BlockState, BlockState> compile(String rolesName, String paletteName) {
        Data loaded = data();
        Map<String, String> roles = loaded.roleSets.get(rolesName);
        Map<String, String> palette = loaded.palettes.get(paletteName).blocks();
        Map<BlockState, BlockState> states = new IdentityHashMap<>();
        boolean lava = false;
        for (Map.Entry<String, String> entry : roles.entrySet()) {
            String target = palette.get(entry.getValue());
            if (target == null) continue;
            Block block = block(target);
            lava |= block == Blocks.LAVA;
            addMappings(states, block(entry.getKey()), block);
        }
        if (lava) {
            // Lava cannot fill a block, so nothing beside it may hold water.
            states.replaceAll(
                    (source, state) ->
                            state.hasProperty(BlockStateProperties.WATERLOGGED)
                                    ? state.setValue(BlockStateProperties.WATERLOGGED, false)
                                    : state);
        }
        return Collections.unmodifiableMap(states);
    }

    private static Block block(String id) {
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
        if (block == null) throw new IllegalStateException("Unknown block " + id + " in " + DATA);
        return block;
    }

    private static void addMappings(
            Map<BlockState, BlockState> states, Block source, Block target) {
        for (BlockState state : source.getStateDefinition().getPossibleStates()) {
            BlockState replacement = target.withPropertiesOf(state);
            for (Property<?> property : state.getProperties()) {
                Property<?> targetProperty =
                        target.getStateDefinition().getProperty(property.getName());
                if (targetProperty != null && !replacement.hasProperty(property)) {
                    replacement = copyCompatibleValue(state, replacement, property, targetProperty);
                }
            }
            states.put(state, replacement);
        }
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
