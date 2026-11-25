"""The test cut_film.py and measure_film.py share for a picture shown twice.

A step is how much the picture changes from one frame of a half to the next. The camera's steps change
slowly along the path, so a step is compared with the median of the NEIGHBOURS steps either side of it.
A frame that repeats the one before it is under FRACTION of that median: take 14's stage 8 (`fortress_overworld/snowy`)
showed one pose twice, with a step of 0.35 between neighbours at 26 to 31 (ratio 0.012). The smallest ratio of
any other half of that film was 0.50.

A bound on a half's own median does not work: a genuine half's first steps are a tenth of its median
(0.10 to 0.16 in five halves of take 14's film, from the camera's slow start).
"""
import statistics

NEIGHBOURS = 4
FRACTION = 0.2


def ratios(steps):
    """Each step over the median of the NEIGHBOURS steps on each side of it (1.0 when it has none)."""
    out = []
    for i, step in enumerate(steps):
        near = steps[max(0, i - NEIGHBOURS):i] + steps[i + 1:i + 1 + NEIGHBOURS]
        median = statistics.median(near) if near else 0.0
        out.append(step / median if median > 0.0 else 1.0)
    return out


def repeated(steps):
    """The indexes of the steps that show the picture of the frame before them again."""
    return [i for i, ratio in enumerate(ratios(steps)) if ratio < FRACTION]
