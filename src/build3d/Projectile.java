package build3d;

/** A flying rocket or devastator-round. */
public class Projectile {
    public enum Kind { ROCKET, DEVASTATOR_ROCKET }

    public double x, y, z;
    public double vx, vy, vz;
    public int sector;
    public final Kind kind;
    public double life;
    /** Seconds until the next smoke-trail puff. */
    public double trailT = 0;

    public Projectile(double x, double y, double z, double vx, double vy, double vz, int sector, Kind kind) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        this.sector = sector;
        this.kind = kind;
        this.life = 8.0;
    }
}
