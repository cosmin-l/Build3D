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
 *   SPRITE x y sector height color scale [imageTex]
 *   IMAGE id path [tileFeet]
 *   VOXELMODEL id path
 *   VOXSPRITE x y sector baseZ yawDeg scale modelId
 *
 * Colors are 0xRRGGBB. Wall vertices of a sector must be listed in order
 * (they form a closed polygon; the last wall should return to the first
 * vertex) and wound so the interior is on the left as you walk x1->x2 in
 * increasing order (counter-clockwise looking from above).
 *
 * `texId` on a WALL, or `floorTex`/`ceilTex` on a SECTOR, selects a texture:
 * ids 0/1/2 are the built-in procedural brick/panel/stripes patterns, -1 (or
 * omitted) is a flat color, and any id registered by an IMAGE directive uses
 * that image, tiled across world feet. IMAGE/VOXELMODEL paths, like the map
 * path itself, are resolved relative to the process's working directory
 * (i.e. wherever the game is launched from) when not absolute.
 */
public class MapLoader {
    public static GameMap load(String path) throws IOException {
        GameMap map = new GameMap();
        List<String> lines = Files.readAllLines(Paths.get(path));

        Textures.clearImages();
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
                    int imageTex = t.length > 7 ? Integer.parseInt(t[7]) : -1;
                    map.sprites.add(new Sprite(x, y, sector, height, color, scale, imageTex));
                    break;
                }

                case "IMAGE": {
                    int id = Integer.parseInt(t[1]);
                    double tileFeet = t.length > 3 ? Double.parseDouble(t[3]) : 64.0;
                    Textures.registerImage(id, ImageTexture.load(t[2], tileFeet));
                    break;
                }

                case "VOXELMODEL": {
                    int id = Integer.parseInt(t[1]);
                    map.voxelModels.put(id, VoxelLoader.load(t[2]));
                    break;
                }

                case "VOXSPRITE": {
                    double x = Double.parseDouble(t[1]);
                    double y = Double.parseDouble(t[2]);
                    int sector = Integer.parseInt(t[3]);
                    double baseZ = Double.parseDouble(t[4]);
                    double yawDeg = Double.parseDouble(t[5]);
                    double scale = Double.parseDouble(t[6]);
                    int modelId = Integer.parseInt(t[7]);
                    VoxelModel model = map.voxelModels.get(modelId);
                    if (model == null) throw new IllegalStateException("VOXSPRITE references unknown model id " + modelId);
                    map.voxelSprites.add(new VoxelSprite(x, y, sector, baseZ, Math.toRadians(yawDeg), scale, model));
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
