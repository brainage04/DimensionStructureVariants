package io.github.brainage04.dimensionstructurevariants;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.IdentityHashMap;
import java.util.Map;

/** Vanilla block states only: clients need neither this mod nor a resource pack. */
public final class StructurePalettes {
	private static final ThreadLocal<Map<BlockState, BlockState>> ACTIVE = new ThreadLocal<>();
	private static final Map<BlockState, BlockState> FROZEN_CITY = palette(
			Blocks.END_STONE_BRICKS, Blocks.PACKED_ICE,
			Blocks.PURPUR_BLOCK, Blocks.BLUE_ICE,
			Blocks.PURPUR_PILLAR, Blocks.SNOW_BLOCK,
			Blocks.PURPUR_STAIRS, Blocks.PRISMARINE_STAIRS,
			Blocks.PURPUR_SLAB, Blocks.PRISMARINE_BRICK_SLAB,
			Blocks.STAINED_GLASS.pick(DyeColor.MAGENTA), Blocks.STAINED_GLASS.pick(DyeColor.CYAN),
			Blocks.STAINED_GLASS_PANE.pick(DyeColor.MAGENTA), Blocks.STAINED_GLASS_PANE.pick(DyeColor.CYAN)
	);
	private static final Map<BlockState, BlockState> OCEAN_BASTION = palette(
			Blocks.BLACKSTONE, Blocks.PRISMARINE,
			Blocks.POLISHED_BLACKSTONE, Blocks.DARK_PRISMARINE,
			Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.PRISMARINE_BRICKS,
			Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, Blocks.PRISMARINE,
			Blocks.CHISELED_POLISHED_BLACKSTONE, Blocks.DARK_PRISMARINE,
			Blocks.GILDED_BLACKSTONE, Blocks.SEA_LANTERN,
			Blocks.GOLD_BLOCK, Blocks.SEA_LANTERN,
			Blocks.BLACKSTONE_STAIRS, Blocks.PRISMARINE_STAIRS,
			Blocks.POLISHED_BLACKSTONE_STAIRS, Blocks.DARK_PRISMARINE_STAIRS,
			Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.PRISMARINE_BRICK_STAIRS,
			Blocks.BLACKSTONE_SLAB, Blocks.PRISMARINE_SLAB,
			Blocks.POLISHED_BLACKSTONE_SLAB, Blocks.DARK_PRISMARINE_SLAB,
			Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, Blocks.PRISMARINE_BRICK_SLAB,
			Blocks.BLACKSTONE_WALL, Blocks.PRISMARINE_WALL,
			Blocks.POLISHED_BLACKSTONE_WALL, Blocks.PRISMARINE_WALL,
			Blocks.POLISHED_BLACKSTONE_BRICK_WALL, Blocks.PRISMARINE_WALL,
			Blocks.BASALT, Blocks.DARK_PRISMARINE,
			Blocks.POLISHED_BASALT, Blocks.DARK_PRISMARINE,
			Blocks.SMOOTH_BASALT, Blocks.DARK_PRISMARINE,
			Blocks.NETHERRACK, Blocks.PRISMARINE,
			Blocks.SOUL_SAND, Blocks.SAND,
			Blocks.MAGMA_BLOCK, Blocks.SEA_LANTERN,
			Blocks.LAVA, Blocks.WATER,
			Blocks.CRYING_OBSIDIAN, Blocks.SEA_LANTERN
	);
	private static final Map<BlockState, BlockState> END_COMPLEX = palette(
			Blocks.BLACKSTONE, Blocks.END_STONE,
			Blocks.POLISHED_BLACKSTONE, Blocks.PURPUR_BLOCK,
			Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.END_STONE_BRICKS,
			Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, Blocks.END_STONE,
			Blocks.CHISELED_POLISHED_BLACKSTONE, Blocks.PURPUR_PILLAR,
			Blocks.GILDED_BLACKSTONE, Blocks.PURPUR_BLOCK,
			Blocks.GOLD_BLOCK, Blocks.PURPUR_PILLAR,
			Blocks.BLACKSTONE_STAIRS, Blocks.END_STONE_BRICK_STAIRS,
			Blocks.POLISHED_BLACKSTONE_STAIRS, Blocks.PURPUR_STAIRS,
			Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.END_STONE_BRICK_STAIRS,
			Blocks.BLACKSTONE_SLAB, Blocks.END_STONE_BRICK_SLAB,
			Blocks.POLISHED_BLACKSTONE_SLAB, Blocks.PURPUR_SLAB,
			Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, Blocks.END_STONE_BRICK_SLAB,
			Blocks.BLACKSTONE_WALL, Blocks.END_STONE_BRICK_WALL,
			Blocks.POLISHED_BLACKSTONE_WALL, Blocks.END_STONE_BRICK_WALL,
			Blocks.POLISHED_BLACKSTONE_BRICK_WALL, Blocks.END_STONE_BRICK_WALL,
			Blocks.BASALT, Blocks.PURPUR_PILLAR,
			Blocks.POLISHED_BASALT, Blocks.PURPUR_PILLAR,
			Blocks.SMOOTH_BASALT, Blocks.PURPUR_BLOCK,
			Blocks.NETHERRACK, Blocks.END_STONE,
			Blocks.NETHER_BRICKS, Blocks.END_STONE_BRICKS,
			Blocks.NETHER_BRICK_STAIRS, Blocks.END_STONE_BRICK_STAIRS,
			Blocks.NETHER_BRICK_SLAB, Blocks.END_STONE_BRICK_SLAB,
			Blocks.NETHER_BRICK_FENCE, Blocks.END_STONE_BRICK_WALL,
			Blocks.SOUL_SAND, Blocks.END_STONE,
			Blocks.NETHER_WART, Blocks.AIR,
			Blocks.MAGMA_BLOCK, Blocks.PURPUR_BLOCK,
			Blocks.LAVA, Blocks.PURPUR_BLOCK,
			Blocks.CRYING_OBSIDIAN, Blocks.PURPUR_PILLAR
	);
	private static final Map<BlockState, BlockState> END_VILLAGE = palette(
			Blocks.COBBLESTONE, Blocks.END_STONE_BRICKS,
			Blocks.MOSSY_COBBLESTONE, Blocks.END_STONE,
			Blocks.COBBLESTONE_STAIRS, Blocks.END_STONE_BRICK_STAIRS,
			Blocks.MOSSY_COBBLESTONE_STAIRS, Blocks.END_STONE_BRICK_STAIRS,
			Blocks.COBBLESTONE_SLAB, Blocks.END_STONE_BRICK_SLAB,
			Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.END_STONE_BRICK_SLAB,
			Blocks.COBBLESTONE_WALL, Blocks.END_STONE_BRICK_WALL,
			Blocks.MOSSY_COBBLESTONE_WALL, Blocks.END_STONE_BRICK_WALL,
			Blocks.OAK_LOG, Blocks.PURPUR_PILLAR,
			Blocks.STRIPPED_OAK_LOG, Blocks.PURPUR_PILLAR,
			Blocks.OAK_WOOD, Blocks.PURPUR_PILLAR,
			Blocks.OAK_PLANKS, Blocks.PURPUR_BLOCK,
			Blocks.OAK_STAIRS, Blocks.PURPUR_STAIRS,
			Blocks.OAK_SLAB, Blocks.PURPUR_SLAB,
			Blocks.OAK_FENCE, Blocks.END_STONE_BRICK_WALL,
			Blocks.OAK_LEAVES, Blocks.PURPUR_BLOCK,
			Blocks.GRASS_BLOCK, Blocks.END_STONE,
			Blocks.DIRT, Blocks.END_STONE,
			Blocks.DIRT_PATH, Blocks.END_STONE_BRICKS,
			Blocks.HAY_BLOCK, Blocks.PURPUR_PILLAR,
			Blocks.DYED_TERRACOTTA.pick(DyeColor.WHITE), Blocks.END_STONE_BRICKS,
			Blocks.TERRACOTTA, Blocks.PURPUR_BLOCK,
			Blocks.GLASS_PANE, Blocks.STAINED_GLASS_PANE.pick(DyeColor.MAGENTA),
			Blocks.TORCH, Blocks.END_ROD,
			Blocks.WALL_TORCH, Blocks.END_ROD,
			Blocks.DANDELION, Blocks.CHORUS_FLOWER,
			Blocks.POPPY, Blocks.CHORUS_FLOWER,
			Blocks.SHORT_GRASS, Blocks.AIR,
			Blocks.TALL_GRASS, Blocks.AIR
	);
	private static final Map<BlockState, BlockState> NETHER_VILLAGE = palette(
			Blocks.COBBLESTONE, Blocks.NETHER_BRICKS,
			Blocks.MOSSY_COBBLESTONE, Blocks.BLACKSTONE,
			Blocks.COBBLESTONE_STAIRS, Blocks.NETHER_BRICK_STAIRS,
			Blocks.MOSSY_COBBLESTONE_STAIRS, Blocks.BLACKSTONE_STAIRS,
			Blocks.COBBLESTONE_SLAB, Blocks.NETHER_BRICK_SLAB,
			Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.BLACKSTONE_SLAB,
			Blocks.COBBLESTONE_WALL, Blocks.NETHER_BRICK_WALL,
			Blocks.MOSSY_COBBLESTONE_WALL, Blocks.BLACKSTONE_WALL,
			Blocks.OAK_LOG, Blocks.BASALT,
			Blocks.STRIPPED_OAK_LOG, Blocks.POLISHED_BASALT,
			Blocks.OAK_WOOD, Blocks.BASALT,
			Blocks.OAK_PLANKS, Blocks.RED_NETHER_BRICKS,
			Blocks.OAK_STAIRS, Blocks.RED_NETHER_BRICK_STAIRS,
			Blocks.OAK_SLAB, Blocks.RED_NETHER_BRICK_SLAB,
			Blocks.OAK_FENCE, Blocks.NETHER_BRICK_FENCE,
			Blocks.OAK_FENCE_GATE, Blocks.CRIMSON_FENCE_GATE,
			Blocks.OAK_DOOR, Blocks.CRIMSON_DOOR,
			Blocks.OAK_TRAPDOOR, Blocks.CRIMSON_TRAPDOOR,
			Blocks.OAK_PRESSURE_PLATE, Blocks.CRIMSON_PRESSURE_PLATE,
			Blocks.OAK_BUTTON, Blocks.CRIMSON_BUTTON,
			Blocks.OAK_LEAVES, Blocks.NETHER_WART_BLOCK,
			Blocks.GRASS_BLOCK, Blocks.NETHERRACK,
			Blocks.DIRT, Blocks.NETHERRACK,
			Blocks.DIRT_PATH, Blocks.GRAVEL,
			Blocks.HAY_BLOCK, Blocks.SOUL_SAND,
			Blocks.DYED_TERRACOTTA.pick(DyeColor.WHITE), Blocks.NETHER_BRICKS,
			Blocks.TERRACOTTA, Blocks.RED_NETHER_BRICKS,
			Blocks.GLASS_PANE, Blocks.STAINED_GLASS_PANE.pick(DyeColor.ORANGE),
			Blocks.TORCH, Blocks.SOUL_TORCH,
			Blocks.WALL_TORCH, Blocks.SOUL_WALL_TORCH,
			Blocks.DANDELION, Blocks.CRIMSON_FUNGUS,
			Blocks.POPPY, Blocks.CRIMSON_FUNGUS,
			Blocks.SHORT_GRASS, Blocks.CRIMSON_ROOTS,
			Blocks.TALL_GRASS, Blocks.AIR
	);

