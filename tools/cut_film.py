#!/usr/bin/env python3
"""Cuts a DSV recording into one clip per stage and joins each stage's two halves with a crossfade.

usage: cut_film.py LOG VIDEO META.json OUT.mp4 SIDECAR.json

Each stage prints four markers: `STAGE_START`/`STAGE_END <ms> <id>` around the vanilla half and
`STAGE_START`/`STAGE_END <ms> <id>/on` around the palette half. Between the halves the camera and
the game stand still while the server swaps the blocks and the client builds them; the cut drops
that pause, so the two halves' audio are not contiguous in the capture and a hard join clicks.

The join is a crossfade of JOIN seconds: the vanilla half's audio keeps its last JOIN seconds and
the palette half's audio starts JOIN seconds before its picture, so the two overlap and the clip
keeps the length of its two halves. The vanilla half's picture loses those JOIN seconds, which are
frames of the pose the camera stopped on, so nothing moves in them. A stage's first and last
JOIN seconds fade, so a cut between stages does not jump.

The capture is variable frame rate, so a frame is found by its own timestamp, not by n / rate.
"""
import bisect
import json
import re
import subprocess
import sys
from pathlib import Path

log_path, video_path, meta_path, out_path, sidecar_path = map(Path, sys.argv[1:6])
log = log_path.read_text(errors="replace")
meta = json.loads(meta_path.read_text())
RATE = 30.0
LEAD = 0.10
TRAIL = 0.12
JOIN = 0.15
PALETTE_HALF = "/on"

starts = [(int(m.group(1)), m.group(2)) for m in re.finditer(r"STAGE_START (\d+) (\S+)", log)]
ends = [(int(m.group(1)), m.group(2)) for m in re.finditer(r"STAGE_END (\d+) (\S+)", log)]
if not starts or len(starts) != len(ends) or len(starts) % 2:
    raise SystemExit(f"markers: {len(starts)} starts, {len(ends)} ends")
stages = []
for i in range(0, len(starts), 2):
    (s1, id1), (e1, end1), (s2, id2), (e2, end2) = starts[i], ends[i], starts[i + 1], ends[i + 1]
    if not (id1 == end1 and id2 == end2 == id1 + PALETTE_HALF and s1 < e1 < s2 < e2):
        raise SystemExit(f"marker mismatch near {id1}: {id1} {end1} {id2} {end2}")
    stages.append((id1, s1, e1, s2, e2))

summary = {}
for m in re.finditer(r"\[DSV\] SUMMARY (.*)", log):
    fields = [f.strip() for f in m.group(1).split(" | ")]
    summary[fields[0]] = fields

epoch = float(meta["boundaries"]["selectedStartEpochSeconds"])
probe = subprocess.run(
    ["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries",
     "stream=time_base:packet=pts", "-of", "json", str(video_path)],
    check=True, capture_output=True, text=True).stdout
probed = json.loads(probe)
num, den = (int(x) for x in probed["streams"][0]["time_base"].split("/"))
pts = sorted(int(p["pts"]) for p in probed["packets"])
SOURCE_TIMES = [(p - pts[0]) * num / den for p in pts]


def at(ms):
    return max(0.0, ms / 1000.0 - epoch)


graph = []
parts = []
shots = []
position = 0.0
for index, (stage, s1, e1, s2, e2) in enumerate(stages):
    begin = max(0.0, at(s1) - LEAD)
    off_end = at(e1) + TRAIL
    on_begin = max(0.0, at(s2) - LEAD)
    finish = at(e2) + TRAIL
    # The vanilla half's picture stops JOIN seconds early; its audio does not, so the two halves'
    # audio overlap by exactly the time the picture loses.
    off_picture = off_end - JOIN
    if off_picture <= begin:
        raise SystemExit(f"{stage}: the vanilla half is shorter than the {JOIN} s join")
    off_len = off_picture - begin
    on_len = finish - on_begin
    length = off_len + on_len
    graph.append(
        f"[0:v]trim=start={begin:.6f}:end={off_picture:.6f},setpts=PTS-STARTPTS[v{index}a];"
        f"[0:v]trim=start={on_begin:.6f}:end={finish:.6f},setpts=PTS-STARTPTS[v{index}b];"
        f"[v{index}a][v{index}b]concat=n=2:v=1:a=0[v{index}]")
    graph.append(
        f"[0:a]atrim=start={begin:.6f}:end={off_end:.6f},asetpts=PTS-STARTPTS[a{index}a];"
        f"[0:a]atrim=start={on_begin:.6f}:end={finish:.6f},asetpts=PTS-STARTPTS[a{index}b];"
        f"[a{index}a][a{index}b]acrossfade=d={JOIN}:c1=tri:c2=tri[a{index}]")
    parts.append((stage, length, off_len))
    position += length

