#!/usr/bin/env python3
"""Cuts a DSV recording into one clip per stage and joins each stage's two halves with a crossfade.

usage: cut_film.py LOG VIDEO META.json OUT.mp4 SIDECAR.json

Each stage prints four markers: `STAGE_START`/`STAGE_END <ms> <id>` around the vanilla half and
`STAGE_START`/`STAGE_END <ms> <id>/on` around the palette half. Between the halves the camera and
the game stand still while the server swaps the blocks and the client builds them; the cut drops
that pause, so the two halves' audio are not contiguous in the capture and a hard join clicks.

The picture joins the halves on the pose the camera stops on: the vanilla half ends on that pose's
first frame and the palette half starts on the first frame after it, so the blocks and the caption
change on one frame and the camera carries on. The audio crossfades over JOIN seconds across the same
join, so the two halves' sound overlaps instead of meeting at a fade to silence.

A stage's outer edges are trimmed to the frames that move. The camera is held still before a shot
starts and after it ends, so the frames the markers sit in do not move, and a cut on them shows a
still picture. A frame moves when it differs from the one before it by at least a twentieth of the
stage's own median difference, which is the rule the film is reviewed by. The trim takes the first
and last frame of the stage that move, and the frame after the first and the frame before the last
have to move too, so the cut never lands next to a still.

Consecutive stages crossfade their audio over JOIN seconds as well, each stage's sound running JOIN
seconds past its picture into the next stage's, so the Nether drone carries through a stage cut
instead of fading to silence and back. The picture still cuts hard, and the film's audio is exactly
as long as its picture.

The capture is resampled to the film's rate before anything is measured or cut, so the frames the
still detection counts are the frames the film shows.
"""
import json
import re
import statistics
import subprocess
import sys
from pathlib import Path

log_path, video_path, meta_path, out_path, sidecar_path = map(Path, sys.argv[1:6])
log = log_path.read_text(errors="replace")
meta = json.loads(meta_path.read_text())
# The capture's own frame rate and size, from the recorder's metadata. The still detection reads the
# picture left of the caption panel, whose left edge the film logs (its width follows the longest
# line it draws), at a tenth of the size.
RATE = float(meta.get("fps", 30))
SCREEN = meta.get("xvfbScreen", "1920x1080x24").split("x")
HEIGHT = int(SCREEN[1])
edges = [int(m.group(1)) for m in re.finditer(r"caption panel left edge (\d+) px", log)]
if not edges:
    raise SystemExit("the log has no caption panel left edge")
WORLD_END = min(edges)
WORLD = f"crop={WORLD_END}:{HEIGHT}:0:0,scale=275:270,format=gray"
# The capture's own frames are irregular (a slow tick leaves a longer one), so the picture is
# resampled to the film's rate first: every trim then holds exactly the frames its length says,
# and the still detection counts the frames the film shows.
CFR = f"fps={RATE:.0f},setpts=PTS-STARTPTS"
# How far outside a stage's markers the trim looks for the stage's first and last moving frame: the
# camera is held still for a few ticks either side, so the search has to reach past them.
LEAD = 0.10
TRAIL = 0.12
JOIN = 0.15
# A frame is still when it differs from the one before it by less than this share of the stage's
# own motion: the rule the film is reviewed by, which measures against the stage's median
# difference. The reference here is the stage's upper quartile instead, because a capture the
# client drew slowly repeats slots, and on such a capture half the frames are repeats and the
# median is a still's own difference.
STILL_SHARE = 0.05
MOTION_QUANTILE = 0.75
# The frame a stage is cut on has to move like the stage does, not merely clear the still
# threshold: a capture the client drew slowly repeats slots, and a cut on a repeat shows a still
# picture even though the frame before it moved. A repeat is a tenth of the stage's motion or less;
# the shot's own first frames are slower than the rest, so the share stays well under a quarter.
BOUNDARY_SHARE = 0.10
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


def at(ms):
    return max(0.0, ms / 1000.0 - epoch)


