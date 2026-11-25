package io.github.brainage04.dimensionstructurevariants;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.brainage04.minecraftmodconventions.ClientGameTestServers;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Takes the pictures for comparing the share of a palette's rare block, one fortress at each of 1
 * to 5 per cent: the same start, the same random for the pieces' details, the same camera and the
 * same light. Runs only when {@value #KIND_ENV} names a sheet ({@code snowy} or {@code ocean}), and
 * then the film does not run (see {@link #requested}). Each share is applied by rewriting the rare
 * block's weight in a copy of the bundled table and handing it to {@code
 * StructurePalettes.useTable}, so {@code palettes.json} is never edited.
 *
 * <p>The rare block's positions are nested: every weight map totals 100 before and after, the rare
 * block comes last, and a position's roll does not change, so a position that is rare at 2 per cent
 * is rare at 3. The other blocks of a map are not held still: the difference comes off the map's
 * largest other block, so where a map has several (the ocean walls: prismarine bricks 50,
 * prismarine 49) up to {@code MAX_PERCENT - 1} per cent of its positions swap between the two from
 * the first panel to the last.
 *
 * <p>Needs the recording lane ({@code :fabric:recordClientGameTest}) for its display; the pictures
 * go to {@value #DIR_ENV} (default {@code build/percent-sheet}), named {@code
 * <kind>-overview-N.png} and {@code <kind>-closeup-N.png}.
 */
@SuppressWarnings("UnstableApiUsage")
public final class DimensionStructureVariantsPercentSheet implements FabricClientGameTest {
    static final String KIND_ENV = "DSV_PERCENT_SHEET";
    static final String DIR_ENV = "DSV_PERCENT_DIR";

    private static final int MIN_PERCENT = 1;
    private static final int MAX_PERCENT = 5;

    /** Every weight map of a palette totals this, so a share in per cent is its weight. */
    private static final int WEIGHT_TOTAL = 100;

    private static final long DETAIL_SEED = 20261125L;
    private static final int VIEW_DISTANCE = 12;
    private static final int START_SEARCH_RINGS = 12;
    private static final int START_SPACING = 8;
    private static final ChunkPos START_ORIGIN = new ChunkPos(1200, 1200);

    /** Client ticks the sections need to rebuild after a placement under software rendering. */
    private static final int SETTLE_TICKS = 160;

    private static final double OVERVIEW_DISTANCE = 52.0D;
    private static final double OVERVIEW_HEIGHT = 24.0D;
    private static final double CLOSEUP_DISTANCE = 11.0D;
    private static final int WALL_HALF = 3;

    /** Open blocks required in front of a wall section: the camera's whole line of sight. */
    private static final int WALL_DEPTH = 11;

    private static final Identifier FORTRESS_ENTRANCE = Identifier.withDefaultNamespace("nece");

    private record Sheet(ResourceKey<Biome> biome, String palette, Set<String> rare) {}

    private record Pose(double x, double y, double z, float yaw, float pitch) {}

    private record Poses(Pose overview, Pose closeup) {}

    private static StructureStart start;
    private static List<ChunkPos> chunks;
    private static JsonObject shipped;

    /** Whether this run takes percent sheets instead of the film. */
    static boolean requested() {
        return System.getenv(KIND_ENV) != null;
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        String kind = System.getenv(KIND_ENV);
        if (kind == null) return;
        Sheet sheet =
                switch (kind) {
                    case "snowy" ->
                            new Sheet(
                                    Biomes.SNOWY_PLAINS,
                                    "fortress_snowy",
                                    Set.of("minecraft:snow_block"));
                    case "ocean" ->
                            new Sheet(
                                    Biomes.OCEAN,
                                    "fortress_ocean",
                                    Set.of(
                                            "minecraft:dark_prismarine",
                                            "minecraft:dark_prismarine_stairs",
                                            "minecraft:dark_prismarine_slab"));
                    default ->
                            throw new IllegalArgumentException(
                                    KIND_ENV + " must be snowy or ocean, got " + kind);
                };
        Path out = Path.of(System.getenv().getOrDefault(DIR_ENV, "build/percent-sheet"));
        Properties properties = ClientGameTestServers.flatServerProperties();
        properties.setProperty("view-distance", Integer.toString(VIEW_DISTANCE));
        properties.setProperty("max-tick-time", "-1");
        ClientGameTestServers.withDedicatedServer(
                context,
                properties,
                "DimensionStructureVariants percent sheet",
                server -> {
                    server.runOnServer(DimensionStructureVariantsPercentSheet::setUp);
                    context.runOnClient(
                            client -> {
                                client.smartCull = false;
                                if (!client.gui.hud.isHidden()) client.gui.hud.toggle();
                                client.options.chunkSectionFadeInTime().set(0.0D);
                                freezeAnimatedTextures(client);
                            });
                    Poses poses =
                            server.computeOnServer(
                                    minecraftServer -> prepare(minecraftServer, sheet));
                    try {
                        for (int percent = MIN_PERCENT; percent <= MAX_PERCENT; percent++) {
                            int share = percent;
                            server.runOnServer(
                                    minecraftServer -> place(minecraftServer, sheet, share));
                            shoot(
                                    context,
                                    server,
                                    out,
                                    kind + "-overview-" + percent,
                                    poses.overview());
                            shoot(
                                    context,
                                    server,
                                    out,
                                    kind + "-closeup-" + percent,
                                    poses.closeup());
                        }
                    } finally {
                        server.runOnServer(minecraftServer -> StructurePalettes.useTable(null));
                    }
                });
    }

    /**
     * Stops every animated texture on the frame it shows now. Prismarine cycles through green and
     * blue-grey over 6600 ticks ({@code prismarine.png.mcmeta}), so panels shot a few seconds apart
     * would differ in colour whatever the share. {@code Minecraft.runTick} advances the animations
     * through {@code TextureManager.tick}, which walks {@code tickableTextures} ({@code
     * TextureManager.java:99-102}); emptying the set freezes them for the run.
     */
    private static void freezeAnimatedTextures(Minecraft client) {
        try {
            Field field = TextureManager.class.getDeclaredField("tickableTextures");
            field.setAccessible(true);
            ((Set<?>) field.get(client.getTextureManager())).clear();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not freeze the animated textures", exception);
        }
    }

    private static void shoot(
            ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext server,
            Path out,
            String name,
            Pose pose) {
        server.runOnServer(minecraftServer -> teleport(minecraftServer, pose));
        context.waitTicks(SETTLE_TICKS);
        context.takeScreenshot(
                TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(out));
    }

    private static void setUp(MinecraftServer server) {
        for (String command :
                List.of(
                        "gamerule advance_time false",
                        "gamerule advance_weather false",
                        "gamerule send_command_feedback false",
                        "weather clear",
                        "gamerule max_block_modifications 2147483647")) {
            run(server, command);
        }
        Holder<WorldClock> clock =
                server.registryAccess()
                        .lookupOrThrow(Registries.WORLD_CLOCK)
                        .getOrThrow(WorldClocks.OVERWORLD);
        server.clockManager().moveToTimeMarker(clock, ClockTimeMarkers.NOON);
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        player.setGameMode(GameType.SPECTATOR);
        player.addEffect(
                new MobEffectInstance(
                        MobEffects.NIGHT_VISION,
                        MobEffectInstance.INFINITE_DURATION,
                        0,
                        false,
                        false,
                        false));
    }

    /**
     * Generates the fortress, puts its biome under it, places it at the first share and picks the
     * two camera poses from what that placement built.
     */
    private static Poses prepare(MinecraftServer server, Sheet sheet) {
        ServerLevel level = server.overworld();
        Holder<Structure> structure =
                level.registryAccess()
                        .lookupOrThrow(Registries.STRUCTURE)
                        .getOrThrow(
                                ResourceKey.create(
                                        Registries.STRUCTURE,
                                        DimensionStructureVariants.of("fortress_overworld")));
        for (int ring = 0; ring < START_SEARCH_RINGS && start == null; ring++) {
            for (int dx = -ring; dx <= ring && start == null; dx++) {
                for (int dz = -ring; dz <= ring && start == null; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    StructureStart candidate =
                            generate(
                                    level,
                                    structure,
                                    new ChunkPos(
                                            START_ORIGIN.x() + dx * START_SPACING,
                                            START_ORIGIN.z() + dz * START_SPACING));
                    if (candidate.isValid()) start = candidate;
                }
            }
        }
        if (start == null) throw new AssertionError("No fortress_overworld start found");
        BoundingBox box = start.getBoundingBox();
        chunks = new ArrayList<>();
        ChunkPos.rangeClosed(
                        ChunkPos.containing(new BlockPos(box.minX(), 0, box.minZ())),
                        ChunkPos.containing(new BlockPos(box.maxX(), 0, box.maxZ())))
                .forEach(chunks::add);
        for (ChunkPos chunk : chunks) {
            level.setChunkForced(chunk.x(), chunk.z(), true);
            level.getChunk(chunk.x(), chunk.z());
        }
        run(
                server,
                "fillbiome "
                        + box.minX()
                        + " "
                        + box.minY()
                        + " "
                        + box.minZ()
                        + " "
                        + box.maxX()
                        + " "
                        + box.maxY()
                        + " "
                        + box.maxZ()
                        + " "
                        + sheet.biome().identifier());
        BlockPos centre = box.getCenter();
        if (!level.getBiome(centre).is(sheet.biome())) {
            throw new AssertionError(
                    "The fortress's biome is " + level.getBiome(centre) + ", not " + sheet.biome());
        }
        place(server, sheet, MIN_PERCENT);
        return poses(level, box);
    }

    /** Places the fortress with the rare block at {@code percent} of every role that has one. */
    private static void place(MinecraftServer server, Sheet sheet, int percent) {
        StructurePalettes.useTable(withShare(sheet, percent));
        ServerLevel level = server.overworld();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        for (ChunkPos chunk : chunks) {
            start.placeInChunk(
                    level,
                    level.structureManager(),
                    generator,
                    new WorldgenRandom(new LegacyRandomSource(DETAIL_SEED)),
                    new BoundingBox(
                            chunk.getMinBlockX(),
                            level.getMinY(),
                            chunk.getMinBlockZ(),
                            chunk.getMaxBlockX(),
                            level.getMaxY() + 1,
                            chunk.getMaxBlockZ()),
                    chunk);
        }
        System.out.println(
                "[DSV] percent sheet: " + sheet.palette() + " placed at " + percent + " per cent");
    }

    /**
     * A copy of the bundled table whose palette gives each rare block {@code percent} of the weight
     * in every choice of several blocks, taking the difference from the choice's largest other
     * block so that the choice still totals {@link #WEIGHT_TOTAL}.
     */
    private static JsonObject withShare(Sheet sheet, int percent) {
        JsonObject table = bundled().deepCopy();
        JsonObject palette = table.getAsJsonObject("palettes").getAsJsonObject(sheet.palette());
        int[] changed = {0};
        JsonObject blocks = palette.getAsJsonObject("blocks");
        for (Map.Entry<String, JsonElement> role : blocks.entrySet()) {
            changed[0] += share(role.getValue(), sheet.rare(), percent);
        }
        if (palette.has("piece_accent")) {
            JsonObject accent = palette.getAsJsonObject("piece_accent").getAsJsonObject("blocks");
            for (Map.Entry<String, JsonElement> role : accent.entrySet()) {
                changed[0] += share(role.getValue(), sheet.rare(), percent);
            }
        }
        if (changed[0] == 0) {
            throw new AssertionError(sheet.palette() + " holds none of " + sheet.rare());
        }
        return table;
    }

    /** Sets the rare entries of one choice to {@code percent}; returns how many it changed. */
    private static int share(JsonElement choice, Set<String> rare, int percent) {
        if (!choice.isJsonObject()) return 0;
        JsonObject weights = choice.getAsJsonObject();
        String largest = null;
        int changed = 0;
        for (Map.Entry<String, JsonElement> entry : weights.entrySet()) {
            if (rare.contains(entry.getKey())) continue;
            if (largest == null || entry.getValue().getAsInt() > weights.get(largest).getAsInt()) {
                largest = entry.getKey();
            }
        }
        if (largest == null) return 0;
        for (String block : rare) {
            if (!weights.has(block)) continue;
            int shipped = weights.get(block).getAsInt();
            weights.addProperty(block, percent);
            weights.addProperty(largest, weights.get(largest).getAsInt() - (percent - shipped));
            changed++;
        }
        int total = 0;
        for (Map.Entry<String, JsonElement> entry : weights.entrySet()) {
            total += entry.getValue().getAsInt();
        }
        if (changed > 0 && total != WEIGHT_TOTAL) {
            throw new AssertionError("A choice must total " + WEIGHT_TOTAL + ", got " + weights);
        }
        return changed;
    }

    private static JsonObject bundled() {
        if (shipped == null) {
            try (InputStream stream =
                            StructurePalettes.class.getResourceAsStream(
                                    "/" + DimensionStructureVariants.MOD_ID + "/palettes.json");
                    Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                shipped = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (IOException exception) {
                throw new IllegalStateException("Unreadable palette table", exception);
            }
        }
        return shipped;
    }

    /**
     * Two poses from the built fortress: the overview looks at its castle entrance (any piece if it
     * has none) from outside, toward the fortress's centre; the close-up stands in front of the
     * first wall section of that piece that is five blocks wide and high, solid, and open in front.
     */
    private static Poses poses(ServerLevel level, BoundingBox box) {
        StructurePiece target = null;
        for (StructurePiece piece : start.getPieces()) {
            if (FORTRESS_ENTRANCE.equals(
                    BuiltInRegistries.STRUCTURE_PIECE.getKey(piece.getType()))) {
                target = piece;
                break;
            }
        }
        if (target == null) throw new AssertionError("The fortress has no castle entrance");
        BlockPos pieceCentre = target.getBoundingBox().getCenter();
        BlockPos fortressCentre = box.getCenter();
        double dx = pieceCentre.getX() - fortressCentre.getX();
        double dz = pieceCentre.getZ() - fortressCentre.getZ();
        double length = Math.hypot(dx, dz);
        if (length < 1.0D) {
            dx = 0.0D;
            dz = 1.0D;
            length = 1.0D;
        }
        Pose overview =
                look(
                        pieceCentre.getX() + 0.5D + dx / length * OVERVIEW_DISTANCE,
                        pieceCentre.getY() + OVERVIEW_HEIGHT,
                        pieceCentre.getZ() + 0.5D + dz / length * OVERVIEW_DISTANCE,
                        pieceCentre.getX() + 0.5D,
                        pieceCentre.getY() + 0.5D,
                        pieceCentre.getZ() + 0.5D);
        BoundingBox pieceBox = target.getBoundingBox();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = pieceBox.minY() + WALL_HALF; y <= pieceBox.maxY() - WALL_HALF; y++) {
            for (int x = pieceBox.minX(); x <= pieceBox.maxX(); x++) {
                for (int z = pieceBox.minZ(); z <= pieceBox.maxZ(); z++) {
                    for (Direction face : Direction.Plane.HORIZONTAL) {
                        if (wall(level, cursor, x, y, z, face)) {
                            return new Poses(
                                    overview,
                                    look(
                                            x + 0.5D + face.getStepX() * CLOSEUP_DISTANCE,
                                            y + 0.5D,
                                            z + 0.5D + face.getStepZ() * CLOSEUP_DISTANCE,
                                            x + 0.5D,
                                            y + 0.5D,
                                            z + 0.5D));
                        }
                    }
                }
            }
        }
        throw new AssertionError("No open wall section in the castle entrance " + pieceBox);
    }

    /**
     * A solid 5 by 5 square around (x, y, z) in the plane facing {@code face}, with open air in
     * front.
     */
    private static boolean wall(
            ServerLevel level,
            BlockPos.MutableBlockPos cursor,
            int x,
            int y,
            int z,
            Direction face) {
        Direction side = face.getClockWise();
        for (int along = -WALL_HALF; along <= WALL_HALF; along++) {
            for (int up = -WALL_HALF; up <= WALL_HALF; up++) {
                int wx = x + side.getStepX() * along;
                int wz = z + side.getStepZ() * along;
                if (!level.getBlockState(cursor.set(wx, y + up, wz)).isSolidRender()) return false;
                for (int out = 1; out <= WALL_DEPTH; out++) {
                    if (!level.getBlockState(
                                    cursor.set(
                                            wx + face.getStepX() * out,
                                            y + up,
                                            wz + face.getStepZ() * out))
                            .isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static Pose look(double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x;
        double dy = ty - y;
        double dz = tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        return new Pose(x, y, z, yaw, pitch);
    }

    private static void teleport(MinecraftServer server, Pose pose) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        player.teleportTo(
                server.overworld(),
                pose.x(),
                pose.y(),
                pose.z(),
                Set.of(),
                pose.yaw(),
                pose.pitch(),
                false);
    }

    private static void run(MinecraftServer server, String command) {
        CommandSourceStack source =
                server.createCommandSourceStack()
                        .withPermission(PermissionSet.ALL_PERMISSIONS)
                        .withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, command);
    }

    private static StructureStart generate(
            ServerLevel level, Holder<Structure> structure, ChunkPos chunk) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        var randomState = level.getChunkSource().randomState();
        return structure
                .value()
                .generate(
                        structure,
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
                        biome -> true);
    }
}