# Stage boundaries: a short fade on each side, so a cut between stages does not jump.
faded = []
for index, (stage, length, off_len) in enumerate(parts):
    graph.append(
        f"[a{index}]afade=t=in:st=0:d={JOIN},"
        f"afade=t=out:st={length - JOIN:.6f}:d={JOIN}[f{index}]")
    faded.append(f"[f{index}]")
graph.append("".join(faded) + f"concat=n={len(faded)}:v=0:a=1[aout]")
graph.append("".join(f"[v{i}]" for i in range(len(parts))) + f"concat=n={len(parts)}:v=1:a=0[vout]")

print("encoding...", flush=True)
subprocess.run([
    "ffmpeg", "-v", "error", "-y", "-i", str(video_path),
    "-filter_complex", ";".join(graph), "-map", "[vout]", "-map", "[aout]",
    "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p",
    "-c:a", "aac", "-b:a", "192k", "-r", f"{RATE:.0f}", str(out_path),
], check=True)


def loudness(path):
    out = subprocess.run(
        ["ffmpeg", "-v", "info", "-nostats", "-i", str(path),
         "-af", "loudnorm=I=-20:TP=-1.5:print_format=json", "-f", "null", "-"],
        capture_output=True, text=True, errors="replace").stderr
    m = re.search(r"\{[^{}]*\"input_i\"[^{}]*\}", out, re.S)
    if not m:
        raise SystemExit(f"no loudness measurement for {path}")
    stats = json.loads(m.group(0))
    return float(stats["input_i"]), float(stats["input_tp"])


before_i, before_tp = loudness(out_path)
print(f"loudness before: {before_i} LUFS, {before_tp} dBTP", flush=True)
if before_i != float("-inf"):
    gain = -20.0 - before_i
    if abs(gain) >= 0.1:
        flat = out_path.with_name(out_path.stem + "-flat" + out_path.suffix)
        out_path.rename(flat)
        subprocess.run(
            ["ffmpeg", "-v", "error", "-y", "-i", str(flat), "-c:v", "copy",
             "-af", f"volume={gain:.2f}dB,alimiter=limit={10 ** (-1.5 / 20.0):.6f}:level=false",
             "-c:a", "aac", "-b:a", "192k", str(out_path)], check=True)
        flat.unlink()
        after_i, after_tp = loudness(out_path)
        print(f"applied {gain:+.2f} dB with a -1.5 dBFS limiter", flush=True)
        print(f"loudness after:  {after_i} LUFS, {after_tp} dBTP", flush=True)

entries = []
position = 0.0
for stage, length, off_len in parts:
    fields = summary.get(stage, [])
    shot = {
        "stage": stage,
        "video_start_s": round(position, 3),
        "video_end_s": round(position + length, 3),
        "palette_at_s": round(position + off_len, 3),
    }
    if fields:
        shot.update({
            "title": fields[1],
            "subtitle": fields[2],
            "camera": " | ".join(fields[4:7]),
            "mobs_at_start": fields[7].removeprefix("mobs at start "),
            "mobs_at_end": fields[8].removeprefix("mobs at end "),
        })
    entries.append(shot)
    print(f"{len(entries):2d} {position:7.2f}s {length:5.2f}s {stage}")
    position += length

sidecar_path.write_text(json.dumps({
    "video": str(out_path),
    "source_recording": str(video_path),
    "join_crossfade_s": JOIN,
    "shots": entries,
}, indent=2) + "\n")
print("wrote", out_path, "and", sidecar_path)
