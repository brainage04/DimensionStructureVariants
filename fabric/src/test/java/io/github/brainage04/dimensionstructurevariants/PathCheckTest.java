package io.github.brainage04.dimensionstructurevariants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import net.jqwik.api.Example;

/** Each way a camera can leave the plan is a problem the frame check names. */
class PathCheckTest {
    private static final CameraPlan.Plan PLAN =
            CameraPlan.Plan.of(0.0D, 64.0D, 0.0D, 40.0D, 1.5D, 300.0D, 0.0D);

    /** The plan's own frames, each drawn from the plan's frame {@code source.applyAsInt(n)}. */
    private static List<PathCheck.Frame> frames(
            int count, IntUnaryOperator source, int paletteFrame) {
        List<PathCheck.Frame> frames = new ArrayList<>();
        for (int n = 0; n < count; n++) {
            ShowcaseCamera.Pose pose = PLAN.at(source.applyAsInt(n));
            frames.add(
                    new PathCheck.Frame(
                            pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch(), n >= paletteFrame));
        }
        return frames;
    }

    private static List<String> problems(List<PathCheck.Frame> frames) {
        return PathCheck.verify(PLAN, frames).problems();
    }

    private static boolean mentions(List<String> problems, String text) {
        return problems.stream().anyMatch(line -> line.contains(text));
    }

    @Example
    void theFirstFrameOneFrameLateIsOffThePlan() {
        List<String> problems =
                problems(
                        frames(
                                CameraPlan.SHOT_FRAMES + 1,
                                n -> Math.min(n + 1, CameraPlan.SHOT_FRAMES),
                                CameraPlan.PALETTE_FRAME));
        assertTrue(mentions(problems, "frame 0 is"), problems.toString());
    }

    @Example
    void aDescentThatStopsIsCaught() {
        // Frames 80 on stay at frame 80's height, as a floor clamp holds a camera.
        List<PathCheck.Frame> frames =
                frames(CameraPlan.SHOT_FRAMES + 1, n -> n, CameraPlan.PALETTE_FRAME);
        PathCheck.Frame held = frames.get(80);
        for (int n = 81; n < frames.size(); n++) {
            PathCheck.Frame was = frames.get(n);
            frames.set(
                    n,
                    new PathCheck.Frame(was.x(), held.y(), was.z(), was.yaw(), was.pitch(), true));
        }
        assertTrue(mentions(problems(frames), "does not go down"), problems(frames).toString());
    }

    @Example
    void oneTurnInsteadOfTwoIsCaught() {
        // Turns at half the rate: the position and yaw are the plan's at half the frame number.
        List<PathCheck.Frame> slow = new ArrayList<>();
        for (int n = 0; n <= CameraPlan.SHOT_FRAMES; n++) {
            ShowcaseCamera.Pose pose = PLAN.at(n);
            ShowcaseCamera.Pose half = PLAN.at(n / 2.0D);
            slow.add(
                    new PathCheck.Frame(
                            half.x(),
                            pose.y(),
                            half.z(),
                            half.yaw(),
                            pose.pitch(),
                            n >= CameraPlan.PALETTE_FRAME));
        }
        List<String> problems = problems(slow);
        assertTrue(mentions(problems, "times around the structure"), problems.toString());
    }

    @Example
    void aPaletteSwitchOneFrameLateIsCaught() {
        List<String> problems =
                problems(
                        frames(
                                CameraPlan.SHOT_FRAMES + 1,
                                n -> n,
                                CameraPlan.PALETTE_FRAME + 1));
        assertTrue(mentions(problems, "palette came on at frame 61"), problems.toString());
    }

    @Example
    void aCameraThatStandsStillAtTheSwitchIsCaught() {
        // The camera shows the pose of frame 59 twice, then carries on one frame behind.
        List<String> problems =
                problems(
                        frames(
                                CameraPlan.SHOT_FRAMES + 1,
                                n -> n < CameraPlan.PALETTE_FRAME ? n : n - 1,
                                CameraPlan.PALETTE_FRAME));
        assertTrue(mentions(problems, "does not go down from frame 59 to 60"), problems.toString());
    }

    @Example
    void aShotOfTheWrongLengthIsCaught() {
        List<String> problems =
                problems(frames(CameraPlan.SHOT_FRAMES, n -> n, CameraPlan.PALETTE_FRAME));
        assertTrue(mentions(problems, "drew 120 frames, not the 121"), problems.toString());
    }

    @Example
    void thePlanReportsNoDeviation() {
        PathCheck.Report report =
                PathCheck.verify(
                        PLAN, frames(CameraPlan.SHOT_FRAMES + 1, n -> n, CameraPlan.PALETTE_FRAME));
        assertEquals(0.0D, report.worstPosition(), 1.0E-9D);
        assertTrue(report.problems().isEmpty(), report.problems().toString());
    }
}
