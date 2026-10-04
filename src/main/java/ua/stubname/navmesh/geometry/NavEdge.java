package ua.stubname.navmesh.geometry;

import net.minecraft.util.math.BlockPos;

public class NavEdge {
    private final int targetNodeId;
    private final double distanceCost;
    private final boolean isDoor;
    private final BlockPos doorPos;
    private final float heightDelta;

    public NavEdge(int targetNodeId, double distanceCost, boolean isDoor, BlockPos doorPos, float heightDelta) {
        this.targetNodeId = targetNodeId;
        this.distanceCost = distanceCost;
        this.isDoor = isDoor;
        this.doorPos = doorPos;
        this.heightDelta = heightDelta;
    }

    public int getTargetNodeId() {
        return targetNodeId;
    }

    public double getDistanceCost() {
        return distanceCost;
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
