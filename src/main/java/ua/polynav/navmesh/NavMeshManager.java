package ua.polynav.navmesh;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.polynav.config.NavMeshConfig;
import ua.polynav.navmesh.geometry.NavMesh;
import ua.polynav.navmesh.geometry.NavNode;
import ua.polynav.navmesh.storage.NavMeshStorage;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NavMeshManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("NPCnavs-Manager");
    private static NavMeshManager INSTANCE;

    private final NavMeshConfig config;
    private final Map<String, NavMesh> meshes = new ConcurrentHashMap<>();
    private File storageDirectory;

    public NavMeshManager(NavMeshConfig config) {
        this.config = config;
    }

    public static NavMeshManager getInstance() {
        return INSTANCE;
    }

    public static void init(NavMeshConfig config) {
        INSTANCE = new NavMeshManager(config);
    }

    public void onServerStarted(MinecraftServer server) {
        File worldDir = server.getSavePath(WorldSavePath.ROOT).toFile();
        this.storageDirectory = new File(worldDir, "navmesh");
        loadAll();
    }

    public void registerMesh(NavMesh mesh) {
        meshes.put(mesh.getName(), mesh);
    }

    public NavMesh getMesh(String name) {
        return meshes.get(name);
    }

    public Collection<NavMesh> getAllMeshes() {
        return meshes.values();
    }

    public NavMesh findMeshForPos(Vec3d pos) {
        for (NavMesh mesh : meshes.values()) {
            if (mesh.getBounds().contains((int) Math.floor(pos.x), (int) Math.floor(pos.y), (int) Math.floor(pos.z))) {
                return mesh;
            }
        }
        return null;
    }

    public NavNode findNearestNodeAnyMesh(Vec3d pos, double maxRadius) {
        NavMesh mesh = findMeshForPos(pos);
        if (mesh != null) {
            return mesh.findNearestNode(pos, maxRadius);
        }
        for (NavMesh m : meshes.values()) {
            NavNode node = m.findNearestNode(pos, maxRadius);
            if (node != null) return node;
        }
        return null;
    }

    public void saveMesh(String name) throws IOException {
        NavMesh mesh = meshes.get(name);
        if (mesh == null) throw new IllegalArgumentException("No such navmesh: " + name);
        if (storageDirectory == null) throw new IllegalStateException("Storage directory not initialized!");

        File file = new File(storageDirectory, name + ".dat");
        NavMeshStorage.save(mesh, file);
        LOGGER.info("Saved NavMesh '{}' ({} nodes) to {}", name, mesh.getNodeCount(), file.getAbsolutePath());
    }

    public void loadAll() {
        if (storageDirectory == null || !storageDirectory.exists()) return;

        File[] files = storageDirectory.listFiles((dir, name) -> name.endsWith(".dat"));
        if (files == null) return;

        for (File file : files) {
            try {
                NavMesh mesh = NavMeshStorage.load(file);
                registerMesh(mesh);
                LOGGER.info("Loaded NavMesh '{}' with {} nodes", mesh.getName(), mesh.getNodeCount());
            } catch (Exception e) {
                LOGGER.error("Failed to load NavMesh from {}", file.getName(), e);
            }
        }
    }
}
