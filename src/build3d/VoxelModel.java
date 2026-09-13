package build3d;

import java.util.Arrays;

/** A small 3D grid of colored cubes -- a Build-engine-style ("KVX") voxel sprite model. */
public class VoxelModel {
    public final int sizeX, sizeY, sizeZ;
    /** World-space edge length of one voxel cube. */
    public final double voxelSize;
    /** Flattened [x + y*sizeX + z*sizeX*sizeY] -> 0xRRGGBB, or -1 if empty. */
    private final int[] colors;

    public VoxelModel(int sizeX, int sizeY, int sizeZ, double voxelSize) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.voxelSize = voxelSize;
        this.colors = new int[sizeX * sizeY * sizeZ];
        Arrays.fill(colors, -1);
    }

    public void set(int x, int y, int z, int rgb) {
        colors[idx(x, y, z)] = rgb;
    }

    /** Returns the voxel's 0xRRGGBB color, or -1 if empty/out of range. */
    public int get(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) return -1;
        return colors[idx(x, y, z)];
    }

    private int idx(int x, int y, int z) {
        return x + y * sizeX + z * sizeX * sizeY;
    }
}
