package ua.polynav.navmesh.geometry;

import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class NavMesh {
    private final String name;
    private final BlockBox bounds;
    private final Map<Integer, NavNode> nodes = new HashMap<>();

    // Spatial hash grid for fast node querying (cell size 4x4 blocks horizontally)
    private static final int GRID_CELL_SIZE = 4;
    private final Map<Long, List<NavNode>> spatialGrid = new HashMap<>();

    public NavMesh(String name, BlockBox bounds) {
        this.name = name;
        this.bounds = bounds;
    }

    public String getName() {
        return name;
    }

    public BlockBox getBounds() {
        return bounds;
    }

    public Map<Integer, NavNode> getNodes() {
        return Collections.unmodifiableMap(nodes);
    }

    public NavNode getNode(int id) {
        return nodes.get(id);
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public void addNode(NavNode node) {
        nodes.put(node.getId(), node);
        addToSpatialGrid(node);
    }

    private long getSpatialKey(int cellX, int cellZ) {
        return (((long) cellX) << 32) | (cellZ & 0xFFFFFFFFL);
    }

    private void addToSpatialGrid(NavNode node) {
        int cellX = (int) Math.floor(node.getX() / GRID_CELL_SIZE);
        int cellZ = (int) Math.floor(node.getZ() / GRID_CELL_SIZE);
        long key = getSpatialKey(cellX, cellZ);
        spatialGrid.computeIfAbsent(key, k -> new ArrayList<>()).add(node);
    }

    public NavNode findNearestNode(Vec3d pos, double maxRadius) {
        return findNearestNode(pos.x, pos.y, pos.z, maxRadius);
    }

    public NavNode findNearestNode(double x, double y, double z, double maxRadius) {
        int centerCellX = (int) Math.floor(x / GRID_CELL_SIZE);
        int centerCellZ = (int) Math.floor(z / GRID_CELL_SIZE);
        int cellRadius = (int) Math.ceil(maxRadius / GRID_CELL_SIZE);

        NavNode bestNode = null;
        double bestDistSq = maxRadius * maxRadius;

        for (int cx = centerCellX - cellRadius; cx <= centerCellX + cellRadius; cx++) {
            for (int cz = centerCellZ - cellRadius; cz <= centerCellZ + cellRadius; cz++) {
                List<NavNode> cellNodes = spatialGrid.get(getSpatialKey(cx, cz));
                if (cellNodes == null) continue;

                for (NavNode node : cellNodes) {
                    double distSq = node.distanceSquaredTo(x, y, z);
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        bestNode = node;
                    }
                }
            }
        }

        return bestNode;
    }

    public void rebuildSpatialGrid() {
        spatialGrid.clear();
        for (NavNode node : nodes.values()) {
            addToSpatialGrid(node);
        }
    }
}
