package build3d;

/**
 * One soft smoke puff: a round, alpha-blended billboard that drifts with drag,
 * rises, grows, and fades out. Rendered by {@link Renderer} as a translucent
 * blob with a lumpy edge rather than a solid sprite box.
 */
public class Smoke {
    public double x, y, z;
    public double vx, vy, vz;
    public int sector;
    public double age = 0;
    public final double ttl;
    private final double startRadius, endRadius;
    private final int startColor, endColor;
    private final double startAlpha;
    /** Upward acceleration, world units/s^2 (hot smoke rises). */
    private final double buoyancy;
    /** Per-puff noise offset so neighbouring puffs don't share the same lumps. */
    public final double seed;

    public Smoke(double x, double y, double z, int sector, double vx, double vy, double vz,
                 double ttl, double startRadius, double endRadius,
                 int startColor, int endColor, double startAlpha, double buoyancy, double seed) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.sector = sector;
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        this.ttl = ttl;
        this.startRadius = startRadius;
        this.endRadius = endRadius;
        this.startColor = startColor;
        this.endColor = endColor;
        this.startAlpha = startAlpha;
        this.buoyancy = buoyancy;
        this.seed = seed;
    }

    /** Advances the puff; keeps it inside the map and under the ceiling. Returns false once it has faded out. */
    public boolean update(double dt, GameMap map) {
        age += dt;
        if (age >= ttl) return false;
        double drag = Math.exp(-dt * 2.5);
        vx *= drag;
        vy *= drag;
        vz = vz * drag + buoyancy * dt;

        double nx = x + vx * dt, ny = y + vy * dt;
        int ns = map.findSector(nx, ny, sector, map.sectors.get(sector).floorZ);
        if (ns >= 0) {
            x = nx;
            y = ny;
            sector = ns;
        } else {
            vx = vy = 0; // pressed against a wall: just keep rising
        }
        Sector s = map.sectors.get(sector);
        double r = radius();
        z = Math.max(s.floorZ + r * 0.4, Math.min(s.ceilZ - r * 0.4, z + vz * dt));
        return true;
    }

    private double t() { return Math.min(1.0, age / ttl); }

    public double radius() {
        double t = t();
        // eases out: billows quickly at first, then slows
        return startRadius + (endRadius - startRadius) * (1 - (1 - t) * (1 - t));
    }

    public double alpha() {
        double t = t();
        double fadeIn = Math.min(1.0, age / 0.06);
        return startAlpha * fadeIn * (1 - t) * (1 - t * 0.5);
    }

    public int color() {
        double t = t();
        int ar = (startColor >> 16) & 0xFF, ag = (startColor >> 8) & 0xFF, ab = startColor & 0xFF;
        int br = (endColor >> 16) & 0xFF, bg = (endColor >> 8) & 0xFF, bb = endColor & 0xFF;
        return ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }
}