	private StructurePalettes() {
	}

	/** The caller must restore the returned context in a finally block. */
	public static Map<BlockState, BlockState> enter(WorldGenLevel level, Structure structure) {
		Map<BlockState, BlockState> previous = ACTIVE.get();
		Identifier id = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(structure);
		Map<BlockState, BlockState> palette = null;
		if (id != null && id.getNamespace().equals(DimensionStructureVariants.MOD_ID)) {
			palette = switch (id.getPath()) {
				case "end_city_overworld" -> FROZEN_CITY;
				case "bastion_overworld" -> OCEAN_BASTION;
				case "bastion_end", "fortress_end" -> END_COMPLEX;
				case "village_end" -> END_VILLAGE;
				case "village_nether" -> NETHER_VILLAGE;
				default -> null;
			};
		}
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

	private static Map<BlockState, BlockState> palette(Block... pairs) {
		Map<BlockState, BlockState> states = new IdentityHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			Block target = pairs[i + 1];
			for (BlockState source : pairs[i].getStateDefinition().getPossibleStates()) {
				BlockState replacement = target.withPropertiesOf(source);
				for (Property<?> property : source.getProperties()) {
					Property<?> targetProperty = target.getStateDefinition().getProperty(property.getName());
					if (targetProperty != null && !replacement.hasProperty(property)) {
						replacement = copyCompatibleValue(source, replacement, property, targetProperty);
					}
				}
				states.put(source, replacement);
			}
		}
		return states;
	}

	/** Horizontal and six-way facing use different Property objects but share valid values. */
	private static <T extends Comparable<T>, U extends Comparable<U>> BlockState copyCompatibleValue(
			BlockState source, BlockState target, Property<T> sourceProperty, Property<U> targetProperty) {
		String value = sourceProperty.getName(source.getValue(sourceProperty));
		return targetProperty.getValue(value).map(parsed -> target.setValue(targetProperty, parsed)).orElse(target);
	}
}
