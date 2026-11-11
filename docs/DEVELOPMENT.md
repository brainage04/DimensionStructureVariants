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

`DimensionStructureVariantsClientGameTest` films every variant in each group of biomes that looks different (the groups are listed in the test; every biome a variant lists must be in one), ordered by dimension, structure and biome group. Each shot is 80 game ticks: the camera circles the structure once, at a fixed distance from its centre, while it descends from looking 30 degrees down to looking 30 degrees up at the centre (less where the ground stops it, 2 blocks above it), driven on the client every frame, while the Overworld clock runs from noon to midnight and the camera player has Night Vision. Before each shot natural spawning and the structure's spawners run with the player in creative mode. A shot runs at half the frame rate its rehearsal measured, up to the game's own 20 ticks a second: Mesa's software rasterizer presents a few frames a second, and slowing the shot down gives each tick two frames or more; a graphics card (`GTR_RECORDING_DISPLAY`, see MinecraftModConventions' README) presents 60 frames or more, so the shot films at 20 ticks a second in its own 4 seconds and the cut keeps that speed instead of speeding it up. The test logs the frame rate it measured and `STAGE_START` and `STAGE_END` around each shot, and the cut plays each window back at 20 ticks a second (4 seconds a shot).

```shell
heavy --mem 10G -- ./gradlew --no-daemon :fabric:recordClientGameTest
GTR_RECORDING_DISPLAY=:1 heavy --mem 10G -- ./gradlew --no-daemon :fabric:recordClientGameTest
DSV_STAGES=fortress_overworld/desert,village_nether heavy --mem 10G -- ./gradlew --no-daemon :fabric:recordClientGameTest
```

`GTR_RECORDING_DISPLAY=:1` records an X display that already has the machine's graphics card behind its GLX (an `Xorg` on the card, or `:0` on a machine whose desktop runs on it) instead of the recorder's own `Xvfb`; the recording's metadata then names the graphics device, the presented frame rate and the reason when the software path was kept.

`DSV_STAGES` (comma separated stage ids such as `fortress_overworld/desert`, or variant ids) films only those stages.

Without `DSV_STAGES`, `CLIENT_GAMETEST_PROFILE=smoke` films one stage of each structure (bastion, end city, fortress, village), spread over the three dimensions; both CI jobs that start the client run that profile, because the full run takes about an hour. Items that mobs dropped as they died while a stage was populated are removed before its shot and logged; any other loose item, chest loot above all, fails the run.

## Placement implementation

The palette table lives in [`palettes.json`](../common/src/main/resources/data/dimensionstructurevariants/palette/palettes.json). A role set names a role for each block a vanilla structure places; a palette gives each role the block of one biome, and a structure names the palette each of its biomes uses. `StructurePalettes` reads the table once, checks that every palette answers every role its structure uses (an explicit null keeps the vanilla block), and compiles a block-state map per structure and palette.

`StructureStartMixin` picks the palette from the biome at the structure start's centre, so all chunks of one structure share it, and lays the palette's ground block in every open column of the pieces' footprint before the pieces are placed. Template, jigsaw and procedural fortress writes then pass through the active palette (`StructureBlockMixin`, a hook on `setBlock`). Compatible block-state properties are preserved, including horizontal wall attachments; unrelated structures and later terrain or player block writes are untouched.

The Nether village pools under `worldgen/template_pool/village_nether` are rigid copies of the plains pools, so every piece stands at the structure's own height instead of following the terrain. Its `start_height` puts that height at y 129, one block above the first free block over the Nether roof, which leaves the ground layer its palettes name (crimson nylium, warped nylium, soul soil, netherrack or blackstone) at y 128; the bedrock roof at y 127 stays untouched.

Custom Nether/End villager types are not added: in Minecraft 26.3, villager types are a built-in static registry rather than a datapack-loaded dynamic registry. New types and their client textures would break the no-client-requirement contract. Existing vanilla villagers and professions remain unchanged.
