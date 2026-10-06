package ua.polynav.navmesh.geometry;

import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;

public class NavNode {
    public enum NodeType {
        NORMAL,
        DOOR,
        WATER,
        STEP
    }

    private final int id;
    private final double x;
    private final double y;
    private final double z;
    private final double costMultiplier;
    private final NodeType type;
    private final List<NavEdge> edges = new ArrayList<>();

    public NavNode(int id, double x, double y, double z, double costMultiplier, NodeType type) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.costMultiplier = costMultiplier;
        this.type = type;
    }

    public int getId() {
        return id;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public Vec3d getPos() {
        return new Vec3d(x, y, z);
    }

    public double getCostMultiplier() {
        return costMultiplier;
    }

    public NodeType getType() {
        return type;
    }

    public List<NavEdge> getEdges() {
        return edges;
    }

    public void addEdge(NavEdge edge) {
        this.edges.add(edge);
    }

    public double distanceSquaredTo(double ox, double oy, double oz) {
        double dx = this.x - ox;
        double dy = this.y - oy;
        double dz = this.z - oz;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distanceTo(double ox, double oy, double oz) {
        return Math.sqrt(distanceSquaredTo(ox, oy, oz));
    }
}
