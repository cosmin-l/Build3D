package build3d;

import java.util.List;

/** A convex-ish room: a closed polygon of walls plus floor/ceiling heights. */
public class Sector {
    public final List<Wall> walls;
    public final double floorZ, ceilZ;
    public final int floorColor, ceilColor;
    public double light = 1.0;
    public int floorTex = -1, ceilTex = -1;

    public Sector(List<Wall> walls, double floorZ, double ceilZ, int floorColor, int ceilColor) {
        this.walls = walls;
        this.floorZ = floorZ;
        this.ceilZ = ceilZ;
        this.floorColor = floorColor;
        this.ceilColor = ceilColor;
    }

    /** Standard ray-casting point-in-polygon test using the wall vertices in order. */
    public boolean contains(double px, double py) {
        boolean inside = false;
        int n = walls.size();
        for (int i = 0; i < n; i++) {
            Wall w = walls.get(i);
            double x1 = w.x1, y1 = w.y1, x2 = w.x2, y2 = w.y2;
            if ((y1 > py) != (y2 > py)) {
                double xCross = x1 + (py - y1) / (y2 - y1) * (x2 - x1);
                if (px < xCross) inside = !inside;
            }
        }
        return inside;
    }
}
