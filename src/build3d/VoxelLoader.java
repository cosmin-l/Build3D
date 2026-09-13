package build3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Loads the simple sparse voxel model format:
 *
 *   DIM sizeX sizeY sizeZ
 *   SCALE feetPerVoxel
 *   V x y z 0xRRGGBB     (repeated; x/y/z are 0-based grid indices)
 *
 * x is "east", y is "north" (matching map-space), z is "up" from the model's
 * own base. Voxels left unset are empty (transparent).
 */
public class VoxelLoader {
    public static VoxelModel load(String path) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(path));
        int sx = 0, sy = 0, sz = 0;
        double scale = 2.0;
        VoxelModel model = null;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] t = line.split("\\s+");

            switch (t[0]) {
                case "DIM":
                    sx = Integer.parseInt(t[1]);
                    sy = Integer.parseInt(t[2]);
                    sz = Integer.parseInt(t[3]);
                    break;

                case "SCALE":
                    scale = Double.parseDouble(t[1]);
                    break;

                case "V": {
                    if (model == null) {
                        if (sx <= 0 || sy <= 0 || sz <= 0) {
                            throw new IllegalStateException("V before DIM in " + path);
                        }
                        model = new VoxelModel(sx, sy, sz, scale);
                    }
                    int x = Integer.parseInt(t[1]);
                    int y = Integer.parseInt(t[2]);
                    int z = Integer.parseInt(t[3]);
                    int color = Integer.decode(t[4]);
                    model.set(x, y, z, color);
                    break;
                }

                default:
                    throw new IllegalArgumentException("Unknown voxel directive: " + t[0]);
            }
        }
        if (model == null) throw new IllegalStateException("Empty voxel model: " + path);
        return model;
    }
}
