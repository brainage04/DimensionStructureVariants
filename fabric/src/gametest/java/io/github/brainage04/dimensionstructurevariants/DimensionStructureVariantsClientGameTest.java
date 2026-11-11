package io.github.brainage04.dimensionstructurevariants;

import io.github.brainage04.minecraftmodconventions.ClientGameTestRecorder;
import io.github.brainage04.minecraftmodconventions.ClientGameTestRecordingHud;
import io.github.brainage04.minecraftmodconventions.ClientGameTestServers;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.EndCityPieces;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;

/**
 * Films every structure variant the mod adds in each group of biomes that looks different, one
 * 4-second shot each, ordered by dimension (Overworld, Nether, End), then structure, then biome
 * group. The variants and their biomes come from the registry at runtime.
 *
 * <p>Stages are built one at a time while the client looks at the previous one: the structure is
 * placed, the biome is filled around it and the ground matched to it, natural spawning and the
 * structure's spawners then run with the player in creative mode (spectators do not count for
 * spawning), and the server is frozen until the shot. The camera rehearses the shot off screen
 * until a whole pass builds no new section; the filmed pass must then build and lose none.
 *
 * <p>Every shot runs the Overworld clock from noon to midnight with Night Vision on the camera
 * player, and circles the structure once per shot at a fixed distance, descending from looking 30
 * degrees down at the centre to looking 30 degrees up at it. {@code STAGE_START} and {@code
 * STAGE_END} with a wall-clock time mark each shot, and the delivered video is cut to them.
 */
@SuppressWarnings("UnstableApiUsage")
public final class DimensionStructureVariantsClientGameTest implements FabricClientGameTest {
    /** The Nether fog end the test mod's dimension type sets, in blocks (vanilla: 96). */
    private static final double FOG_END_NETHER = 220.0D;

    /** The pop-in check runs every this many ticks of the shot. */
    private static final int CHECK_INTERVAL = 4;

    /** Pieces of fewer blocks than this that touch nothing else are removed. */
    private static final int MIN_PIECE_BLOCKS = 40;

    /** The server's view distance a stage opens with; it is raised in steps. */
    private static final int START_DISTANCE = 12;

    private static final int DISTANCE_STEP = 4;

    /** The smallest client render distance: the horizon stays this far away. */
    private static final int MIN_RENDER_DISTANCE = 12;

    private static final int SIMULATION_DISTANCE = 12;

    private static final int MAX_REHEARSALS = 6;

    /** Camera poses per tick in the rehearsal, so it covers what the frames between ticks show. */
    private static final int REHEARSAL_STEPS_PER_TICK = 2;

    private static final int STABLE_TICKS = 20;

    /** Blocks a structure may float above the flat world's ground before it is moved onto it. */
    private static final int FOUNDATION_DEPTH = 8;

    private static final int LOAD_TIMEOUT_TICKS = 6000;

    /** How far the camera stays from every block of the structure (and the End's islands). */
    private static final double REQUIRED_CLEARANCE = 4.0D;

    /** How far above the ground's surface the camera's descent stops. */
    private static final double FLOOR_ABOVE_GROUND = 2.0D;

    /**
     * Game ticks per second while a shot is filmed: the frame rate the rehearsal measured divided
     * by {@link #FRAMES_PER_TICK}, between these bounds. Software rendering manages 3 to 18 frames
     * a second and the game ticks at most once per frame in a client GameTest, so at 20 ticks a
     * second every frame would be a new tick, 4.5 degrees of orbit apart. At a lower rate each tick
     * gets several frames, the camera moves between them, and the cut plays the shot back at 20
     * ticks a second.
     */
    private static final double MAX_SHOT_TICK_RATE = 4.0D;

    private static final double MIN_SHOT_TICK_RATE = 1.0D;

    private static final double FRAMES_PER_TICK = 2.0D;

    /** Chunks loaded and fed to the GameTest server in one task, so no task blocks for long. */
    private static final int CHUNK_BATCH = 96;

    private static final long NOON = 6000L;
    private static final long MIDNIGHT = 18000L;
    private static final long DAY = 24000L;

    /** Ticks of natural spawning in daylight (one creature pass runs every 400 ticks). */
    private static final int DAY_SPAWN_TICKS = 400;

    /** Ticks of natural spawning at night (Overworld only). */
    private static final int NIGHT_SPAWN_TICKS = 200;

    /**
     * Ticks the player stays beside each spawner: the first spawn comes after 20 ticks and the next
     * after 200 to 800 more, so this sees one or two of them.
     */
    private static final int SPAWNER_TICKS = 240;

    /** Radii the camera search tries, 6 blocks apart, from the one that frames the structure. */
    private static final int ORBIT_ROUNDS = 16;

    /**
     * Bastion piglins and hoglins convert after 300 ticks outside the Nether (vanilla). Spawning
     * before a bastion shot may take this many of them, so the shot's 80 ends before any convert.
     */
    private static final int BASTION_SPAWN_BUDGET = 180;

    /** The player watches from this far above the structure's top. */
    private static final int WATCH_HEIGHT = 30;

    /** Distance between the stages of one dimension. */
    private static final int OVERWORLD_SPACING = 4096;

    private static final int NETHER_SPACING = 2048;
    private static final int END_SPACING = 1600;

    /** Problems found while filming; reported after the last stage. */
    private static final List<String> PROBLEMS = new ArrayList<>();

    /** Item entities that mobs dropped as they died, removed before each shot. */
    private static final Set<UUID> MOB_DROPS = new HashSet<>();

    /** Item entities added to a server level in the current server tick. */
    private static final List<ItemEntity> NEW_ITEMS = new ArrayList<>();

    /** The client GameTest profile CI asks for: one stage of each structure. */
    private static final String SMOKE_PROFILE = "smoke";

    private enum Dimension {
        OVERWORLD(Level.OVERWORLD, BiomeTags.IS_OVERWORLD, "Overworld"),
        NETHER(Level.NETHER, BiomeTags.IS_NETHER, "Nether"),
        END(Level.END, BiomeTags.IS_END, "End");

        final ResourceKey<Level> level;
        final TagKey<Biome> biomes;
        final String label;

        Dimension(ResourceKey<Level> level, TagKey<Biome> biomes, String label) {
            this.level = level;
            this.biomes = biomes;
            this.label = label;
        }
    }

    /** What the flat Overworld's grass top becomes around a stage. */
    private enum Ground {
        NATURAL(null, null),
        SAND(Blocks.SAND, null),
        RED_SAND(Blocks.RED_SAND, null),
        SNOW(Blocks.SNOW_BLOCK, null),
        MYCELIUM(Blocks.MYCELIUM, null),
        WARM_SEA(Blocks.WATER, Blocks.SAND),
        SEA(Blocks.WATER, Blocks.GRAVEL),
        ICE(Blocks.ICE, Blocks.GRAVEL);

        final Block top;
        final Block below;

        Ground(Block top, Block below) {
            this.top = top;
            this.below = below;
        }
    }

    /**
     * Overworld biomes that look the same on the flat ground: same ground block, about the same
     * grass, water and sky colour. Every Overworld biome must be in exactly one group.
     */
    private record Group(String id, String label, Ground ground, List<String> biomes) {}

    private static final List<Group> OVERWORLD_GROUPS =
            List.of(
                    new Group(
                            "plains",
                            "green grass biomes",
                            Ground.NATURAL,
                            List.of(
                                    "plains",
                                    "sunflower_plains",
                                    "meadow",
                                    "forest",
                                    "flower_forest",
                                    "birch_forest",
                                    "old_growth_birch_forest",
                                    "dappled_forest",
                                    "dark_forest",
                                    "pale_garden",
                                    "cherry_grove",
                                    "taiga",
                                    "old_growth_pine_taiga",
                                    "old_growth_spruce_taiga",
                                    "savanna",
                                    "savanna_plateau",
                                    "windswept_savanna",
                                    "windswept_hills",
                                    "windswept_gravelly_hills",
                                    "windswept_forest",
                                    "stony_peaks",
                                    "river",
                                    "beach",
                                    "stony_shore",
                                    "dripstone_caves",
                                    "lush_caves",
                                    "deep_dark",
                                    "sulfur_caves")),
                    new Group("desert", "desert", Ground.SAND, List.of("desert")),
                    new Group(
                            "badlands",
                            "badlands biomes",
                            Ground.RED_SAND,
                            List.of("badlands", "eroded_badlands", "wooded_badlands")),
                    new Group(
                            "jungle",
                            "jungle biomes",
                            Ground.NATURAL,
                            List.of("jungle", "sparse_jungle", "bamboo_jungle")),
                    new Group(
                            "swamp", "swamps", Ground.NATURAL, List.of("swamp", "mangrove_swamp")),
                    new Group(
                            "snowy",
                            "snowy biomes",
                            Ground.SNOW,
                            List.of(
                                    "snowy_plains",
                                    "ice_spikes",
                                    "snowy_taiga",
                                    "snowy_slopes",
                                    "grove",
                                    "frozen_peaks",
                                    "jagged_peaks",
                                    "frozen_river",
                                    "snowy_beach")),
                    new Group(
                            "mushroom",
                            "mushroom fields",
                            Ground.MYCELIUM,
                            List.of("mushroom_fields")),
                    new Group(
                            "warm_ocean",
                            "warm oceans",
                            Ground.WARM_SEA,
                            List.of("warm_ocean", "lukewarm_ocean", "deep_lukewarm_ocean")),
                    new Group(
                            "ocean",
                            "temperate and cold oceans",
                            Ground.SEA,
                            List.of("ocean", "deep_ocean", "cold_ocean", "deep_cold_ocean")),
                    new Group(
                            "frozen_ocean",
                            "frozen oceans",
                            Ground.ICE,
                            List.of("frozen_ocean", "deep_frozen_ocean")));

    /** Every Nether and End biome is its own group, in this order. */
    private static final List<String> NETHER_BIOMES =
            List.of(
                    "nether_wastes",
                    "crimson_forest",
                    "warped_forest",
                    "soul_sand_valley",
                    "basalt_deltas");

    private static final List<String> END_BIOMES =
            List.of("the_end", "end_highlands", "end_midlands", "end_barrens", "small_end_islands");

