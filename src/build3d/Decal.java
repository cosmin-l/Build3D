package build3d;

/**
 * A bullet hole stamped onto a wall, positioned in the wall's own texture
 * space: {@code u} is the distance along the wall from (x1,y1), {@code z} the
 * world height. Drawn by {@link Renderer} while it textures that wall, so it
 * scales, shades and occludes exactly like the wall itself.
 */
public class Decal {
    /** Outer radius in world units: dark hole in the middle, scorched ring around it. */
    public static final double RADIUS = 2.6;
    private static final double HOLE_RADIUS = 1.1;

    public final double u, z;
    /** Per-decal random rotation for the ragged ring, so holes don't all look identical. */
    private final double phase;

    public Decal(double u, double z, double phase) {
        this.u = u;
        this.z = z;
        this.phase = phase;
    }

    /**
     * Returns the decal's effect on a wall texel at (pu, pz): -1 if the texel is
     * outside the decal, otherwise the base color multiplied down toward black.
     */
    public int apply(double pu, double pz, int baseColor) {
        double du = pu - u, dz = pz - z;
        double d2 = du * du + dz * dz;
        if (d2 >= RADIUS * RADIUS) return -1;
        double d = Math.sqrt(d2);
        if (d < HOLE_RADIUS) return 0x0c0b0a;
        // Ragged scorch ring: its edge wobbles with angle, darkest next to the hole.
        double edge = RADIUS * (0.75 + 0.25 * Math.sin(Math.atan2(dz, du) * 5 + phase));
        if (d >= edge) return -1;
        double f = (d - HOLE_RADIUS) / (edge - HOLE_RADIUS);
        return Textures.darken(baseColor, 0.3 + 0.55 * f);
    }
}
