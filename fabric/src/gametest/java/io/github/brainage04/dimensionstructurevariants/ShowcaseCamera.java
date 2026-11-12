package io.github.brainage04.dimensionstructurevariants;

/**
 * The recording's camera, posed on the client for every frame the client draws, so the orbit moves
 * between the game's ticks as well as on them. {@code gametest.mixin.CameraMixin} asks {@link
 * #framePose} for the pose of each frame.
 *
 * <p>The test thread either holds the camera at one point of an orbit (off camera, while the client
 * loads and rehearses the shot) or runs the orbit; a running orbit is a function of the film's own
 * clock, which starts at the first frame the shot draws: {@link #SHOT_TICKS} game ticks at the
 * game's {@link #SHOT_TICK_RATE} are {@link #SHOT_NANOS} of picture, and the orbit turns once in
 * them. Counting the frame's time rather than its tick is what keeps a new pose on every frame when
 * the client draws thirty frames a second and ticks twenty times, and what lets the orbit keep its
 * pace while the client waits for a slow server tick: that wait costs the frames the client does
 * not draw, which {@link #takeStill()} measures.
 */
public final class ShowcaseCamera {
    /** Length of one shot in ticks: 4 seconds. */
    public static final int SHOT_TICKS = 80;

    /** Game ticks a second while a shot is filmed: the game's own rate, so a shot is 4 seconds. */
    public static final int SHOT_TICK_RATE = 20;

    /** The film a shot covers: its {@link #SHOT_TICKS} ticks at {@link #SHOT_TICK_RATE}. */
    public static final long SHOT_NANOS = SHOT_TICKS * 1_000_000_000L / SHOT_TICK_RATE;

    /** Full turns around the structure in one shot: one, so a turn takes the shot's 4 seconds. */
    public static final int REVOLUTIONS = 1;

    /** Degrees below the horizon the camera looks at the centre when a shot starts. */
    public static final double PITCH_START = 30.0D;

    /** Degrees above the horizon the camera looks at the centre when a shot ends. */
    public static final double PITCH_END = -30.0D;

    private static Orbit orbit;
    private static double heldProgress;
    private static boolean running;
    private static long frames;
    private static double startProgress;
    private static double lastProgress;
    private static long startNanos;
    private static long lastFrameNanos;
    private static long longestFrameGapNanos;
    private static long stillStartNanos;
    private static long longestStillNanos;
    private static long stillRepeats;
    private static long longestStillRepeats;
    private static long repeats;
    private static long pictures;

    private ShowcaseCamera() {}

    /** Holds the camera at {@code progress} (0 to 1) of {@code orbit}; client thread only. */
    public static void hold(Orbit orbit, double progress) {
        ShowcaseCamera.orbit = orbit;
        heldProgress = progress;
        lastProgress = progress;
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
        lastProgress = from;
        // The clock starts at the first frame the shot draws, so that frame shows the pose the cut
        // joins the shot's halves on and no time is counted for the call itself.
        startNanos = 0L;
        stillStartNanos = 0L;
        longestStillNanos = 0L;
        stillRepeats = 0L;
        longestStillRepeats = 0L;
        repeats = 0L;
        pictures = 0L;
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

    /**
     * The pose this frame shows, or null when the camera follows the player. Client thread only,
     * once per frame the client draws.
     */
    public static Pose framePose() {
        frames++;
        long now = System.nanoTime();
        if (lastFrameNanos != 0L)
            longestFrameGapNanos = Math.max(longestFrameGapNanos, now - lastFrameNanos);
        lastFrameNanos = now;
        Orbit current = orbit;
        if (current == null) return null;
        double progress = heldProgress;
        if (running) {
            if (startNanos == 0L) {
                // The shot's first frame shows the pose it starts on: the clock and the run of
                // stills both start here, and no picture is counted yet.
                startNanos = now;
                stillStartNanos = now;
                progress = startProgress;
            } else {
                progress = progressAt(startProgress, now - startNanos);
                // The picture stands still while the pose stays where it was: the frames of a
                // stall, which the film shows as a repeated image.
                if (progress != lastProgress) {
                    pictures++;
                    if (now - stillStartNanos > longestStillNanos) {
                        longestStillNanos = now - stillStartNanos;
                        longestStillRepeats = stillRepeats;
                    }
                    stillStartNanos = now;
                    stillRepeats = 0L;
                } else {
                    stillRepeats++;
                    repeats++;
                }
            }
        }
        lastProgress = progress;
        return current.at(progress);
    }

    /** The camera's progress through a shot {@code elapsedNanos} after its first frame. */
    static double progressAt(double from, long elapsedNanos) {
        return Math.max(0.0D, Math.min(1.0D, from + elapsedNanos / (double) SHOT_NANOS));
    }

    /**
     * The longest time between two frames since the last call, in nanoseconds: the longest the
     * client went without drawing, which is a stall only if no new pose came of it either. Client
     * thread only.
     */
    public static long takeLongestFrameGap() {
        long longest = longestFrameGapNanos;
        longestFrameGapNanos = 0L;
        return longest;
    }

    /**
     * How long one picture ever stood still since the last call, how many frames repeated it, and
     * how many pictures came. A stall shows as one long still with no repeat; a client drawing the
     * same pose twice shows as a still with repeats. Client thread only.
     */
    public static Still takeStill() {
        Still taken = new Still(longestStillNanos, longestStillRepeats, repeats, pictures);
        longestStillNanos = 0L;
        longestStillRepeats = 0L;
        repeats = 0L;
        pictures = 0L;
        return taken;
    }

    /**
     * The longest a picture stood still while a shot ran, how many frames repeated the picture of
     * that still, how many frames repeated a picture in the whole shot and how many pictures came.
     * A long still with no repeats means the client drew no frame meanwhile.
     */
    public record Still(long nanos, long longestRepeats, long repeats, long pictures) {}

    /**
     * The frames posed since the client started: one per frame, so the difference across a span of
     * time is what the client presented then.
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
