package build3d;

import java.util.ArrayList;
import java.util.List;

public class GameMap {
    public List<Sector> sectors = new ArrayList<>();
    public List<Sprite> sprites = new ArrayList<>();
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
}
