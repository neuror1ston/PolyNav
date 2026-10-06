package ua.polynav.navmesh.pathfinding;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class NavPathPoint {
    private final Vec3d pos;
    private final boolean isDoor;
    private final BlockPos doorPos;
    private final float heightDelta;

    public NavPathPoint(Vec3d pos, boolean isDoor, BlockPos doorPos, float heightDelta) {
        this.pos = pos;
        this.isDoor = isDoor;
        this.doorPos = doorPos;
        this.heightDelta = heightDelta;
    }

    public Vec3d getPos() {
        return pos;
    }

    public double getX() {
        return pos.x;
    }

    public double getY() {
        return pos.y;
    }

    public double getZ() {
        return pos.z;
    }

    public boolean isDoor() {
        return isDoor;
    }

    public BlockPos getDoorPos() {
        return doorPos;
    }

    public float getHeightDelta() {
        return heightDelta;
    }
}