    /**
     * One shot: a variant in one biome group. {@code biome} is the group's biome the stage is
     * filled with; {@code members} are the variant's biomes the group stands for.
     */
    private record Stage(
            String id,
            String variant,
            Dimension dimension,
            String group,
            String groupLabel,
            ResourceKey<Biome> biome,
            List<ResourceKey<Biome>> members,
            Ground ground,
            int ordinal) {
        String title() {
            return structureName(variant) + " in the " + dimension.label;
        }

        String subtitle() {
            String name = biomeName(biome);
            if (members.size() == 1) return "Biome: " + name;
            return "Biome: " + name + " (stands for " + members.size() + " " + groupLabel + ")";
        }

        boolean bastion() {
            return variant.startsWith("bastion");
        }

        boolean hasGround() {
            return dimension != Dimension.END;
        }

        double visibleRange() {
            return dimension == Dimension.NETHER ? FOG_END_NETHER + 10.0D : Double.MAX_VALUE;
        }
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        trackMobDrops();
        Properties serverProperties = ClientGameTestServers.flatServerProperties();
        serverProperties.setProperty("view-distance", Integer.toString(START_DISTANCE));
        // Software rendering can keep a client tick, and so the server waiting for it, busy for
        // more than the watchdog's minute.
        serverProperties.setProperty("max-tick-time", "-1");
        serverProperties.setProperty("simulation-distance", Integer.toString(SIMULATION_DISTANCE));
        // Mobs are tracked from the player, who watches from the structure's centre; the camera
        // circles up to a few hundred blocks away.
        serverProperties.setProperty("entity-broadcast-range-percentage", "500");
        ClientGameTestServers.withDedicatedServer(
                context,
                serverProperties,
                "DimensionStructureVariants visual GameTest",
                server ->
                        server.runOnServer(
                                minecraftServer -> {
                                    for (String command :
                                            List.of(
                                                    "gamerule advance_time false",
                                                    "gamerule advance_weather false",
                                                    // The sprint report and advancements for
                                                    // entering the Nether and the End would
                                                    // reach the player's chat on camera.
                                                    "gamerule send_command_feedback false",
                                                    "gamerule show_advancement_messages false",
                                                    "weather clear",
                                                    "gamerule max_block_modifications 2147483647",
                                                    // The client GameTest world starts with
                                                    // spawning off.
                                                    "gamerule spawn_mobs true",
                                                    "gamerule spawn_monsters true",
                                                    "gamerule spawner_blocks_work true",
                                                    // Natural generation places plants after
                                                    // structures; a structure placed into a
                                                    // live world strands chorus plants and
                                                    // flowers, which break and would litter the
                                                    // stage. Containers spill whatever this rule
                                                    // says, so the loot check still holds.
                                                    "gamerule block_drops false")) {
                                        run(minecraftServer, command);
                                    }
                                    // Each level's monster switch is spawn_mobs and
                                    // spawn_monsters, but the game recomputes it only when
                                    // spawn_monsters changes, so turning spawn_mobs on leaves
                                    // monsters off without this.
                                    minecraftServer.updateMobSpawningFlags();
                                    setDayTime(minecraftServer, NOON);
                                    System.out.println(
                                            "[DSV] difficulty="
                                                    + minecraftServer.getWorldData().getDifficulty()
                                                    + " seed="
                                                    + minecraftServer.overworld().getSeed());
                                }),
                server -> {
                    ClientGameTestServers.assertClientWorldAndPlayerAvailable(context);
                    context.runOnClient(
                            client -> {
                                // Without occlusion culling every section in range is drawn
                                // whatever the camera did in the last ticks.
                                client.smartCull = false;
                                client.options.chunkSectionFadeInTime().set(0.0D);
                                // Mobs are drawn out to 64 blocks times their size by default; the
                                // shot starts much farther out.
                                client.options.entityDistanceScaling().set(5.0D);
                            });
                    server.runOnServer(
                            minecraftServer -> {
                                ServerPlayer player = player(minecraftServer);
                                player.setGameMode(GameType.SPECTATOR);
                                player.addEffect(
                                        new MobEffectInstance(
                                                MobEffects.NIGHT_VISION,
                                                MobEffectInstance.INFINITE_DURATION,
                                                0,
                                                false,
                                                false,
                                                false));
                            });
                    List<Stage> stages =
                            server.computeOnServer(
                                    DimensionStructureVariantsClientGameTest::activeStages);
                    System.out.println("[DSV] " + stages.size() + " stages:");
                    for (Stage stage : stages) {
                        System.out.println("[DSV]   " + stage.id() + " " + stage.members());
                    }
                    ClientGameTestRecorder.startRecording(context);
                    List<BlockPos> endStages = new ArrayList<>();
                    for (Stage stage : stages) {
                        Prepared prepared = prepareStage(context, server, stage, endStages);
                        filmStage(context, server, stage, prepared);
                        release(server, stage, prepared);
                    }
                });
        if (!PROBLEMS.isEmpty()) throw new AssertionError(String.join("\n", PROBLEMS));
    }

    // ------------------------------------------------------------------------------------------
    // The stages
    // ------------------------------------------------------------------------------------------

    /**
     * Records the items a mob drops as it dies: LivingEntity.die adds its loot to the level and
     * then fires AFTER_DEATH in the same server tick, so the items added in that tick around the
     * mob are its drops.
     */
    private static void trackMobDrops() {
        ServerEntityEvents.ENTITY_LOAD.register(
                (entity, level) -> {
                    if (entity instanceof ItemEntity item) NEW_ITEMS.add(item);
                });
        ServerLivingEntityEvents.AFTER_DEATH.register(
                (entity, source) -> {
                    AABB around = entity.getBoundingBox().inflate(2.0D);
                    for (ItemEntity item : NEW_ITEMS) {
                        if (item.level() == entity.level() && around.contains(item.position()))
                            MOB_DROPS.add(item.getUUID());
                    }
                });
        ServerTickEvents.END_SERVER_TICK.register(minecraftServer -> NEW_ITEMS.clear());
    }

    /**
     * Every stage, or those whose id or variant is named in {@code DSV_STAGES}. Without {@code
     * DSV_STAGES}, the smoke profile CI runs takes one stage of each structure.
     */
    private static List<Stage> activeStages(MinecraftServer server) {
        List<Stage> all = allStages(server);
        String only = System.getenv("DSV_STAGES");
        if (only == null || only.isBlank()) {
            return SMOKE_PROFILE.equals(System.getenv("CLIENT_GAMETEST_PROFILE"))
                    ? smokeStages(all)
                    : all;
        }
        Set<String> wanted = Set.of(only.split(","));
        return all.stream()
                .filter(stage -> wanted.contains(stage.id()) || wanted.contains(stage.variant()))
                .toList();
    }

    /**
     * One stage of each structure (bastion, end city, fortress, village), first in a dimension no
     * earlier pick covers, so the smoke run still visits all three dimensions.
     */
    private static List<Stage> smokeStages(List<Stage> all) {
        Set<String> structures = new HashSet<>();
        Set<Dimension> dimensions = EnumSet.noneOf(Dimension.class);
        Set<Stage> picked = new HashSet<>();
        for (Stage stage : all) {
            String structure = structureOf(stage);
            if (!structures.contains(structure) && dimensions.add(stage.dimension())) {
                structures.add(structure);
                picked.add(stage);
            }
        }
        for (Stage stage : all) {
            if (structures.add(structureOf(stage))) picked.add(stage);
        }
        return all.stream().filter(picked::contains).toList();
    }

    /** The structure a stage's variant moves, such as end_city for end_city_nether. */
    private static String structureOf(Stage stage) {
        return stage.variant().substring(0, stage.variant().lastIndexOf('_'));
    }

    private static List<Stage> allStages(MinecraftServer server) {
        var structures = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        record Key(Dimension dimension, String variant, int groupIndex) {}
        TreeMap<Key, List<ResourceKey<Biome>>> byGroup =
                new TreeMap<>(
                        Comparator.comparing(Key::dimension)
                                .thenComparing(Key::variant)
                                .thenComparingInt(Key::groupIndex));
        for (StructureVariantCatalog.Variant variant : StructureVariantCatalog.VARIANTS) {
            Structure structure = structures.getOrThrow(variant.structure()).value();
            String id = variant.structure().identifier().getPath();
            for (Holder<Biome> biome : structure.biomes()) {
                ResourceKey<Biome> key = biome.unwrapKey().orElseThrow();
                Dimension dimension = null;
                for (Dimension candidate : Dimension.values()) {
                    if (biome.is(candidate.biomes)) dimension = candidate;
                }
                if (dimension == null)
                    throw new AssertionError(id + " lists " + key + ", which is in no dimension");
                int group = groupIndex(dimension, key);
                byGroup.computeIfAbsent(new Key(dimension, id, group), k -> new ArrayList<>())
                        .add(key);
            }
        }
        List<Stage> stages = new ArrayList<>();
        Map<Dimension, Integer> ordinals = new HashMap<>();
        for (Map.Entry<Key, List<ResourceKey<Biome>>> entry : byGroup.entrySet()) {
            Key key = entry.getKey();
            List<ResourceKey<Biome>> members = new ArrayList<>(entry.getValue());
            members.sort(
                    Comparator.comparingInt(
                            biome -> memberIndex(key.dimension(), biome.identifier().getPath())));
            String group;
            String label;
            Ground ground;
            if (key.dimension() == Dimension.OVERWORLD) {
                Group definition = OVERWORLD_GROUPS.get(key.groupIndex());
                group = definition.id();
                label = definition.label();
                ground = definition.ground();
            } else {
                group = members.getFirst().identifier().getPath();
                label = biomeName(members.getFirst());
                ground = Ground.NATURAL;
            }
            int ordinal = ordinals.merge(key.dimension(), 1, Integer::sum) - 1;
            stages.add(
                    new Stage(
                            key.variant() + "/" + group,
                            key.variant(),
                            key.dimension(),
                            group,
                            label,
                            members.getFirst(),
                            List.copyOf(members),
                            ground,
                            ordinal));
        }
        return stages;
    }

    private static int groupIndex(Dimension dimension, ResourceKey<Biome> biome) {
        String path = biome.identifier().getPath();
        if (!"minecraft".equals(biome.identifier().getNamespace()))
            throw new AssertionError("No biome group for " + biome);
        int index =
                switch (dimension) {
                    case OVERWORLD -> {
                        for (int i = 0; i < OVERWORLD_GROUPS.size(); i++) {
                            if (OVERWORLD_GROUPS.get(i).biomes().contains(path)) yield i;
                        }
                        yield -1;
                    }
                    case NETHER -> NETHER_BIOMES.indexOf(path);
                    case END -> END_BIOMES.indexOf(path);
                };
        if (index < 0) throw new AssertionError("No biome group for " + biome);
        return index;
    }

    /** Where a biome stands in its group, so the group's first biome is the one filmed. */
    private static int memberIndex(Dimension dimension, String path) {
        return switch (dimension) {
            case OVERWORLD -> {
                for (Group group : OVERWORLD_GROUPS) {
                    int index = group.biomes().indexOf(path);
                    if (index >= 0) yield index;
                }
                yield Integer.MAX_VALUE;
            }
            case NETHER -> NETHER_BIOMES.indexOf(path);
            case END -> END_BIOMES.indexOf(path);
        };
    }

    private static String structureName(String variant) {
        if (variant.startsWith("fortress")) return "Nether fortress";
        if (variant.startsWith("bastion")) return "Bastion remnant";
        if (variant.startsWith("end_city")) return "End city";
        if (variant.startsWith("village")) return "Plains village";
        return variant;
    }

