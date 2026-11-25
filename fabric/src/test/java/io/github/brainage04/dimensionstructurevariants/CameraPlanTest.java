package io.github.brainage04.dimensionstructurevariants;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.jqwik.api.Example;

/**
 * The camera's path is one function of the film's frame number: these tests state what Thomas asked
 * of it (24 Nov 2026, A-2311) in numbers worked out by hand, not by calling the plan on itself.
 */
class CameraPlanTest {
    private static final double CENTRE_X = 1000.0D;
    private static final double CENTRE_Y = 70.0D;
    private static final double CENTRE_Z = -300.0D;
    private static final double RADIUS = 45.0D;
    private static final double GROUND_LEVEL = 65.5D;
    private static final double CEILING = 318.0D;
    private static final double START_ANGLE = 0.0D;

    /** The camera's height at frame 0: 45 blocks out and 60 degrees down lands on the centre. */
    private static final double START_HEIGHT = 147.94228634D;

    /** Height lost in one frame: (65.5 - 147.94228634) / 120. */
    private static final double HEIGHT_STEP = -0.68701905D;

    private static final double YAW_STEP = 6.0D;
    private static final double PITCH_STEP = -0.5D;

    private static CameraPlan.Plan plan() {
        return CameraPlan.Plan.of(
                CENTRE_X, CENTRE_Y, CENTRE_Z, RADIUS, GROUND_LEVEL, CEILING, START_ANGLE);
    }

    /** The orbit the film flies: its frames are the plan's. */
    private static ShowcaseCamera.Orbit orbit() {
        return new ShowcaseCamera.Orbit(
                CENTRE_X, CENTRE_Y, CENTRE_Z, RADIUS, GROUND_LEVEL, CEILING, START_ANGLE);
    }

    private static void assertPose(
            ShowcaseCamera.Pose pose, double x, double y, double z, double yaw, double pitch) {
        assertEquals(x, pose.x(), 1.0E-6D, "x");
        assertEquals(y, pose.y(), 1.0E-6D, "y");
        assertEquals(z, pose.z(), 1.0E-6D, "z");
        assertEquals(yaw, pose.yaw(), 1.0E-4D, "yaw");
        assertEquals(pitch, pose.pitch(), 1.0E-4D, "pitch");
    }

    /** Smallest signed difference of two angles in degrees. */
    private static double angleBetween(double from, double to) {
        double difference = (to - from) % 360.0D;
        if (difference > 180.0D) difference -= 360.0D;
        if (difference <= -180.0D) difference += 360.0D;
        return difference;
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

    /**
     * Frames 0, 30, 60, 90 and 120 are a quarter of the shot apart, which is half a turn of the
     * two turns: the camera is on the east side at 0, 60 and 120 and on the west side at 30 and 90,
     * always looking at the centre. The numbers are worked out by hand.
     */
    @Example
    void theOrbitIsOnTheHandWorkedPosesAtEveryQuarter() {
        ShowcaseCamera.Orbit orbit = orbit();
        double east = CENTRE_X + RADIUS;
        double west = CENTRE_X - RADIUS;
        assertPose(orbit.atFrame(0), east, START_HEIGHT, CENTRE_Z, 90.0D, 60.0D);
        assertPose(orbit.atFrame(30), west, 127.33171476D, CENTRE_Z, -90.0D, 45.0D);
        assertPose(orbit.atFrame(60), east, 106.72114317D, CENTRE_Z, 90.0D, 30.0D);
        assertPose(orbit.atFrame(90), west, 86.11057159D, CENTRE_Z, -90.0D, 15.0D);
        assertPose(orbit.atFrame(120), east, GROUND_LEVEL, CENTRE_Z, 90.0D, 0.0D);
    }

    /**
     * The palette goes in at frame 60 and changes nothing about the motion: every frame, the ones
     * either side of 59 to 60 included, turns the camera 6 degrees of yaw (two turns in 120
     * frames), tilts it half a degree and lowers it by the same height.
     */
    @Example
    void everyFrameStepIsTheSameAcrossThePaletteSwitch() {
        ShowcaseCamera.Orbit orbit = orbit();
        for (int frame = 1; frame <= CameraPlan.SHOT_FRAMES; frame++) {
            ShowcaseCamera.Pose before = orbit.atFrame(frame - 1);
            ShowcaseCamera.Pose after = orbit.atFrame(frame);
            String at = "frame " + (frame - 1) + " to " + frame;
            assertEquals(YAW_STEP, angleBetween(before.yaw(), after.yaw()), 1.0E-4D, at);
            assertEquals(PITCH_STEP, after.pitch() - before.pitch(), 1.0E-4D, at);
            assertEquals(HEIGHT_STEP, after.y() - before.y(), 1.0E-6D, at);
        }
    }
}
