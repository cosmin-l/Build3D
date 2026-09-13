package build3d;

public class Player {
    public double x, y;
    public double angle; // radians, 0 = facing +Y, increases turning toward +X
    public double pitch; // radians, clamped, drives the Build-style screen shear
    public int sector;
    public double eyeHeightOffset = 41;
    public double eyeZ; // absolute world Z of the eye, smoothed toward sector floor + offset

    public static final double HEIGHT = 56;
    public static final double RADIUS = 16;
    public static final double STEP_MAX = 24;
    public static final double PITCH_LIMIT = 0.62;

    public void applyMap(GameMap map) {
        x = map.startX;
        y = map.startY;
        angle = Math.toRadians(map.startAngleDeg);
        sector = map.startSector;
        eyeHeightOffset = map.startEyeHeight;
        eyeZ = map.sectors.get(sector).floorZ + eyeHeightOffset;
    }

    public double forwardX() { return Math.sin(angle); }
    public double forwardY() { return Math.cos(angle); }
    public double rightX() { return Math.cos(angle); }
    public double rightY() { return -Math.sin(angle); }
}
