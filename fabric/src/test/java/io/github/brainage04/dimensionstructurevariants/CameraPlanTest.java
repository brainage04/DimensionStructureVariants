package io.github.brainage04.dimensionstructurevariants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Example;

/**
 * The camera's path is one function of the film's frame number: these tests state what Thomas asked
 * of it (24 Nov 2026, A-2311) and run the same frame check the recording runs on the camera it
 * really used.
 */
class CameraPlanTest {
    private static final double CENTRE_X = 1000.0D;
    private static final double CENTRE_Y = 70.0D;
    private static final double CENTRE_Z = -300.0D;
    private static final double RADIUS = 45.0D;
    private static final double GROUND_LEVEL = 65.5D;
    private static final double CEILING = 318.0D;
    private static final double START_ANGLE = 0.0D;

    private static CameraPlan.Plan plan() {
        return CameraPlan.Plan.of(
                CENTRE_X, CENTRE_Y, CENTRE_Z, RADIUS, GROUND_LEVEL, CEILING, START_ANGLE);
    }

    /** Frames drawn from {@code poses}, the palette on from {@code paletteFrame}. */
    private static List<PathCheck.Frame> frames(
            java.util.function.IntFunction<ShowcaseCamera.Pose> poses,
            int count,
            int paletteFrame) {
        List<PathCheck.Frame> frames = new ArrayList<>();
        for (int n = 0; n < count; n++) {
            ShowcaseCamera.Pose pose = poses.apply(n);
            frames.add(
                    new PathCheck.Frame(
                            pose.x(),
                            pose.y(),
                            pose.z(),
                            pose.yaw(),
                            pose.pitch(),
                            n >= paletteFrame));
        }
        return frames;
    }

    @Example
    void theFirstFrameLooksSixtyDegreesDownAtTheCentre() {
        ShowcaseCamera.Pose first = plan().at(0);
        assertEquals(60.0D, first.pitch(), 1.0E-4D);
        double horizontal = Math.hypot(first.x() - CENTRE_X, first.z() - CENTRE_Z);
        assertEquals(RADIUS, horizontal, 1.0E-9D);
        // 60 degrees down from the camera at the radius lands on the centre's own height.
        assertEquals(CENTRE_Y, first.y() - RADIUS * Math.tan(Math.toRadians(60.0D)), 1.0E-9D);
    }

    @Example
    void theLastFrameLooksLevelFromTheGroundLevel() {
        ShowcaseCamera.Pose last = plan().at(CameraPlan.SHOT_FRAMES);
        assertEquals(0.0D, last.pitch(), 1.0E-4D);
        assertEquals(GROUND_LEVEL, last.y(), 1.0E-9D);
    }

    @Example
    void theCeilingStopsOnlyTheStartHeight() {
        CameraPlan.Plan low = CameraPlan.Plan.of(0, 100, 0, 80, 5.5D, 120.0D, 0.0D);
        assertEquals(120.0D, low.at(0).y(), 1.0E-9D);
        assertEquals(5.5D, low.at(CameraPlan.SHOT_FRAMES).y(), 1.0E-9D);
        assertEquals(60.0D, low.at(0).pitch(), 1.0E-4D);
    }

    @Example
    void theFrameCheckAcceptsThePlanItself() {
        CameraPlan.Plan plan = plan();
        PathCheck.Report report =
                PathCheck.verify(
                        plan,
                        frames(plan::at, CameraPlan.SHOT_FRAMES + 1, CameraPlan.PALETTE_FRAME));
        assertTrue(report.problems().isEmpty(), report.problems().toString());
        assertEquals(CameraPlan.REVOLUTIONS, report.orbits(), 1.0E-9D);
        assertEquals(CameraPlan.PALETTE_FRAME, report.paletteFrame());
    }

    /**
     * The orbit the film flies must be the plan: this is the frame check on the film's own class.
     */
    @Example
    void theOrbitFliesThePlan() {
        ShowcaseCamera.Orbit orbit =
                new ShowcaseCamera.Orbit(
                        CENTRE_X, CENTRE_Y, CENTRE_Z, RADIUS, GROUND_LEVEL, CEILING, START_ANGLE);
        PathCheck.Report report =
                PathCheck.verify(
                        plan(),
                        frames(
                                orbit::atFrame,
                                CameraPlan.SHOT_FRAMES + 1,
                                CameraPlan.PALETTE_FRAME));
        assertTrue(report.problems().isEmpty(), String.join("\n", report.problems()));
    }
}
