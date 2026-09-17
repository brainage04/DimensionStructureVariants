# DimensionStructureVariants icon

## What this is

The mod's icon: `icon.png` — 1024x1024 RGB PNG, 827 953 bytes,
sha256 `87f1cffb6b744481e4ada2cc38d44de44753eb0a3b93155236c784376dfc0082`.

It shows this mod's `dimensionstructurevariants:end_city_overworld` structure — an End
city generated naturally in the Overworld — sitting on a cold ocean, and is a real
in-game screenshot with a single square crop and one resample.

## How it was made

**Method: real Minecraft capture** (not a Blender render, not generated pixel art). The
default End-city geometry was left untouched; the structure comes from the mod's own
structure type, from ordinary natural world generation.

Capture session:

| | |
|---|---|
| Client | Minecraft 26.2, Fabric Loader 0.19.3, OpenJDK 25.0.4.1+1 |
| Mod | the approved DimensionStructureVariants build `dimensionstructurevariants-1.0.0.jar` (shipped in `provenance/mods/`, 23 474 bytes, byte-identical to the build that was approved) |
| Display | Xvfb `:205`, 1600x1200x24, software OpenGL (Mesa llvmpipe); the primary desktop was never used |
| Audio | own PulseAudio null sink `round3_dimension5` |
| Shader pack | **none** |
| Resource pack | **none** — no recolouring and no compositing either |
| World | `Dimension Natural 4`, seed `4680596595205837332`; a byte-copy of the approved `captures-dimension4` natural save, in which four additional structure starts were found by a read-only region scan and one `/locate structure` query, then given ordinary chunk generation by visiting them (no `/place`, no hand-building, no block edits) |
| Camera | `/tp @s -1360 152 -1130 facing -1505 107 -1076` → camera at x=-1359.5, y=152, z=-1129.5 |
| Lighting | `/time set noon` before the final F2, `/weather clear`, `/effect clear @s` — native daylight, **no night vision** |
| Screenshot | the game's own native F2 PNG, taken with **F1** (HUD hidden), no post-processing |

The structure at that location: `end_city_overworld`, 82 pieces, start chunk `[-92, -66]`,
structure start `[-1472, ~, -1056]`, centre `(-1508.5, 107.0, -1076.5)`, piece bounding box
`[-1556, 62, -1107] .. [-1461, 152, -1046]`, chunk status `minecraft:full`. A nearby
fortress falls outside the final crop.

The delivered image is the 700x700 px window at `(410, 279)` of the raw F2 frame
`2026-09-16_17.13.25.png` — centred on the visually annotated city bounding box
`[542, 382, 978, 876]` (structure margins L132 / R132 / T103 / B103 px) — Lanczos-resampled
to 1024x1024. The annotated bounding box and the camera are recorded in
`capture-selections.json`; the 700 px window and the Lanczos step are recorded in the
`manifest.json` `notes` (no script was kept for that final re-output). Verified while
creating this provenance: re-applying the recorded window to the raw frame reproduces
`icon.png` byte for byte.

## Provenance files

