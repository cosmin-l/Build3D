package build3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the simple line-based Build3D map format:
 *
 *   PLAYER x y angleDeg sector [eyeHeight]
 *   SECTOR id floor ceil floorColor ceilColor [light] [floorTex] [ceilTex]
 *   WALL x1 y1 x2 y2 portal color [texId]     (repeated, belongs to last SECTOR)
 *   ENDSECTOR
 *   SPRITE x y sector height color scale
 *
 * Colors are 0xRRGGBB. Wall vertices of a sector must be listed in order
 * (they form a closed polygon; the last wall should return to the first
 * vertex) and wound so the interior is on the left as you walk x1->x2 in
 * increasing order (counter-clockwise looking from above).
 */
public class MapLoader {
    public static GameMap load(String path) throws IOException {
        GameMap map = new GameMap();
        List<String> lines = Files.readAllLines(Paths.get(path));

        Map<Integer, Sector> byId = new HashMap<>();
        Sector current = null;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] t = line.split("\\s+");

            switch (t[0]) {
                case "PLAYER":
                    map.startX = Double.parseDouble(t[1]);
                    map.startY = Double.parseDouble(t[2]);
                    map.startAngleDeg = Double.parseDouble(t[3]);
                    map.startSector = Integer.parseInt(t[4]);
                    if (t.length > 5) map.startEyeHeight = Double.parseDouble(t[5]);
                    break;

                case "SECTOR": {
                    int id = Integer.parseInt(t[1]);
                    double floor = Double.parseDouble(t[2]);
                    double ceil = Double.parseDouble(t[3]);
                    int floorColor = Integer.decode(t[4]);
                    int ceilColor = Integer.decode(t[5]);
                    current = new Sector(new ArrayList<>(), floor, ceil, floorColor, ceilColor);
                    if (t.length > 6) current.light = Double.parseDouble(t[6]);
                    if (t.length > 7) current.floorTex = Integer.parseInt(t[7]);
                    if (t.length > 8) current.ceilTex = Integer.parseInt(t[8]);
                    byId.put(id, current);
                    break;
                }

                case "WALL": {
                    if (current == null) throw new IllegalStateException("WALL outside of SECTOR");
                    double x1 = Double.parseDouble(t[1]);
                    double y1 = Double.parseDouble(t[2]);
                    double x2 = Double.parseDouble(t[3]);
                    double y2 = Double.parseDouble(t[4]);
                    int portal = Integer.parseInt(t[5]);
                    int color = Integer.decode(t[6]);
                    int texId = t.length > 7 ? Integer.parseInt(t[7]) : -1;
                    current.walls.add(new Wall(x1, y1, x2, y2, portal, color, texId));
                    break;
                }

                case "ENDSECTOR":
                    current = null;
                    break;

                case "SPRITE": {
                    double x = Double.parseDouble(t[1]);
                    double y = Double.parseDouble(t[2]);
                    int sector = Integer.parseInt(t[3]);
                    double height = Double.parseDouble(t[4]);
                    int color = Integer.decode(t[5]);
                    double scale = Double.parseDouble(t[6]);
                    map.sprites.add(new Sprite(x, y, sector, height, color, scale));
                    break;
                }

                default:
                    throw new IllegalArgumentException("Unknown map directive: " + t[0]);
            }
        }

        int maxId = -1;
        for (int id : byId.keySet()) maxId = Math.max(maxId, id);
        Sector[] arr = new Sector[maxId + 1];
        for (Map.Entry<Integer, Sector> e : byId.entrySet()) {
            arr[e.getKey()] = e.getValue();
        }
        map.sectors = new ArrayList<>(Arrays.asList(arr));
        return map;
    }
}
