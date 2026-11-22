#!/usr/bin/env python3
"""Measures a cut film the way the review does: stills at every cut, pitch per stage, audio at joins.

usage: measure_film.py FILM.mp4 SIDECAR.json [--json OUT.json]

Streams the film through ffmpeg once for the per-frame difference in the world region and once for
the 20 ms RMS envelope, so nothing holds a film's frames in memory.
"""
import json
import re
import statistics
import subprocess
import sys
from pathlib import Path

film, sidecar_path = Path(sys.argv[1]), Path(sys.argv[2])
out_json = None
if "--json" in sys.argv:
    out_json = Path(sys.argv[sys.argv.index("--json") + 1])
sidecar = json.loads(sidecar_path.read_text())
shots = sidecar["shots"]
RATE = 30.0
# The review's world region: the picture left of the caption panel, at full height. A film smaller
# than that region is measured whole, so the same script runs on a test capture.
probe = subprocess.run(
    ["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=width,height",
     "-of", "csv=p=0", str(film)], check=True, capture_output=True, text=True).stdout.strip()
width, height = (int(x) for x in probe.split(",")[:2])
if width >= 900 and height >= 900:
    WORLD = "crop=900:700:0:200,format=gray"
else:
    WORLD = "format=gray"


def differences():
    out = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", str(film),
         "-vf", f"{WORLD},tblend=all_mode=difference,signalstats,metadata=print:file=-",
         "-f", "null", "-"], check=True, capture_output=True, text=True).stdout
    values, current = {}, 0
    for line in out.splitlines():
        m = re.match(r"frame:(\d+)\s", line)
        if m:
            current = int(m.group(1))
        elif "YAVG=" in line:
            values[current] = float(line.split("=")[1])
    return values


def rms_envelope():
    """20 ms RMS in dBFS, one value per 20 ms window."""
    out = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", str(film), "-af",
         "asetnsamples=n=960:p=0,astats=metadata=1:reset=1,"
         "ametadata=print:key=lavfi.astats.Overall.RMS_level:file=-",
         "-f", "null", "-"], check=True, capture_output=True, text=True).stdout
    values = []
    for line in out.splitlines():
        if "RMS_level=" in line:
            text = line.split("=", 1)[1]
            values.append(float(text) if text not in ("-inf", "inf") else -120.0)
    return values


steps = differences()
# tblend writes one difference per frame after the first, so the film has one more frame than
# there are differences.
frames = len(steps) + 1
medians = []
for shot in shots:
    a = int(round(shot["video_start_s"] * RATE))
    b = int(round(shot["video_end_s"] * RATE))
    inner = [steps[f] for f in range(a + 6, b - 6) if f in steps]
    medians.append(statistics.median(inner) if inner else 0.0)

cuts = []
for i in range(len(shots) - 1):
    cut = int(round(shots[i]["video_end_s"] * RATE))
    # The review measures the still run on each side of the cut, against that side's own stage
    # median, and adds them: a cut that lands on a moving frame still counts the stills either side.
    left = 0.05 * medians[i]
    right = 0.05 * medians[i + 1]
    before = 0
    while cut - 1 - before in steps and steps[cut - 1 - before] < left:
        before += 1
    after = 0
    while cut + after in steps and steps[cut + after] < right:
        after += 1
    cuts.append({
        "cut_s": shots[i]["video_end_s"],
        "still_ms": round((before + after) * 1000.0 / RATE),
        "stage_median": round(min(medians[i], medians[i + 1]), 1),
        "frames": [cut - before, cut + after],
    })

# The film's own ends: a still there is a still the viewer sees.
def run_at(frame, limit):
    lo = frame
    while lo - 1 in steps and steps[lo - 1] < limit:
        lo -= 1
    hi = frame
    while hi in steps and steps[hi] < limit:
        hi += 1
    return round((hi - lo) * 1000.0 / RATE)


start_still = run_at(0, 0.05 * medians[0])
end_still = run_at(frames - 1, 0.05 * medians[-1])

# Frame hashes in the world region, so a repeated picture is named as one.
hashes = subprocess.run(
    ["ffmpeg", "-v", "error", "-i", str(film), "-vf", f"{WORLD}", "-f", "framemd5", "-"],
    check=True, capture_output=True, text=True).stdout
digests = [line.rsplit(",", 1)[1].strip() for line in hashes.splitlines()
           if line and not line.startswith("#")]
repeats = [i for i in range(1, len(digests)) if digests[i] == digests[i - 1]]

# Audio: the 20 ms RMS at each palette join and at each stage cut, against the stage's own median.
rms = rms_envelope()
window = 0.25


def dip(at):
    lo = max(0, int((at - window) / 0.02))
    hi = min(len(rms), int((at + window) / 0.02) + 1)
    return min(rms[lo:hi]) if hi > lo else None


joins = []
for shot in shots:
    at = shot["palette_at_s"]
    lo = max(0, int((shot["video_start_s"]) / 0.02))
    hi = min(len(rms), int(shot["video_end_s"] / 0.02))
    stage_median = statistics.median(rms[lo:hi]) if hi > lo else None
    joins.append({
        "join_s": at,
        "stage_median_db": round(stage_median, 1) if stage_median is not None else None,
        "join_min_db": round(dip(at), 1) if dip(at) is not None else None,
        "dip_db": round(stage_median - dip(at), 1) if stage_median is not None and dip(at) is not None else None,
    })

stage_cuts = []
for i in range(len(shots) - 1):
    at = shots[i]["video_end_s"]
    lo = max(0, int(shots[i]["video_start_s"] / 0.02))
    hi = min(len(rms), int(shots[i + 1]["video_end_s"] / 0.02))
    stage_median = statistics.median(rms[lo:hi]) if hi > lo else None
    stage_cuts.append({
        "cut_s": at,
        "stage_median_db": round(stage_median, 1) if stage_median is not None else None,
        "cut_min_db": round(dip(at), 1) if dip(at) is not None else None,
        "dip_db": round(stage_median - dip(at), 1) if stage_median is not None and dip(at) is not None else None,
    })

# The palette join: the camera stops on one pose while the palette goes in, and the cut joins the
# halves on that pose. A join that shows the pose for more than a frame is a halt the viewer sees,
# which is what the review measures at each join.
join_stills = []
for i, shot in enumerate(shots):
    at = int(round(shot["palette_at_s"] * RATE))
    limit = 0.05 * medians[i]
    lo = at
    while lo - 1 in steps and steps[lo - 1] < limit:
        lo -= 1
    hi = at
    while hi in steps and steps[hi] < limit:
        hi += 1
    join_stills.append({
        "join_s": shot["palette_at_s"],
        "still_ms": round((hi - lo) * 1000.0 / RATE),
        "frames": [lo, hi],
    })

result = {
    "film": str(film),
    "frames": frames,
    "stage_cuts": cuts,
    "palette_joins": join_stills,
    "film_start_still_ms": start_still,
    "film_end_still_ms": end_still,
    "repeated_frame_pairs": len(repeats),
    "repeated_frames": repeats,
    "palette_joins_audio": joins,
    "stage_cuts_audio": stage_cuts,
    "final_pitch_deg": [shot.get("final_pitch_deg") for shot in shots],
}
print(json.dumps(result, indent=2))
if out_json:
    out_json.write_text(json.dumps(result, indent=2) + "\n")
