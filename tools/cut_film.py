#!/usr/bin/env python3
"""Cuts a DSV recording into one clip per stage and joins each stage's two halves with a crossfade.

usage: cut_film.py LOG VIDEO META.json OUT.mp4 SIDECAR.json

Each stage prints four markers: `STAGE_START`/`STAGE_END <ms> <id>` around the vanilla half and
`STAGE_START`/`STAGE_END <ms> <id>/on` around the palette half. Between the halves the camera and
the game stand still while the server swaps the blocks and the client builds them; the cut drops
that pause, so the two halves' audio are not contiguous in the capture and a hard join clicks.

Each half is cut to exactly the frames the camera drew. The camera draws every frame of the shot as
a new pose, frame n at n / 30 s of the plan, the client presents no two frames within one capture
slot (ShowcaseCamera.spaceFrames), and the recorder repeats a slot's picture when no frame came, so
a slot that repeats the one before it is one the client did not draw in, and the cut drops it. A
half is the slots that change the picture from the camera's first step of the half on (count and
draw times from the film's own FRAME log lines for the stage: the palette frame for the vanilla
half, the rest of the shot for the palette half), and the vanilla half also opens on the frame
before the first step, which shows the held start pose, the shot's frame 0. The cut fails a half
during which the recorder dropped a frame (the film logs the recorder's drop count at each half's
start and end as CAPTURE_DROPS), a half whose capture holds fewer changing slots
than the camera drew frames, and a half in which a frame is not within a few slots of the slot it
was drawn at (a pose missing or a change the camera did not make). The film shows
frame 0 first, frame 59 last of the vanilla half and frame 60 first of the palette half, with no
halt at the join. The audio crossfades over JOIN seconds across the join, so the two halves' sound
overlaps instead of meeting at a fade to silence.

A slot repeats the one before it when the pictures differ in no more than STILL_LEVEL (CHANGED).

Consecutive stages crossfade their audio over JOIN seconds as well, each stage's sound running JOIN
seconds past its picture into the next stage's, so the Nether drone carries through a stage cut
instead of fading to silence and back. The picture still cuts hard, and the film's audio is exactly
as long as its picture.

The capture is resampled to the film's rate before anything is measured or cut, so the frames the
still detection counts are the frames the film shows.
"""
import json
import math
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
TRAIL = 0.25
JOIN = 0.15
# The picture's change between two slots is the share of the world region's pixels that differ by more
# than CHANGED_BY grey levels, times 255. A repeated slot is the same raw frame, which the encoder
# reproduces to within a grey level or two: its change measured at most 0.07 in take 14's film, and
# a camera step at least 1.0 (the cut prints both extremes, and the level sits between them). A mean
# difference did not separate them in end city's ocean stage (repeats up to 0.4 against steps near
# 1.0), and a level of 10 grey levels missed the camera's steps in the Nether's dark fog (basalt
# deltas).
CHANGED_BY = 5
CHANGED = f"lut=y=if(gt(val\\,{CHANGED_BY})\\,255\\,0)"
STILL_LEVEL = 0.25
STEP_LEVELS = []
REPEAT_LEVELS = []
# The most a half's frames may differ in how long after their draw slot they were captured: the
# recorder hands a frame on up to 4 presents after its own and holds a slot 250 ms for it
# (GameCapture.java BUFFERS, HOLD_US in MinecraftModConventions 3.5.0), 7.5 slots.
MAX_LAG_SPREAD_SLOTS = 7.5
LAGS = []
PALETTE_HALF = "/on"
# The shot's frame counts come from the film's own path check, which logs `[DSV] <stage> path: N
# frames ... palette on from frame M` for each stage, not from a copy of CameraPlan here.
DRAWS = {}
for m in re.finditer(r"\[DSV\] FRAME (\S+) (\d+) (\d+) ", log):
    DRAWS.setdefault(m.group(1), []).append(int(m.group(3)))
