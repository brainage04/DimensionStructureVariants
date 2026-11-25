package io.github.brainage04.dimensionstructurevariants;

/**
 * The camera's path through a shot, as one function of the film's frame number: it starts looking
 * {@link #PITCH_START} degrees down and, over the shot's {@link #SHOT_SECONDS} seconds, goes to
 * {@link #PITCH_END} degrees while it descends from its start height to the structure's ground
 * level and turns {@link #REVOLUTIONS} times around the structure at one radius.
 *
 * <p>Pitch, height and angle are straight lines of the frame number, and the palette switch at
 * {@link #PALETTE_FRAME} only chooses which blocks frame 60 shows, so it changes nothing about
 * the motion. Frame 0 is at 0 s and frame {@link #SHOT_FRAMES} at {@link #SHOT_SECONDS} s, so a
 * shot is {@code SHOT_FRAMES + 1} frames and ends on the pose it is planned to end on.
 *
 * <p>This class uses no Minecraft class: the unit tests and the recording share it.
 */
public final class CameraPlan {
    /** The rate the film plays at, and the frame counter's unit. */
    public static final int FRAMES_PER_SECOND = 30;

    /** What a shot lasts from its first frame to its last. */
    public static final int SHOT_SECONDS = 4;

    /** The last frame's number: frame 0 is the start pose and this one the end pose. */
    public static final int SHOT_FRAMES = SHOT_SECONDS * FRAMES_PER_SECOND;

    /** The first frame with the palette on: the shot's 2-second mark. */
    public static final int PALETTE_FRAME = SHOT_FRAMES / 2;

    /** Full turns around the structure in one shot. */
    public static final int REVOLUTIONS = 2;

    /** Degrees below the horizon the camera looks at frame 0. */
    public static final double PITCH_START = 60.0D;

    /** Degrees below the horizon the camera looks at the last frame: level. */
    public static final double PITCH_END = 0.0D;

    private CameraPlan() {}

    /**
     * A circle of {@code radius} around {@code (centreX, centreZ)} that the camera flies from
     * {@code startY} down to {@code endY} while it turns.
     */
    public record Plan(
            double centreX,
            double centreY,
            double centreZ,
            double radius,
            double startY,
            double endY,
            double startAngle) {
        /**
         * A plan whose first frame looks {@link #PITCH_START} degrees down at the centre: the camera
         * starts {@code radius * tan(PITCH_START)} above it, or at {@code ceilingY} where the world
         * ends first.
         */
        public static Plan of(
                double centreX,
                double centreY,
                double centreZ,
                double radius,
                double endY,
                double ceilingY,
                double startAngle) {
            double startY =
                    Math.min(ceilingY, centreY + radius * Math.tan(Math.toRadians(PITCH_START)));
            return new Plan(centreX, centreY, centreZ, radius, startY, endY, startAngle);
        }

        /** The camera at {@code frame}, a film frame number or a point between two of them. */
        public ShowcaseCamera.Pose at(double frame) {
            double u = frame / SHOT_FRAMES;
            double angle = startAngle + 2.0D * Math.PI * REVOLUTIONS * u;
            double x = centreX + Math.cos(angle) * radius;
            double z = centreZ + Math.sin(angle) * radius;
            double y = startY + (endY - startY) * u;
            float yaw = (float) (Math.toDegrees(Math.atan2(centreZ - z, centreX - x)) - 90.0D);
            float pitch = (float) (PITCH_START + (PITCH_END - PITCH_START) * u);
            return new ShowcaseCamera.Pose(x, y, z, yaw, pitch);
        }
    }
}
