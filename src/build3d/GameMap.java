package build3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameMap {
    public List<Sector> sectors = new ArrayList<>();
    public List<Sprite> sprites = new ArrayList<>();
    public List<VoxelSprite> voxelSprites = new ArrayList<>();
    public List<Pickup> pickups = new ArrayList<>();
    public Map<Integer, VoxelModel> voxelModels = new HashMap<>();
    public double startX, startY, startAngleDeg;
    public double startEyeHeight = 41;
    public int startSector;

    /**
     * Finds which sector contains the given point, preferring to stay in
     * {@code preferredIndex} (for continuity) and otherwise preferring the
     * sector whose floor is closest to {@code preferredFloorZ} (to break ties
     * when sectors overlap in the XY plane, e.g. room-over-room).
     */
    public int findSector(double x, double y, int preferredIndex, double preferredFloorZ) {
        if (preferredIndex >= 0 && preferredIndex < sectors.size()
                && sectors.get(preferredIndex).contains(x, y)) {
            return preferredIndex;
        }
        int best = -1;
        double bestDelta = Double.MAX_VALUE;
        for (int i = 0; i < sectors.size(); i++) {
            Sector s = sectors.get(i);
            if (!s.contains(x, y)) continue;
            double delta = Math.abs(s.floorZ - preferredFloorZ);
            if (delta < bestDelta) {
                bestDelta = delta;
                best = i;
            }
        }
        return best;
    }

    /**
     * Marches a ray in small steps from (x, y) in {@code sector} along
     * {@code angle} (same convention as Player.angle) until it exits every
     * sector's polygon (a wall hit) or reaches {@code maxDist}. Ignores
     * floor/ceiling height -- good enough for hitscan weapon sparks, which
     * this engine has no enemies to need better for.
     */
    public RaycastHit raycast(double x, double y, int sector, double angle, double maxDist) {
        double dx = Math.sin(angle), dy = Math.cos(angle);
        double step = 3.0;
        double px = x, py = y;
        int sec = sector;
        double traveled = 0;
        while (traveled < maxDist) {
            double nx = px + dx * step, ny = py + dy * step;
            int found = -1;
            if (sec >= 0 && sec < sectors.size() && sectors.get(sec).contains(nx, ny)) {
                found = sec;
            } else {
                for (int i = 0; i < sectors.size(); i++) {
                    if (sectors.get(i).contains(nx, ny)) { found = i; break; }
                }
            }
            if (found < 0) return new RaycastHit(px, py, sec, traveled);
            px = nx; py = ny; sec = found; traveled += step;
        }
        return new RaycastHit(px, py, sec, traveled);
    }

    public static class RaycastHit {
        public final double x, y;
        public final int sector;
        public final double dist;

        public RaycastHit(double x, double y, int sector, double dist) {
            this.x = x;
            this.y = y;
            this.sector = sector;
            this.dist = dist;
        }
    }
}
