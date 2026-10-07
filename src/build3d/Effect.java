package build3d;

/** A short-lived world-space visual: bullet spark, explosion flash, smoke, or a rocket's smoke-trail puff. */
public class Effect {
    public enum Kind { SPARK, SMOKE, EXPLOSION, TRAIL }

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
