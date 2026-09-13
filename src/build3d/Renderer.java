package build3d;

import java.util.List;

/**
 * A software sector/portal renderer in the spirit of the Build engine:
 * no BSP tree, no true 3D transform of level geometry -- each frame it walks
 * outward from the sector the player is standing in, through portals, only
 * drawing what is still visible through the shrinking on-screen "window"
 * left open by nearer geometry. Rooms can sit above/below/behind one another
 * with independent floor and ceiling heights (the engine's signature trick).
 *
 * Look direction is a true yaw rotation plus a Build-style vertical
 * "y-shear" for pitch (an offset added after projection) rather than a full
 * 3D camera rotation -- exactly how the original engine faked looking
 * up/down without re-projecting the whole world.
 */
public class Renderer {
    public final int width, height;
    private final double screenDist;
    private static final double NEAR = 0.05;
    private static final double FOG_DIST = 1400;
    private static final double MIN_BRIGHT = 0.12;
    private static final int MAX_DEPTH = 28;

    private int[] pixels;
    private final int[] topOpen;
    private final int[] bottomOpen;
    private final double[] wallDepth;

    public boolean minimapOn = false;

    public Renderer(int width, int height, double fovDegrees) {
        this.width = width;
        this.height = height;
        double fov = Math.toRadians(fovDegrees);
        this.screenDist = (width / 2.0) / Math.tan(fov / 2.0);
        this.topOpen = new int[width];
        this.bottomOpen = new int[width];
        this.wallDepth = new double[width];
    }

    public void render(int[] pixels, GameMap map, Player player) {
        this.pixels = pixels;
        java.util.Arrays.fill(pixels, 0x05050a);
        java.util.Arrays.fill(topOpen, 0);
        java.util.Arrays.fill(bottomOpen, height);
        java.util.Arrays.fill(wallDepth, Double.POSITIVE_INFINITY);

        double pitchShear = player.pitch * screenDist;
        boolean[] visited = new boolean[map.sectors.size()];
        renderSector(map, player, player.sector, 0, width - 1, visited, 0, pitchShear);
        renderSprites(map, player, pitchShear);

        if (minimapOn) drawMinimap(map, player);
    }

