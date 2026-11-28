# DimensionStructureVariants

DimensionStructureVariants is a server-side only Fabric and NeoForge mod for Minecraft 26.3 that adds natural cross-dimension variants of vanilla structures.

The eight variants use three independent random-spread structure sets, so they generate alongside rather than replace vanilla structures. Existing chunks are unchanged; explore new chunks after installing the mod.

## Features

### Variants

- Plains village: Nether and End.
- Nether fortress: Overworld and End.
- Bastion remnant: Overworld and End.
- End city: Overworld and Nether.

The mod keeps vanilla piece layouts and generation algorithms while targeting biomes in another dimension. Every variant substitutes vanilla blocks during placement, in the materials of the biome it stands in. The table, [`palettes.json`](common/src/main/resources/dimensionstructurevariants/palettes.json), is bundled in the mod's jar and cannot be changed by a datapack: one palette per structure and biome, and a default palette per structure for any other biome, such as a beach at the centre of an ocean bastion or a biome a datapack adds to the Nether.

Every palette also names the loot table of its chests, `dimensionstructurevariants:chests/<palette>` (for example `chests/fortress_ocean`), so an ocean fortress holds prismarine, nautilus shells and the heart of the sea instead of nether wart. The tables are data files in the mod's jar, and every chest a variant places carries its palette's table in place of the one its piece or template names. The mobs the fortress and the bastion place come from the biome they stand in: the throne room's spawner holds a monster from the biome's natural monster list instead of a blaze, and the bastion's piglins, piglin brutes, hoglins and lava basin spawner become monsters from that list too. A biome with no monster, such as the mushroom fields, gets no throne room spawner and no bastion mobs.

- Nether fortress in the Overworld: stone bricks with mossy, cracked and chiselled stone bricks in the green biomes, sandstone with cut, smooth and chiselled sandstone and terracotta in the desert, red sandstone with cut, smooth and chiselled red sandstone in the badlands, mossy stone bricks with cobblestone and mossy cobblestone and vines here and there in the jungles, mud bricks with packed mud, coarse and rooted dirt in the swamps, packed ice with blue ice bridges and about 2 blocks in 100 of snow in the snowy biomes, red mushroom blocks with white mushroom stem bridges in the mushroom fields, and prismarine and prismarine bricks with about 3 blocks in 100 of dark prismarine in the warm, temperate and cold oceans. The mix of blocks follows from each block's position, so a fortress looks the same every time its world generates. In the snowy biomes and the mushroom fields the fortress's bridge straights, crossings and end fillers take their own blocks (blue ice and packed ice rails, mushroom stems and white stained glass pane rails), and every other piece takes the main blocks, so the two textures do not fight across the whole build. Its windows hold iron bars, ice, red stained glass panes or a fence of the biome's wood.
- Bastion in the Overworld: oceans only, resting on the sea floor; prismarine in the temperate and cold oceans, sandstone and coral in the warm oceans, stone and ice in the frozen oceans; lava becomes water.
- End city in the Overworld: oceans other than the warm ones. Prismarine and light blue glazing in the plain and cold oceans, packed ice, blue ice, snow, prismarine trims and cyan glazing in the frozen oceans.
- Village in the Nether: one family per Nether biome: nether bricks and red nether bricks in the wastes, crimson materials in the crimson forest, warped materials in the warped forest, soul soil and basalt in the soul sand valley, blackstone and smooth basalt in the basalt deltas. Every village piece is placed rigidly one block above the Nether's surface, which in a vanilla Nether is the bedrock roof, and the village lays its biome's ground under its pieces and in a patch around them whose edge wanders, so it never replaces the bedrock. Where a datapack's Nether terrain reaches the build limit and leaves no room above it, no Nether village generates. The ground mixes its biome's blocks in clumps: crimson or warped nylium with netherrack, with now and then a crimson or warped fungus, soul soil with soul sand, blackstone with basalt, and netherrack in the wastes. Gravel and netherrack stay for the paths. Water becomes lava (farm channels, wells, fountains, ponds), and the fisher cottage's barrels beside it become stripped crimson hyphae so the lava cannot set them alight. The farms keep their composters, although lava's fire can reach a composter standing within a few blocks of it, and their farmland becomes soul sand with nether wart where the crops were.
- End city in the Nether: one family per Nether biome, so the Nether's own blocks form the city.
- Village in the End: outer End islands only; end-stone foundations and paths, purpur roofs/pillars, magenta glazing and end rods.
- Bastion in the End: outer End islands only; end stone, end stone bricks and purpur; lava becomes solid purpur.
- Fortress in the End: end stone bricks, including downward supports, stairs and walls.

End villages and bastions generate only in the outer islands' `end_highlands`/`end_midlands` biomes (never on the main island or in the void ring around it), and only where terrain lies under the core of their footprint, so they never start over the void. End fortresses generate only in the outer islands' `end_highlands`, `end_midlands` and `end_barrens` biomes, not in the main island's biome, where a fortress would stand over the void. Once a start's pieces are laid out, End bastions and fortresses are refused unless an island lies under their footprint: a bastion needs ground under every sampled column within 12 blocks of its pieces, the reach of its terrain adaptation, so the island is not stretched to hold it, and a fortress, which has no terrain adaptation, may overhang the void over at most 40 percent of its footprint. The Nether End city, like vanilla End cities, rises from the surface heightmap, which in the Nether is the bedrock roof.

Unmapped functional blocks (loot containers, workstations, beds, bells and ladders) and entity data stay vanilla. End villages retain wooden doors/trapdoors because vanilla has no End equivalent.

## Getting started

- Fabric Loader >=0.19.5 with Fabric API, or NeoForge >=26.3.0.99.
- Install exactly one matching JAR on the server: the Fabric JAR with Fabric API, or the NeoForge JAR with NeoForge.

Generation uses vanilla blocks and entities only, so vanilla clients can join without installing the mod or a resource pack.

## Commands

No commands yet.

## Configuration

No configuration yet.

## Compatibility

No known incompatibilities.

## Documentation

- [Development](docs/DEVELOPMENT.md) - build, GameTests and placement implementation.
- [Release automation](docs/RELEASE.md) - GitHub release workflow.
- [Modrinth publishing](docs/MODRINTH.md) - optional publishing setup.
