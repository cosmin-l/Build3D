package build3d;

/** A placed instance of a VoxelModel in the world -- a Build-engine-style voxel prop. */
public class VoxelSprite {
    public final double x, y;
    public final int sector;
    public final double baseZ;
    public final double yaw;   // radians
    public final double scale; // multiplies the model's voxelSize
    public final VoxelModel model;

    public VoxelSprite(double x, double y, int sector, double baseZ, double yaw, double scale, VoxelModel model) {
        this.x = x;
        this.y = y;
        this.sector = sector;
        this.baseZ = baseZ;
        this.yaw = yaw;
        this.scale = scale;
        this.model = model;
    }
}
