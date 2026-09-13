package build3d;

/** A billboard prop rendered camera-facing: flat-shaded, or image-textured with alpha cutout. */
public class Sprite {
    public final double x, y;
    public final int sector;
    public final double baseZ;
    public final int color;
    public final double scale;
    /** Registered image texture id, or -1 for a flat-shaded color box. */
    public final int imageTex;

    public Sprite(double x, double y, int sector, double baseZ, int color, double scale) {
        this(x, y, sector, baseZ, color, scale, -1);
    }

    public Sprite(double x, double y, int sector, double baseZ, int color, double scale, int imageTex) {
        this.x = x;
        this.y = y;
        this.sector = sector;
        this.baseZ = baseZ;
        this.color = color;
        this.scale = scale;
        this.imageTex = imageTex;
    }
}
