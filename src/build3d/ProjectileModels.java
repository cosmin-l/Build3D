package build3d;

/**
 * Procedurally built voxel models for in-flight rockets. Each kind has a few
 * animation frames that differ only in their exhaust flame, so flicking
 * between them makes the flame flicker. Models are laid out with the rocket's
 * long axis along model +Y (nose at high Y), the cross-section in X/Z, so a
 * {@link VoxelSprite} yaw of {@code -atan2(vx, vy)} points the nose along the
 * velocity.
 */
public final class ProjectileModels {
    private static final int FRAMES = 2;
    private static final VoxelModel[] RPG = new VoxelModel[FRAMES];
    private static final VoxelModel[] DEVASTATOR = new VoxelModel[FRAMES];

    static {
        for (int f = 0; f < FRAMES; f++) {
            RPG[f] = buildRpg(f);
            DEVASTATOR[f] = buildDevastator(f);
        }
    }

    private ProjectileModels() {}

    public static VoxelModel frame(Projectile.Kind kind, int frame) {
        VoxelModel[] set = kind == Projectile.Kind.DEVASTATOR_ROCKET ? DEVASTATOR : RPG;
        return set[Math.floorMod(frame, FRAMES)];
    }

    /** Distance from the model's center to its rear end, in world units (where smoke should come out). */
    public static double tailOffset(Projectile.Kind kind) {
        VoxelModel m = frame(kind, 0);
        return m.sizeY * m.voxelSize / 2.0;
    }

    /** RPG rocket: olive body, brass band, grey warhead, four tail fins. */
    private static VoxelModel buildRpg(int frame) {
        final int w = 9, len = 26, c = 4;
        VoxelModel m = new VoxelModel(w, len, w, 0.9);
        int flameLen = frame == 0 ? 6 : 4;
        for (int y = 0; y < len; y++) {
            for (int z = 0; z < w; z++) {
                for (int x = 0; x < w; x++) {
                    int dx = x - c, dz = z - c;
                    double r = Math.hypot(dx, dz);
                    int col = -1;
                    if (y < 6) {
                        col = flame(y, 6, flameLen, 1.8, r);
                    } else if (y == 6) {
                        if (r <= 1.6) col = 0x2a2a2a;
                    } else if (y <= 21) {
                        if (r <= 2.3) col = y == 20 ? 0xc8a030 : 0x4a6b2a;
                        else if (y <= 10 && (dx == 0 || dz == 0) && r <= 4 - (y - 7) / 3.0) col = 0x34461e;
                    } else {
                        double tip = new double[] { 2.3, 1.8, 1.2, 0.5 }[y - 22];
                        if (r <= tip) col = 0x6e6e64;
                    }
                    if (col >= 0) m.set(x, y, z, lit(col, dz, flameLen > 0 && y < 6));
                }
            }
        }
        return m;
    }

    /** Devastator round: small silver dart with a red nose and tiny fins. */
    private static VoxelModel buildDevastator(int frame) {
        final int w = 5, len = 16, c = 2;
        VoxelModel m = new VoxelModel(w, len, w, 0.8);
        int flameLen = frame == 0 ? 4 : 3;
        for (int y = 0; y < len; y++) {
            for (int z = 0; z < w; z++) {
                for (int x = 0; x < w; x++) {
                    int dx = x - c, dz = z - c;
                    double r = Math.hypot(dx, dz);
                    int col = -1;
                    if (y < 4) {
                        col = flame(y, 4, flameLen, 1.1, r);
                    } else if (y == 4) {
                        if (r <= 1.0) col = 0x2a2a2a;
                    } else if (y <= 12) {
                        if (r <= 1.3) col = y == 11 ? 0xb02818 : 0xb8bcc4;
                        else if (y <= 6 && (dx == 0 || dz == 0) && r <= 2) col = 0x8a8e96;
                    } else {
                        double tip = new double[] { 1.0, 1.0, 0.3 }[y - 13];
                        if (r <= tip) col = 0xc03020;
                    }
                    if (col >= 0) m.set(x, y, z, lit(col, dz, y < 4));
                }
            }
        }
        return m;
    }

    /**
     * Exhaust flame voxel color, or -1 for empty. The flame occupies
     * [nozzleY - flameLen, nozzleY), widest at the nozzle and tapering away,
     * with a white-hot core shading out to orange.
     */
    private static int flame(int y, int nozzleY, int flameLen, double maxR, double r) {
        int dist = nozzleY - y; // 1 = right behind the nozzle
        if (dist > flameLen) return -1;
        double t = (dist - 1) / (double) flameLen; // 0 at nozzle .. ~1 at the tip
        double radius = maxR * (1.0 - t * 0.75);
        if (r > radius) return -1;
        if (t < 0.4 && r <= radius * 0.55) return 0xfff2b0;
        return t < 0.6 ? 0xffb030 : 0xff6a10;
    }

    /** Fake top-down lighting so the flat-shaded voxels read as round; the flame stays at full brightness. */
    private static int lit(int col, int dz, boolean emissive) {
        return emissive ? col : Textures.darken(col, 0.85 + 0.08 * dz);
    }
}
