# DimensionStructureVariants

DimensionStructureVariants is a server-side only Fabric and NeoForge mod for Minecraft 26.3 that adds natural cross-dimension variants of vanilla structures.

The eight variants use three independent random-spread structure sets, so they generate alongside rather than replace vanilla structures. Existing chunks are unchanged; explore new chunks after installing the mod.

## Features

### Variants

- Plains village: Nether and End.
- Nether fortress: Overworld and End.
- Bastion remnant: Overworld and End.
- End city: Overworld and Nether.

The mod keeps vanilla piece layouts, loot tables, mobs and generation algorithms, and places each structure in biomes of another dimension. As it places a variant, the mod swaps vanilla blocks for blocks that suit the biome at the structure's centre. The swaps are listed in [`palettes.json`](common/src/main/resources/dimensionstructurevariants/palettes.json), which ships inside the mod's jar, so a datapack cannot change them. Each variant has a palette for each biome it names and a default palette for any other biome, such as a beach at the centre of an ocean bastion or a biome a datapack adds to the Nether. Where a palette mixes several blocks, each block's position picks the block, so a structure looks the same every time its world generates.

- Nether fortress in the Overworld: lava becomes water, and the blocks follow the biome.
  - Deserts: sandstone with smooth, cut and chiselled sandstone, and orange and blue terracotta.
  - Badlands: red sandstone with terracotta.
  - Jungles: mossy stone bricks, stone bricks, mossy cobblestone and cobblestone.
  - Swamps: mud bricks with packed mud, coarse dirt and rooted dirt.
  - Snowy biomes: packed ice with snow, blue ice, ice and powder snow.
  - Mushroom fields: mushroom stems with brown and red mushroom blocks.
  - Warm, lukewarm, plain and cold oceans: prismarine bricks, prismarine and dark prismarine.
  - Frozen oceans: prismarine bricks and prismarine with packed ice and blue ice.
  - Every other biome: stone bricks with mossy, cracked and chiselled stone bricks.

  The nether brick fences become glass panes in the snowy biomes, a wooden fence in the badlands, jungles and swamps, and iron bars everywhere else.
- Bastion in the Overworld: oceans only, and lava becomes water. Plain and cold oceans get prismarine, prismarine bricks and dark prismarine. Warm and lukewarm oceans get sandstone with brain coral blocks. Frozen oceans get stone bricks, packed ice, blue ice, ice and snow. Gold blocks become sea lanterns.
- End city in the Overworld: plain, cold and frozen oceans, deep ones included. Plain and cold oceans get prismarine, prismarine bricks, dark prismarine and light blue glass. Frozen oceans get packed ice, blue ice, snow, prismarine stairs and slabs, and cyan glass.
- Village in the Nether: a palette for each Nether biome.
  - Nether wastes and any other biome: nether bricks, red nether bricks and basalt, on netherrack.
  - Crimson forest: nether bricks with crimson stems and planks, on crimson nylium and netherrack, with now and then a huge crimson fungus.
  - Warped forest: nether bricks with warped stems and planks, on warped nylium and netherrack, with now and then a huge warped fungus.
  - Soul sand valley: blackstone, smooth basalt and basalt, on soul soil, soul sand and bone blocks.
  - Basalt deltas: polished blackstone, smooth basalt and basalt, on blackstone, basalt and gilded blackstone.

  Every piece of a Nether village stands at the same height, just above the Nether's surface, which in a vanilla Nether is the bedrock roof. The village fills the empty blocks under its pieces, and a patch around them with an uneven edge, with its biome's ground in clumps. It never replaces the bedrock. Where a datapack's Nether terrain reaches the top of the world and leaves no room above it, no Nether village generates. Dirt paths become gravel. Water becomes lava in the farm channels, wells, fountains and ponds. Barrels become stripped crimson hyphae, so the lava cannot set them alight. Farmland becomes soul sand, and crops become nether wart. Composters stay, and lava can set fire to a composter within a few blocks of it.
- End city in the Nether: a palette for each Nether biome. The wastes and any other biome get nether bricks and red nether bricks. The crimson and warped forests get their own stems and planks. The soul sand valley gets soul soil, soul sand and basalt. The basalt deltas get blackstone and basalt.
- Village in the End: outer End islands only; end-stone foundations and paths, purpur roofs/pillars, magenta glazing and end rods.
- Bastion in the End: outer End islands only; end stone, end stone bricks and purpur; lava becomes solid purpur.
- Fortress in the End: end stone bricks, including downward supports, stairs and walls.

End villages and bastions generate only in the outer islands' `end_highlands`/`end_midlands` biomes (never on the main island or in the void ring around it), and only where terrain lies under the core of their footprint, so they never start over the void. The Nether End city, like vanilla End cities, rises from the surface heightmap, which in the Nether is the bedrock roof.

Other functional blocks (loot containers, workstations, beds, bells and ladders) and entity data stay vanilla, with two exceptions. In End villages composters become purpur blocks and barrels become purpur pillars. In Nether villages barrels become stripped crimson hyphae. End villages keep wooden doors and trapdoors, because vanilla has no End version of them.

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
