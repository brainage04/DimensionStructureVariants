package io.github.brainage04.dimensionstructurevariants;

import it.unimi.dsi.fastutil.HashCommon;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/**
 * The mobs a moved structure's pieces place (the fortress throne room's spawner, the bastion's
 * piglins, hoglins and magma cube spawner) come from the monster list of the biome they stand in,
 * the list natural spawning reads there ({@code ChunkGenerator.getMobsAt}, {@code
 * ChunkGenerator.java:473-497}). A variant opts in with {@code monsters_from_biome} in {@code
 * palettes.json}.
 */
public final class StructureMobs {
    private static final String SPAWNER_ID = "minecraft:mob_spawner";

    private StructureMobs() {}

    /** Whether the structure being placed takes its mobs from the biome. */
    public static boolean replaces() {
        StructurePalettes.Palette palette = StructurePalettes.active();
        return palette != null && palette.biomeMonsters();
    }

    /**
     * A monster of the biome at {@code pos}, or empty where the biome spawns none. The pick follows
     * from {@code pos} alone, so a structure holds the same mobs every time the world generates it.
     */
    public static Optional<EntityType<?>> monsterAt(LevelReader level, BlockPos pos) {
        MobSpawnSettings spawns =
                level.environmentAttributes()
                        .getValue(EnvironmentAttributes.NATURAL_MOB_SPAWNS, pos);
        return spawns.getMobsToSpawn(MobCategory.MONSTER)
                .getRandom(RandomSource.create(HashCommon.mix(pos.asLong())))
                .map(data -> data.type());
    }

    /**
     * {@code info} with the spawner's mob swapped for a monster of the biome at its position, or
     * air where the biome has none; any other block is returned as it is.
     */
    public static StructureBlockInfo biomeSpawner(LevelReader level, StructureBlockInfo info) {
        CompoundTag nbt = info.nbt();
        if (nbt == null || !SPAWNER_ID.equals(nbt.getStringOr("id", ""))) return info;
        Optional<EntityType<?>> type = monsterAt(level, info.pos());
        if (type.isEmpty()) {
            return new StructureBlockInfo(info.pos(), Blocks.AIR.defaultBlockState(), null);
        }
        CompoundTag entity = new CompoundTag();
        entity.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(type.get()).toString());
        CompoundTag spawnData = new CompoundTag();
        spawnData.put("entity", entity);
        CompoundTag potential = new CompoundTag();
        potential.put("data", spawnData.copy());
        potential.putInt("weight", 1);
        ListTag potentials = new ListTag();
        potentials.add(potential);
        CompoundTag swapped = nbt.copy();
        swapped.put("SpawnData", spawnData);
        swapped.put("SpawnPotentials", potentials);
        return new StructureBlockInfo(info.pos(), info.state(), swapped);
    }

    /**
     * The entity tag a template entity at {@code pos} becomes: a monster of the biome, standing
     * where the template put the original, or empty where the biome has none.
     */
    public static Optional<CompoundTag> biomeMob(LevelReader level, BlockPos pos, CompoundTag tag) {
        return monsterAt(level, pos)
                .map(
                        type -> {
                            CompoundTag mob = new CompoundTag();
                            mob.putString(
                                    "id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
                            mob.put("Pos", tag.get("Pos"));
                            mob.putBoolean("PersistenceRequired", true);
                            return mob;
                        });
    }
}
