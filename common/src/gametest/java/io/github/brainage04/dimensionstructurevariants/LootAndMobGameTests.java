package io.github.brainage04.dimensionstructurevariants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.EndCityPieces;
import net.minecraft.world.level.levelgen.structure.structures.NetherFortressPieces;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;

/**
 * The loot and the mobs a moved structure places come from its dimension: its chests carry a DSV
 * table per structure and biome group, and the mobs its pieces place come from the biome's own
 * monster list ({@code ChunkGenerator.getMobsAt}, {@code ChunkGenerator.java:473-497}).
 */
public final class LootAndMobGameTests {
    /** A variant in a biome, and the palette, hence the loot table, the biome gives it. */
    private record Case(String variant, ResourceKey<Biome> biome, String palette) {}

    /** Every palette the table names, reached through a biome its rules give it. */
    private static final List<Case> CASES =
            List.of(
                    new Case("fortress_overworld", Biomes.PLAINS, "fortress_grass"),
                    new Case("fortress_overworld", Biomes.DESERT, "fortress_desert"),
                    new Case("fortress_overworld", Biomes.BADLANDS, "fortress_badlands"),
                    new Case("fortress_overworld", Biomes.JUNGLE, "fortress_jungle"),
                    new Case("fortress_overworld", Biomes.SWAMP, "fortress_swamp"),
                    new Case("fortress_overworld", Biomes.SNOWY_PLAINS, "fortress_snowy"),
                    new Case("fortress_overworld", Biomes.MUSHROOM_FIELDS, "fortress_mushroom"),
                    new Case("fortress_overworld", Biomes.OCEAN, "fortress_ocean"),
                    new Case("fortress_overworld", Biomes.WARM_OCEAN, "fortress_ocean"),
                    new Case("fortress_overworld", Biomes.FROZEN_OCEAN, "fortress_frozen_ocean"),
                    new Case("fortress_end", Biomes.END_HIGHLANDS, "fortress_end"),
                    new Case("bastion_overworld", Biomes.OCEAN, "bastion_ocean"),
                    new Case("bastion_overworld", Biomes.WARM_OCEAN, "bastion_warm_ocean"),
                    new Case("bastion_overworld", Biomes.FROZEN_OCEAN, "bastion_frozen_ocean"),
                    new Case("bastion_end", Biomes.END_HIGHLANDS, "bastion_end"),
                    new Case("end_city_overworld", Biomes.OCEAN, "end_city_ocean"),
                    new Case("end_city_overworld", Biomes.FROZEN_OCEAN, "end_city_frozen_ocean"),
                    new Case("end_city_nether", Biomes.NETHER_WASTES, "end_city_nether_wastes"),
                    new Case("end_city_nether", Biomes.CRIMSON_FOREST, "end_city_nether_crimson"),
                    new Case("end_city_nether", Biomes.WARPED_FOREST, "end_city_nether_warped"),
                    new Case("end_city_nether", Biomes.SOUL_SAND_VALLEY, "end_city_nether_soul"),
                    new Case("end_city_nether", Biomes.BASALT_DELTAS, "end_city_nether_basalt"),
                    new Case("village_nether", Biomes.NETHER_WASTES, "village_nether"),
                    new Case("village_nether", Biomes.CRIMSON_FOREST, "village_nether_crimson"),
                    new Case("village_nether", Biomes.WARPED_FOREST, "village_nether_warped"),
                    new Case("village_nether", Biomes.SOUL_SAND_VALLEY, "village_nether_soul"),
                    new Case("village_nether", Biomes.BASALT_DELTAS, "village_nether_basalt"),
                    new Case("village_end", Biomes.END_HIGHLANDS, "village_end"));

    /** Where each placement test starts; tests are 100 chunks apart so no two share a chunk. */
    private static final int CHUNK_SPACING = 100;

    /** The surface the flat test world offers an End city: well above its floor. */
    private static final int END_CITY_Y = 64;

    /** Blocks between the templates a test places side by side. */
    private static final int TEMPLATE_SPACING = 4;

    /** Blocks around a test position that are loaded and given the test's biome. */
    private static final int PREPARED_RADIUS = 48;