    private void renderSector(GameMap map, Player player, int secIdx, int xMin, int xMax,
                               boolean[] visited, int depth, double pitchShear) {
        if (depth > MAX_DEPTH || secIdx < 0 || secIdx >= map.sectors.size() || xMin > xMax) return;
        if (visited[secIdx]) return;
        visited[secIdx] = true;

        Sector sec = map.sectors.get(secIdx);
        double cosA = Math.cos(player.angle), sinA = Math.sin(player.angle);
        double eyeZ = player.eyeZ;

        for (Wall w : sec.walls) {
            // Back-face cull: only draw walls whose front (interior) face points at the player.
            double edgeX = w.x2 - w.x1, edgeY = w.y2 - w.y1;
            double cross = edgeX * (player.y - w.y1) - edgeY * (player.x - w.x1);
            if (cross <= 0) continue;

            double rx1 = w.x1 - player.x, ry1 = w.y1 - player.y;
            double rx2 = w.x2 - player.x, ry2 = w.y2 - player.y;
            double cx1 = rx1 * cosA - ry1 * sinA, cz1 = rx1 * sinA + ry1 * cosA;
            double cx2 = rx2 * cosA - ry2 * sinA, cz2 = rx2 * sinA + ry2 * cosA;
            double u1 = 0, u2 = w.length();

            if (cz1 < NEAR && cz2 < NEAR) continue;
            if (cz1 < NEAR) {
                double t = (NEAR - cz1) / (cz2 - cz1);
                cx1 += (cx2 - cx1) * t;
                u1 += (u2 - u1) * t;
                cz1 = NEAR;
            } else if (cz2 < NEAR) {
                double t = (NEAR - cz2) / (cz1 - cz2);
                cx2 += (cx1 - cx2) * t;
                u2 += (u1 - u2) * t;
                cz2 = NEAR;
            }

            double sx1 = width / 2.0 + (cx1 / cz1) * screenDist;
            double sx2 = width / 2.0 + (cx2 / cz2) * screenDist;
            // Screen-space left/right order depends on view direction, not on
            // vertex order in the polygon -- sort so sx1 is always the left edge.
            if (sx2 < sx1) {
                double tmp;
                tmp = sx1; sx1 = sx2; sx2 = tmp;
                tmp = cz1; cz1 = cz2; cz2 = tmp;
                tmp = u1; u1 = u2; u2 = tmp;
            }
            if (sx2 - sx1 < 1e-6) continue; // degenerate sliver after clipping

            int xStart = Math.max(xMin, Math.max(0, (int) Math.round(sx1)));
            int xEnd = Math.min(xMax, Math.min(width - 1, (int) Math.round(sx2) - 1));
            if (xStart > xEnd) continue;

            double invCz1 = 1.0 / cz1, invCz2 = 1.0 / cz2;
            double uoz1 = u1 * invCz1, uoz2 = u2 * invCz2;
            double dsx = sx2 - sx1;

            boolean isPortal = w.portal >= 0 && w.portal < map.sectors.size();
            Sector nsec = isPortal ? map.sectors.get(w.portal) : null;

            for (int x = xStart; x <= xEnd; x++) {
                if (topOpen[x] >= bottomOpen[x]) continue;
                double t = (x + 0.5 - sx1) / dsx;
                if (t < 0) t = 0; else if (t > 1) t = 1;
                double invCz = invCz1 + (invCz2 - invCz1) * t;
                double camZ = 1.0 / invCz;
                double u = (uoz1 + (uoz2 - uoz1) * t) / invCz;

                int top = topOpen[x], bot = bottomOpen[x];
                int ceilY = clamp(worldToScreenY(sec.ceilZ, eyeZ, camZ, pitchShear), top, bot);
                int floorY = clamp(worldToScreenY(sec.floorZ, eyeZ, camZ, pitchShear), top, bot);
                double shade = shadeFactor(camZ, sec.light);

                if (!isPortal) {
                    drawFlat(x, top, ceilY, sec.ceilColor, shade);
                    drawTexturedWall(x, ceilY, floorY, w, u, eyeZ, camZ, pitchShear, shade);
                    drawFlat(x, floorY, bot, sec.floorColor, shade);
                    wallDepth[x] = Math.min(wallDepth[x], camZ);
                    topOpen[x] = bot;
                } else {
                    int nCeilY = clamp(worldToScreenY(nsec.ceilZ, eyeZ, camZ, pitchShear), top, bot);
                    int nFloorY = clamp(worldToScreenY(nsec.floorZ, eyeZ, camZ, pitchShear), top, bot);
                    int openTop = Math.max(ceilY, nCeilY);
                    int openBottom = Math.min(floorY, nFloorY);

                    drawFlat(x, top, ceilY, sec.ceilColor, shade);
                    if (openTop > ceilY) {
                        drawTexturedWall(x, ceilY, openTop, w, u, eyeZ, camZ, pitchShear, shade);
                        wallDepth[x] = Math.min(wallDepth[x], camZ);
                    }
                    if (openBottom < floorY) {
                        drawTexturedWall(x, openBottom, floorY, w, u, eyeZ, camZ, pitchShear, shade);
                        wallDepth[x] = Math.min(wallDepth[x], camZ);
                    }
                    drawFlat(x, floorY, bot, sec.floorColor, shade);

                    topOpen[x] = openTop;
                    bottomOpen[x] = openBottom;
                }
            }

            if (isPortal) {
                renderSector(map, player, w.portal, xStart, xEnd, visited.clone(), depth + 1, pitchShear);
            }
        }
    }

    private void drawTexturedWall(int x, int y0, int y1, Wall w, double u, double eyeZ,
                                   double camZ, double pitchShear, double shade) {
        if (y0 >= y1) return;
        y0 = Math.max(0, y0);
        y1 = Math.min(height, y1);
        for (int y = y0; y < y1; y++) {
            double worldZ = eyeZ - (y - height / 2.0 - pitchShear) * camZ / screenDist;
            int color = w.textureId >= 0 ? Textures.sampleWall(w.textureId, u, worldZ, w.color) : w.color;
            pixels[y * width + x] = shadeColor(color, shade);
        }
    }

    private void drawFlat(int x, int y0, int y1, int color, double shade) {
        if (y0 >= y1) return;
        y0 = Math.max(0, y0);
        y1 = Math.min(height, y1);
        int shaded = shadeColor(color, shade);
        int base = y0 * width + x;
        for (int y = y0; y < y1; y++) pixels[base + (y - y0) * width] = shaded;
    }

