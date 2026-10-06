package ua.polynav.test;

import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import ua.polynav.config.NavMeshConfig;
import ua.polynav.navmesh.geometry.NavEdge;
import ua.polynav.navmesh.geometry.NavMesh;
import ua.polynav.navmesh.geometry.NavNode;
import ua.polynav.navmesh.pathfinding.NavMeshPathfinder;
import ua.polynav.navmesh.pathfinding.NavPath;

import static org.junit.jupiter.api.Assertions.*;

public class PathfindingCostBiasTest {

    @Test
    public void testAStarPrefersDirtPathOverWaterAndGrass() {
        NavMeshConfig config = new NavMeshConfig();
        config.initDefaults();

        BlockBox box = new BlockBox(0, 60, 0, 100, 70, 100);
        NavMesh mesh = new NavMesh("bias_test", box);

        // Start at (0, 64, 0)
        NavNode start = new NavNode(1, 0.0, 64.0, 0.0, 1.0, NavNode.NodeType.NORMAL);

        // Route A: through dirt_path (cost 0.5) - longer distance 6 blocks
        // Node 2 at (1, 64, 2), Node 3 at (3, 64, 2), Node 4 at (5, 64, 0)
        NavNode p1 = new NavNode(2, 1.0, 64.0, 2.0, 0.5, NavNode.NodeType.NORMAL);
        NavNode p2 = new NavNode(3, 3.0, 64.0, 2.0, 0.5, NavNode.NodeType.NORMAL);

        // Route B: straight line through water (cost 1000.0) - shorter distance 5 blocks
        NavNode w1 = new NavNode(10, 2.5, 64.0, 0.0, 1000.0, NavNode.NodeType.WATER);

        // Goal at (5, 64, 0)
        NavNode goal = new NavNode(4, 5.0, 64.0, 0.0, 1.0, NavNode.NodeType.NORMAL);

        // Connect Route A (path)
        start.addEdge(new NavEdge(p1.getId(), 2.0 * 0.5, false, null, 0f));
        p1.addEdge(new NavEdge(start.getId(), 2.0 * 0.5, false, null, 0f));

        p1.addEdge(new NavEdge(p2.getId(), 2.0 * 0.5, false, null, 0f));
        p2.addEdge(new NavEdge(p1.getId(), 2.0 * 0.5, false, null, 0f));

        p2.addEdge(new NavEdge(goal.getId(), 2.0 * 0.5, false, null, 0f));
        goal.addEdge(new NavEdge(p2.getId(), 2.0 * 0.5, false, null, 0f));

        // Connect Route B (water)
        start.addEdge(new NavEdge(w1.getId(), 2.5 * 1000.0, false, null, 0f));
        w1.addEdge(new NavEdge(start.getId(), 2.5 * 1000.0, false, null, 0f));

        w1.addEdge(new NavEdge(goal.getId(), 2.5 * 1000.0, false, null, 0f));
        goal.addEdge(new NavEdge(w1.getId(), 2.5 * 1000.0, false, null, 0f));

        mesh.addNode(start);
        mesh.addNode(p1);
        mesh.addNode(p2);
        mesh.addNode(w1);
        mesh.addNode(goal);

        NavMeshPathfinder pathfinder = new NavMeshPathfinder(config);
        // Find path from start to goal (passing null world for raw graph test without block collision smoothing)
        NavPath path = pathfinder.findPath(null, mesh, new Vec3d(0, 64, 0), new Vec3d(5, 64, 0));

        assertNotNull(path);
        // Total cost of Route A is ~3.0, while Route B is 5000.0!
        assertTrue(path.getTotalCost() < 10.0, "Pathfinder should choose dirt path with cost ~3.0 instead of water with 5000.0!");
    }

    @Test
    public void testStairClimbingPathPreservation() {
        NavMeshConfig config = new NavMeshConfig();
        config.initDefaults();

        BlockBox box = new BlockBox(0, 60, 0, 10, 75, 10);
        NavMesh mesh = new NavMesh("stair_test", box);

        // Ground floor
        NavNode floor = new NavNode(1, 1.0, 64.0, 1.0, 1.0, NavNode.NodeType.NORMAL);
        // 3 stair steps
        NavNode s1 = new NavNode(2, 1.0, 65.0, 2.0, 1.0, NavNode.NodeType.STEP);
        NavNode s2 = new NavNode(3, 1.0, 66.0, 3.0, 1.0, NavNode.NodeType.STEP);
        NavNode s3 = new NavNode(4, 1.0, 67.0, 4.0, 1.0, NavNode.NodeType.STEP);
        // Second floor landing
        NavNode landing = new NavNode(5, 1.0, 67.0, 5.0, 1.0, NavNode.NodeType.NORMAL);

        floor.addEdge(new NavEdge(s1.getId(), 1.41, false, null, 1.0f));
        s1.addEdge(new NavEdge(floor.getId(), 1.41, false, null, -1.0f));

        s1.addEdge(new NavEdge(s2.getId(), 1.41, false, null, 1.0f));
        s2.addEdge(new NavEdge(s1.getId(), 1.41, false, null, -1.0f));

        s2.addEdge(new NavEdge(s3.getId(), 1.41, false, null, 1.0f));
        s3.addEdge(new NavEdge(s2.getId(), 1.41, false, null, -1.0f));

        s3.addEdge(new NavEdge(landing.getId(), 1.0, false, null, 0.0f));
        landing.addEdge(new NavEdge(s3.getId(), 1.0, false, null, 0.0f));

        mesh.addNode(floor);
        mesh.addNode(s1);
        mesh.addNode(s2);
        mesh.addNode(s3);
        mesh.addNode(landing);

        NavMeshPathfinder pathfinder = new NavMeshPathfinder(config);
        NavPath path = pathfinder.findPath(null, mesh, new Vec3d(1, 64, 1), new Vec3d(1, 67, 5));

        assertNotNull(path);
        assertFalse(path.getPoints().isEmpty());
        // All intermediate stair steps must be preserved for smooth climbing
        assertEquals(5, path.getPoints().size(), "Floor + 3 stair steps + landing should preserve all stair waypoints!");
    }
}