    private static String biomeName(ResourceKey<Biome> biome) {
        StringBuilder name = new StringBuilder();
        for (String word : biome.identifier().getPath().split("_")) {
            if (!name.isEmpty()) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }

    // ------------------------------------------------------------------------------------------
    // Building a stage
    // ------------------------------------------------------------------------------------------

    /** What a stage needs once it is built. */
    private record Prepared(
            BoundingBox occupied,
            int groundY,
            ChunkRange padded,
            Region region,
            List<ChunkPos> forced,
            ShowcaseCamera.Orbit orbit,
            ShowcaseCamera.Pose[] samples,
            int renderDistance,
            int viewDistance,
            BlockPos watch) {}

    /** The parts of a stage that are found one server task after the other. */
    private static final class Build {
        ServerLevel level;
        Holder.Reference<Structure> structure;
        StructureStart start;
        BoundingBox startBox;
        BoundingBox pieces;
        BoundingBox occupied;
        int groundY;
        ChunkRange padded;

        /** Chunks held loaded until the stage is released. */
        final List<ChunkPos> forced = new ArrayList<>();
    }

    private static Prepared prepareStage(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            Stage stage,
            List<BlockPos> endStages) {
        long began = System.currentTimeMillis();
        Build build = new Build();
        // Nothing moves from the moment the structure exists until the shot, except while natural
        // spawning and the spawners run.
        server.runOnServer(
                minecraftServer -> {
                    minecraftServer.tickRateManager().setFrozen(true);
                    build.level = minecraftServer.getLevel(stage.dimension().level);
                    if (build.level == null)
                        throw new AssertionError("Expected dimension " + stage.dimension());
                    build.structure =
                            build.level
                                    .registryAccess()
                                    .lookupOrThrow(Registries.STRUCTURE)
                                    .getOrThrow(
                                            ResourceKey.create(
                                                    Registries.STRUCTURE,
                                                    DimensionStructureVariants.of(
                                                            stage.variant())));
                    ChunkPos chunk = location(build, stage, endStages);
                    build.start = start(build, stage, chunk);
                    build.startBox = build.start.getBoundingBox();
                    build.pieces =
                            BoundingBox.encapsulatingBoxes(
                                            build.start.getPieces().stream()
                                                    .map(StructurePiece::getBoundingBox)
                                                    .toList())
                                    .orElseThrow();
                });
        // The start box is the most the structure can fill; it and a margin of chunks are loaded
        // before it is placed.
        loadChunks(server, build, chunks(chunkRange(build.startBox, 3)));
        server.runOnServer(minecraftServer -> place(build, stage));
        if (!stage.hasGround()) {
            // The camera's floor follows the End's real terrain out to the widest circle tried.
            BoundingBox occupied = build.occupied;
            int reach = (int) Math.ceil(startRadius(occupied) + ORBIT_ROUNDS * 6.0D) + 16;
            int centreX = (occupied.minX() + occupied.maxX()) / 2;
            int centreZ = (occupied.minZ() + occupied.maxZ()) / 2;
            List<ChunkPos> terrain = new ArrayList<>();
            for (ChunkPos chunk :
                    chunks(
                            new ChunkRange(
                                    SectionPos.blockToSectionCoord(centreX - reach),
                                    SectionPos.blockToSectionCoord(centreX + reach),
                                    SectionPos.blockToSectionCoord(centreZ - reach),
                                    SectionPos.blockToSectionCoord(centreZ + reach)))) {
                if (!build.forced.contains(chunk)) terrain.add(chunk);
            }
            loadChunks(server, build, terrain);
        }

        Prepared[] plan = new Prepared[1];
        server.runOnServer(minecraftServer -> plan[0] = plan(build, stage));
        Prepared prepared = plan[0];

        // The ground and the biome around the stage: in the Overworld everything the camera can
        // see, elsewhere the volume the camera flies through (the fog colour and the particles
        // follow the biome at the camera).
        double reach =
                stage.dimension() == Dimension.OVERWORLD
                        ? prepared.orbit().radius() + prepared.renderDistance() * 16.0D + 16.0D
                        : Math.max(
                                        prepared.orbit().radius(),
                                        Math.max(
                                                        prepared.occupied().getXSpan(),
                                                        prepared.occupied().getZSpan())
                                                / 2.0D)
                                + 32.0D;
        int minX = (int) Math.floor(prepared.orbit().centreX() - reach);
        int maxX = (int) Math.ceil(prepared.orbit().centreX() + reach);
        int minZ = (int) Math.floor(prepared.orbit().centreZ() - reach);
        int maxZ = (int) Math.ceil(prepared.orbit().centreZ() + reach);
        ChunkRange filled =
                new ChunkRange(
                        SectionPos.blockToSectionCoord(minX),
                        SectionPos.blockToSectionCoord(maxX),
                        SectionPos.blockToSectionCoord(minZ),
                        SectionPos.blockToSectionCoord(maxZ));
        List<ChunkPos> held = new ArrayList<>(chunks(filled));
        for (ChunkPos chunk : chunks(prepared.region().chunks())) {
            if (!filled.contains(chunk)) held.add(chunk);
        }
        loadChunks(server, build, held);
        prepared = withForced(prepared, build.forced);
        server.runOnServer(
                minecraftServer -> {
                    Holder<Biome> biome =
                            build.level
                                    .registryAccess()
                                    .lookupOrThrow(Registries.BIOME)
                                    .getOrThrow(stage.biome());
                    var result =
                            FillBiomeCommand.fill(
                                    build.level,
                                    new BlockPos(
                                            SectionPos.sectionToBlockCoord(filled.minX()),
                                            build.level.getMinY(),
                                            SectionPos.sectionToBlockCoord(filled.minZ())),
                                    new BlockPos(
                                            SectionPos.sectionToBlockCoord(filled.maxX() + 1) - 1,
                                            build.level.getMaxY(),
                                            SectionPos.sectionToBlockCoord(filled.maxZ() + 1) - 1),
                                    biome);
                    // The flat Overworld is plains already; nothing to change is fine.
                    result.ifRight(
                            error -> {
                                if (error.getType() == FillBiomeCommand.ERROR_NO_BIOMES_SET) return;
                                throw new AssertionError(
                                        "Could not fill the biome of " + stage.id(), error);
                            });
                });
        if (stage.ground() != Ground.NATURAL) {
            List<ChunkPos> ground = chunks(filled);
            for (int from = 0; from < ground.size(); from += CHUNK_BATCH) {
                List<ChunkPos> batch =
                        ground.subList(from, Math.min(ground.size(), from + CHUNK_BATCH));
                server.runOnServer(
                        minecraftServer -> convertGround(build.level, stage, build.groundY, batch));
            }
        }
        waitForServerLight(context, server, stage);
        Prepared ready = prepared;
        populate(context, server, stage, build, ready);
        waitForServerLight(context, server, stage);
        System.out.println(
                "[DSV] "
                        + stage.id()
                        + " prepared in "
                        + (System.currentTimeMillis() - began) / 1000
                        + " s");
        return ready;
    }

    private static Prepared withForced(Prepared prepared, List<ChunkPos> forced) {
        return new Prepared(
                prepared.occupied(),
                prepared.groundY(),
                prepared.padded(),
                prepared.region(),
                List.copyOf(forced),
                prepared.orbit(),
                prepared.samples(),
                prepared.renderDistance(),
                prepared.viewDistance(),
                prepared.watch());
    }

    /**
     * The chunk to start the structure in. Overworld and Nether stages stand in a row far enough
     * apart that none is ever in sight of another. End stages stand where the End really has the
     * stage's biome, so the islands under them are the biome's own, and solid-ground variants where
     * the mod lets them start.
     */
    private static ChunkPos location(Build build, Stage stage, List<BlockPos> endStages) {
        return switch (stage.dimension()) {
            case OVERWORLD ->
                    ChunkPos.containing(
                            new BlockPos(OVERWORLD_SPACING * (stage.ordinal() + 1), 0, 0));
            case NETHER ->
                    ChunkPos.containing(new BlockPos(NETHER_SPACING * (stage.ordinal() + 1), 0, 0));
            case END -> endLocation(build, stage, endStages);
        };
    }

    /**
     * A start the game itself would accept for the stage's biome: the structure's own placement
     * rules (the mod's solid ground for the End island variants) with the stage's biome at the
     * start, which is where vanilla checks it. The biome at the middle of the start chunk must be
     * the stage's biome too, so the structure's surroundings are that biome's terrain.
     */
    private static ChunkPos endLocation(Build build, Stage stage, List<BlockPos> endStages) {
        ServerLevel level = build.level;
        var generator = level.getChunkSource().getGenerator();
        BiomeResolver biomes =
                generator
                        .getBiomeSource()
                        .createUncachedResolver(level.getChunkSource().randomState());
        boolean mainIsland = stage.biome().identifier().getPath().equals("the_end");
        // The End biome fills the 64 chunks around the origin: the main island and the void
        // ring around it. A stage there stands in the ring, well away from the pillars.
        int[] radii = mainIsland ? new int[] {44, 40, 48, 52} : radii(70, 600, 5);
        for (int radius : radii) {
            for (int step = 0; step < 64; step++) {
                double angle = Math.PI * 2.0D * step / 64.0D;
                ChunkPos chunk =
                        new ChunkPos(
                                (int) Math.round(Math.cos(angle) * radius),
                                (int) Math.round(Math.sin(angle) * radius));
                BlockPos middle = new BlockPos(chunk.getBlockX(8), 64, chunk.getBlockZ(8));
                boolean free = true;
                for (BlockPos other : endStages) {
                    if (other.distSqr(middle) < (double) END_SPACING * END_SPACING) free = false;
                }
                if (!free) continue;
                if (!biomes.getNoiseBiome(
                                QuartPos.fromBlock(middle.getX()),
                                QuartPos.fromBlock(64),
                                QuartPos.fromBlock(middle.getZ()))
                        .is(stage.biome())) continue;
                if (!generate(build, chunk, biome -> biome.is(stage.biome())).isValid()) continue;
                endStages.add(middle);
                System.out.println("[DSV] " + stage.id() + " stands in the End at chunk " + chunk);
                return chunk;
            }
        }
        throw new AssertionError("Found no place in the End for " + stage.id());
    }

    private static int[] radii(int from, int to, int step) {
        int[] radii = new int[(to - from) / step + 1];
        for (int i = 0; i < radii.length; i++) radii[i] = from + i * step;
        return radii;
    }

    private static StructureStart generate(
            Build build, ChunkPos chunk, java.util.function.Predicate<Holder<Biome>> validBiome) {
        ServerLevel level = build.level;
        var generator = level.getChunkSource().getGenerator();
        var randomState = level.getChunkSource().randomState();
        return build.structure
                .value()
                .generate(
                        build.structure,
                        level.dimension(),
                        level.registryAccess(),
                        generator,
                        generator.getBiomeSource(),
                        randomState.createClimateSampler(
                                SamplerContext.builder().enableCaches().build()),
                        randomState,
                        level.getStructureTemplateManager(),
                        level.getSeed(),
                        chunk,
                        0,
                        level,
                        validBiome);
    }

    /**
     * The structure's start, as {@code /place structure} makes it. End cities stand on the lowest
     * surface of their 5 by 5 corner and refuse anything below y 60, which an ocean's surface (y
     * 63) passes and the flat test world's (y -60) does not; the Overworld End city is built from
     * the same pieces and the same random at the flat world's surface instead.
     */
    private static StructureStart start(Build build, Stage stage, ChunkPos chunk) {
        if (stage.variant().equals("end_city_overworld")) {
            WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
            random.setLargeFeatureSeed(build.level.getSeed(), chunk.x(), chunk.z());
            Rotation rotation = Rotation.getRandom(random);
            int surface =
                    build.level
                            .getChunkSource()
                            .getGenerator()
                            .getFirstOccupiedHeight(
                                    chunk.getBlockX(7),
                                    chunk.getBlockZ(7),
                                    net.minecraft.world.level.levelgen.Heightmap.Types
                                            .WORLD_SURFACE_WG,
                                    build.level,
                                    build.level.getChunkSource().randomState());
            List<StructurePiece> pieces = new ArrayList<>();
            EndCityPieces.startHouseTower(
                    build.level.getStructureTemplateManager(),
                    new BlockPos(chunk.getBlockX(7), surface, chunk.getBlockZ(7)),
                    rotation,
                    pieces,
                    random);
            return new StructureStart(
                    build.structure.value(), chunk, 0, new PiecesContainer(pieces));
        }
        // Overworld and Nether stages stand on ground the test gives the stage's biome afterwards.
        StructureStart start =
                stage.dimension() == Dimension.END
                        ? generate(build, chunk, biome -> biome.is(stage.biome()))
                        : generate(build, chunk, biome -> true);
        if (!start.isValid())
            throw new AssertionError("Expected a valid structure start for " + stage.id());
        return start;
    }

    /**
     * Places the structure, moves a floating fortress onto the ground, registers the start in its
     * chunks as natural generation does and measures it. {@code /place structure} builds the blocks
     * only, so without the registration the game would not know the structure is there: the
     * fortress's own monster spawns (its {@code spawn_overrides}, by piece) need it.
     */
    private static void place(Build build, Stage stage) {
        ServerLevel level = build.level;
        BoundingBox box = build.startBox;
        ChunkPos.rangeClosed(
                        ChunkPos.containing(new BlockPos(box.minX(), 0, box.minZ())),
                        ChunkPos.containing(new BlockPos(box.maxX(), 0, box.maxZ())))
                .forEach(
                        chunk ->
                                build.start.placeInChunk(
                                        level,
                                        level.structureManager(),
                                        level.getChunkSource().getGenerator(),
                                        level.getRandom(),
                                        new BoundingBox(
                                                chunk.getMinBlockX(),
                                                level.getMinY(),
                                                chunk.getMinBlockZ(),
                                                chunk.getMaxBlockX(),
                                                level.getMaxY() + 1,
                                                chunk.getMaxBlockZ()),
                                        chunk));
        if (stage.dimension() == Dimension.OVERWORLD) {
            int dy = dropToGround(level, stage, box);
            if (dy != 0) {
                for (StructurePiece piece : build.start.getPieces()) piece.move(0, dy, 0);
            }
            build.startBox = box.moved(0, dy, 0);
            build.pieces = build.pieces.moved(0, dy, 0);
        }
        StructureStart registered =
                new StructureStart(
                        build.structure.value(),
                        build.start.getChunkPos(),
                        0,
                        new PiecesContainer(build.start.getPieces()));
        ChunkPos origin = registered.getChunkPos();
        level.getChunk(origin.x(), origin.z())
                .setStartForStructure(build.structure.value(), registered);
        BoundingBox registeredBox = registered.getBoundingBox();
        ChunkPos.rangeClosed(
                        ChunkPos.containing(
                                new BlockPos(registeredBox.minX(), 0, registeredBox.minZ())),
                        ChunkPos.containing(
                                new BlockPos(registeredBox.maxX(), 0, registeredBox.maxZ())))
                .forEach(
                        chunk ->
                                level.getChunk(chunk.x(), chunk.z())
                                        .addReferenceForStructure(
                                                build.structure.value(), origin.pack()));
        int strays = stage.hasGround() ? removeStrays(level, stage, build.startBox) : 0;
        build.groundY =
                stage.hasGround()
                        ? groundHeight(level, chunkRange(build.startBox, 1))
                        : level.getMinY() - 1;
        build.occupied = occupiedBounds(level, build.pieces, build.groundY);
        build.padded = chunkRange(build.occupied, 1);
        if (stage.hasGround()) assertGround(level, stage, build.padded, build.groundY);
        System.out.println(
                "[DSV] "
                        + stage.id()
                        + " placed: start="
                        + build.startBox
                        + " occupied="
                        + build.occupied
                        + " ground="
                        + build.groundY
                        + " strays="
                        + strays
                        + " first piece "
                        + build.start.getPieces().getFirst().getBoundingBox()
                        + " villagers "
                        + villagers(level, AABB.of(build.startBox).inflate(64.0D)));
    }

    /** Where the villagers are, which are inside blocks and which asleep, for the log. */
    private static String villagers(ServerLevel level, AABB area) {
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, area);
        StringBuilder text = new StringBuilder().append(villagers.size());
        for (Villager villager : villagers) {
            BlockPos pos = villager.blockPosition();
            text.append(" [")
                    .append(pos.getX())
                    .append(' ')
                    .append(pos.getY())
                    .append(' ')
                    .append(pos.getZ())
                    .append(villager.isInWall() ? " in wall" : "")
                    .append(villager.isSleeping() ? " asleep" : "")
                    .append(" health ")
                    .append((int) villager.getHealth())
                    .append(']');
        }
        return text.toString();
    }

