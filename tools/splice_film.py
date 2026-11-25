#!/usr/bin/env python3
"""Replaces one stage of a cut film with the same stage cut from another recording.

usage: splice_film.py FILM.mp4 SIDECAR.json STAGE_FILM.mp4 STAGE_SIDECAR.json OUT.mp4 OUT_SIDECAR.json

STAGE_FILM and STAGE_SIDECAR are what cut_film.py wrote for a recording of that one stage (DSV_STAGES),
so the replacement went through the same cut, with the same refusals, as every other stage. The stage
is found by its id in both sidecars and must have the same number of frames in both. The frames of the
other stages are decoded and encoded again with the cut's encoder settings, so they are one encode
generation older than the replacement.
"""
import json
import subprocess
import sys
from pathlib import Path

RATE = 30.0

film, sidecar, stage_film, stage_sidecar, out, out_sidecar = map(Path, sys.argv[1:7])
shots = json.loads(sidecar.read_text())
new = json.loads(stage_sidecar.read_text())
if len(new["shots"]) != 1:
    raise SystemExit(f"{stage_sidecar} holds {len(new['shots'])} stages, not one")
replacement = new["shots"][0]
ids = [s["stage"] for s in shots["shots"]]
if replacement["stage"] not in ids:
    raise SystemExit(f"{replacement['stage']} is not a stage of {sidecar}")
index = ids.index(replacement["stage"])


def frames(shot):
    return round(shot["video_start_s"] * RATE), round(shot["video_end_s"] * RATE)


first, end = frames(shots["shots"][index])
new_first, new_end = frames(replacement)
if end - first != new_end - new_first:
    raise SystemExit(f"the old stage has {end - first} frames, the new one {new_end - new_first}")

graph = []
parts = []
total = round(shots["shots"][-1]["video_end_s"] * RATE)
if first > 0:
    graph.append(f"[0:v]select='lt(n,{first})',setpts=N/({RATE:.0f}*TB)[a]")
    parts.append("[a]")
graph.append(f"[1:v]setpts=N/({RATE:.0f}*TB)[b]")
parts.append("[b]")
if end < total:
    graph.append(f"[0:v]select='gte(n,{end})',setpts=N/({RATE:.0f}*TB)[c]")
    parts.append("[c]")
graph.append("".join(parts) + f"concat=n={len(parts)}:v=1:a=0[vout]")
subprocess.run([
    "ffmpeg", "-v", "error", "-y", "-i", str(film), "-i", str(stage_film),
    "-filter_complex", ";".join(graph), "-map", "[vout]", "-an",
    "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p",
    "-r", f"{RATE:.0f}", str(out),
], check=True)

old = shots["shots"][index]
replacement = dict(replacement)
for key in ("video_start_s", "video_end_s", "palette_at_s"):
    replacement[key] = old[key] if key != "palette_at_s" else round(
        old["video_start_s"] + (replacement["palette_at_s"] - new["shots"][0]["video_start_s"]), 3)
shots["shots"][index] = replacement
shots["video"] = str(out)
shots["source_recording"] = [shots["source_recording"], new["source_recording"]]
out_sidecar.write_text(json.dumps(shots, indent=2) + "\n")
print(f"stage {replacement['stage']} (frames {first}-{end - 1}) replaced; wrote {out} and {out_sidecar}")
