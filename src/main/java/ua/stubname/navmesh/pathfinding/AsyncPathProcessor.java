package ua.stubname.navmesh.pathfinding;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import ua.stubname.config.NavMeshConfig;
import ua.stubname.navmesh.NavMeshManager;
import ua.stubname.navmesh.geometry.NavMesh;

import java.util.concurrent.*;
import java.util.function.Consumer;

public class AsyncPathProcessor {
    private static AsyncPathProcessor INSTANCE;

    private final ExecutorService executor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2), r -> {
        Thread t = new Thread(r, "NPC-Pathfinding-Thread");
        t.setDaemon(true);
        return t;
    });

    private final NavMeshPathfinder pathfinder;

    public AsyncPathProcessor(NavMeshConfig config) {
        this.pathfinder = new NavMeshPathfinder(config);
    }

    public static AsyncPathProcessor getInstance() {
        return INSTANCE;
    }

    public static void init(NavMeshConfig config) {
        INSTANCE = new AsyncPathProcessor(config);
    }

    public void requestPath(ServerWorld world, Vec3d start, Vec3d goal, Consumer<NavPath> callback) {
        NavMeshManager manager = NavMeshManager.getInstance();
        if (manager == null) {
            callback.accept(null);
            return;
        }

        NavMesh mesh = manager.findMeshForPos(start);
        if (mesh == null) {
            mesh = manager.findMeshForPos(goal);
        }

        if (mesh == null) {
            callback.accept(null);
            return;
        }

        final NavMesh targetMesh = mesh;
        executor.submit(() -> {
            try {
                NavPath path = pathfinder.findPath(world, targetMesh, start, goal);
                world.getServer().execute(() -> callback.accept(path));
            } catch (Exception e) {
                e.printStackTrace();
                world.getServer().execute(() -> callback.accept(null));
            }
        });
    }

    public void shutdown() {
        executor.shutdown();
    }
}