def frame_at(seconds):
    return int(round(seconds * RATE))


def still_frames():
    """Per frame n, the mean difference between frame n and frame n + 1 in the world region.

    The camera stops on one pose while the palette goes in, so the frames of that pose differ by
    nothing. The cut joins the halves on that pose: the vanilla half ends on its first frame and
    the palette half starts on its last, so the camera carries on without a still.
    """
    out = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", str(video_path),
         "-vf", f"{CFR},{WORLD},tblend=all_mode=difference,signalstats,metadata=print:file=-",
         "-f", "null", "-"], check=True, capture_output=True, text=True).stdout
    values = {}
    current = 0
    for line in out.splitlines():
        m = re.match(r"frame:(\d+)\s", line)
        if m:
            current = int(m.group(1))
        elif "YAVG=" in line:
            values[current] = float(line.split("=")[1])
    return values


STEPS = still_frames()


def still(frame, limit):
    """Whether frame {@code frame} repeats the one before it, by the film's own rule.

    A frame with no measured difference, which is the film's first frame and anything past its
    last, counts as still: nothing shows it moving.
    """
    return STEPS.get(frame - 1, 0.0) < limit


def held_pose(start_ms, end_ms, stage):
    """The frames the two halves meet on: the vanilla half's last and the palette half's first.

    The markers say where each half ends and starts, and the camera is held on one pose between
    them, so the join is that pose with the blocks and the caption changed. The frames between the
    markers are not a still stretch: the client draws the structure again as it rebuilds it.
    """
    first = frame_at(at(start_ms))
    last = frame_at(at(end_ms))
    if last <= first:
        raise SystemExit(f"{stage}: the palette half starts before the vanilla half ends")
    return first, last


graph = [f"[0:v]{CFR}[cfr]"]
parts = []
position = 0.0
for index, (stage, s1, e1, s2, e2) in enumerate(stages):
    # The stage's own motion, from the frames of its two halves and not the held pose between them.
    span = [STEPS[f] for f in list(range(frame_at(at(s1)), frame_at(at(e1)) + 1))
            + list(range(frame_at(at(s2)), frame_at(at(e2)) + 1)) if f in STEPS]
    if not span:
        raise SystemExit(f"{stage}: no frames between its markers")
    span.sort()
    motion = span[int(MOTION_QUANTILE * (len(span) - 1))]
    limit = STILL_SHARE * motion
    boundary = BOUNDARY_SHARE * motion
    # The first and last frame of the shot that move: the frames outside them are the held pose the
    # camera waits on, and a cut on one of those shows a still picture. A frame moves when it
    # differs from the one before it. The frame after the first has to move too, or the film opens
    # on a still, and the frame before the last, or the film ends on one.
    # The search starts at the previous stage's last frame, so a stage that opens on a still cannot
    # take the frame before it, which belongs to the stage before.
    floor = 0 if index == 0 else frame_at(at(stages[index - 1][4]))
    begin = next(
        (f for f in range(max(floor, frame_at(at(s1) - LEAD)), frame_at(at(e1)))
         if not still(f, boundary) and not still(f + 1, boundary)),
        None)
    last = next(
        (f for f in range(frame_at(at(e2) + TRAIL), frame_at(at(s2)), -1)
         if not still(f, boundary) and not still(f - 1, boundary)),
        None)
    if begin is None or last is None or last <= begin:
        raise SystemExit(f"{stage}: no moving frames between its markers")
    finish = last + 1
    off_stop, on_start = held_pose(e1, s2, stage)
    # The camera resumes a few ticks after the marker, so the palette half opens on the held pose
    # again. Start the half on the first frame after that still run, or the film shows the pose
    # twice. The run is measured by the still rule the film is reviewed by, not the boundary rule,
    # so a slow first frame of the shot is kept.
    while on_start + 1 < finish and still(on_start + 1, limit):
        on_start += 1

    # The vanilla half ends on the held pose's first frame, which still shows the vanilla blocks;
    # the palette half starts on the first frame after the pose, the same pose with the palette and
    # its caption. The held pose's own frames are one picture, so the cut keeps its first and drops
    # the rest: the blocks and the caption change on the join and the camera carries on from the
    # pose it stopped on.
    off_frames = off_stop - begin
    on_frames = finish - on_start
    if off_frames < 1 or on_frames < 1:
        raise SystemExit(f"{stage}: a half holds no frame")
    begin_s = begin / RATE
    off_picture = off_stop / RATE
    on_begin = on_start / RATE
    finish_s = finish / RATE
    # The vanilla half's audio runs JOIN seconds past its picture, so the crossfade starts on the
    # join and the audio is exactly as long as the picture.
    off_end = off_picture + JOIN
    if finish_s - on_begin < 2.0 * JOIN:
        raise SystemExit(
                f"{stage}: the palette half is {finish_s - on_begin:.2f} s, shorter than two"
                        + f" {JOIN} s fades")
    off_len = off_frames / RATE
    on_len = on_frames / RATE
    length = off_len + on_len
    # The picture is trimmed by frame index, not by time: a trim on a timestamp can take or drop
    # the frame that sits exactly on its boundary, which is the frame the still detection chose.
    graph.append(
        f"[cfr]trim=start_frame={begin}:end_frame={off_stop},setpts=PTS-STARTPTS[v{index}a];"
        f"[cfr]trim=start_frame={on_start}:end_frame={finish},setpts=PTS-STARTPTS[v{index}b];"
        f"[v{index}a][v{index}b]concat=n=2:v=1:a=0[v{index}]")
    graph.append(
        f"[0:a]atrim=start={begin_s:.6f}:end={off_end:.6f},asetpts=PTS-STARTPTS[a{index}a];"
        f"[0:a]atrim=start={on_begin:.6f}:end={finish_s:.6f},asetpts=PTS-STARTPTS[a{index}b];"
        f"[a{index}a][a{index}b]acrossfade=d={JOIN}:c1=tri:c2=tri[a{index}]")
    parts.append((stage, length, off_len, finish_s))
    position += length

