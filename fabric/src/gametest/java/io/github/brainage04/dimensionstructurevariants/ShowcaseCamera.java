package io.github.brainage04.dimensionstructurevariants;

import net.minecraft.client.Minecraft;

/**
 * The recording's camera, driven on the client every frame instead of by a server teleport every
 * tick, so the orbit moves smoothly between ticks at 30 frames per second. {@code
 * gametest.mixin.CameraMixin} asks {@link #framePose} for the pose of each frame.
 *
 * <p>The test thread either holds the camera at one point of an orbit (off camera, while the client
 * loads and rehearses the shot) or runs the orbit from the current tick; a running orbit is a
 * function of client ticks plus the frame's partial tick, so it moves at the game's own pace and
 * stays in step with the day passing on the server.
 */
public final class ShowcaseCamera {
    /** Length of one shot in ticks: 4 seconds. */
    public static final int SHOT_TICKS = 80;

    /** Full turns around the structure in one shot: one, so a turn takes the shot's 4 seconds. */
    public static final int REVOLUTIONS = 1;

    /** Degrees below the horizon the camera looks at the centre when a shot starts. */
    public static final double PITCH_START = 30.0D;

    /** Degrees above the horizon the camera looks at the centre when a shot ends. */
    public static final double PITCH_END = -30.0D;

    private static Orbit orbit;
    private static double heldProgress;
    private static boolean running;
    private static long ticks;
    private static long frames;
    private static long startTick;
    private static float startPartial;
    private static double startProgress;
    private static double lastProgress;
    private static long lastFrameNanos;
    private static long longestFrameGapNanos;

    private ShowcaseCamera() {}

    /** Holds the camera at {@code progress} (0 to 1) of {@code orbit}; client thread only. */
    public static void hold(Orbit orbit, double progress) {
        ShowcaseCamera.orbit = orbit;
        heldProgress = progress;
        running = false;
    }

    /**
     * Runs the shot from {@code from} (0 to 1) of {@code orbit}, continuing from the pose a held
     * camera shows; test thread's client tasks only.
     */
    public static void run(Orbit orbit, double from) {
        ShowcaseCamera.orbit = orbit;
        running = true;
        startProgress = from;
        // A client GameTest hands the client its tasks just before the tick of the frame they
        // run in (Fabric's gametest MinecraftMixin.postRunTasksHook), so that tick has not counted
        // yet: counting it here puts the first frame on the held pose instead of a tick ahead.
        startTick = ticks + 1;
        startPartial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
    }

    /**
     * Stops a running shot on the pose of the last frame and returns its progress; client thread
     * only.
     */
    public static double pause() {
        hold(orbit, lastProgress);
        return lastProgress;
    }

    /** Hands the camera back to the player; client thread only. */
    public static void release() {
        orbit = null;
        running = false;
    }

    /** Counts client ticks; called by the camera once per client tick. */
    public static void onTick() {
        ticks++;
    }

    /** The progress of the running shot at the given partial tick, or the held one. */
    public static double progress(float partialTicks) {
        if (!running) return heldProgress;
        double elapsed = ticks - startTick + partialTicks - startPartial;
        return Math.max(0.0D, Math.min(1.0D, startProgress + elapsed / SHOT_TICKS));
    }

    /** The pose this frame shows, or null when the camera follows the player. */
    public static Pose framePose(float partialTicks) {
        frames++;
        long now = System.nanoTime();
        if (lastFrameNanos != 0L)
            longestFrameGapNanos = Math.max(longestFrameGapNanos, now - lastFrameNanos);
        lastFrameNanos = now;
        Orbit current = orbit;
        if (current == null) return null;
        lastProgress = progress(partialTicks);
        return current.at(lastProgress);
    }

    /**
     * The longest time between two frames since the last call, in nanoseconds: the longest the
     * picture stood still. Client thread only.
     */
    public static long takeLongestFrameGap() {
        long longest = longestFrameGapNanos;
        longestFrameGapNanos = 0L;
        return longest;
    }

    /**
     * The frames posed since the client started: one per frame, so the difference across a span of
     * time is what the client presented then. A card presents several frames per tick, software
     * less than one, and the test reads this to film each shot at a rate its client can keep up
     * with.
     */
    public static long frames() {
        return frames;
    }

    /** A camera position and the rotation that looks at the orbit's centre. */
    public record Pose(double x, double y, double z, float yaw, float pitch) {}

    /**
     * A circle around {@code (centreX, centreY, centreZ)} at a fixed {@code radius}, flown {@link
     * #REVOLUTIONS} time per shot while the camera descends from looking {@link #PITCH_START}
     * degrees down at the centre to looking {@link #PITCH_END} degrees up at it. The height stays
     * between {@code floorY} and {@code ceilingY}; where the floor stops the descent the camera
     * keeps circling on it and the final angle is smaller.
     */
    public record Orbit(
            double centreX,
            double centreY,
            double centreZ,
            double radius,
            double floorY,
            double ceilingY,
            double startAngle) {
        public double height(double progress) {
            double pitch = Math.toRadians(PITCH_START + (PITCH_END - PITCH_START) * progress);
            double y = centreY + radius * Math.tan(pitch);
            return Math.max(floorY, Math.min(ceilingY, y));
        }

        public Pose at(double progress) {
            double angle = startAngle + 2.0D * Math.PI * REVOLUTIONS * progress;
            double x = centreX + Math.cos(angle) * radius;
            double y = height(progress);
            double z = centreZ + Math.sin(angle) * radius;
            double dx = centreX - x;
            double dy = centreY - y;
            double dz = centreZ - z;
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            return new Pose(x, y, z, yaw, pitch);
        }

        /** Degrees the camera looks up at the centre at the end (negative: still looking down). */
        public double finalUpAngle() {
            return Math.toDegrees(Math.atan2(centreY - height(1.0D), radius));
        }
    }
}
