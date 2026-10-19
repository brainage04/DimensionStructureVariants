# DimensionStructureVariants

DimensionStructureVariants is a server-side only Fabric and NeoForge mod for Minecraft 26.2 that adds natural cross-dimension variants of vanilla structures.

The eight variants use three independent random-spread structure sets, so they generate alongside rather than replace vanilla structures. Existing chunks are unchanged; explore new chunks after installing the mod.

## Features

### Variants

- Plains village: Nether and End.
- Nether fortress: Overworld and End.
- Bastion remnant: Overworld and End.
- End city: Overworld and Nether.

The mod keeps vanilla piece layouts, loot tables, mobs, and generation algorithms while targeting biomes in another dimension. Selected variants substitute vanilla blocks during placement:

- End city in the Overworld: cold/frozen oceans only; packed ice, blue ice, snow, prismarine trims and cyan glazing.
- Bastion in the Overworld: ocean biomes only; prismarine, prismarine bricks, dark prismarine and sea lanterns; lava becomes water.
- Village in the End: outer End islands only; end-stone foundations and paths, purpur roofs/pillars, magenta glazing and end rods.
- Bastion in the End: outer End islands only; end stone, end stone bricks and purpur; lava becomes solid purpur.
- Fortress in the End: end stone bricks, including downward supports, stairs and walls.
- Village in the Nether: generic Nether palette: nether bricks, red nether bricks, basalt, blackstone, crimson fittings and gravel paths.

The ocean bastion is intentionally a maritime fortress on its natural terrain-adapted island, not a blackstone/lava land structure stranded at sea. The Overworld fortress and Nether End city retain their original palettes.

End villages and bastions generate only in the outer islands' `end_highlands`/`end_midlands` biomes (never on the main island or in the void ring around it), and only where terrain lies under the core of their footprint, so they never start over the void. The Nether End city, like vanilla End cities, rises from the surface heightmap, which in the Nether is the bedrock roof.

Unmapped functional blocks (loot containers, workstations, beds, bells and ladders) and entity data stay vanilla. End villages retain wooden doors/trapdoors because vanilla has no End equivalent.

## Getting started

- Fabric Loader >=0.19.3 with Fabric API, or NeoForge >=26.2.0.88.
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