# Stage boundaries: each stage's sound runs JOIN seconds past its picture, into the next stage's,
# and the two crossfade there. The picture cuts hard; the sound carries the drone through the cut
# instead of fading it to silence and back. The last stage's sound ends with its picture, so the
# film's audio is exactly as long as its picture.
for index, (stage, length, off_len, finish_s) in enumerate(parts):
    if index == len(parts) - 1:
        graph.append(f"[a{index}]anull[e{index}]")
    else:
        graph.append(
            f"[0:a]atrim=start={finish_s:.6f}:end={finish_s + JOIN:.6f},asetpts=PTS-STARTPTS"
            f"[t{index}];[a{index}][t{index}]concat=n=2:v=0:a=1[e{index}]")
chain = "[e0]"
for index in range(1, len(parts)):
    graph.append(f"{chain}[e{index}]acrossfade=d={JOIN}:c1=tri:c2=tri[c{index}]")
    chain = f"[c{index}]"
graph.append(f"{chain}afade=t=in:st=0:d={JOIN},afade=t=out:st={position - JOIN:.6f}:d={JOIN}[aout]")
graph.append("".join(f"[v{i}]" for i in range(len(parts))) + f"concat=n={len(parts)}:v=1:a=0[vout]")

print("encoding...", flush=True)
subprocess.run([
    "ffmpeg", "-v", "error", "-y", "-i", str(video_path),
    "-filter_complex", ";".join(graph), "-map", "[vout]", "-map", "[aout]",
    "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p",
    # A constant rate, so the film plays at the rate the sidecar counts in.
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
for stage, length, off_len, finish_s in parts:
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
            "camera": " | ".join(fields[4:8]),
            "final_pitch_deg": float(fields[7].removeprefix("final pitch ")),
            "mobs_at_start": fields[8].removeprefix("mobs at start "),
            "mobs_at_end": fields[9].removeprefix("mobs at end "),
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
