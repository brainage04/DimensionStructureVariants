# DimensionStructureVariants

DimensionStructureVariants adds natural cross-dimension variants of vanilla structures.

## Variants

The mod keeps each structure's vanilla pieces, loot, mobs, and generation algorithm while targeting biomes in another dimension:

| Source structure | Added dimensions |
| --- | --- |
| Plains village | Nether and End |
| Nether fortress | Overworld and End |
| Bastion remnant | Overworld and End |
| End city | Overworld and Nether |

The eight variants use three independent random-spread structure sets, so they generate alongside rather than replace vanilla structures. Existing chunks are unchanged; explore new chunks after installing the mod.

## Loaders and installation

Fabric and NeoForge builds are provided for Minecraft 26.2.

Install exactly one matching JAR on the server: the Fabric JAR with Fabric API, or the NeoForge JAR with NeoForge. The structures are data-driven and vanilla clients can join without installing the mod.

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