    /** Natural starts to try for one variant before a start without a chest fails the test. */
    private static final int START_ATTEMPTS = 10;

    private static final List<EntityType<?>> NETHER_MOBS =
            List.of(
                    EntityTypes.PIGLIN,
                    EntityTypes.PIGLIN_BRUTE,
                    EntityTypes.HOGLIN,
                    EntityTypes.BLAZE,
                    EntityTypes.MAGMA_CUBE,
                    EntityTypes.WITHER_SKELETON);

    private LootAndMobGameTests() {}

    private static ResourceKey<LootTable> expectedTable(String palette) {
        return ResourceKey.create(
                Registries.LOOT_TABLE, DimensionStructureVariants.of("chests/" + palette));
    }

    /**
     * Every biome group of every moved structure names its own table, and the table is one of the
     * mod's data files rather than a name that loads as an empty table.
     */
    public static void everyMovedStructureNamesItsOwnLootTable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
        Set<ResourceKey<LootTable>> seen = new HashSet<>();
        for (Case each : CASES) {
            StructurePalettes.Palette palette =
                    StructurePalettes.palette(
                            DimensionStructureVariants.of(each.variant()),
                            biomes.getOrThrow(each.biome()));
            helper.assertTrue(
                    palette != null, each.variant() + " has no palette in " + each.biome());
            ResourceKey<LootTable> expected = expectedTable(each.palette());
            helper.assertTrue(
                    expected.equals(palette.lootTable()),
                    each.variant()
                            + " in "
                            + each.biome().identifier()
                            + " must carry "
                            + expected.identifier()
                            + ", got "
                            + palette.lootTable());
            helper.assertTrue(
                    level.getServer().reloadableRegistries().getLootTable(expected)
                            != LootTable.EMPTY,
                    "The loot table " + expected.identifier() + " must be a data file of the mod");
            seen.add(expected);
        }
        helper.assertTrue(
                seen.size() == CASES.stream().map(Case::palette).distinct().count(),
                "Every palette must name a table of its own");
        helper.succeed();
    }

    /**
     * Places a natural start of each moved structure in a biome of its dimension and reads every
     * chest the pieces wrote: each carries the variant's table, not the table its piece or its
     * template names.
     */
    public static void movedStructuresPlaceTheirOwnLoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int row = 0;
        for (Case each : CASES) {
            // One placement per variant: the first case of the variant is its representative.
            if (CASES.stream().filter(c -> c.variant().equals(each.variant())).findFirst().get()
                    != each) {
                continue;
            }
            int chests = 0;
            for (int attempt = 0; attempt < START_ATTEMPTS && chests == 0; attempt++) {
                ChunkPos chunk = new ChunkPos(2000 + row * CHUNK_SPACING, 2000 + attempt * 40);
                chests += placeAndCheckChests(helper, level, each, chunk);
            }
            helper.assertTrue(
                    chests > 0,
                    each.variant() + " placed no chest in " + START_ATTEMPTS + " natural starts");
            row++;
        }
        helper.succeed();
    }

    /** Places the start, checks its chests, and returns how many it found. */
    private static int placeAndCheckChests(
            GameTestHelper helper, ServerLevel level, Case each, ChunkPos chunk) {
        StructureStart start = startOf(helper, level, each.variant(), chunk);
        BoundingBox box = start.getBoundingBox();
        List<ChunkPos> chunks = PaletteGameTests.chunks(box);
        for (ChunkPos c : chunks) {
            level.setChunkForced(c.x(), c.z(), true);
            level.getChunk(c.x(), c.z());
        }
        PaletteGameTests.fillBiome(helper, level, box, each.biome());
        for (ChunkPos c : chunks) PaletteGameTests.place(level, start, c);
        ResourceKey<LootTable> expected = expectedTable(each.palette());
        int chests = 0;
        for (BlockPos pos :
                BlockPos.betweenClosed(
                        box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                chests++;
                helper.assertTrue(
                        expected.equals(chest.getLootTable()),
                        each.variant()
                                + " in "
                                + each.biome().identifier()
                                + ": the chest at "
                                + pos
                                + " must carry "
                                + expected.identifier()
                                + ", got "
                                + chest.getLootTable());
            }
        }
        for (ChunkPos c : chunks) level.setChunkForced(c.x(), c.z(), false);
        return chests;
    }

    /**
     * A natural start of {@code id}. An End city start refuses a surface under y 60 ({@code
     * EndCityStructure.java:34-35}) and the flat test world's is lower, so its pieces are built as
     * the structure builds them ({@code EndCityStructure.java:43}), from the surface of that world.
     */
    private static StructureStart startOf(
            GameTestHelper helper, ServerLevel level, String id, ChunkPos chunk) {
        if (!id.startsWith("end_city")) return PaletteGameTests.start(helper, level, id, chunk);
        List<StructurePiece> pieces = new ArrayList<>();
        EndCityPieces.startHouseTower(
                level.getStructureTemplateManager(),
                new BlockPos(chunk.getBlockX(7), END_CITY_Y, chunk.getBlockZ(7)),
                Rotation.NONE,
                pieces,
                RandomSource.create(chunk.pack()));
        return new StructureStart(
                level.registryAccess()
                        .lookupOrThrow(Registries.STRUCTURE)
                        .getValue(DimensionStructureVariants.of(id)),
                chunk,
                0,
                new PiecesContainer(pieces));
    }

    /** The monsters a biome spawns naturally, read as the game reads them. */
    private static List<EntityType<?>> monsters(ServerLevel level, BlockPos pos) {
        MobSpawnSettings settings =
                level.environmentAttributes()
                        .getValue(EnvironmentAttributes.NATURAL_MOB_SPAWNS, pos);
        List<EntityType<?>> types = new ArrayList<>();
        for (Weighted<MobSpawnSettings.SpawnerData> each :
                settings.getMobsToSpawn(MobCategory.MONSTER).unwrap()) {
            types.add(each.value().type());
        }
        return types;
    }

    private static Holder<Biome> biome(ServerLevel level, ResourceKey<Biome> key) {
        return level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(key);
    }

    /** Fills a cube around {@code pos} with {@code biome} and loads its chunks. */
    private static void prepare(
            GameTestHelper helper, ServerLevel level, BlockPos pos, ResourceKey<Biome> biome) {
        BoundingBox box = new BoundingBox(pos).inflatedBy(PREPARED_RADIUS);
        for (ChunkPos c : PaletteGameTests.chunks(box)) {
            level.setChunkForced(c.x(), c.z(), true);
            level.getChunk(c.x(), c.z());
        }
        PaletteGameTests.fillBiome(helper, level, box, biome);
        discardMobs(level, box);
    }

    /** Generating a chunk spawns its animals; the tests count only the mobs a structure places. */
    private static void discardMobs(ServerLevel level, BoundingBox box) {
        for (Mob mob : level.getEntitiesOfClass(Mob.class, AABB.of(box).inflate(1.0D))) {
            mob.discard();
        }
    }

    private static String spawnerEntity(ServerLevel level, SpawnerBlockEntity spawner) {
        return spawner.saveWithoutMetadata(level.registryAccess())
                .getCompoundOrEmpty("SpawnData")
                .getCompoundOrEmpty("entity")
                .getStringOr("id", "");
    }

    /**
     * The fortress's throne room puts a monster of the biome in its spawner instead of a blaze, and
     * in a biome with no monster it places no spawner.
     */
    public static void fortressThroneSpawnerTakesAMonsterOfItsBiome(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int row = 0;
        List<Case> cases =
                List.of(
                        new Case("fortress_overworld", Biomes.PLAINS, ""),
                        new Case("fortress_overworld", Biomes.DESERT, ""),
                        new Case("fortress_overworld", Biomes.SNOWY_PLAINS, ""),
                        new Case("fortress_overworld", Biomes.OCEAN, ""),
                        new Case("fortress_end", Biomes.END_HIGHLANDS, ""),
                        new Case("fortress_overworld", Biomes.MUSHROOM_FIELDS, ""));
        for (Case each : cases) {
            BlockPos origin = new BlockPos(3000 + row * 64, 70, 3000);
            row++;
            prepare(helper, level, origin, each.biome());
            BoundingBox box =
                    BoundingBox.orientBox(
                            origin.getX(),
                            origin.getY(),
                            origin.getZ(),
                            -2,
                            0,
                            0,
                            7,
                            8,
                            9,
                            Direction.NORTH);
            NetherFortressPieces.MonsterThrone throne =
                    new NetherFortressPieces.MonsterThrone(0, box, Direction.NORTH);
            StructurePalettes.Palette previous =
                    StructurePalettes.push(
                            StructurePalettes.palette(
                                    DimensionStructureVariants.of(each.variant()),
                                    biome(level, each.biome())));
            try {
                throne.postProcess(
                        level,
                        level.structureManager(),
                        level.getChunkSource().getGenerator(),
                        RandomSource.create(row),
                        box.inflatedBy(16),
                        ChunkPos.containing(origin),
                        origin);
            } finally {
                StructurePalettes.restore(previous);
            }
            List<EntityType<?>> monsters = monsters(level, origin);
            SpawnerBlockEntity spawner = null;
            for (BlockPos pos :
                    BlockPos.betweenClosed(
                            box.minX(),
                            box.minY(),
                            box.minZ(),
                            box.maxX(),
                            box.maxY(),
                            box.maxZ())) {
                if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity found) spawner = found;
            }
            String where = each.variant() + " in " + each.biome().identifier();
            if (monsters.isEmpty()) {
                helper.assertTrue(
                        spawner == null,
                        where + " has no monster, so its throne room must place no spawner");
                continue;
            }
            helper.assertTrue(spawner != null, where + " must place the throne room's spawner");
            Identifier id = Identifier.parse(spawnerEntity(level, spawner));
            helper.assertTrue(
                    monsters.stream()
                            .anyMatch(
                                    type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).equals(id)),
                    where + ": the spawner's mob " + id + " must be one of " + monsters);
        }
        helper.succeed();
    }

    /**
     * The bastion's own mobs (piglins, brutes and hoglins) and the magma cube spawner of its lava
     * basin become monsters of the biome; in a biome with no monster none is placed.
     */
    public static void bastionMobsAreTheBiomesMonsters(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int row = 0;
        List<Case> cases =
                List.of(
                        new Case("bastion_overworld", Biomes.OCEAN, ""),
                        new Case("bastion_overworld", Biomes.FROZEN_OCEAN, ""),
                        new Case("bastion_end", Biomes.END_HIGHLANDS, ""),
                        new Case("bastion_overworld", Biomes.MUSHROOM_FIELDS, ""));
        for (Case each : cases) {
            BlockPos origin = new BlockPos(4000 + row * 64, 70, 4000);
            row++;
            prepare(helper, level, origin, each.biome());
            List<EntityType<?>> monsters = monsters(level, origin);
            String where = each.variant() + " in " + each.biome().identifier();
            StructurePalettes.Palette previous =
                    StructurePalettes.push(
                            StructurePalettes.palette(
                                    DimensionStructureVariants.of(each.variant()),
                                    biome(level, each.biome())));
            try {
                int slot = 0;
                for (String name :
                        List.of(
                                "mobs/sword_piglin",
                                "mobs/crossbow_piglin",
                                "mobs/melee_piglin",
                                "mobs/melee_piglin_always",
                                "mobs/hoglin",
                                "treasure/bases/lava_basin")) {
                    StructureTemplate template =
                            level.getStructureTemplateManager()
                                    .get(Identifier.withDefaultNamespace("bastion/" + name))
                                    .orElseThrow();
                    BlockPos at = origin.offset(slot++ * TEMPLATE_SPACING, 0, 0);
                    template.placeInWorld(
                            level,
                            at,
                            at,
                            new StructurePlaceSettings().setFinalizeEntities(true),
                            RandomSource.create(row),
                            2);
                    BoundingBox box =
                            BoundingBox.fromCorners(
                                    at, at.offset(template.getSize()).offset(-1, -1, -1));
                    List<Mob> mobs =
                            level.getEntitiesOfClass(Mob.class, AABB.of(box).inflate(1.0D));
                    for (Mob mob : mobs) {
                        helper.assertTrue(
                                monsters.contains(mob.getType()),
                                where
                                        + ": "
                                        + name
                                        + " placed "
                                        + mob.getType()
                                        + ", not one of "
                                        + monsters);
                        mob.discard();
                    }
                    if (monsters.isEmpty()) {
                        helper.assertTrue(
                                mobs.isEmpty(),
                                where + ": " + name + " placed a mob in a biome with no monster");
                    } else if (name.startsWith("mobs/")) {
                        helper.assertTrue(
                                mobs.size() == 1,
                                where + ": " + name + " must place one mob, got " + mobs.size());
                    }
                    for (BlockPos pos :
                            BlockPos.betweenClosed(
                                    box.minX(),
                                    box.minY(),
                                    box.minZ(),
                                    box.maxX(),
                                    box.maxY(),
                                    box.maxZ())) {
                        BlockEntity entity = level.getBlockEntity(pos);
                        if (!(entity instanceof SpawnerBlockEntity spawner)) continue;
                        helper.assertTrue(
                                !monsters.isEmpty(),
                                where + " must place no spawner without a monster");
                        var tag = spawner.saveWithoutMetadata(level.registryAccess());
                        List<String> ids = new ArrayList<>();
                        ids.add(spawnerEntity(level, spawner));
                        tag.getListOrEmpty("SpawnPotentials")
                                .forEach(
                                        potential ->
                                                ids.add(
                                                        potential
                                                                .asCompound()
                                                                .orElseThrow()
                                                                .getCompoundOrEmpty("data")
                                                                .getCompoundOrEmpty("entity")
                                                                .getStringOr("id", "")));
                        for (String id : ids) {
                            helper.assertTrue(
                                    monsters.stream()
                                            .anyMatch(
                                                    type ->
                                                            BuiltInRegistries.ENTITY_TYPE
                                                                    .getKey(type)
                                                                    .toString()
                                                                    .equals(id)),
                                    where
                                            + ": the lava basin's spawner holds "
                                            + id
                                            + ", not one of "
                                            + monsters);
                        }
                    }
                }
            } finally {
                StructurePalettes.restore(previous);
            }
        }
        helper.succeed();
    }

    /**
     * A bastion start placed in the ocean leaves no piglin, brute or hoglin, and every mob the
     * pieces placed is a monster of the biome.
     */
    public static void placedBastionsHoldNoNetherMob(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int row = 0;
        for (Case each :
                List.of(
                        new Case("bastion_overworld", Biomes.OCEAN, ""),
                        new Case("bastion_end", Biomes.END_HIGHLANDS, ""))) {
            int found = 0;
            for (int attempt = 0; attempt < START_ATTEMPTS && found == 0; attempt++) {
                ChunkPos chunk = new ChunkPos(5000 + row * CHUNK_SPACING, 5000 + attempt * 40);
                StructureStart start = PaletteGameTests.start(helper, level, each.variant(), chunk);
                BoundingBox box = start.getBoundingBox();
                List<ChunkPos> chunks = PaletteGameTests.chunks(box);
                for (ChunkPos c : chunks) {
                    level.setChunkForced(c.x(), c.z(), true);
                    level.getChunk(c.x(), c.z());
                }
                PaletteGameTests.fillBiome(helper, level, box, each.biome());
                discardMobs(level, box);
                for (ChunkPos c : chunks) PaletteGameTests.place(level, start, c);
                for (Mob mob : level.getEntitiesOfClass(Mob.class, AABB.of(box))) {
                    found++;
                    helper.assertTrue(
                            !NETHER_MOBS.contains(mob.getType()),
                            each.variant() + " placed the Nether mob " + mob.getType());
                    helper.assertTrue(
                            monsters(level, mob.blockPosition()).contains(mob.getType()),
                            each.variant()
                                    + " placed "
                                    + mob.getType()
                                    + ", not a monster of its biome");
                    mob.discard();
                }
                for (ChunkPos c : chunks) level.setChunkForced(c.x(), c.z(), false);
            }
            helper.assertTrue(
                    found > 0,
                    each.variant() + " placed no mob in " + START_ATTEMPTS + " natural starts");
            row++;
        }
        helper.succeed();
    }
}