    private void renderSprites(GameMap map, Player player, double pitchShear) {
        List<Sprite> sprites = map.sprites;
        double cosA = Math.cos(player.angle), sinA = Math.sin(player.angle);

        // Painter's algorithm back-to-front for sprite/sprite overlap; walls are
        // handled separately via the wallDepth buffer recorded during the sector pass.
        Integer[] order = new Integer[sprites.size()];
        double[] depths = new double[sprites.size()];
        for (int i = 0; i < sprites.size(); i++) {
            Sprite s = sprites.get(i);
            double rx = s.x - player.x, ry = s.y - player.y;
            depths[i] = rx * sinA + ry * cosA;
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Double.compare(depths[b], depths[a]));

        for (int idx : order) {
            Sprite s = sprites.get(idx);
            double rx = s.x - player.x, ry = s.y - player.y;
            double cx = rx * cosA - ry * sinA, cz = rx * sinA + ry * cosA;
            if (cz < 0.2) continue;

            double sx = width / 2.0 + (cx / cz) * screenDist;
            double worldHalfW = s.scale * 16;
            double worldHeight = s.scale * 64;
            double halfWpx = (worldHalfW / cz) * screenDist;

            int xStart = (int) Math.floor(sx - halfWpx);
            int xEnd = (int) Math.ceil(sx + halfWpx);
            if (xEnd < 0 || xStart >= width) continue;
            xStart = Math.max(0, xStart);
            xEnd = Math.min(width - 1, xEnd);

            int topY = worldToScreenY(s.baseZ + worldHeight, player.eyeZ, cz, pitchShear);
            int botY = worldToScreenY(s.baseZ, player.eyeZ, cz, pitchShear);
            double shade = shadeFactor(cz, 1.0);
            int shaded = shadeColor(s.color, shade);

            for (int x = xStart; x <= xEnd; x++) {
                if (cz >= wallDepth[x]) continue;
                int y0 = Math.max(0, topY);
                int y1 = Math.min(height, botY);
                for (int y = y0; y < y1; y++) pixels[y * width + x] = shaded;
            }
        }
    }

    private void drawMinimap(GameMap map, Player player) {
        int size = Math.min(width, height) / 3;
        int ox = 8, oy = 8;
        double scale = 0.12;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                pixels[(oy + y) * width + (ox + x)] = 0x000000;
            }
        }
        for (Sector sec : map.sectors) {
            for (Wall w : sec.walls) {
                int x1 = ox + size / 2 + (int) ((w.x1 - player.x) * scale);
                int y1 = oy + size / 2 - (int) ((w.y1 - player.y) * scale);
                int x2 = ox + size / 2 + (int) ((w.x2 - player.x) * scale);
                int y2 = oy + size / 2 - (int) ((w.y2 - player.y) * scale);
                drawLine(x1, y1, x2, y2, w.portal < 0 ? 0x00cc66 : 0x336699, ox, oy, size);
            }
        }
        // player marker
        int pcx = ox + size / 2, pcy = oy + size / 2;
        double dx = Math.sin(player.angle) * 6, dy = -Math.cos(player.angle) * 6;
        drawLine(pcx, pcy, (int) (pcx + dx), (int) (pcy + dy), 0xffff33, ox, oy, size);
        setPixelClamped(pcx, pcy, 0xffff33, ox, oy, size);
    }

    private void drawLine(int x0, int y0, int x1, int y1, int color, int ox, int oy, int size) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            setPixelClamped(x0, y0, color, ox, oy, size);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    private void setPixelClamped(int x, int y, int color, int ox, int oy, int size) {
        if (x < ox || y < oy || x >= ox + size || y >= oy + size) return;
        pixels[y * width + x] = color;
    }

    private int worldToScreenY(double worldZ, double eyeZ, double camZ, double pitchShear) {
        double y = height / 2.0 - (worldZ - eyeZ) / camZ * screenDist + pitchShear;
        return (int) Math.round(y);
    }

    private double shadeFactor(double dist, double sectorLight) {
        double f = sectorLight * (1.0 - dist / FOG_DIST);
        if (f < MIN_BRIGHT) f = MIN_BRIGHT;
        if (f > 1.0) f = 1.0;
        return f;
    }

    private int shadeColor(int rgb, double f) {
        return Textures.darken(rgb, f);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
