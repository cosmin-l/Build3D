package build3d;

/** A simple billboard prop rendered as a flat-shaded vertical box facing the camera. */
public class Sprite {
    public final double x, y;
    public final int sector;
    public final double baseZ;
    public final int color;
    public final double scale;

    public Sprite(double x, double y, int sector, double baseZ, int color, double scale) {
        this.x = x;
        this.y = y;
        this.sector = sector;
        this.baseZ = baseZ;
        this.color = color;
        this.scale = scale;
    }
}
