# Development

Build both loader artifacts:

```shell
heavy -- ./gradlew --no-daemon build
```

The release JARs are collected under `build/libs`.

Run the registry, biome-targeting, natural-structure-set, End solid-ground, palette, placement-context and natural-generation GameTests on both loaders. The natural-generation test places a vanilla fortress and the Overworld fortress variant through the FEATURES step's `WorldGenRegion`, in a chunk two or more chunks from each start's centre, as natural generation does; a GameTest world makes no structure starts of its own.

```shell
heavy -- ./gradlew --no-daemon runAllGameTests
```

Run the unit tests: `WeightedPickTest` checks with jqwik that the weighted pick behind a palette role with several blocks (`WeightedPick`) stays among the role's blocks and picks each about as often as its weight says, and pins the pick to a reference copy of its arithmetic, so a structure keeps its blocks from one release to the next. `StructurePalettesTest` checks the palette table and lookup without a server: a variant centred in a biome its rules do not name (a beach, a warm ocean, a datapack Nether biome) gets its default palette, a table with an unknown block id or a structure without a default fails to load, and a lookup through a FEATURES `WorldGenRegion` reads no chunk the region withholds.

```shell
heavy --quick -- ./gradlew --no-daemon :fabric:test
```

`:fabric:pitest` runs mutation tests on `WeightedPick` against those tests. It is not part of `check`; the report is written to `fabric/build/reports/pitest`.

`check` and `build` run the MinecraftModConventions quality policy (formatting, Checkstyle, deprecation lints as errors) and its vanilla identity, tag identity and world height checks, in CI too. Keep `minecraftmodconventions_version` and the reusable-workflow versions in `.github/workflows/` the same.

