package ua.polynav.navmesh.pathfinding;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import ua.polynav.config.NavMeshConfig;
import ua.polynav.navmesh.NavMeshManager;
import ua.polynav.navmesh.geometry.NavMesh;

import java.util.concurrent.*;
import java.util.function.Consumer;

public class AsyncPathProcessor {
    private static AsyncPathProcessor INSTANCE;

    private ExecutorService executor = createExecutor();

    private static ExecutorService createExecutor() {
        return Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2), r -> {
            Thread t = new Thread(r, "NPC-Pathfinding-Thread");
            t.setDaemon(true);
            return t;
        });
    }

    private synchronized ExecutorService getExecutor() {
        if (executor == null || executor.isShutdown() || executor.isTerminated()) {
            executor = createExecutor();
        }
        return executor;
    }

    private final NavMeshPathfinder pathfinder;

    public AsyncPathProcessor(NavMeshConfig config) {
        this.pathfinder = new NavMeshPathfinder(config);
    }

    public static synchronized AsyncPathProcessor getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new AsyncPathProcessor(NavMeshConfig.load());
        }
        return INSTANCE;
    }

    public static synchronized void init(NavMeshConfig config) {
        if (INSTANCE != null) {
            INSTANCE.shutdown();
        }
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
        getExecutor().submit(() -> {
            try {
                NavPath path = pathfinder.findPath(world, targetMesh, start, goal);
                world.getServer().execute(() -> callback.accept(path));
            } catch (Exception e) {
                e.printStackTrace();
                world.getServer().execute(() -> callback.accept(null));
            }
        });
    }

    public synchronized void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}