PATHS = {m.group(1): (int(m.group(2)), int(m.group(3)))
         for m in re.finditer(r"\[DSV\] (\S+) path: (\d+) frames,.*palette on from frame (\d+)", log)}

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
# The recorder's own count of frames it dropped (a frame it could not copy or write), read by the
# film at each half's start and end: a drop outside the halves (loading, the block swap) costs the
# film nothing.
DROPS = {(m.group(1), m.group(2)): int(m.group(3))
         for m in re.finditer(r"CAPTURE_DROPS (\S+) (start|end) (\d+)", log)}
dropped = int(meta["boundaries"].get("gameCaptureDroppedFrames", 0))
print(f"the recorder dropped {dropped} frames in the whole recording", flush=True)


def at(ms):
    return max(0.0, ms / 1000.0 - epoch)


def frame_at(seconds):
    return int(round(seconds * RATE))


def still_frames():
    """Per frame n, the mean difference between frame n and frame n + 1 in the world region.

    The camera stops on one pose while the palette goes in, so the frames of that pose differ by
    nothing. The cut joins the halves on that pose: the vanilla half ends on its first frame and
    the palette half starts on the first frame after it, so the camera carries on without a still.
    """
    out = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", str(video_path),
         "-vf", f"{CFR},{WORLD},tblend=all_mode=difference,{CHANGED},signalstats,metadata=print:file=-",
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


graph = [f"[0:v]{CFR}[cfr]"]
parts = []
position = 0.0


def moving(frame, limit):
    """Whether frame {@code frame} differs from the one before it by more than a repeat does."""
    return not still(frame, limit)


def runs(frames):
    """The frames as (first, last) runs of consecutive numbers, for a select expression."""
    out = []
    for f in frames:
        if out and out[-1][1] == f - 1:
            out[-1][1] = f
        else:
            out.append([f, f])
    return out


def select(frames):
    return "+".join(f"between(n,{lo},{hi})" for lo, hi in runs(frames))


def take_half(stage, label, start_frame, stop_frame, draws, with_start_pose):
    """The frames of one half of a stage, as the camera's own frame counter drew them.

    `draws` are the capture slots the camera drew the half's frames at, from the film's own FRAME
    log. The camera draws every frame of the shot as a new pose, and the client presents no two
    frames within one slot (ShowcaseCamera.spaceFrames), so each presented frame has its own slot
    and the recorder repeats a slot's picture when none came. A half is therefore the slots that
    change the picture from the camera's first step of the half on, and a slot that repeats the
    one before it is dropped. The search starts where the camera's own first step of the half is
    drawn, so a change the world makes while the camera still holds (mobs, the clock starting) is
    not taken for a frame. The first half also opens on the frame before that step, which shows
    the held start pose, the shot's frame 0.

    The half must hold at least `len(draws)` changing slots, and its first `len(draws)` are the
    frames. The sun and the clock still move for a slot or two after the camera's last frame, until
    the game freezes, so slots beyond the count are ignored. The frames' lags (capture slot minus
    draw slot) may differ by at most MAX_LAG_SPREAD_SLOTS within the half: a frame missing from
    the capture, or a change the camera did not make taken for a frame, shifts the frames after
    it against their draws.
    """
    count = len(draws)
    from_slot = draws[1] if with_start_pose else draws[0]
    search = max(start_frame, int(math.floor(from_slot - 0.5)))
    moves = [f for f in range(search, stop_frame) if moving(f, STILL_LEVEL)]
    needed = count - 1 if with_start_pose else count
    if len(moves) < needed:
        raise SystemExit(
            f"{stage} {label}: only {len(moves)} of the {needed} frames the camera drew after its"
            f" first step are in the capture (slots {search}-{stop_frame})")
    kept = ([moves[0] - 1] if with_start_pose else []) + moves[:needed]
    lags = [kept[i] - draws[i] for i in range(1 if with_start_pose else 0, count)]
    LAGS.append((max(lags) - min(lags), stage, label))
    if max(lags) - min(lags) > MAX_LAG_SPREAD_SLOTS:
        raise SystemExit(
            f"{stage} {label}: the frames are {min(lags):.1f} to {max(lags):.1f} slots from the slots"
            " they were drawn at: a pose is missing or a change the camera did not make was taken"
            " for a frame")
    taken = set(kept)
    STEP_LEVELS.append((min(STEPS[f - 1] for f in kept[1:]), stage, label))
    REPEAT_LEVELS.append(
        (max((STEPS.get(f - 1, 0.0) for f in range(kept[0], kept[-1]) if f not in taken), default=0.0),
         stage, label))
    return kept


