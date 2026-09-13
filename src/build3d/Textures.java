package build3d;

/**
 * Procedural wall/flat "textures" so the engine needs no image assets.
 * Each function takes world-space coordinates in feet and a base color to
 * tint, and returns a shaded 0xRRGGBB color.
 */
public final class Textures {
    private Textures() {}

    public static int sampleWall(int texId, double u, double v, int base) {
        switch (texId) {
            case 0: return brick(u, v, base);
            case 1: return panel(u, v, base);
            case 2: return stripes(u, v, base);
            default: return base;
        }
    }

    private static int brick(double u, double v, int base) {
        double tileW = 32, tileH = 16, mortar = 2;
        double row = Math.floor(mod(v, tileH * 1000) / tileH);
        double uu = u + ((((long) row) % 2 == 0) ? 0 : tileW / 2);
        double fu = mod(uu, tileW);
        double fv = mod(v, tileH);
        boolean isMortar = fu < mortar || fv < mortar;
        return isMortar ? darken(base, 0.55) : darken(base, 0.92);
    }

    private static int panel(double u, double v, int base) {
        double cell = 48, line = 2;
        double fu = mod(u, cell);
        double fv = mod(v, cell);
        boolean isLine = fu < line || fv < line;
        return isLine ? darken(base, 0.5) : darken(base, 1.0);
    }

    private static int stripes(double u, double v, int base) {
        double cell = 24;
        boolean alt = ((long) Math.floor(mod(u, cell * 2) / cell)) == 0;
        return darken(base, alt ? 1.0 : 0.75);
    }

    private static double mod(double a, double m) {
        double r = a % m;
        return r < 0 ? r + m : r;
    }

    static int darken(int rgb, double f) {
        f = Math.max(0, Math.min(1.2, f));
        int r = (int) (((rgb >> 16) & 0xFF) * f);
        int g = (int) (((rgb >> 8) & 0xFF) * f);
        int b = (int) ((rgb & 0xFF) * f);
        r = Math.min(255, r);
        g = Math.min(255, g);
        b = Math.min(255, b);
        return (r << 16) | (g << 8) | b;
    }
}
