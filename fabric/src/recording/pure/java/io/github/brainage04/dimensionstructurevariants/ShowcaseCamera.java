package io.github.brainage04.dimensionstructurevariants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * The recording's camera, posed on the client for every frame the client draws. {@code
 * recording.mixin.CameraMixin} asks {@link #framePose} for the pose of each frame and then reports
 * back what the game's own camera holds ({@link #applied}).
 *
 * <p>The test thread either holds the camera at one frame of an orbit (off camera, while the client
 * loads and rehearses the shot) or runs the shot. A running shot is a function of the film's own
 * frame counter, not of any clock: the first frame the client draws after {@link #run} shows frame
 * 0 of the {@link CameraPlan}, and every frame after it the next one, so the film's frame {@code n}
 * is the plan's pose at {@code n / 30} s whatever the client's speed or the game's tick. Frames the
 * client draws while it is slow or waits repeat a picture, and the cut drops the repeats.
 *
 * <p>At {@link CameraPlan#PALETTE_FRAME} the camera holds on the previous frame's pose until {@link
 * #resume}: the palette goes in off camera, and the first frame after it is the plan's frame 60, so
 * the switch changes nothing about the motion.
 */
public final class ShowcaseCamera {
    /**
     * The game's own tick rate, which the server is set to while a shot is filmed. The film does
     * not play the world at it: the world ticks about once per drawn frame.
     */
    public static final int SHOT_TICK_RATE = 20;

    /**
     * The least time between two frames of a shot reaching the screen, in milliseconds: more than
     * the capture's slot of {@link CameraPlan#FRAMES_PER_SECOND} a second (33.3 ms). The recorder
     * shows the newest frame at each of its slots, so a frame presented less than a slot after the
     * one before it hides that one, and its pose is missing from the film. The game's own frame cap
     * does not prevent it: after a slow frame the next one is drawn at once, and a cap only spaces
     * the frames' starts, not their presents. The gap is 50 ms so the recorder's copy buffers have
     * time between two frames.
     */
    public static final long PRESENT_GAP_MILLIS = 50L;

    /** Frames of one shot the log keeps: more than a slow client draws in a shot. */
    private static final int LOG_FRAMES = 1024;

    private static final double[] LOG_X = new double[LOG_FRAMES];
    private static final double[] LOG_Y = new double[LOG_FRAMES];
    private static final double[] LOG_Z = new double[LOG_FRAMES];
    private static final float[] LOG_YAW = new float[LOG_FRAMES];
    private static final float[] LOG_PITCH = new float[LOG_FRAMES];
    private static final boolean[] LOG_PALETTE = new boolean[LOG_FRAMES];
    private static final long[] LOG_MILLIS = new long[LOG_FRAMES];

    private static Orbit orbit;

    /** The orbit's pose at every film frame, built once so a frame allocates nothing. */
    private static Pose[] poses;

    private static int heldFrame;
    private static boolean running;
    private static long frames;

    /** The number of the shot's next frame; one past the last once the shot has ended. */
    private static int nextFrame;

    /** The number of the frame last shown, -1 before the shot's first. */
    private static int lastFrame = -1;

    private static boolean holding;
    private static boolean passedPalette;
    private static boolean paletteOn;
    private static int logged;
    private static boolean pendingLog;
    private static long lastFrameNanos;
    private static long longestFrameGapNanos;
    private static long stillStartNanos;
    private static long longestStillNanos;
    private static long stillRepeats;
    private static long longestStillRepeats;
    private static long repeats;
    private static long pictures;

    private ShowcaseCamera() {}

    private static void use(Orbit next) {
        if (next == orbit && poses != null) return;
        orbit = next;
        poses = new Pose[CameraPlan.SHOT_FRAMES + 1];
        CameraPlan.Plan plan = next.plan();
        for (int n = 0; n < poses.length; n++) poses[n] = plan.at(n);
    }

    /** Holds the camera at {@code frame} of {@code orbit}; client thread only. */
    public static void hold(Orbit orbit, int frame) {
        use(orbit);
        heldFrame = frame;
        running = false;
    }

    /**
     * Runs the shot from its frame 0 on {@code orbit}, continuing from the pose a held camera shows
     * at frame 0; test thread's client tasks only.
     */
    public static void run(Orbit orbit) {
        use(orbit);
        running = true;
        nextFrame = 0;
        lastFrame = -1;
        holding = false;
        passedPalette = false;
        paletteOn = false;
        logged = 0;
        pendingLog = false;
        resetStills();
    }

    private static void resetStills() {
        stillStartNanos = 0L;
        longestStillNanos = 0L;
        stillRepeats = 0L;
        longestStillRepeats = 0L;
        repeats = 0L;
        pictures = 0L;
    }

    /**
     * Stops a running shot on the pose of the last frame and returns that frame's number; client
     * thread only.
     */
    public static int pause() {
        int frame = Math.max(0, lastFrame);
        hold(orbit, frame);
        return frame;
    }

    /**
     * Carries the shot on from the palette frame, which the camera has been waiting at; client
     * thread only. The still clock starts at the first frame after it: the wait's own tail is not a
     * still the film shows.
     */
    public static void resume() {
        holding = false;
        passedPalette = true;
        paletteOn = true;
        running = true;
        resetStills();
    }

    /** The number of the frame the last drawn frame showed; client thread only. */
    public static int lastFrame() {
        return lastFrame;
    }

    /** Whether the camera waits at the palette frame for {@link #resume}; client thread only. */
    public static boolean holding() {
        return holding;
    }

    /** Hands the camera back to the player; client thread only. */
    public static void release() {
        orbit = null;
        poses = null;
        running = false;
    }

    /**
     * Waits {@link #PRESENT_GAP_MILLIS} while a shot runs, called right after a frame was presented
     * and before the next one starts, so the next frame reaches the screen at least that long after
     * this one. Client thread only.
     */
    public static void spaceFrames() {
        if (!running) return;
        long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(PRESENT_GAP_MILLIS);
        long left;
        while ((left = until - System.nanoTime()) > 0L) LockSupport.parkNanos(left);
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
        if (orbit == null) return null;
        int frame = heldFrame;
        pendingLog = false;
        if (running) {
            if (holding || (!passedPalette && nextFrame == CameraPlan.PALETTE_FRAME)) {
                // The frame before the palette frame again: the shot waits off camera.
                holding = true;
                frame = lastFrame;
            } else {
                frame = Math.min(nextFrame, CameraPlan.SHOT_FRAMES);
                pendingLog = nextFrame <= CameraPlan.SHOT_FRAMES;
                nextFrame = Math.min(nextFrame + 1, CameraPlan.SHOT_FRAMES + 1);
            }
            // The picture stands still while the pose stays where it was: the frames of a wait or
            // of a shot past its end. The still clock starts at the shot's first frame and at the
            // first after a resume.
            if (stillStartNanos == 0L) stillStartNanos = now;
            if (frame != lastFrame) {
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
        lastFrame = frame;
        return poses[frame];
    }

    /**
     * Records the camera the frame was drawn from, read back from the game's own camera after it
     * was placed; client thread only, once per frame, after {@link #framePose} placed it.
     */
    public static void applied(double x, double y, double z, float yaw, float pitch) {
        if (!pendingLog) return;
        pendingLog = false;
        if (logged < LOG_FRAMES) {
            LOG_X[logged] = x;
            LOG_Y[logged] = y;
            LOG_Z[logged] = z;
            LOG_YAW[logged] = yaw;
            LOG_PITCH[logged] = pitch;
            LOG_PALETTE[logged] = paletteOn;
            LOG_MILLIS[logged] = System.currentTimeMillis();
        }
        logged++;
    }

    /** The wall-clock time, in epoch milliseconds, each frame of the last shot was drawn at. */
    public static long[] loggedMillis() {
        return Arrays.copyOf(LOG_MILLIS, Math.min(logged, LOG_FRAMES));
    }

    /** The frames of the last shot, in the order they were drawn; client thread only. */
    public static List<PathCheck.Frame> appliedFrames() {
        List<PathCheck.Frame> frames = new ArrayList<>();
        for (int n = 0; n < Math.min(logged, LOG_FRAMES); n++) {
            frames.add(
                    new PathCheck.Frame(
                            LOG_X[n],
                            LOG_Y[n],
                            LOG_Z[n],
                            LOG_YAW[n],
                            LOG_PITCH[n],
                            LOG_PALETTE[n]));
        }
        return frames;
    }

    /**
     * The longest time between two frames since the last call, in nanoseconds: the longest the
     * client went without drawing. Client thread only.
     */
    public static long takeLongestFrameGap() {
        long longest = longestFrameGapNanos;
        longestFrameGapNanos = 0L;
        return longest;
    }

    /**
     * How long one picture ever stood still since the last call, how many frames repeated it, and
     * how many pictures came. A client that draws nothing for a while shows as one long still; one
     * that draws the same frame twice as a still with repeats. Client thread only.
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
     * A circle around {@code (centreX, centreZ)} at a fixed {@code radius}, flown {@link
     * CameraPlan#REVOLUTIONS} times per shot while the camera descends from looking {@link
     * CameraPlan#PITCH_START} degrees down at {@code centreY} to looking level at {@code endY}, the
     * structure's ground level. The start height is the world's {@code ceilingY} where the
     * look-down height is above it.
     */
    public record Orbit(
            double centreX,
            double centreY,
            double centreZ,
            double radius,
            double endY,
            double ceilingY,
            double startAngle) {
        public CameraPlan.Plan plan() {
            return CameraPlan.Plan.of(
                    centreX, centreY, centreZ, radius, endY, ceilingY, startAngle);
        }

        /** The pose at film frame {@code frame}, or between two frames. */
        public Pose at(double frame) {
            return plan().at(frame);
        }

        /** The pose at film frame {@code frame}. */
        public Pose atFrame(int frame) {
            return at(frame);
        }

        /** The camera's height at the shot's first frame. */
        public double startY() {
            return plan().startY();
        }

        /** Degrees the camera looks up at the centre at the end (negative: still looking down). */
        public double finalUpAngle() {
            return Math.toDegrees(Math.atan2(centreY - endY, radius));
        }

        /** Degrees below the horizon the camera looks at the end. */
        public double finalPitch() {
            return at(CameraPlan.SHOT_FRAMES).pitch();
        }
    }
}
