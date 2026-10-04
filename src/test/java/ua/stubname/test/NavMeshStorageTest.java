package ua.stubname.test;

import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import ua.stubname.config.NavMeshConfig;
import ua.stubname.navmesh.geometry.NavEdge;
import ua.stubname.navmesh.geometry.NavMesh;
import ua.stubname.navmesh.geometry.NavNode;
import ua.stubname.navmesh.storage.NavMeshStorage;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class NavMeshStorageTest {

    @Test
    public void testSerializationAndDeserialization() throws IOException {
        BlockBox box = new BlockBox(0, 60, 0, 50, 80, 50);
        NavMesh mesh = new NavMesh("city_center", box);

        NavNode node1 = new NavNode(1, 10.5, 64.0, 10.5, 0.5, NavNode.NodeType.NORMAL);
        NavNode node2 = new NavNode(2, 11.5, 64.125, 10.5, 1.0, NavNode.NodeType.STEP);
        NavNode node3 = new NavNode(3, 12.5, 64.0, 10.5, 1.0, NavNode.NodeType.DOOR);

        // Edge 1 -> 2 (0.125 step up)
        node1.addEdge(new NavEdge(2, 1.0, false, null, 0.125f));
        node2.addEdge(new NavEdge(1, 1.0, false, null, -0.125f));

        // Edge 2 -> 3 (door transition)
        node2.addEdge(new NavEdge(3, 1.0, true, new BlockPos(12, 64, 10), -0.125f));
        node3.addEdge(new NavEdge(2, 1.0, true, new BlockPos(12, 64, 10), 0.125f));

        mesh.addNode(node1);
        mesh.addNode(node2);
        mesh.addNode(node3);

        File tempFile = File.createTempFile("navmesh_test", ".dat");
        tempFile.deleteOnExit();

        NavMeshStorage.save(mesh, tempFile);
        NavMesh loaded = NavMeshStorage.load(tempFile);

        assertEquals("city_center", loaded.getName());
        assertEquals(3, loaded.getNodeCount());

        NavNode loaded1 = loaded.getNode(1);
        assertNotNull(loaded1);
        assertEquals(10.5, loaded1.getX(), 0.001);
        assertEquals(64.0, loaded1.getY(), 0.001);
        assertEquals(0.5, loaded1.getCostMultiplier(), 0.001);
        assertEquals(1, loaded1.getEdges().size());
        assertEquals(0.125f, loaded1.getEdges().get(0).getHeightDelta(), 0.001f);

        NavNode loaded2 = loaded.getNode(2);
        assertEquals(NavNode.NodeType.STEP, loaded2.getType());
        assertEquals(2, loaded2.getEdges().size());

        NavNode loaded3 = loaded.getNode(3);
        assertEquals(NavNode.NodeType.DOOR, loaded3.getType());
        assertTrue(loaded3.getEdges().get(0).isDoor());
        assertEquals(new BlockPos(12, 64, 10), loaded3.getEdges().get(0).getDoorPos());
    }

    @Test
    public void testSpatialGridLookup() {
        BlockBox box = new BlockBox(0, 0, 0, 100, 100, 100);
        NavMesh mesh = new NavMesh("spatial_test", box);

        mesh.addNode(new NavNode(1, 10.0, 64.0, 10.0, 1.0, NavNode.NodeType.NORMAL));
        mesh.addNode(new NavNode(2, 20.0, 64.0, 20.0, 1.0, NavNode.NodeType.NORMAL));
        mesh.addNode(new NavNode(3, 50.0, 64.0, 50.0, 1.0, NavNode.NodeType.NORMAL));

        NavNode nearest = mesh.findNearestNode(10.2, 64.0, 10.1, 5.0);
        assertNotNull(nearest);
        assertEquals(1, nearest.getId());

        NavNode none = mesh.findNearestNode(90.0, 64.0, 90.0, 5.0);
        assertNull(none);
    }
}
