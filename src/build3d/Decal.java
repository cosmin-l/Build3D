package build3d;

/**
 * A mark stamped onto a wall -- a bullet hole or an explosion scorch --
 * positioned in the wall's own texture space: {@code u} is the distance along
 * the wall from (x1,y1), {@code z} the world height. Drawn by {@link Renderer}
 * while it textures that wall, so it scales, shades and occludes exactly like
 * the wall itself.
 */
public class Decal {
    public enum Kind { BULLET, SCORCH }

    private static final double BULLET_RADIUS = 2.6;
    private static final double HOLE_RADIUS = 1.1;

    public final Kind kind;
    public final double u, z;
    /** Outer radius in world units; nothing outside it is touched. */
    public final double radius;
    /** Per-decal random rotation for the ragged edge, so marks don't all look identical. */
    private final double phase;

    private Decal(Kind kind, double u, double z, double radius, double phase) {
        this.kind = kind;
        this.u = u;
        this.z = z;
        this.radius = radius;
        this.phase = phase;
    }

    public static Decal bullet(double u, double z, double phase) {
        return new Decal(Kind.BULLET, u, z, BULLET_RADIUS, phase);
    }

    public static Decal scorch(double u, double z, double radius, double phase) {
        return new Decal(Kind.SCORCH, u, z, radius, phase);
    }

    /**
     * Returns the decal's effect on a wall texel at (pu, pz): -1 if the texel is
     * outside the decal, otherwise the color multiplied down toward black.
     */
    public int apply(double pu, double pz, int color) {
        double du = pu - u, dz = pz - z;
        double d2 = du * du + dz * dz;
        if (d2 >= radius * radius) return -1;
        double d = Math.sqrt(d2);
        double ang = Math.atan2(dz, du);

        if (kind == Kind.BULLET) {
            if (d < HOLE_RADIUS) return 0x0c0b0a;
            // Ragged scorch ring: its edge wobbles with angle, darkest next to the hole.
            double edge = radius * (0.75 + 0.25 * Math.sin(ang * 5 + phase));
            if (d >= edge) return -1;
            double f = (d - HOLE_RADIUS) / (edge - HOLE_RADIUS);
            return Textures.darken(color, 0.3 + 0.55 * f);
        }

        // Scorch: soot blotch whose edge splays out in uneven tongues, black at the
        // center and blending smoothly back to the untouched wall at the rim.
        double edge = radius * (0.72
                + 0.14 * Math.sin(ang * 3 + phase)
                + 0.09 * Math.sin(ang * 7 + phase * 2.3)
                + 0.05 * Math.sin(ang * 13 + phase * 4.1));
        if (d >= edge) return -1;
        double f = d / edge;
        return Textures.darken(color, 0.08 + 0.92 * f * f);
    }
}
