package io.github.brainage04.dimensionstructurevariants;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Shared by both loaders: an End city built in the Nether wears the Nether's blocks.
 *
 * <p>An End city is nothing but its templates: {@code EndCityPieces} places the twenty below and
 * sets no block of its own ({@code EndCityPieces.java:29-442}). A template block the palette does
 * not map is therefore a block that stays vanilla purpur in the Nether, which is what the film
 * showed on the base floor of the Nether End cities.
 */
public final class EndCityNetherGameTests {
    private static final ResourceKey<Structure> END_CITY_NETHER =
            ResourceKey.create(
                    Registries.STRUCTURE, DimensionStructureVariants.of("end_city_nether"));

    /**
     * Every template {@code EndCityPieces} places: the house tower's floors and roofs ({@code
     * EndCityPieces.java:44-88}), the tower and its bridges ({@code :96-200}), the fat tower
     * ({@code :232-280}) and the ship ({@code :205-215}).
     */
    private static final List<String> TEMPLATES =
            List.of(
                    "base_floor",
                    "base_roof",
                    "second_floor_1",
                    "second_floor_2",
                    "second_roof",
                    "third_floor_1",
                    "third_floor_2",
                    "third_roof",
                    "tower_base",
                    "tower_piece",
                    "tower_floor",
                    "tower_top",
                    "bridge_end",
                    "bridge_piece",
                    "bridge_gentle_stairs",
                    "bridge_steep_stairs",
                    "fat_tower_base",
                    "fat_tower_middle",
                    "fat_tower_top",
                    "ship");

    /** The blocks an End city is built from that must not survive in the Nether. */
    private static final List<Block> PURPUR =
            List.of(
                    Blocks.PURPUR_BLOCK,
                    Blocks.PURPUR_PILLAR,
                    Blocks.PURPUR_STAIRS,
                    Blocks.PURPUR_SLAB);

    private EndCityNetherGameTests() {}

    /**
     * Every block state of every End city template is mapped by the Nether palette, and no purpur
     * is among the blocks that palette can place.
     */
    public static void endCityInTheNetherLeavesNoPurpur(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "The Nether must be loaded");
        Holder<Structure> city =
                nether.registryAccess()
                        .lookupOrThrow(Registries.STRUCTURE)
                        .getOrThrow(END_CITY_NETHER);
        var biomes = nether.registryAccess().lookupOrThrow(Registries.BIOME);
        List<ResourceKey<Biome>> netherBiomes =
                List.of(
                        Biomes.NETHER_WASTES,
                        Biomes.CRIMSON_FOREST,
                        Biomes.WARPED_FOREST,
                        Biomes.SOUL_SAND_VALLEY,
                        Biomes.BASALT_DELTAS);
        Set<BlockState> templateStates = new LinkedHashSet<>();
        for (String name : TEMPLATES) {
            templateStates.addAll(purpurStates(nether, name));
        }
        helper.assertTrue(
                templateStates.size() > 4,
                "The End city templates must hold purpur, read " + templateStates);
        List<String> unmapped = new ArrayList<>();
        List<String> purpur = new ArrayList<>();
        for (ResourceKey<Biome> biome : netherBiomes) {
            StructurePalettes.Palette palette =
                    StructurePalettes.palette(
                            DimensionStructureVariants.of("end_city_nether"),
                            biomes.getOrThrow(biome));
            helper.assertTrue(palette != null, "end_city_nether has no palette in " + biome);
            for (BlockState state : templateStates) {
                if (palette.choiceFor(state) == null) {
                    unmapped.add(biome.identifier().getPath() + ": " + state);
                }
            }
            for (BlockState target : palette.targets()) {
                for (Block block : PURPUR) {
                    if (target.is(block)) {
                        purpur.add(biome.identifier().getPath() + ": " + target);
                    }
                }
            }
        }
        helper.assertTrue(
                unmapped.isEmpty(),
                "The Nether palette must map every purpur block of the End city templates, left alone: "
                        + unmapped);
        helper.assertTrue(
                purpur.isEmpty(), "The Nether palette must place no purpur, found " + purpur);
        helper.succeed();
    }

    /**
     * The purpur block states of one End city template, read through the game's template manager,
     * which loads the vanilla jar's templates on both loaders.
     */
    private static Set<BlockState> purpurStates(ServerLevel level, String name) {
        StructureTemplate template =
                level.getStructureTemplateManager()
                        .get(Identifier.withDefaultNamespace("end_city/" + name))
                        .orElseThrow(
                                () -> new AssertionError("Missing End city template " + name));
        Set<BlockState> states = new LinkedHashSet<>();
        for (Block block : PURPUR) {
            for (StructureTemplate.StructureBlockInfo info :
                    template.filterBlocks(
                            BlockPos.ZERO, new StructurePlaceSettings(), block, false)) {
                states.add(info.state());
            }
        }
        return states;
    }
}
