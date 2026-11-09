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

## Showcase recording

`DimensionStructureVariantsClientGameTest` films every variant in each group of biomes that looks different (the groups are listed in the test; every biome a variant lists must be in one), ordered by dimension, structure and biome group. Each shot is 80 game ticks: the camera circles the structure once, at a fixed distance from its centre, while it descends from looking 30 degrees down to looking 30 degrees up at the centre (less where the ground stops it, 2 blocks above it), driven on the client every frame, while the Overworld clock runs from noon to midnight and the camera player has Night Vision. Before each shot natural spawning and the structure's spawners run with the player in creative mode. A shot runs at half the frame rate its rehearsal measured (1 to 4 ticks a second), so that software rendering gives each tick two frames or more; the test logs `STAGE_START` and `STAGE_END` around each shot, and the cut plays each window back at 20 ticks a second (4 seconds a shot).

```shell
heavy --mem 10G -- ./gradlew --no-daemon :fabric:recordClientGameTest
DSV_STAGES=fortress_overworld/desert,village_nether heavy --mem 10G -- ./gradlew --no-daemon :fabric:recordClientGameTest
```

`DSV_STAGES` (comma separated stage ids such as `fortress_overworld/desert`, or variant ids) films only those stages.

Without `DSV_STAGES`, `CLIENT_GAMETEST_PROFILE=smoke` films one stage of each structure (bastion, end city, fortress, village), spread over the three dimensions; both CI jobs that start the client run that profile, because the full run takes about an hour. Items that mobs dropped as they died while a stage was populated are removed before its shot and logged; any other loose item, chest loot above all, fails the run.

## Placement implementation

The exact block-to-block tables live in [`StructurePalettes.java`](../common/src/main/java/io/github/brainage04/dimensionstructurevariants/StructurePalettes.java). A shared server-side placement context selects the palette by structure registry ID. Both template/jigsaw writes and procedural fortress foundations pass through it. Compatible block-state properties are preserved, including horizontal wall attachments; unrelated structures and later terrain/player block writes are untouched.

Custom Nether/End villager types are not added: in Minecraft 26.3, villager types are a built-in static registry rather than a datapack-loaded dynamic registry. New types and their client textures would break the no-client-requirement contract. Existing vanilla villagers and professions remain unchanged.