for index, (stage, s1, e1, s2, e2) in enumerate(stages):
    if stage not in PATHS:
        raise SystemExit(f"{stage}: the log has no path check line for it")
    shot_frames, palette_frame = PATHS[stage]
    drawn = DRAWS.get(stage, [])
    if len(drawn) != shot_frames:
        raise SystemExit(f"{stage}: the log has {len(drawn)} FRAME lines, the path check {shot_frames}")
    for half in (stage, stage + PALETTE_HALF):
        begin, end = DROPS.get((half, "start")), DROPS.get((half, "end"))
        if begin is None or end is None:
            raise SystemExit(f"{half}: the log has no CAPTURE_DROPS line for its start and end")
        if end != begin:
            raise SystemExit(f"{half}: the recorder dropped {end - begin} frames while the half ran:"
                             " a dropped frame is a pose the film cannot show; record it again")
    slots = [at(ms) * RATE for ms in drawn]
    # The search starts at the previous stage's last frame, so a stage cannot take a frame that
    # belongs to the stage before.
    floor = 0 if index == 0 else frame_at(at(stages[index - 1][4]))
    off = take_half(stage, "vanilla half", max(floor, frame_at(at(s1) - LEAD)),
                    frame_at(at(e1) + TRAIL), slots[:palette_frame], True)
    on = take_half(stage, "palette half", frame_at(at(s2) - LEAD),
                   frame_at(at(e2) + TRAIL), slots[palette_frame:], False)
    off_frames = len(off)
    on_frames = len(on)
    begin_s = off[0] / RATE
    on_begin = on[0] / RATE
    off_len = off_frames / RATE
    on_len = on_frames / RATE
    length = off_len + on_len
    # The audio is cut by length from where each half starts, so it is as long as the picture
    # whatever repeated slots the capture held between the frames.
    finish_s = on_begin + on_len
    if on_len < 2.0 * JOIN:
        raise SystemExit(f"{stage}: the palette half is {on_len:.2f} s, shorter than two {JOIN} s fades")
    graph.append(
        f"[cfr]select='{select(off)}',setpts=N/({RATE:.0f}*TB)[v{index}a];"
        f"[cfr]select='{select(on)}',setpts=N/({RATE:.0f}*TB)[v{index}b];"
        f"[v{index}a][v{index}b]concat=n=2:v=1:a=0[v{index}]")
    graph.append(
        f"[0:a]atrim=start={begin_s:.6f}:end={begin_s + off_len + JOIN:.6f},asetpts=PTS-STARTPTS[a{index}a];"
        f"[0:a]atrim=start={on_begin:.6f}:end={finish_s:.6f},asetpts=PTS-STARTPTS[a{index}b];"
        f"[a{index}a][a{index}b]acrossfade=d={JOIN}:c1=tri:c2=tri[a{index}]")
    parts.append((stage, length, off_len, finish_s))
    position += length
    print(f"{stage}: {off_frames}+{on_frames} frames, vanilla {off[0]}-{off[-1]}, palette {on[0]}-{on[-1]}"
          f" of the capture ({off[-1] - off[0] + 1 - off_frames} + {on[-1] - on[0] + 1 - on_frames}"
          " repeated slots dropped)", flush=True)

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

print(f"largest spread of a half's frame lags (capture slot minus draw slot): {max(LAGS)[0]:.1f} slots"
      f" ({max(LAGS)[1:]})", flush=True)
print(f"smallest camera step {min(STEP_LEVELS)[0]:.2f} ({min(STEP_LEVELS)[1:]}), largest repeated"
      f" slot {max(REPEAT_LEVELS)[0]:.3f} ({max(REPEAT_LEVELS)[1:]}), level {STILL_LEVEL}", flush=True)
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
