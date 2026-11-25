package io.github.brainage04.dimensionstructurevariants;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Checks the camera a shot really used, frame by frame, against its {@link CameraPlan.Plan}: every
 * frame's position, yaw and pitch, a descent that never stops, two full turns, the same step across
 * the palette switch as anywhere else, and the switch at {@link CameraPlan#PALETTE_FRAME}.
 */
public final class PathCheck {
    /** How far a frame's position may be from the plan, in blocks. */
    public static final double POSITION_TOLERANCE = 0.001D;

    /** How far a frame's yaw or pitch may be from the plan, in degrees. */
    public static final double ANGLE_TOLERANCE = 0.01D;

    /** How far the turns the camera made may be from {@link CameraPlan#REVOLUTIONS}. */
    public static final double REVOLUTION_TOLERANCE = 0.001D;

    /** A frame-by-frame problem is listed for this many frames; the rest would repeat it. */
    private static final int MAX_FRAME_PROBLEMS = 12;

    private PathCheck() {}

    /** The camera one frame was drawn from, and whether the palette was on in it. */
    public record Frame(double x, double y, double z, float yaw, float pitch, boolean paletteOn) {}

    /**
     * What {@link #verify} measured: the problems found, and the numbers the report quotes.
     *
     * @param orbits full turns around the centre, summed over the frames
     */
    public record Report(
            List<String> problems,
            int frames,
            double worstPosition,
            double worstYaw,
            double worstPitch,
            double orbits,
            int paletteFrame) {
        public String summary() {
            return String.format(
                    Locale.ROOT,
                    "%d frames, worst deviation from the plan %.6f blocks, %.6f degrees of yaw and"
                            + " %.6f degrees of pitch, %.4f orbits, palette on from frame %d",
                    frames,
                    worstPosition,
                    worstYaw,
                    worstPitch,
                    orbits,
                    paletteFrame);
        }
    }

    public static Report verify(CameraPlan.Plan plan, List<Frame> frames) {
        List<String> problems = new ArrayList<>();
        List<String> deviations = new ArrayList<>();
        List<String> descents = new ArrayList<>();
        int expected = CameraPlan.SHOT_FRAMES + 1;
        if (frames.size() != expected) {
            problems.add(
                    "the shot drew "
                            + frames.size()
                            + " frames, not the "
                            + expected
                            + " it plans");
        }
        int count = frames.size();
        double worstPosition = 0.0D;
        double worstYaw = 0.0D;
        double worstPitch = 0.0D;
        for (int n = 0; n < count; n++) {
            Frame frame = frames.get(n);
            ShowcaseCamera.Pose planned = plan.at(n);
            double position =
                    Math.sqrt(
                            square(frame.x() - planned.x())
                                    + square(frame.y() - planned.y())
                                    + square(frame.z() - planned.z()));
            double yaw = Math.abs(wrapDegrees(frame.yaw() - planned.yaw()));
            double pitch = Math.abs(frame.pitch() - planned.pitch());
            worstPosition = Math.max(worstPosition, position);
            worstYaw = Math.max(worstYaw, yaw);
            worstPitch = Math.max(worstPitch, pitch);
            if (position > POSITION_TOLERANCE || yaw > ANGLE_TOLERANCE || pitch > ANGLE_TOLERANCE) {
                if (deviations.size() < MAX_FRAME_PROBLEMS) {
                    deviations.add(
                            String.format(
                                    Locale.ROOT,
                                    "frame %d is %.4f blocks, %.4f degrees of yaw and %.4f degrees"
                                            + " of pitch from the plan (at (%.3f, %.3f, %.3f) yaw"
                                            + " %.3f pitch %.3f, planned (%.3f, %.3f, %.3f) yaw"
                                            + " %.3f pitch %.3f)",
                                    n,
                                    position,
                                    yaw,
                                    pitch,
                                    frame.x(),
                                    frame.y(),
                                    frame.z(),
                                    frame.yaw(),
                                    frame.pitch(),
                                    planned.x(),
                                    planned.y(),
                                    planned.z(),
                                    planned.yaw(),
                                    planned.pitch()));
                }
            }
        }

        double orbits = 0.0D;
        double[] angleSteps = new double[Math.max(0, count - 1)];
        double[] heightSteps = new double[angleSteps.length];
        double[] pitchSteps = new double[angleSteps.length];
        for (int n = 1; n < count; n++) {
            Frame before = frames.get(n - 1);
            Frame now = frames.get(n);
            double angleBefore =
                    Math.atan2(before.z() - plan.centreZ(), before.x() - plan.centreX());
            double angleNow = Math.atan2(now.z() - plan.centreZ(), now.x() - plan.centreX());
            double step = Math.toDegrees(angleNow - angleBefore);
            step = wrapDegrees(step);
            angleSteps[n - 1] = step;
            heightSteps[n - 1] = now.y() - before.y();
            pitchSteps[n - 1] = now.pitch() - before.pitch();
            orbits += step / 360.0D;
            if (now.y() >= before.y()) {
                if (descents.size() < MAX_FRAME_PROBLEMS) {
                    descents.add(
                            String.format(
                                    Locale.ROOT,
                                    "the camera does not go down from frame %d to %d (%.4f to"
                                            + " %.4f)",
                                    n - 1,
                                    n,
                                    before.y(),
                                    now.y()));
                }
            }
        }
        problems.addAll(deviations);
        problems.addAll(descents);
        // Orbit direction is the plan's: counter-clockwise by atan2, so two turns sum to +2.
        if (count > 1 && Math.abs(orbits - CameraPlan.REVOLUTIONS) > REVOLUTION_TOLERANCE) {
            problems.add(
                    String.format(
                            Locale.ROOT,
                            "the camera turned %.4f times around the structure, not %d",
                            orbits,
                            CameraPlan.REVOLUTIONS));
        }

        int switchFrame = -1;
        for (int n = 0; n < count; n++) {
            if (frames.get(n).paletteOn()) {
                switchFrame = n;
                break;
            }
        }
        boolean steady = switchFrame >= 0;
        for (int n = Math.max(0, switchFrame); steady && n < count; n++) {
            steady = frames.get(n).paletteOn();
        }
        if (switchFrame != CameraPlan.PALETTE_FRAME || !steady) {
            problems.add(
                    "the palette came on at frame "
                            + switchFrame
                            + (steady ? "" : " and went off again")
                            + ", not at frame "
                            + CameraPlan.PALETTE_FRAME);
        }
        if (switchFrame >= 1 && switchFrame < count) {
            // The step into the first palette frame against the steps beside it: a stop or a
            // reversal at the switch shows here even where every frame is within tolerance.
            int across = switchFrame - 1;
            problems.addAll(acrossSwitch("angle", angleSteps, across, ANGLE_TOLERANCE));
            problems.addAll(acrossSwitch("height", heightSteps, across, POSITION_TOLERANCE));
            problems.addAll(acrossSwitch("pitch", pitchSteps, across, ANGLE_TOLERANCE));
        }
        return new Report(
                problems, count, worstPosition, worstYaw, worstPitch, orbits, switchFrame);
    }

    private static List<String> acrossSwitch(
            String what, double[] steps, int across, double tolerance) {
        List<String> problems = new ArrayList<>();
        if (across <= 0 || across >= steps.length - 1) return problems;
        double beside = (steps[across - 1] + steps[across + 1]) / 2.0D;
        if (Math.abs(steps[across] - beside) > tolerance) {
            problems.add(
                    String.format(
                            Locale.ROOT,
                            "the %s step into the first palette frame is %.5f, beside %.5f before"
                                    + " and %.5f after it",
                            what,
                            steps[across],
                            steps[across - 1],
                            steps[across + 1]));
        }
        return problems;
    }

    private static double square(double value) {
        return value * value;
    }

    private static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0D;
        if (wrapped >= 180.0D) wrapped -= 360.0D;
        if (wrapped < -180.0D) wrapped += 360.0D;
        return wrapped;
    }
}
