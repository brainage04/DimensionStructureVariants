# DimensionStructureVariants todo

## Loader parity findings (2026-09-29)

From running the release NeoForge jar on a real NeoForge 26.2.0.41-beta server and client. Items marked *both loaders* come from shared code.

- [x] **Medium, both loaders:** End village and End bastion starts over the void generate at y≈0 (a lone bell; a bastion whose piglins fall out). `village_end.json`/`bastion_end.json` allow all of `#minecraft:is_end` with `start_height 0` and `WORLD_SURFACE_WG` projection; restrict them to island biomes or require ground.
  - Fixed: biomes limited to `end_highlands`/`end_midlands`, and a `JigsawStructure` mixin rejects starts of `#dimensionstructurevariants:requires_solid_ground` structures unless terrain lies under a 64×64 footprint; shared GameTest `end_island_variants_only_start_on_solid_ground`.
- [x] Low: the Nether End city generates on the bedrock roof; the README only documents roof placement for Nether villages.
  - Intended (vanilla End city surface placement, matching Nether villages); now documented in the README.
