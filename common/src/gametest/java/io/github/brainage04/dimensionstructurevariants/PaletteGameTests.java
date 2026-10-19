package io.github.brainage04.dimensionstructurevariants;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Runs the production palette tables and placement mixins on both loaders. */
public final class PaletteGameTests {
	private PaletteGameTests() {
	}

	public static void dimensionMaterialsAndProperties(GameTestHelper helper) {
		assertRemap(helper, "end_city_overworld", Blocks.PURPUR_BLOCK.defaultBlockState(), Blocks.BLUE_ICE.defaultBlockState());
		assertRemap(helper, "bastion_overworld", Blocks.LAVA.defaultBlockState().setValue(BlockStateProperties.LEVEL, 5),
				Blocks.WATER.defaultBlockState().setValue(BlockStateProperties.LEVEL, 5));
		assertRemap(helper, "bastion_end", Blocks.LAVA.defaultBlockState(), Blocks.PURPUR_BLOCK.defaultBlockState());
		assertRemap(helper, "fortress_end", Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.END_STONE_BRICKS.defaultBlockState());
		assertRemap(helper, "village_end", Blocks.WALL_TORCH.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST),
				Blocks.END_ROD.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.WEST));
		assertRemap(helper, "village_nether", Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X),
				Blocks.BASALT.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
		assertRemap(helper, "village_end", Blocks.OAK_STAIRS.defaultBlockState()
						.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST).setValue(BlockStateProperties.HALF, Half.TOP)
						.setValue(BlockStateProperties.WATERLOGGED, true),
				Blocks.PURPUR_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
						.setValue(BlockStateProperties.HALF, Half.TOP).setValue(BlockStateProperties.WATERLOGGED, true));
		for (StructureVariantCatalog.Variant variant : StructureVariantCatalog.VARIANTS) {
			assertRemap(helper, variant.structure().identifier().getPath(), Blocks.CHEST.defaultBlockState(), Blocks.CHEST.defaultBlockState());
		}
		assertRemap(helper, "fortress_overworld", Blocks.NETHER_BRICKS.defaultBlockState(), Blocks.NETHER_BRICKS.defaultBlockState());
		assertRemap(helper, "end_city_nether", Blocks.PURPUR_BLOCK.defaultBlockState(), Blocks.PURPUR_BLOCK.defaultBlockState());
		helper.succeed();
	}

	public static void placementRestoresContextAfterSuccessAndFailure(GameTestHelper helper) {
		var level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(0, 2, 0));
		BoundingBox bounds = new BoundingBox(pos);
		Structure structure = structure(helper, DimensionStructureVariants.of("bastion_overworld"));
		for (boolean fail : new boolean[]{false, true}) {
			RuntimeException failure = new RuntimeException("Deliberate piece failure");
			StructureStart start = new StructureStart(structure, ChunkPos.containing(pos), 0,
					new PiecesContainer(List.of(new ProbePiece(pos, fail ? failure : null))));
			try {
				start.placeInChunk(level, level.structureManager(), level.getChunkSource().getGenerator(),
						RandomSource.create(0), bounds, ChunkPos.containing(pos));
				helper.assertTrue(!fail, "The failing piece must throw");
			} catch (RuntimeException exception) {
				if (exception != failure) throw exception;
			}
			helper.assertTrue(level.getBlockState(pos).is(Blocks.PRISMARINE), "Structure placement must apply the ocean palette");
			level.setBlock(pos, Blocks.BLACKSTONE.defaultBlockState(), 2);
			helper.assertTrue(level.getBlockState(pos).is(Blocks.BLACKSTONE), "Later world writes must not inherit a structure palette");
		}
		helper.succeed();
	}

	public static void nestedAndWorkerContextsStayIsolated(GameTestHelper helper) {
		Map<BlockState, BlockState> previous = StructurePalettes.enter(helper.getLevel(),
				structure(helper, DimensionStructureVariants.of("bastion_overworld")));
		try {
			BlockState source = Blocks.BLACKSTONE.defaultBlockState();
			AtomicReference<BlockState> workerResult = new AtomicReference<>();
			Thread worker = Thread.ofPlatform().unstarted(() -> workerResult.set(StructurePalettes.remap(source)));
			worker.start();
			try {
				worker.join();
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new AssertionError("Interrupted while checking palette isolation", exception);
			}
			helper.assertTrue(workerResult.get() == source, "A worker must not inherit another thread's palette");
			Map<BlockState, BlockState> outer = StructurePalettes.enter(helper.getLevel(),
					structure(helper, Identifier.withDefaultNamespace("bastion_remnant")));
			try {
				helper.assertTrue(StructurePalettes.remap(source) == source, "Nested vanilla structures must retain vanilla blocks");
			} finally {
				StructurePalettes.restore(outer);
			}
			helper.assertTrue(StructurePalettes.remap(source).is(Blocks.PRISMARINE), "The enclosing palette must resume after nested placement");
		} finally {
			StructurePalettes.restore(previous);
		}
		helper.succeed();
	}

	private static void assertRemap(GameTestHelper helper, String id, BlockState source, BlockState expected) {
		Map<BlockState, BlockState> previous = StructurePalettes.enter(helper.getLevel(), structure(helper, DimensionStructureVariants.of(id)));
		try {
			BlockState actual = StructurePalettes.remap(source);
			helper.assertTrue(actual == expected, id + ": expected " + expected + " for " + source + ", got " + actual);
		} finally {
			StructurePalettes.restore(previous);
		}
	}

	private static Structure structure(GameTestHelper helper, Identifier id) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(id);
	}

	/** Minimal real piece: its write passes through StructureStart and Level, not a mock remapper. */
	private static final class ProbePiece extends StructurePiece {
		private final BlockPos pos;
		private final RuntimeException failure;

		private ProbePiece(BlockPos pos, RuntimeException failure) {
			super(StructurePieceType.NETHER_FORTRESS_BRIDGE_STRAIGHT, 0, new BoundingBox(pos));
			this.pos = pos;
			this.failure = failure;
		}

		@Override
		protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
			throw new UnsupportedOperationException("The placement probe is never serialized");
		}

		@Override
		public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
				RandomSource random, BoundingBox bounds, ChunkPos chunk, BlockPos pivot) {
			level.setBlock(pos, Blocks.BLACKSTONE.defaultBlockState(), 2);
			if (failure != null) throw failure;
		}
	}
}