    /** The camera, the region the pop-in check watches and the distances for the stage. */
    private static Prepared plan(Build build, Stage stage) {
        ServerLevel level = build.level;
        ShowcaseCamera.Orbit orbit = planOrbit(level, stage, build.occupied, build.groundY);
        ShowcaseCamera.Pose[] samples = new ShowcaseCamera.Pose[201];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = orbit.at(i / (double) (samples.length - 1));
        }
        ChunkRange padded = build.padded;
        int lowestSection =
                SectionPos.blockToSectionCoord(
                        stage.hasGround() ? build.groundY : build.occupied.minY());
        int highestSection = SectionPos.blockToSectionCoord(build.occupied.maxY());
        Region region = new Region(padded, lowestSection, highestSection);
        int minimum =
                stage.dimension() == Dimension.NETHER
                        ? (int) Math.ceil(FOG_END_NETHER / 16.0D) + 1
                        : MIN_RENDER_DISTANCE;
        // The server sends chunks around the player, who stays at the centre, and the camera sees
        // renderDistance chunks around itself: what the camera sees from its farthest point must
        // lie inside the sent circle, or the far ground ends in a straight edge. The render
        // distance is cut to fit; only the structure itself must stay in view.
        int orbitChunks = (int) Math.ceil(orbit.radius() / 16.0D);
        int fits = 32 - orbitChunks - 1;
        int renderDistance = Math.min(fits, Math.max(minimum, requiredDistance(padded, samples)));
        int structureDistance = requiredDistance(chunkRange(build.occupied, 0), samples) - 1;
        if (renderDistance < structureDistance) {
            PROBLEMS.add(
                    stage.id()
                            + ": the camera, "
                            + orbitChunks
                            + " chunks out, cannot see the whole structure ("
                            + structureDistance
                            + " chunks) inside the 32 chunks the server sends");
        }
        int viewDistance = Math.min(32, renderDistance + orbitChunks + 2);
        int watchY = (int) Math.min(level.getMaxY() - 4, build.occupied.maxY() + WATCH_HEIGHT);
        BlockPos watch = BlockPos.containing(orbit.centreX(), watchY, orbit.centreZ());
        System.out.println(
                String.format(
                        Locale.ROOT,
                        "[DSV] %s camera: centre=(%.1f, %.1f, %.1f) radius %.1f, height"
                                + " %.1f -> %.1f, final up angle %.1f, render %d, view %d",
                        stage.id(),
                        orbit.centreX(),
                        orbit.centreY(),
                        orbit.centreZ(),
                        orbit.radius(),
                        orbit.height(0.0D),
                        orbit.height(1.0D),
                        orbit.finalUpAngle(),
                        renderDistance,
                        viewDistance));
        return new Prepared(
                build.occupied,
                build.groundY,
                padded,
                region,
                List.of(),
                orbit,
                samples,
                renderDistance,
                viewDistance,
                watch);
    }

    /**
     * Loads the chunks and keeps them loaded until the stage is released, a batch per server task
     * so no task keeps the server (and the client's connection) waiting for long.
     */
    private static void loadChunks(
            TestDedicatedServerContext server, Build build, List<ChunkPos> chunks) {
        for (int from = 0; from < chunks.size(); from += CHUNK_BATCH) {
            List<ChunkPos> batch =
                    chunks.subList(from, Math.min(chunks.size(), from + CHUNK_BATCH));
            server.runOnServer(
                    minecraftServer -> {
                        // Forcing a whole batch first lets the workers generate it in parallel.
                        for (ChunkPos chunk : batch) {
                            build.level.setChunkForced(chunk.x(), chunk.z(), true);
                        }
                        for (ChunkPos chunk : batch) {
                            build.level.getChunk(chunk.x(), chunk.z());
                        }
                    });
            build.forced.addAll(batch);
        }
    }

    private static List<ChunkPos> chunks(ChunkRange range) {
        List<ChunkPos> chunks = new ArrayList<>();
        for (int chunkX = range.minX(); chunkX <= range.maxX(); chunkX++) {
            for (int chunkZ = range.minZ(); chunkZ <= range.maxZ(); chunkZ++) {
                chunks.add(new ChunkPos(chunkX, chunkZ));
            }
        }
        return chunks;
    }

    /**
     * Gives the flat world's grass top the stage's ground: sand in the desert, red sand in the
     * badlands, snow, mycelium, or a shallow sea (ice in frozen oceans) over sand or gravel. Only
     * open grass changes; what the structure stands on stays.
     */
    private static void convertGround(
            ServerLevel level, Stage stage, int groundY, List<ChunkPos> chunks) {
        BlockState top = stage.ground().top.defaultBlockState();
        BlockState below =
                stage.ground().below == null ? null : stage.ground().below.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (ChunkPos chunk : chunks) {
            for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
                for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                    if (!level.getBlockState(cursor.set(x, groundY, z)).is(Blocks.GRASS_BLOCK))
                        continue;
                    if (!level.getBlockState(cursor.set(x, groundY + 1, z)).isAir()) continue;
                    if (below != null)
                        level.setBlock(cursor.set(x, groundY - 1, z), below, Block.UPDATE_CLIENTS);
                    level.setBlock(cursor.set(x, groundY, z), top, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /**
     * Lets the game populate the stage: the player turns creative (spectators count neither for
     * natural spawning nor for spawners), stays beside each spawner of the structure and then high
     * above its centre while natural spawning runs, in sprinted game ticks. The Overworld runs a
     * day pass (animals need light) and then the night (monsters, spawner blazes included, fail
     * their spawn rules in daylight); the Nether and the End have no day, and their passes run at
     * the Overworld clock's noon, which villager schedules follow everywhere. Nothing is spawned by
     * the test.
     */
    private static void populate(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            Stage stage,
            Build build,
            Prepared prepared) {
        List<BlockPos> spawners =
                server.computeOnServer(minecraftServer -> spawners(build.level, prepared.padded()));
        server.runOnServer(
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer);
                    watch(player, build.level, prepared.watch());
                    player.setGameMode(GameType.CREATIVE);
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                });
        int budget = stage.bastion() ? BASTION_SPAWN_BUDGET : Integer.MAX_VALUE;
        int used = 0;
        boolean overworld = stage.dimension() == Dimension.OVERWORLD;
        if (overworld && !stage.bastion()) {
            server.runOnServer(minecraftServer -> setDayTime(minecraftServer, NOON));
            sprint(context, server, DAY_SPAWN_TICKS);
            used += DAY_SPAWN_TICKS;
        }
        server.runOnServer(
                minecraftServer -> setDayTime(minecraftServer, overworld ? MIDNIGHT : NOON));
        for (BlockPos spawner : spawners) {
            if (used + SPAWNER_TICKS > budget) break;
            server.runOnServer(
                    minecraftServer ->
                            watch(player(minecraftServer), build.level, spawner.above()));
            sprint(context, server, SPAWNER_TICKS);
            used += SPAWNER_TICKS;
        }
        server.runOnServer(
                minecraftServer -> watch(player(minecraftServer), build.level, prepared.watch()));
        int last = Math.min(overworld ? NIGHT_SPAWN_TICKS : DAY_SPAWN_TICKS, budget - used);
        sprint(context, server, last);
        used += last;
        int total = used;
        server.runOnServer(
                minecraftServer -> {
                    ServerPlayer player = player(minecraftServer);
                    player.setGameMode(GameType.SPECTATOR);
                    watch(player, build.level, prepared.watch());
                    setDayTime(minecraftServer, NOON);
                    System.out.println(
                            "[DSV] "
                                    + stage.id()
                                    + " populated for "
                                    + total
                                    + " ticks, spawners "
                                    + spawners
                                    + ", mobs "
                                    + mobs(build.level, prepared)
                                    + ", villagers "
                                    + villagers(
                                            build.level, AABB.of(build.startBox).inflate(64.0D)));
                });
    }

    private static List<BlockPos> spawners(ServerLevel level, ChunkRange range) {
        List<BlockPos> spawners = new ArrayList<>();
        for (ChunkPos chunk : chunks(range)) {
            for (BlockEntity entity :
                    level.getChunk(chunk.x(), chunk.z()).getBlockEntities().values()) {
                if (entity instanceof SpawnerBlockEntity) spawners.add(entity.getBlockPos());
            }
        }
        return spawners;
    }

    /** Runs this many game ticks as fast as the server can, then freezes again. */
    private static void sprint(
            ClientGameTestContext context, TestDedicatedServerContext server, int ticks) {
        if (ticks <= 0) return;
        server.runOnServer(
                minecraftServer -> minecraftServer.tickRateManager().requestGameToSprint(ticks));
        for (int tick = 0; tick < LOAD_TIMEOUT_TICKS; tick++) {
            context.waitTick();
            if (!server.computeOnServer(
                    minecraftServer -> minecraftServer.tickRateManager().isSprinting())) {
                return;
            }
        }
        throw new AssertionError("The sprint of " + ticks + " ticks did not end");
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static void watch(ServerPlayer player, ServerLevel level, BlockPos at) {
        player.teleportTo(
                level, at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, Set.of(), 0.0F, 90.0F, false);
    }

    private static Holder<WorldClock> overworldClock(MinecraftServer server) {
        return server.registryAccess()
                .lookupOrThrow(Registries.WORLD_CLOCK)
                .getOrThrow(WorldClocks.OVERWORLD);
    }

    /** Sets the Overworld clock to this time of the current day. */
    private static void setDayTime(MinecraftServer server, long time) {
        Holder<WorldClock> clock = overworldClock(server);
        long total = server.clockManager().getInstance(clock).totalTicks();
        server.clockManager().setTotalTicks(clock, total - Math.floorMod(total, DAY) + time);
    }

    private static void run(MinecraftServer server, String command) {
        CommandSourceStack source =
                server.createCommandSourceStack()
                        .withPermission(PermissionSet.ALL_PERMISSIONS)
                        .withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, command);
    }

    /**
     * Moves a structure that floats far above the flat world's ground (the Nether fortress keeps
     * its height of y 48 to 70, on supports that run down to the ground) down onto that ground and
     * clears the supports. Block entities move with their data (an unopened chest keeps its loot
     * table and spills nothing), and so do the structure's entities. Returns how far it moved.
     */
    private static int dropToGround(ServerLevel level, Stage stage, BoundingBox start) {
        BoundingBox occupied = flatOccupied(level, start);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int x = occupied.minX() - 8;
        int z = occupied.minZ() - 8;
        int ground = level.getMinY();
        for (int y = occupied.minY(); y > level.getMinY(); y--) {
            if (!level.getBlockState(cursor.set(x, y, z)).isAir()) {
                ground = y;
                break;
            }
        }
        int dy = ground + 1 - occupied.minY();
        if (-dy <= FOUNDATION_DEPTH) return 0;
        if (-dy <= occupied.getYSpan()) {
            throw new AssertionError("The structure " + stage.id() + " overlaps its own new place");
        }
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        // Removing a container without this flag drops its contents (and rolls an unopened loot
        // table) at the old place.
        int removal = flags | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
        for (int y = ground + 1; y < occupied.minY(); y++) {
            for (int bx = start.minX() - 4; bx <= start.maxX() + 4; bx++) {
                for (int bz = start.minZ() - 4; bz <= start.maxZ() + 4; bz++) {
                    if (level.getBlockState(cursor.set(bx, y, bz)).isAir()) continue;
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), removal);
                }
            }
        }
        List<Entity> entities =
                level.getEntitiesOfClass(
                        Entity.class,
                        AABB.of(occupied).inflate(1.0D),
                        entity -> !(entity instanceof Player));
        List<ResourceKey<?>> lootBefore = new ArrayList<>();
        List<ResourceKey<?>> lootAfter = new ArrayList<>();
        BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
        for (int y = occupied.minY(); y <= occupied.maxY(); y++) {
            for (int bx = occupied.minX(); bx <= occupied.maxX(); bx++) {
                for (int bz = occupied.minZ(); bz <= occupied.maxZ(); bz++) {
                    BlockState state = level.getBlockState(cursor.set(bx, y, bz));
                    if (state.isAir()) continue;
                    BlockEntity entity = level.getBlockEntity(cursor);
                    if (entity instanceof RandomizableContainerBlockEntity container
                            && container.getLootTable() != null)
                        lootBefore.add(container.getLootTable());
                    CompoundTag data =
                            entity == null
                                    ? null
                                    : entity.saveWithoutMetadata(level.registryAccess());
                    level.setBlock(target.set(bx, y + dy, bz), state, flags);
                    if (data != null) {
                        BlockEntity moved = level.getBlockEntity(target);
                        if (moved != null) {
                            moved.loadWithComponents(
                                    TagValueInput.create(
                                            ProblemReporter.DISCARDING,
                                            level.registryAccess(),
                                            data));
                            moved.setChanged();
                            if (moved instanceof RandomizableContainerBlockEntity container
                                    && container.getLootTable() != null)
                                lootAfter.add(container.getLootTable());
                        }
                    }
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), removal);
                }
            }
        }
        for (Entity entity : entities) {
            entity.teleportTo(entity.getX(), entity.getY() + dy, entity.getZ());
        }
        System.out.println(
                "[DSV] "
                        + stage.id()
                        + " moved "
                        + dy
                        + " blocks down onto the ground with "
                        + lootAfter.size()
                        + " of "
                        + lootBefore.size()
                        + " unopened loot containers and "
                        + entities.size()
                        + " entities");
        if (!lootAfter.equals(lootBefore))
            throw new AssertionError(
                    stage.id() + " lost loot while moving: " + lootBefore + " became " + lootAfter);
        return dy;
    }

    /** The blocks of a structure in the flat Overworld, without the flat world's own layers. */
    private static BoundingBox flatOccupied(ServerLevel level, BoundingBox start) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int x = start.minX() - 4; x <= start.maxX() + 4; x++) {
            for (int z = start.minZ() - 4; z <= start.maxZ() + 4; z++) {
                for (int y = start.minY(); y <= start.maxY() + 8; y++) {
                    BlockState state = level.getBlockState(cursor.set(x, y, z));
                    if (state.isAir()
                            || state.is(Blocks.GRASS_BLOCK)
                            || state.is(Blocks.DIRT)
                            || state.is(Blocks.STONE)
                            || state.is(Blocks.BEDROCK)) continue;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    maxZ = Math.max(maxZ, z);
                }
            }
        }
        if (minX > maxX)
            throw new AssertionError("Expected the structure to occupy blocks near " + start);
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * The blocks the structure really occupies around its pieces' boxes, above the ground (the flat
     * Overworld's layers, the Nether's bedrock roof). Pieces that follow the terrain are placed on
     * the real surface, which may lie above their box, so the scan reaches 64 blocks above the
     * ground at least.
     */
    private static BoundingBox occupiedBounds(ServerLevel level, BoundingBox pieces, int groundY) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int lowest = groundY >= level.getMinY() ? groundY + 1 : pieces.minY();
        int highest = Math.min(level.getMaxY(), Math.max(pieces.maxY() + 8, lowest + 64));
        for (int x = pieces.minX() - 4; x <= pieces.maxX() + 4; x++) {
            for (int z = pieces.minZ() - 4; z <= pieces.maxZ() + 4; z++) {
                for (int y = lowest; y <= highest; y++) {
                    if (level.getBlockState(cursor.set(x, y, z)).isAir()) continue;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    maxZ = Math.max(maxZ, z);
                }
            }
        }
        if (minX > maxX)
            throw new AssertionError("Expected the structure to occupy blocks near " + pieces);
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Removes the stray pieces of the structure: groups of fewer than {@link #MIN_PIECE_BLOCKS}
     * blocks that touch nothing else, which hang in the air beside the structure like a lone block
     * over the horizon.
     */
    private static int removeStrays(ServerLevel level, Stage stage, BoundingBox start) {
        boolean nether = stage.dimension() == Dimension.NETHER;
        int lowest = nether ? 128 : start.minY();
        int highest = start.maxY() + 8;
        int x0 = start.minX() - 4;
        int z0 = start.minZ() - 4;
        int width = start.maxX() + 4 - x0 + 1;
        int depth = start.maxZ() + 4 - z0 + 1;
        int height = highest - lowest + 1;
        BitSet solid = new BitSet(width * height * depth);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = lowest; y <= highest; y++) {
            for (int z = 0; z < depth; z++) {
                for (int x = 0; x < width; x++) {
                    BlockState state = level.getBlockState(cursor.set(x0 + x, y, z0 + z));
                    if (state.isAir()) continue;
                    if (!nether
                            && (state.is(Blocks.GRASS_BLOCK)
                                    || state.is(Blocks.DIRT)
                                    || state.is(Blocks.STONE)
                                    || state.is(Blocks.BEDROCK))) {
                        continue;
                    }
                    solid.set((y - lowest) * width * depth + z * width + x);
                }
            }
        }
        int[] stack = new int[Math.max(1, solid.cardinality())];
        int[] piece = new int[stack.length];
        int removed = 0;
        BitSet seen = new BitSet(solid.size());
        for (int start0 = solid.nextSetBit(0); start0 >= 0; start0 = solid.nextSetBit(start0 + 1)) {
            if (seen.get(start0)) continue;
            int top = 0;
            int size = 0;
            stack[top++] = start0;
            seen.set(start0);
            while (top > 0) {
                int index = stack[--top];
                piece[size++] = index;
                int y = index / (width * depth);
                int z = (index / width) % depth;
                int x = index % width;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx;
                            int ny = y + dy;
                            int nz = z + dz;
                            if (nx < 0
                                    || ny < 0
                                    || nz < 0
                                    || nx >= width
                                    || ny >= height
                                    || nz >= depth) continue;
                            int next = ny * width * depth + nz * width + nx;
                            if (!solid.get(next) || seen.get(next)) continue;
                            seen.set(next);
                            stack[top++] = next;
                        }
                    }
                }
            }
            if (size < MIN_PIECE_BLOCKS) {
                for (int i = 0; i < size; i++) {
                    int index = piece[i];
                    level.setBlock(
                            cursor.set(
                                    x0 + index % width,
                                    lowest + index / (width * depth),
                                    z0 + (index / width) % depth),
                            Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
                }
                removed += size;
            }
        }
        return removed;
    }

    /** Waits until the stage dimension's light engine has been idle for a while. */
    private static void waitForServerLight(
            ClientGameTestContext context, TestDedicatedServerContext server, Stage stage) {
        int quiet = 0;
        for (int tick = 0; tick < LOAD_TIMEOUT_TICKS; tick++) {
            boolean busy =
                    server.computeOnServer(
                            minecraftServer -> {
                                ServerLevel level =
                                        minecraftServer.getLevel(stage.dimension().level);
                                return level != null && level.getLightEngine().hasLightWork();
                            });
            quiet = busy ? 0 : quiet + 1;
            if (quiet >= 40) return;
            context.waitTick();
        }
        throw new AssertionError("The server's light engine did not go idle");
    }

    /** The top block of the ground at a corner of the padded region, which holds no structure. */
    private static int groundHeight(ServerLevel level, ChunkRange padded) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int x = SectionPos.sectionToBlockCoord(padded.minX());
        int z = SectionPos.sectionToBlockCoord(padded.minZ());
        for (int y = level.getMaxY(); y > level.getMinY(); y--) {
            if (!level.getBlockState(cursor.set(x, y, z)).isAir()) return y;
        }
        throw new AssertionError("Expected ground at " + x + ", " + z);
    }

    /** Every column of the structure's chunks and the chunk of padding has ground, never void. */
    private static void assertGround(
            ServerLevel level, Stage stage, ChunkRange padded, int groundY) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = SectionPos.sectionToBlockCoord(padded.minX());
                x < SectionPos.sectionToBlockCoord(padded.maxX() + 1);
                x++) {
            for (int z = SectionPos.sectionToBlockCoord(padded.minZ());
                    z < SectionPos.sectionToBlockCoord(padded.maxZ() + 1);
                    z++) {
                BlockState state = level.getBlockState(cursor.set(x, groundY - 1, z));
                if (state.isAir()) {
                    throw new AssertionError(
                            "No ground under "
                                    + stage.id()
                                    + " at "
                                    + x
                                    + ", "
                                    + (groundY - 1)
                                    + ", "
                                    + z);
                }
            }
        }
    }

    private static void release(TestDedicatedServerContext server, Stage stage, Prepared prepared) {
        server.runOnServer(
                minecraftServer -> {
                    ServerLevel level = minecraftServer.getLevel(stage.dimension().level);
                    for (ChunkPos chunk : prepared.forced()) {
                        level.setChunkForced(chunk.x(), chunk.z(), false);
                    }
                    // The next stage opens small again, or the player's arrival there would
                    // generate the whole of the last stage's window at once.
                    minecraftServer.getPlayerList().setViewDistance(START_DISTANCE);
                });
    }

    // ------------------------------------------------------------------------------------------
    // The camera
    // ------------------------------------------------------------------------------------------

    /**
     * The circle around the structure's centre: the smallest radius, from far enough out that the
     * whole structure is in the frame, from which the camera keeps {@link #REQUIRED_CLEARANCE} from
     * every block for the whole shot (the ground aside, which only bounds the descent). The camera
     * stays on that radius, looking 30 degrees down at the centre at the start and 30 degrees up at
     * the end if the ground allows it; otherwise the descent stops {@link #FLOOR_ABOVE_GROUND}
     * blocks above the ground. The End's islands are not flat: there the floor is the highest block
     * (chorus plants included) on the circle and within the clearance of it, with the clearance on
     * top.
     */
    private static ShowcaseCamera.Orbit planOrbit(
            ServerLevel level, Stage stage, BoundingBox box, int groundY) {
        double centreX = (box.minX() + box.maxX() + 1) / 2.0D;
        double centreZ = (box.minZ() + box.maxZ() + 1) / 2.0D;
        double centreY = (box.minY() + box.maxY() + 1) / 2.0D;
        double ceiling = level.getMaxY() - 2.0D;
        Solids solids = new Solids(level, stage.hasGround() ? groundY : Integer.MIN_VALUE);
        double base = startRadius(box);
        int[] terrain =
                stage.hasGround()
                        ? null
                        : terrainByRadius(
                                level,
                                box,
                                centreX,
                                centreZ,
                                (int) Math.ceil(base + ORBIT_ROUNDS * 6.0D) + 8);
        for (int round = 0; round < ORBIT_ROUNDS; round++) {
            double radius = base + round * 6.0D;
            double floor =
                    terrain == null
                            ? groundY + 1 + FLOOR_ABOVE_GROUND
                            : Math.max(
                                    level.getMinY() + FLOOR_ABOVE_GROUND,
                                    ringTop(terrain, radius) + REQUIRED_CLEARANCE + 0.5D);
            ShowcaseCamera.Orbit orbit =
                    new ShowcaseCamera.Orbit(
                            centreX, centreY, centreZ, radius, floor, ceiling, 0.0D);
            if (clear(orbit, solids)) return orbit;
        }
        throw new AssertionError("Could not keep the camera clear of " + stage.id() + " " + box);
    }

    /** Far enough out that the whole structure is in the frame at the start. */
    private static double startRadius(BoundingBox box) {
        double half = Math.max(box.getXSpan(), box.getZSpan()) / 2.0D;
        // The top of a tall structure, seen from 30 degrees above its centre, has to stay inside
        // the vertical field of view (35 degrees either side at the default 70; 33 to spare).
        double up = box.getYSpan() / 2.0D;
        double tall =
                (0.5D * up + Math.sin(Math.toRadians(60.0D)) * up / Math.tan(Math.toRadians(33.0D)))
                        * Math.cos(Math.toRadians(30.0D));
        return Math.max(16.0D, Math.max(Math.max(half + 8.0D, tall), 1.25D * Math.hypot(half, up)));
    }

    /**
     * The top of the blocks outside the structure's footprint, by whole distance from the centre
     * out to {@code reach}: the world's floor where there is only void.
     */
    private static int[] terrainByRadius(
            ServerLevel level, BoundingBox box, double centreX, double centreZ, int reach) {
        int[] tops = new int[reach + 1];
        java.util.Arrays.fill(tops, level.getMinY());
        int fromX = (int) Math.floor(centreX - reach);
        int fromZ = (int) Math.floor(centreZ - reach);
        for (int x = fromX; x <= centreX + reach; x++) {
            for (int z = fromZ; z <= centreZ + reach; z++) {
                if (x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ())
                    continue;
                int distance = (int) Math.round(Math.hypot(x + 0.5D - centreX, z + 0.5D - centreZ));
                if (distance > reach) continue;
                if (!level.hasChunk(
                        SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)))
                    throw new AssertionError("The terrain at " + x + ", " + z + " is not loaded");
                int top =
                        level.getHeight(
                                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                                x,
                                z);
                tops[distance] = Math.max(tops[distance], top);
            }
        }
        return tops;
    }

    /** The highest top within the clearance of a circle of this radius. */
    private static int ringTop(int[] tops, double radius) {
        int band = (int) Math.ceil(REQUIRED_CLEARANCE) + 1;
        int middle = (int) Math.round(radius);
        int highest = Integer.MIN_VALUE;
        for (int r = Math.max(0, middle - band);
                r <= Math.min(tops.length - 1, middle + band);
                r++) {
            highest = Math.max(highest, tops[r]);
        }
        return highest;
    }

    /** Whether every point of the circle, half a block apart, keeps its distance from blocks. */
    private static boolean clear(ShowcaseCamera.Orbit orbit, Solids solids) {
        double length = 2.0D * Math.PI * ShowcaseCamera.REVOLUTIONS * orbit.radius();
        int samples = Math.max(800, (int) Math.ceil(length / 0.5D));
        // From the end: the low part of the circle is where it fails.
        for (int i = samples; i >= 0; i--) {
            ShowcaseCamera.Pose pose = orbit.at(i / (double) samples);
            if (solids.near(pose.x(), pose.y(), pose.z(), REQUIRED_CLEARANCE)) return false;
        }
        return true;
    }

    /** Which blocks the camera has to keep away from, read lazily in 4 by 4 by 4 cells. */
    private static final class Solids {
        private final ServerLevel level;
        private final int groundTop;
        private final Map<Long, Long> cells = new HashMap<>();

        Solids(ServerLevel level, int groundTop) {
            this.level = level;
            this.groundTop = groundTop;
        }

        private long cell(int cellX, int cellY, int cellZ) {
            long key = BlockPos.asLong(cellX, cellY, cellZ);
            Long known = cells.get(key);
            if (known != null) return known;
            long mask = 0L;
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int i = 0; i < 64; i++) {
                int y = cellY * 4 + (i >> 4);
                if (y <= groundTop || y < level.getMinY() || y > level.getMaxY()) continue;
                BlockState state =
                        level.getBlockState(
                                cursor.set(cellX * 4 + (i & 3), y, cellZ * 4 + ((i >> 2) & 3)));
                if (state.isAir() || state.is(Blocks.LIGHT) || state.is(Blocks.STRUCTURE_VOID))
                    continue;
                mask |= 1L << i;
            }
            cells.put(key, mask);
            return mask;
        }

        boolean near(double x, double y, double z, double radius) {
            int fromX = Math.floorDiv((int) Math.floor(x - radius), 4);
            int toX = Math.floorDiv((int) Math.floor(x + radius), 4);
            int fromY = Math.floorDiv((int) Math.floor(y - radius), 4);
            int toY = Math.floorDiv((int) Math.floor(y + radius), 4);
            int fromZ = Math.floorDiv((int) Math.floor(z - radius), 4);
            int toZ = Math.floorDiv((int) Math.floor(z + radius), 4);
            double limit = radius * radius;
            for (int cellX = fromX; cellX <= toX; cellX++) {
                for (int cellY = fromY; cellY <= toY; cellY++) {
                    for (int cellZ = fromZ; cellZ <= toZ; cellZ++) {
                        long mask = cell(cellX, cellY, cellZ);
                        while (mask != 0L) {
                            int i = Long.numberOfTrailingZeros(mask);
                            mask &= mask - 1L;
                            double bx = cellX * 4 + (i & 3);
                            double by = cellY * 4 + (i >> 4);
                            double bz = cellZ * 4 + ((i >> 2) & 3);
                            double dx = Math.max(0.0D, Math.max(bx - x, x - bx - 1.0D));
                            double dy = Math.max(0.0D, Math.max(by - y, y - by - 1.0D));
                            double dz = Math.max(0.0D, Math.max(bz - z, z - bz - 1.0D));
                            if (dx * dx + dy * dy + dz * dz < limit) return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Filming
    // ------------------------------------------------------------------------------------------

    private static void filmStage(
            ClientGameTestContext context,
            TestDedicatedServerContext server,
            Stage stage,
            Prepared prepared) {
        ShowcaseCamera.Orbit orbit = prepared.orbit();
        context.runOnClient(
                client -> {
                    ClientGameTestRecordingHud.clear();
                    ShowcaseCamera.hold(orbit, 0.0D);
                });
        server.runOnServer(
                minecraftServer ->
                        minecraftServer.getPlayerList().setSimulationDistance(SIMULATION_DISTANCE));
        context.waitForScreen(null);
        // The server's view distance grows in steps, each complete on the client before the
        // next. The client asks for that distance but renders only renderDistance around the
        // camera, so it keeps chunks the server sent beyond what the camera sees.
        int distance = Math.min(START_DISTANCE, prepared.viewDistance());
        while (true) {
            int step = distance;
            context.runOnClient(
                    client -> {
                        client.options.renderDistance().set(step);
                        client.options.broadcastOptions();
                        client.options
                                .renderDistance()
                                .set(Math.min(step, prepared.renderDistance()));
                    });
            server.runOnServer(
                    minecraftServer -> minecraftServer.getPlayerList().setViewDistance(step));
            waitForWindow(context, stage, prepared.watch(), step);
            if (distance >= prepared.viewDistance()) break;
            distance = Math.min(distance + DISTANCE_STEP, prepared.viewDistance());
        }
        waitForRegionLoaded(context, stage, prepared);

        // Rehearsal: the same shot, off camera, until a whole pass builds nothing new.
        int steps = ShowcaseCamera.SHOT_TICKS * REHEARSAL_STEPS_PER_TICK;
        BitSet baseline = settle(context, stage, prepared);
        double framesPerSecond = 0.0D;
        for (int pass = 1; pass <= MAX_REHEARSALS; pass++) {
            long began = System.nanoTime();
            for (int step = 0; step <= steps; step++) {
                double progress = step / (double) steps;
                context.runOnClient(client -> ShowcaseCamera.hold(orbit, progress));
                context.waitTick();
            }
            // Each step is one client tick, and a client GameTest renders a frame per tick at
            // most, so this is the frame rate along the shot (at most 20).
            framesPerSecond = (steps + 1) / ((System.nanoTime() - began) / 1.0E9D);
            context.runOnClient(client -> ShowcaseCamera.hold(orbit, 0.0D));
            waitForRegionLoaded(context, stage, prepared);
            BitSet after = settle(context, stage, prepared);
            boolean converged = after.equals(baseline);
            System.out.println(
                    "[DSV] "
                            + stage.id()
                            + " rehearsal "
                            + pass
                            + ": "
                            + after.cardinality()
                            + " sections built, "
                            + (converged ? "nothing new" : "more than before")
                            + String.format(
                                    Locale.ROOT, ", %.1f frames a second", framesPerSecond));
            baseline = after;
            if (converged) break;
        }
        // Two frames a tick at least, so the 80 ticks get the 120 frames 4 seconds at 30 frames a
        // second need once the cut plays them back at 20 ticks a second.
        float tickRate =
                (float)
                        Math.max(
                                MIN_SHOT_TICK_RATE,
                                Math.min(MAX_SHOT_TICK_RATE, framesPerSecond / FRAMES_PER_TICK));
        System.out.println("[DSV] " + stage.id() + " films at " + tickRate + " ticks a second");
        if (baseline.isEmpty())
            throw new AssertionError("Expected sections to be built for " + stage.id());

        if (stage.hasGround()) {
            String hole = context.computeOnClient(client -> missingGround(client, prepared));
            if (hole != null)
                PROBLEMS.add("No ground under " + stage.id() + " on the client at " + hole);
        }
        // A chest removed with its loot spills (A-0203): no item may lie where the camera can see,
        // above the ground. What mobs dropped as they died while the stage was populated is removed
        // first, so no item lies in the shot, and logged. Items a mob threw (villagers share food)
        // are the game's own and only logged; plants stranded by the placement drop nothing, as
        // block_drops is off.
        String[] items =
                server.computeOnServer(
                        minecraftServer -> {
                            ServerLevel level = minecraftServer.getLevel(stage.dimension().level);
                            AABB area = stageArea(level, prepared);
                            if (stage.hasGround()) area = area.setMinY(prepared.groundY());
                            List<ItemEntity> drops =
                                    level.getEntitiesOfClass(
                                            ItemEntity.class,
                                            stageArea(level, prepared),
                                            item -> MOB_DROPS.contains(item.getUUID()));
                            Map<String, Integer> removed = new TreeMap<>();
                            for (ItemEntity drop : drops) {
                                removed.merge(
                                        BuiltInRegistries.ITEM
                                                .getKey(drop.getItem().getItem())
                                                .toString(),
                                        drop.getItem().getCount(),
                                        Integer::sum);
                                drop.discard();
                            }
                            List<ItemEntity> all = level.getEntitiesOfClass(ItemEntity.class, area);
                            List<ItemEntity> loose =
                                    all.stream().filter(item -> item.getOwner() == null).toList();
                            String thrown = (all.size() - loose.size()) + " thrown by mobs";
                            return new String[] {
                                loose.isEmpty()
                                        ? null
                                        : loose.size()
                                                + " items, e.g. "
                                                + loose.getFirst().getItem()
                                                + " at "
                                                + loose.getFirst().blockPosition(),
                                thrown,
                                drops.size() + " dropped by dying mobs removed " + removed
                            };
                        });
        System.out.println(
                "[DSV] "
                        + stage.id()
                        + " items before the shot: "
                        + items[2]
                        + ", then "
                        + (items[0] == null ? "none loose" : items[0])
                        + ", "
                        + items[1]);
        if (items[0] != null)
            PROBLEMS.add("Loose items at " + stage.id() + " before its shot: " + items[0]);
        String before =
                server.computeOnServer(
                        minecraftServer ->
                                mobs(minecraftServer.getLevel(stage.dimension().level), prepared));

        ClientGameTestRecorder.showStep(
                context,
                "dimensionstructurevariants." + stage.id(),
                stage.title(),
                stage.subtitle());
        // The caption and the still camera are on screen before the shot starts, so the cut never
        // falls on a frame without the caption.
        context.waitTicks(6);
        if (stage.dimension() == Dimension.NETHER) assertNetherFog(context);

        // Noon to midnight over the shot: the clock runs at that rate and the client follows it
        // between the server's updates. A clock change tells the clients the rate, which is 0
        // while advance_time is off, so the game rule goes first.
        server.runOnServer(
                minecraftServer -> {
                    run(minecraftServer, "gamerule advance_time true");
                    setDayTime(minecraftServer, NOON);
                    Holder<WorldClock> clock = overworldClock(minecraftServer);
                    minecraftServer
                            .clockManager()
                            .setRate(clock, (float) (MIDNIGHT - NOON) / ShowcaseCamera.SHOT_TICKS);
                    minecraftServer.tickRateManager().setTickRate(tickRate);
                    minecraftServer.tickRateManager().setFrozen(false);
                });
        context.runOnClient(client -> ShowcaseCamera.run(orbit));
        long startMs = System.currentTimeMillis();
        System.out.println("STAGE_START " + startMs + " " + stage.id());
        int busyTicks = 0;
        int poppedTicks = 0;
        StringBuilder problems = new StringBuilder();
        for (int tick = 1; tick <= ShowcaseCamera.SHOT_TICKS; tick++) {
            context.waitTick();
            if (tick % CHECK_INTERVAL != 0) continue;
            ShowcaseCamera.Pose pose = orbit.at(tick / (double) ShowcaseCamera.SHOT_TICKS);
            Snapshot now =
                    context.computeOnClient(
                            client ->
                                    snapshot(
                                            client, prepared.region(), pose, stage.visibleRange()));
            if (now == null)
                throw new AssertionError(
                        "A chunk of " + stage.id() + " left the client during the shot");
            BitSet built = (BitSet) now.compiled().clone();
            built.andNot(baseline);
            built.and(now.visible());
            BitSet lost = (BitSet) baseline.clone();
            lost.andNot(now.compiled());
            lost.and(now.visible());
            if (!built.isEmpty() || !lost.isEmpty()) {
                poppedTicks++;
                if (problems.length() < 1500) {
                    problems.append("\n  tick ")
                            .append(tick)
                            .append(": ")
                            .append(built.cardinality())
                            .append(" built, ")
                            .append(lost.cardinality())
                            .append(" lost, e.g. ")
                            .append(
                                    describe(
                                            prepared.region(),
                                            built.isEmpty()
                                                    ? lost.nextSetBit(0)
                                                    : built.nextSetBit(0)));
                }
            }
            if (!context.computeOnClient(client -> client.levelRenderer.hasRenderedAllSections()))
                busyTicks++;
        }
        long endMs = System.currentTimeMillis();
        System.out.println("STAGE_END " + endMs + " " + stage.id());
        String after =
                server.computeOnServer(
                        minecraftServer -> {
                            run(minecraftServer, "gamerule advance_time false");
                            Holder<WorldClock> clock = overworldClock(minecraftServer);
                            minecraftServer.clockManager().setRate(clock, 1.0F);
                            minecraftServer.tickRateManager().setFrozen(true);
                            minecraftServer.tickRateManager().setTickRate(20.0F);
                            long total =
                                    minecraftServer.clockManager().getInstance(clock).totalTicks();
                            return mobs(minecraftServer.getLevel(stage.dimension().level), prepared)
                                    + " at day time "
                                    + Math.floorMod(total, DAY);
                        });
        // The camera stays where the shot ended for a moment, so the cut never falls on the next
        // stage's setup.
        context.waitTicks(6);
        System.out.println(
                "[DSV] "
                        + stage.id()
                        + " shot finished: sections="
                        + baseline.cardinality()
                        + " ticksWithMeshWork="
                        + busyTicks
                        + " ticksWithPopIn="
                        + poppedTicks
                        + problems);
        System.out.println(
                "[DSV] SUMMARY "
                        + String.format(
                                Locale.ROOT,
                                "%s | %s | %s | start %d end %d | radius %.1f | height %.1f"
                                        + " -> %.1f | final up angle %.1f | mobs at start %s | mobs"
                                        + " at end %s",
                                stage.id(),
                                stage.title(),
                                stage.subtitle(),
                                startMs,
                                endMs,
                                orbit.radius(),
                                orbit.height(0.0D),
                                orbit.height(1.0D),
                                orbit.finalUpAngle(),
                                before,
                                after));
        if (poppedTicks > 0) {
            PROBLEMS.add(
                    "Sections of "
                            + stage.id()
                            + " popped in or out on camera during "
                            + poppedTicks
                            + " ticks:"
                            + problems);
        }
    }

    /** The structure and the ground around it, full height. */
    private static AABB stageArea(ServerLevel level, Prepared prepared) {
        ChunkRange padded = prepared.padded();
        return new AABB(
                SectionPos.sectionToBlockCoord(padded.minX()),
                level.getMinY(),
                SectionPos.sectionToBlockCoord(padded.minZ()),
                SectionPos.sectionToBlockCoord(padded.maxX() + 1),
                level.getMaxY() + 1,
                SectionPos.sectionToBlockCoord(padded.maxZ() + 1));
    }

    /** The living mobs at the stage by type, with bastion mobs that are converting. */
    private static String mobs(ServerLevel level, Prepared prepared) {
        Map<String, Integer> counts = new TreeMap<>();
        int converting = 0;
        int sleeping = 0;
        int villagers = 0;
        for (LivingEntity entity :
                level.getEntitiesOfClass(
                        LivingEntity.class,
                        stageArea(level, prepared),
                        entity -> !(entity instanceof Player) && entity.isAlive())) {
            counts.merge(
                    BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath(),
                    1,
                    Integer::sum);
            if ((entity instanceof AbstractPiglin piglin && piglin.isConverting())
                    || (entity instanceof Hoglin hoglin && hoglin.isConverting())) converting++;
            if (entity instanceof Villager villager) {
                villagers++;
                if (villager.isSleeping()) sleeping++;
            }
        }
        StringBuilder text = new StringBuilder(counts.toString());
        if (converting > 0) text.append(" converting=").append(converting);
        if (villagers > 0) text.append(" villagersAsleep=").append(sleeping);
        return text.toString();
    }

    /** The Nether fog the camera sees must be the test mod's, not vanilla's 96 blocks. */
    private static void assertNetherFog(ClientGameTestContext context) {
        float[] fog =
                context.computeOnClient(
                        client -> {
                            var probe = client.gameRenderer.mainCamera().attributeProbe();
                            return new float[] {
                                probe.getValue(EnvironmentAttributes.FOG_START_DISTANCE, 1.0F),
                                probe.getValue(EnvironmentAttributes.FOG_END_DISTANCE, 1.0F)
                            };
                        });
        System.out.println("[DSV] Nether fog: start=" + fog[0] + " end=" + fog[1]);
        if (fog[1] < FOG_END_NETHER) {
            throw new AssertionError(
                    "The Nether fog ends at " + fog[1] + " blocks, expected " + FOG_END_NETHER);
        }
    }

    /** The section a bit of a section set stands for, for the log. */
    private static String describe(Region region, int index) {
        int sections = region.highestSection() - region.lowestSection() + 1;
        int depth = region.chunks().depth();
        int chunkX = region.chunks().minX() + index / (sections * depth);
        int chunkZ = region.chunks().minZ() + (index / sections) % depth;
        int section = region.lowestSection() + index % sections;
        return "section " + chunkX + ", " + section + ", " + chunkZ;
    }

    // ------------------------------------------------------------------------------------------
    // What the client has
    // ------------------------------------------------------------------------------------------

    /** Waits until the player is in the stage's dimension with every chunk of the region. */
    private static void waitForRegionLoaded(
            ClientGameTestContext context, Stage stage, Prepared prepared) {
        context.waitFor(
                client ->
                        client.level != null
                                && client.level.dimension().equals(stage.dimension().level)
                                && chunksLoaded(client, prepared.region())
                                && missingInWindow(
                                                client, prepared.watch(), prepared.viewDistance())
                                        == null,
                LOAD_TIMEOUT_TICKS);
    }

    /**
     * The first chunk around the player out to {@code radius} the client lacks: the server sends
     * them in a circle, so a chunk counts when its distance is two chunks inside the radius.
     */
    private static String missingInWindow(Minecraft client, BlockPos centre, int radius) {
        int centreX = SectionPos.blockToSectionCoord(centre.getX());
        int centreZ = SectionPos.blockToSectionCoord(centre.getZ());
        int inner = Math.max(1, radius - 2);
        for (int dx = -inner; dx <= inner; dx++) {
            for (int dz = -inner; dz <= inner; dz++) {
                if (dx * dx + dz * dz > inner * inner) continue;
                if (!chunkLoaded(client, centreX + dx, centreZ + dz))
                    return (centreX + dx) + ", " + (centreZ + dz);
            }
        }
        return null;
    }

    /** Waits for the window and fails with what is missing instead of a bare timeout. */
    private static void waitForWindow(
            ClientGameTestContext context, Stage stage, BlockPos centre, int radius) {
        String missing = null;
        for (int tick = 0; tick < LOAD_TIMEOUT_TICKS; tick++) {
            missing =
                    context.computeOnClient(
                            client ->
                                    client.level == null
                                                    || !client.level
                                                            .dimension()
                                                            .equals(stage.dimension().level)
                                            ? "not in " + stage.dimension()
                                            : missingInWindow(client, centre, radius));
            if (missing == null) return;
            context.waitTick();
        }
        throw new AssertionError(
                "The client never received the chunks within "
                        + radius
                        + " of the player at "
                        + stage.id()
                        + "; first missing chunk "
                        + missing);
    }

    /** Whether the client holds the chunk's data. */
    private static boolean chunkLoaded(Minecraft client, int chunkX, int chunkZ) {
        return client.level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false)
                != null;
    }

    /** The first column of the padding whose ground block is missing on the client, or null. */
    private static String missingGround(Minecraft client, Prepared prepared) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        ChunkRange padded = prepared.padded();
        BoundingBox box = prepared.occupied();
        for (int x = SectionPos.sectionToBlockCoord(padded.minX());
                x < SectionPos.sectionToBlockCoord(padded.maxX() + 1);
                x++) {
            for (int z = SectionPos.sectionToBlockCoord(padded.minZ());
                    z < SectionPos.sectionToBlockCoord(padded.maxZ() + 1);
                    z++) {
                if (x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ())
                    continue;
                if (client.level.getBlockState(cursor.set(x, prepared.groundY(), z)).isAir()) {
                    return x + ", " + prepared.groundY() + ", " + z;
                }
            }
        }
        return null;
    }

    /**
     * Waits until the renderer has nothing queued and the set of built sections has stayed the same
     * for a while, so the next frame is a finished one. Returns that set.
     */
    private static BitSet settle(ClientGameTestContext context, Stage stage, Prepared prepared) {
        BitSet last = null;
        int stable = 0;
        ShowcaseCamera.Pose pose = prepared.orbit().at(0.0D);
        for (int tick = 0; tick < LOAD_TIMEOUT_TICKS; tick++) {
            boolean[] idle = new boolean[1];
            Snapshot now =
                    context.computeOnClient(
                            client -> {
                                idle[0] =
                                        client.levelRenderer.hasRenderedAllSections()
                                                && client.level != null
                                                && !client.level.getLightEngine().hasLightWork();
                                return snapshot(client, prepared.region(), pose, Double.MAX_VALUE);
                            });
            if (now != null && idle[0] && now.compiled().equals(last)) {
                stable++;
            } else {
                stable = 0;
            }
            last = now == null ? null : now.compiled();
            if (stable >= STABLE_TICKS) return last;
            context.waitTick();
        }
        throw new AssertionError("The client did not finish building the chunks of " + stage.id());
    }

    private static boolean chunksLoaded(Minecraft client, Region region) {
        if (client.level == null) return false;
        ChunkRange chunks = region.chunks();
        for (int chunkX = chunks.minX(); chunkX <= chunks.maxX(); chunkX++) {
            for (int chunkZ = chunks.minZ(); chunkZ <= chunks.maxZ(); chunkZ++) {
                if (!chunkLoaded(client, chunkX, chunkZ)) return false;
            }
        }
        return true;
    }

    /**
     * The sections of the region the client has built, and those within {@code range} blocks of the
     * camera (the ones a frame can show), one bit each; null while a chunk of the region is not on
     * the client.
     */
    private static Snapshot snapshot(
            Minecraft client, Region region, ShowcaseCamera.Pose camera, double range) {
        if (!chunksLoaded(client, region)) return null;
        ChunkRange chunks = region.chunks();
        BitSet compiled = new BitSet();
        BitSet visible = new BitSet();
        int index = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int chunkX = chunks.minX(); chunkX <= chunks.maxX(); chunkX++) {
            for (int chunkZ = chunks.minZ(); chunkZ <= chunks.maxZ(); chunkZ++) {
                for (int section = region.lowestSection();
                        section <= region.highestSection();
                        section++) {
                    cursor.set(
                            SectionPos.sectionToBlockCoord(chunkX),
                            SectionPos.sectionToBlockCoord(section),
                            SectionPos.sectionToBlockCoord(chunkZ));
                    if (client.levelRenderer.isSectionCompiledAndVisible(cursor, 0L))
                        compiled.set(index);
                    double dx = cursor.getX() + 8 - camera.x();
                    double dy = cursor.getY() + 8 - camera.y();
                    double dz = cursor.getZ() + 8 - camera.z();
                    // A section's centre is at most 14 blocks from its farthest corner.
                    if (range == Double.MAX_VALUE
                            || Math.sqrt(dx * dx + dy * dy + dz * dz) <= range + 14.0D) {
                        visible.set(index);
                    }
                    index++;
                }
            }
        }
        return new Snapshot(compiled, visible);
    }

    /**
     * The render distance, in chunks, at which every pose sees every chunk of the range inside the
     * loaded circle.
     */
    private static int requiredDistance(ChunkRange range, ShowcaseCamera.Pose[] path) {
        double farthest = 0.0D;
        for (ShowcaseCamera.Pose pose : path) {
            int cameraX = SectionPos.blockToSectionCoord(pose.x());
            int cameraZ = SectionPos.blockToSectionCoord(pose.z());
            for (int chunkX : new int[] {range.minX(), range.maxX()}) {
                for (int chunkZ : new int[] {range.minZ(), range.maxZ()}) {
                    farthest = Math.max(farthest, Math.hypot(chunkX - cameraX, chunkZ - cameraZ));
                }
            }
        }
        return (int) Math.ceil(farthest) + 2;
    }

    private static ChunkRange chunkRange(BoundingBox box, int padding) {
        return new ChunkRange(
                SectionPos.blockToSectionCoord(box.minX()) - padding,
                SectionPos.blockToSectionCoord(box.maxX()) + padding,
                SectionPos.blockToSectionCoord(box.minZ()) - padding,
                SectionPos.blockToSectionCoord(box.maxZ()) + padding);
    }

    /** Inclusive chunk coordinates. */
    private record ChunkRange(int minX, int maxX, int minZ, int maxZ) {
        int width() {
            return maxX - minX + 1;
        }

        int depth() {
            return maxZ - minZ + 1;
        }

        boolean contains(ChunkPos chunk) {
            return chunk.x() >= minX && chunk.x() <= maxX && chunk.z() >= minZ && chunk.z() <= maxZ;
        }
    }

    /** The chunks of a structure plus its padding, and the sections that hold it and its ground. */
    private record Region(ChunkRange chunks, int lowestSection, int highestSection) {}

    /** Built sections and the sections a frame from the camera can show. */
    private record Snapshot(BitSet compiled, BitSet visible) {}
}
