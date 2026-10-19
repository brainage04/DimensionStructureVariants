# Development

Build both loader artifacts:

```shell
heavy -- ./gradlew --no-daemon build
```

The release JARs are collected under `build/libs`.

Run the registry, biome-targeting, natural-structure-set, End solid-ground, palette and placement-context GameTests on both loaders:

```shell
heavy -- ./gradlew --no-daemon runAllGameTests
```

The project was initialized from [ModernMinecraftModTemplate](https://github.com/brainage04/ModernMinecraftModTemplate) and uses [MinecraftModConventions](https://github.com/brainage04/MinecraftModConventions) for shared build, GameTest, recording, and publishing conventions.

## Placement implementation

The exact block-to-block tables live in [`StructurePalettes.java`](../common/src/main/java/io/github/brainage04/dimensionstructurevariants/StructurePalettes.java). A shared server-side placement context selects the palette by structure registry ID. Both template/jigsaw writes and procedural fortress foundations pass through it. Compatible block-state properties are preserved, including horizontal wall attachments; unrelated structures and later terrain/player block writes are untouched.

Custom Nether/End villager types are not added: in Minecraft 26.2, villager types are a built-in static registry rather than a datapack-loaded dynamic registry. New types and their client textures would break the no-client-requirement contract. Existing vanilla villagers and professions remain unchanged.
