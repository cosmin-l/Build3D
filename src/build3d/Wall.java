package build3d;

/** One edge of a sector's polygon boundary. */
public class Wall {
    public final double x1, y1, x2, y2;
    /** Neighbor sector index this wall opens into, or -1 if solid. */
    public final int portal;
    public final int color;
    /** Procedural wall texture id, or -1 for a flat color. */
    public final int textureId;

    public Wall(double x1, double y1, double x2, double y2, int portal, int color, int textureId) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.portal = portal;
        this.color = color;
        this.textureId = textureId;
    }

    public double length() {
        return Math.hypot(x2 - x1, y2 - y1);
    }
}
