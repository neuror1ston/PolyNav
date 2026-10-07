package ua.polynav.navmesh.pathfinding;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import ua.polynav.config.NavMeshConfig;
import ua.polynav.navmesh.geometry.NavEdge;
import ua.polynav.navmesh.geometry.NavMesh;
import ua.polynav.navmesh.geometry.NavNode;

import java.util.*;

public class NavMeshPathfinder {
    private final NavMeshConfig config;

    public NavMeshPathfinder(NavMeshConfig config) {
        this.config = config;
    }

    private static class NodeRecord implements Comparable<NodeRecord> {
        final int nodeId;
        final double fScore;

        NodeRecord(int nodeId, double fScore) {
            this.nodeId = nodeId;
            this.fScore = fScore;
        }

        @Override
        public int compareTo(NodeRecord o) {
            return Double.compare(this.fScore, o.fScore);
        }
    }

    public NavPath findPath(ServerWorld world, NavMesh mesh, Vec3d startPos, Vec3d goalPos) {
        NavNode startNode = mesh.findNearestNode(startPos, 6.0);
        NavNode goalNode = mesh.findNearestNode(goalPos, 6.0);

        if (startNode == null || goalNode == null) {
            return null;
        }

        if (startNode.getId() == goalNode.getId()) {
            if (world == null || FunnelSmoother.hasSafeClearance(world, startPos, goalPos, 0.30, config.agentHeight)) {
                List<NavPathPoint> single = new ArrayList<>();
                single.add(new NavPathPoint(goalPos, false, null, 0f));
                return new NavPath(single, 0.0);
            } else {
                // Goal is obstructed or inside a wall. If startPos hasn't reached goalNode yet, navigate to goalNode
                if (startPos.squaredDistanceTo(goalNode.getPos()) > 0.25 &&
                        (world == null || FunnelSmoother.hasSafeClearance(world, startPos, goalNode.getPos(), 0.30, config.agentHeight))) {
                    List<NavPathPoint> single = new ArrayList<>();
                    single.add(new NavPathPoint(goalNode.getPos(), false, null, 0f));
                    return new NavPath(single, 0.0);
                }
                return null;
            }
        }

        Map<Integer, Double> gScore = new HashMap<>();
        Map<Integer, Double> fScore = new HashMap<>();
        Map<Integer, Integer> cameFromNode = new HashMap<>();
        Map<Integer, NavEdge> cameFromEdge = new HashMap<>();
        PriorityQueue<NodeRecord> openSet = new PriorityQueue<>();

        gScore.put(startNode.getId(), 0.0);
        double initialH = heuristic(startNode, goalNode);
        fScore.put(startNode.getId(), initialH);
        openSet.add(new NodeRecord(startNode.getId(), initialH));

        Set<Integer> closedSet = new HashSet<>();

        while (!openSet.isEmpty()) {
            NodeRecord current = openSet.poll();
            int currentId = current.nodeId;

            if (currentId == goalNode.getId()) {
                // Goal reached! Reconstruct raw path
                List<NavPathPoint> rawPath = reconstructPath(world, mesh, cameFromNode, cameFromEdge, currentId, startPos, goalPos);
                List<NavPathPoint> smoothed = FunnelSmoother.smooth(world, rawPath, config);
                return new NavPath(smoothed, gScore.getOrDefault(currentId, 0.0));
            }

            if (!closedSet.add(currentId)) {
                continue;
            }

            NavNode node = mesh.getNode(currentId);
            if (node == null) continue;

            double currentG = gScore.getOrDefault(currentId, Double.POSITIVE_INFINITY);

            for (NavEdge edge : node.getEdges()) {
                int neighborId = edge.getTargetNodeId();
                if (closedSet.contains(neighborId)) continue;

                NavNode neighbor = mesh.getNode(neighborId);
                if (neighbor == null) continue;

                double tentativeG = currentG + edge.getDistanceCost();
                double neighborG = gScore.getOrDefault(neighborId, Double.POSITIVE_INFINITY);

                if (tentativeG < neighborG) {
                    cameFromNode.put(neighborId, currentId);
                    cameFromEdge.put(neighborId, edge);
                    gScore.put(neighborId, tentativeG);
                    double h = heuristic(neighbor, goalNode);
                    double f = tentativeG + h;
                    fScore.put(neighborId, f);
                    openSet.add(new NodeRecord(neighborId, f));
                }
            }
        }

        return null; // No path found
    }

    private double heuristic(NavNode a, NavNode b) {
        // Euclidean distance multiplied by min possible cost (0.5 for paths)
        return a.distanceTo(b.getX(), b.getY(), b.getZ()) * 0.5;
    }

    private List<NavPathPoint> reconstructPath(ServerWorld world, NavMesh mesh, Map<Integer, Integer> cameFromNode, Map<Integer, NavEdge> cameFromEdge,
                                               int goalId, Vec3d startPos, Vec3d goalPos) {
        LinkedList<NavPathPoint> points = new LinkedList<>();

        int currentId = goalId;
        while (cameFromNode.containsKey(currentId)) {
            NavNode node = mesh.getNode(currentId);
            NavEdge edge = cameFromEdge.get(currentId);

            boolean isDoor = edge != null && edge.isDoor();
            BlockPos doorPos = edge != null ? edge.getDoorPos() : null;
            float heightDelta = edge != null ? edge.getHeightDelta() : 0f;

            points.addFirst(new NavPathPoint(node.getPos(), isDoor, doorPos, heightDelta));
            currentId = cameFromNode.get(currentId);
        }

        // Add start node
        NavNode startNode = mesh.getNode(currentId);
        if (startNode != null) {
            points.addFirst(new NavPathPoint(startNode.getPos(), false, null, 0f));
        }

        // Add startPos if distinct from startNode
        if (points.isEmpty() || startPos.squaredDistanceTo(points.getFirst().getPos()) > 0.05) {
            points.addFirst(new NavPathPoint(startPos, false, null, 0f));
        }

        // Add goalPos only if distinct from last point AND safely reachable without clipping into obstacles
        if (points.isEmpty() || goalPos.squaredDistanceTo(points.getLast().getPos()) > 0.05) {
            NavNode goalNode = mesh.getNode(goalId);
            Vec3d anchorPos = goalNode != null ? goalNode.getPos() : points.getLast().getPos();
            boolean canReachGoal = (world == null) ||
                    FunnelSmoother.hasSafeClearance(world, anchorPos, goalPos, 0.30, config.agentHeight);
            if (canReachGoal) {
                points.addLast(new NavPathPoint(goalPos, false, null, 0f));
            }
        }

        return points;
    }
}
