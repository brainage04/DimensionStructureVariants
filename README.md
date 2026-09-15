# DimensionStructureVariants

DimensionStructureVariants adds natural cross-dimension variants of vanilla structures.

## Variants

The mod keeps vanilla piece layouts, loot tables, mobs, and generation algorithms while targeting biomes in another dimension. Selected variants substitute vanilla blocks during placement:

| Source structure | Added dimensions |
| --- | --- |
| Plains village | Nether and End |
| Nether fortress | Overworld and End |
| Bastion remnant | Overworld and End |
| End city | Overworld and Nether |

The eight variants use three independent random-spread structure sets, so they generate alongside rather than replace vanilla structures. Existing chunks are unchanged; explore new chunks after installing the mod.

## Dimension palettes

| Variant | Palette and placement |
| --- | --- |
| End city in the Overworld | Cold/frozen oceans only; packed ice, blue ice, snow, prismarine trims and cyan glazing |
| Bastion in the Overworld | Ocean biomes only; prismarine, prismarine bricks, dark prismarine and sea lanterns; lava becomes water |
| Village in the End | End-stone foundations and paths, purpur roofs/pillars, magenta glazing and end rods |
| Bastion in the End | End stone, end stone bricks and purpur; lava becomes solid purpur |
| Fortress in the End | End stone bricks, including downward supports, stairs and walls |
| Village in the Nether | Generic Nether palette: nether bricks, red nether bricks, basalt, blackstone, crimson fittings and gravel paths |

The ocean bastion is intentionally a maritime fortress on its natural terrain-adapted island, not a blackstone/lava land structure stranded at sea. The Overworld fortress and Nether End city retain their original palettes.

The exact block-to-block tables live in [`StructurePalettes.java`](common/src/main/java/io/github/brainage04/dimensionstructurevariants/StructurePalettes.java). A shared server-side placement context selects the palette by structure registry ID. Both template/jigsaw writes and procedural fortress foundations pass through it. Compatible block-state properties are preserved, including horizontal wall attachments; unrelated structures and later terrain/player block writes are untouched.

Unmapped functional blocks (loot containers, workstations, beds, bells and ladders) and entity data stay vanilla. End villages retain wooden doors/trapdoors because vanilla has no End equivalent. Nether villages still use the existing terrain projection, which can put houses on the bedrock roof; this palette change does not alter that placement behavior.

Custom Nether/End villager types are not added: in Minecraft 26.2, villager types are a built-in static registry rather than a datapack-loaded dynamic registry. New types and their client textures would break the no-client-requirement contract. Existing vanilla villagers and professions remain unchanged.

## Loaders and installation

Fabric and NeoForge builds are provided for Minecraft 26.2.

Install exactly one matching JAR on the server: the Fabric JAR with Fabric API, or the NeoForge JAR with NeoForge. Generation uses vanilla blocks and entities only, so vanilla clients can join without installing the mod or a resource pack.

## Development

Build both loader artifacts:

```shell
./gradlew build
```

The release JARs are collected under `build/libs`.

Run the registry, biome-targeting, and natural-structure-set GameTests on both loaders:

```shell
./gradlew runAllProductionGameTests
```

The project was initialized from [ModernMinecraftModTemplate](https://github.com/brainage04/ModernMinecraftModTemplate) and uses [FabricModdingConventions](https://github.com/brainage04/FabricModdingConventions) for shared build, GameTest, recording, and publishing conventions.

Release automation is documented in [docs/RELEASE.md](docs/RELEASE.md). Optional Modrinth publishing is documented in [docs/MODRINTH.md](docs/MODRINTH.md).
