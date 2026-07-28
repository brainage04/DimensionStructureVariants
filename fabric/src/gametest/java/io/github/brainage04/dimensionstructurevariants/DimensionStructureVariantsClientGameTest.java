package io.github.brainage04.dimensionstructurevariants;

import io.github.brainage04.fabricmoddingconventions.ClientGameTestRecorder;
import io.github.brainage04.fabricmoddingconventions.ClientGameTestServers;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.PlaceCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Properties;
import java.util.Set;

@SuppressWarnings("UnstableApiUsage")
public final class DimensionStructureVariantsClientGameTest implements FabricClientGameTest {
	private static final List<Stage> STAGES = List.of(
			new Stage("village_nether", Level.NETHER, 64,
					"Nether village variant", "A vanilla plains village generated from the variant registered for Nether biomes", 0),
			new Stage("fortress_overworld", Level.OVERWORLD, -60,
					"Overworld fortress variant", "A vanilla Nether fortress generated from the variant registered for Overworld biomes", 96),
			new Stage("bastion_overworld", Level.OVERWORLD, 33,
					"Overworld bastion variant", "A vanilla bastion generated from the variant registered for Overworld biomes", 192)
	);
	private static BlockPos stageBase;

	@Override
	public void runTest(ClientGameTestContext context) {
		Properties serverProperties = ClientGameTestServers.flatServerProperties();
		serverProperties.setProperty("view-distance", "12");
		ClientGameTestServers.withDedicatedServer(context, serverProperties, "DimensionStructureVariants visual GameTest", server -> { try {
				ClientGameTestServers.assertClientWorldAndPlayerAvailable(context);
				server.runOnServer(minecraftServer ->
						stageBase = minecraftServer.getPlayerList().getPlayers().getFirst().blockPosition());
				ClientGameTestRecorder.startRecording(context);
				for (Stage stage : STAGES) {
					server.runOnServer(minecraftServer -> moveToStage(minecraftServer, stage));
					context.waitTicks(80);
					server.runOnServer(minecraftServer -> placeStage(minecraftServer, stage));
					context.waitTicks(40);
					ClientGameTestRecorder.showStep(
							context,
							"dimensionstructurevariants." + stage.structureId(),
							stage.title(),
							stage.subtitle()
					);
					context.waitTicks(60);
				}
			} finally {
				;
			} });
	}

	private static void moveToStage(MinecraftServer server, Stage stage) {
		ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
		ServerLevel level = server.getLevel(stage.dimension());
		if (level == null) throw new AssertionError("Expected dimension " + stage.dimension().identifier());
		BlockPos origin = origin(stage);
		player.setGameMode(GameType.SPECTATOR);
		player.teleportTo(
				level,
				origin.getX() + 0.5D,
				origin.getY() + 18.0D,
				origin.getZ() - 32.0D,
				Set.of(),
				0.0F,
				18.0F,
				false
		);
	}

	private static void placeStage(MinecraftServer server, Stage stage) {
		ServerLevel level = server.getLevel(stage.dimension());
		if (level == null) throw new AssertionError("Expected dimension " + stage.dimension().identifier());
		BlockPos origin = origin(stage);
		CommandSourceStack source = server.createCommandSourceStack()
				.withLevel(level)
				.withPosition(Vec3.atBottomCenterOf(origin))
				.withPermission(PermissionSet.ALL_PERMISSIONS)
				.withSuppressedOutput();
		var structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(
				ResourceKey.create(Registries.STRUCTURE, DimensionStructureVariants.of(stage.structureId()))
		);
		var generator = level.getChunkSource().getGenerator();
		var preview = structure.value().generate(
				structure,
				level.dimension(),
				level.registryAccess(),
				generator,
				generator.getBiomeSource(),
				level.getChunkSource().randomState(),
				level.getStructureManager(),
				level.getSeed(),
				ChunkPos.containing(origin),
				0,
				level,
				biome -> true
		);
		if (!preview.isValid()) throw new AssertionError("Expected a valid structure start for " + stage.structureId());
		var bounds = preview.getBoundingBox();
		for (int chunkX = SectionPos.blockToSectionCoord(bounds.minX());
			 chunkX <= SectionPos.blockToSectionCoord(bounds.maxX());
			 chunkX++) {
			for (int chunkZ = SectionPos.blockToSectionCoord(bounds.minZ());
				 chunkZ <= SectionPos.blockToSectionCoord(bounds.maxZ());
				 chunkZ++) {
				level.getChunk(chunkX, chunkZ);
			}
		}
		try {
			if (PlaceCommand.placeStructure(source, structure, origin) != 1) {
				throw new AssertionError("Expected structure placement to succeed for " + stage.structureId());
			}
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
			throw new AssertionError("Could not stage " + stage.structureId(), exception);
		}
		ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
		player.teleportTo(
				level,
				(bounds.minX() + bounds.maxX()) / 2.0D,
				bounds.maxY() + 12.0D,
				bounds.minZ() - 24.0D,
				Set.of(),
				0.0F,
				18.0F,
				false
		);
	}

	private static BlockPos origin(Stage stage) {
		return new BlockPos(stageBase.getX() + stage.offset(), stage.y(), stageBase.getZ() + 24);
	}

	private record Stage(
			String structureId,
			ResourceKey<Level> dimension,
			int y,
			String title,
			String subtitle,
			int offset
	) {
	}
}