| Path | What it is |
|---|---|
| `manifest.json` | Round-3 delivery record: label, method, source, camera command, coordinates, lighting, crop rectangle, centring maths, notes |
| `capture-selections.json` | Per-image selection: raw screenshot, chunk, camera command/XYZ, annotated source bounding box, output size |
| `reproduce.json` | Every command of the session in order: Xvfb, audio sink, client, control, scan, crop-and-verify |
| `source-frames/2026-09-16_17.13.25.png` | **The raw native F2 screenshot this icon is cropped from** (the session's `runtime/screenshots/…`) |
| `tools/finalize.py` | The crop/verification script: lossless rectangular crop around the annotated bounds, centre-offset assertions, per-image measured overlays, manifest and crop-verification writer |
| `tools/control.py` | The verified chat-command driver for the capture client |
| `tools/scan-world.py` | Read-only region scan that enumerated natural structure starts |
| `evidence/commands.txt` | Timestamped log of every camera, time, weather and `/locate` command actually executed |
| `evidence/natural-structure-starts.json` | The scanned starts with chunk status and bounding boxes |
| `evidence/natural-generation-delta.json` | What visiting the new starts changed |
| `evidence/crop-verification.json` | Per-image source/result checksums, centre offsets and margins |
| `evidence/source-build.json` | Proof that the mod build used is the approved one |
| `evidence/audio-routing-latest.txt`, `evidence/cpu-policy.txt` | Isolation evidence (own sink; client kept at CPUWeight 20) |
| `launch.sh`, `client.args` | The exact launcher and Java argfile of the session |
| `cleanup-report.json`, `blockers.json` | Resource teardown record and the (empty) blocker list |

Excluded on purpose: the game directory `runtime/` (22 MB — only the one source frame is
kept), session logs, and all the other candidate previews/overlays/contact sheets in
`evidence/` (~16 MB of PNGs that document *unchosen* framings rather than this icon).

## How to regenerate

The session ran with working directory `<round3>/captures-dimension5`. Restore
`provenance/` to that name and copy the source frame back where the session had it:

```sh
cd captures-dimension5
mkdir -p runtime/screenshots
cp provenance/source-frames/2026-09-16_17.13.25.png runtime/screenshots/

# the four final crops + the measured-overlay images + the verification records:
PYTHONPATH=/nix/store/4v9j9wbzyhrlx9980ygbr812313mazy0-python3.13-pillow-12.3.0/lib/python3.13/site-packages \
  python3 tools/finalize.py

# the delivered 1024x1024 re-output of image 03 (see Notes):
python3 - <<'PY'
from PIL import Image
raw = Image.open('runtime/screenshots/2026-09-16_17.13.25.png').convert('RGB')
raw.crop((410, 279, 1110, 979)).resize((1024, 1024), Image.Resampling.LANCZOS) \
   .save('renders/03-three-spire-ocean-city.png')
PY
```

`tools/finalize.py` writes `renders/03-three-spire-ocean-city.png` as the 680x700 output
recorded in the manifest; the second command is the later 1024x1024 re-output that is the
delivered icon — it is one line of Pillow and is reproduced here exactly as recorded, since
the original session did not keep a script for that step.

Prerequisites not shipped: the Minecraft 26.2 client, Fabric Loader, the world save and the
byte-identical mod build (the jar *is* shipped; it is dropped into the session's mods
directory).

## Notes

* **The manifest's `sha256`/`dimensions`/`cropRectangleXYXY` fields for this image are one
  step out of date.** `evidence/crop-verification.json` and the manifest's structured fields
  describe the intermediate **680x700** crop (crop box `[420, 279, 1100, 979]`, sha256
  `c86e408f…`). The file that was actually delivered — and that is copied here — is the
  **1024x1024** re-output described only in the manifest's free-text `notes`, sha256
  `87f1cffb…`. Both describe the same composition; only the canvas differs.
* Re-applying the recorded window `(410, 279, 1110, 979)` plus Lanczos to 1024 to the raw
  frame reproduces `icon.png` byte for byte (checked while creating this provenance).
* The annotation of the city bounding box has an uncertainty of about ±3 px; the crop
  centres the annotated rectangle with an exact centre offset of 0.0 px on both axes.
* The crop is lossless (integer rectangle) but the 1024 re-output is a Lanczos resample of a
  700 px window, so `icon.png` is *not* a native-resolution pixel copy — this is the only
  resampled step.
* Three of the four captures of this session were not chosen and are not reproduced here;
  `capture-selections.json` still documents them.

## Working-tree note

The round-3 working tree that produced this icon was cleaned up after integration. Every file needed to regenerate the icon was copied into `provenance/`; the copies live under `provenance/from-round3/` when they came from the working tree. Any remaining `round3/...` mention records where something came from, not a path that still exists.
