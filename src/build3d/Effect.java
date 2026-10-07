package build3d;

/** A short-lived world-space visual: bullet spark or explosion flash (smoke lives in {@link Smoke}). */
public class Effect {
    public enum Kind { SPARK, EXPLOSION }

    public final double x, y, z;
    public final int sector;
    public double age = 0;
    public final double ttl;
    public final Kind kind;

    public Effect(double x, double y, double z, int sector, double ttl, Kind kind) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.sector = sector;
        this.ttl = ttl;
        this.kind = kind;
    }

    public double progress() { return Math.min(1.0, age / ttl); }
}