The project was initialized from [ModernMinecraftModTemplate](https://github.com/brainage04/ModernMinecraftModTemplate) and uses [MinecraftModConventions](https://github.com/brainage04/MinecraftModConventions) for shared build, GameTest, recording, and publishing conventions.

## Showcase recording

The film lives in the `recording` source set (`fabric/src/recording`): `DimensionStructureVariantsFilm` with its camera (`ShowcaseCamera`), its biome panel (`StageCaptionPanel`), the film-only mixins and the Nether dimension type with the longer fog. `fabric/src/gametest` holds only tests; its client GameTest, `DimensionStructureVariantsClientGameTest`, runs the film over the variants of `StructureVariantCatalog`.

`DimensionStructureVariantsFilm` films every variant in each group of biomes that looks different (the test lists the groups, and every biome a variant lists must be in one), ordered by dimension, structure and biome group. Each shot is 121 frames of the film, 4 seconds at 30 frames a second. The camera (`ShowcaseCamera`, on `CameraPlan`'s path) starts looking 60 degrees down and, as one function of the film's frame number, circles the structure twice at a fixed distance while it descends to the structure's lowest layer and its pitch goes to 0. Where the world's own terrain stands within the 10-block clearance of the orbit (the End's islands), the camera ends that clearance above it instead. Frame 60, the 2-second mark, is where the palette goes in: the camera stops off camera while the server swaps the blocks and the client builds them, and the next frame is frame 60 of the same path. The Overworld clock is held at noon for the whole shot and the camera player has Night Vision. Before each shot natural spawning and the structure's spawners run with the player in creative mode. A shot logs every frame the camera applied (`FRAME` lines), checks them against the plan (`path: 121 frames, worst deviation ...`) and fails the run when a frame differs from the plan or when fewer than 8 frames in 10 the client drew show a new picture. It logs a picture that stands still for more than two and a half of the client's frames without failing the run, because the cut drops the capture's repeated slots. The camera must draw its last frame within `MAX_SHOT_TICKS` ticks. The test logs the `STAGE_START` and `STAGE_END` markers around each half of a shot, and the cut plays each half back at the capture's 30 frames a second.

Each shot opens on the structure's pieces in their vanilla blocks and switches to the mod's palette when the camera is half way round, so the first half shows the structure without the palette and the second half with it. The test keeps the blocks the mod built, places the same pieces again as the vanilla structure (`minecraft:fortress`, `minecraft:bastion_remnant`, `minecraft:end_city` or `minecraft:village_plains`, which no palette touches) and writes the kept blocks back halfway through the shot, the vanilla fluids first (`DimensionStructureVariantsFilm.writeBack`): lava set beside vanilla water that is not yet replaced becomes obsidian or cobblestone. A Nether stage goes back to its bare roof first, because the village's ground patch and fungi belong to the palette, and the structure's own entities, which the vanilla pieces place again, are removed. The pop-in check takes the built sections afresh once the client has rebuilt the switched ones. A panel under the caption names every biome the shot's palette covers and whether the palette is on; it says the palette is on once the client has rebuilt the switched sections, a few ticks after the switch on a large stage.

End stages stand on terrain that generated with the structure's start already in its chunk, as natural generation does, so the terrain rises under the pieces where the structure's terrain adaptation says. The test server makes no structure starts itself (MinecraftModConventions turns `generate-structures` off), so the test gives the start to its chunk before the chunks around it generate.

```shell
heavy --mem 10G -- ./gradlew --no-daemon :fabric:recordClientGameTest
GTR_RECORDING_DISPLAY=:1 heavy --mem 10G -- ./gradlew --no-daemon :fabric:recordClientGameTest
DSV_STAGES=fortress_overworld/desert,village_nether heavy --mem 10G -- ./gradlew --no-daemon :fabric:recordClientGameTest
```

`GTR_RECORDING_DISPLAY=:1` records an X display that already has the machine's graphics card behind its GLX (an `Xorg` on the card, or `:0` on a machine whose desktop runs on it) instead of the recorder's own `Xvfb`; the recording's metadata then names the graphics device, the presented frame rate and the reason when the software path was kept.

The camera's radius follows the 98th percentile of the structure's own blocks around its centre
(`framedHalf`), so a long empty corridor end or an End ship's tip does not push the camera out; the
10-block clearance belongs to that framed mass, while a block outside it only has to be missed.
Stages that stand on a ground of their own have the blocks that stand on that ground outside the
structure's footprint swept before the shot, so a mushroom on the bare bedrock cannot appear in the
foreground.

`tools/cut_film.py` cuts the recording to its shots from the `STAGE_START`/`STAGE_END` markers and the
test's `FRAME` log lines, one clip per stage. Each half keeps exactly the frames the camera drew, in
order (frame 0 first, frame 59 last of the vanilla half, frame 60 first of the palette half), and
refuses a half in which the recorder dropped a frame (`CAPTURE_DROPS` lines) or whose capture holds
fewer changing slots than the camera drew frames. The two halves are joined with no halt, so the blocks and the caption
change on one frame and the camera carries on. The film has no sound: it shows 30 ticks of game time
a second, against the game's 20, so the recording's sound cannot line up with it.

`DSV_STAGES` (comma separated stage ids such as `fortress_overworld/desert`, or variant ids) films only those stages.

Without `DSV_STAGES`, `CLIENT_GAMETEST_PROFILE=smoke` films one stage of each structure (bastion, end city, fortress, village), spread over the three dimensions; both CI jobs that start the client run that profile, because the full run takes about an hour. Items that mobs dropped as they died while a stage was populated are removed before its shot and logged; any other loose item, chest loot above all, fails the run.

A biome's drone is a streamed sound, so the sound engine holds four one-second buffers of it and
refills them once per frame (`ChannelAccess.scheduleTick`, reached from `SoundEngine.tick`); placing a
stage's structure stalls the client for seconds, the buffers run dry, the engine drops the loop, and
vanilla never replays it until the player's ambient setting changes (`BiomeAmbientSoundsHandler.tick`
only reacts to a changed loop event). The recording client therefore runs `AmbientLoopKeeperMixin`,
which forgets a loop instance the engine has dropped so the handler recreates it, and logs each
restart. The mixin is in the recording source set and is never loaded by the mod.

## Placement implementation

The palette table lives in [`palettes.json`](../common/src/main/resources/dimensionstructurevariants/palettes.json), outside `data/`, because it is read from the mod's jar and not from datapacks. A role set names a role for each block a vanilla structure places; a palette gives each role the block of one biome, and a structure names the palette each of its biomes uses and a `default` palette for every biome its rules do not name, such as a beach at the centre of an ocean bastion or a biome a datapack adds to `#minecraft:is_nether`. `StructurePalettes` reads the table once, checks that every block id exists and that every palette answers every role its structure uses (an explicit null keeps the vanilla block), and compiles a block-state map per structure and palette.

`StructureStartMixin` picks the palette from the biome at the structure start's centre, so all chunks of one structure share it, and lays the palette's ground before the pieces are placed (`StructureGround`). Structures outside the mod's namespace return before any biome is read. During natural generation the start's centre can lie in a chunk the decorating `WorldGenRegion` may not read, so there the biome comes from the generator's biome source (`getUncachedNoiseBiome`), which is the noise biome a freshly generated chunk stores, except where the chunk is blended with chunks from before 1.18; placement through a `ServerLevel` (`/place`, the GameTests and the film) reads the chunk's stored biome. Template, jigsaw and procedural fortress writes then pass through the active palette (`StructureBlockMixin`, a hook on `setBlock` that also reads the position). A role may name several blocks with weights, for example `{"minecraft:stone_bricks": 10, "minecraft:cracked_stone_bricks": 3}`. The block at a position comes from a hash of the packed block position alone, so a structure looks the same every time its world generates. Compatible block-state properties are preserved, including horizontal wall attachments, and a number the target block cannot hold, such as a wheat age of 7 on nether wart's 0 to 3, is cut down to the target's largest so a grown crop turns into grown wart. Unrelated structures and later terrain or player block writes are untouched.

`StructureGround` lays the ground in every open column under the pieces and in each column whose distance to the nearest piece stays under an edge of 4 blocks, which simplex noise moves by up to 3 blocks either way, so the patch has the round, wandering outline of terrain and stays inside the 12 blocks a structure with terrain adaptation adds to its bounding box. A palette's `ground` may also name several blocks with weights, picked per clump of about 4 blocks whose borders the noise bends, and its `ground_feature` names a feature (a planted crimson or warped fungus) that grows with the given chance on a ground column at least 5 blocks from every piece.

The Nether village pools under `worldgen/template_pool/village_nether` are rigid copies of the plains pools, so every piece stands at the structure's own height instead of following the terrain. Its `start_height` of 1, projected onto the `WORLD_SURFACE_WG` heightmap like a vanilla village's, puts that height one block above the first free block over the generator's terrain. In the vanilla Nether that block is y 128, over the roof, so the pieces stand at y 129, which leaves the ground layer its palettes name (crimson nylium, warped nylium, soul soil, netherrack or blackstone) at y 128; the bedrock roof at y 127 stays untouched. Its `dimension_padding` of 1 at the top drops a start whose centre piece would reach the dimension's top block or beyond (`JigsawPlacement.java:168-178`), which happens only where a datapack's Nether terrain reaches the build limit: projected onto that terrain, the village would lie wholly above the world.

Custom Nether/End villager types are not added: in Minecraft 26.3, villager types are a built-in static registry rather than a datapack-loaded dynamic registry. New types and their client textures would break the no-client-requirement contract. Existing vanilla villagers and professions remain unchanged.
