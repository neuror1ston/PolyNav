package ua.polynav.api;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.Vec3d;
import ua.polynav.NpcNavsMod;
import ua.polynav.entity.NavMeshNavigation;
import ua.polynav.entity.NpcEntity;
import ua.polynav.navmesh.NavMeshManager;
import ua.polynav.navmesh.baker.NavMeshBaker;
import ua.polynav.navmesh.geometry.NavMesh;
import ua.polynav.navmesh.geometry.NavNode;
import ua.polynav.navmesh.pathfinding.AsyncPathProcessor;
import ua.polynav.navmesh.pathfinding.NavMeshPathfinder;
import ua.polynav.navmesh.pathfinding.NavPath;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Public high-level API facade for the NavMesh Navigation engine.
 * Downstream mods can use this class to compute paths, query baked NavMeshes,
 * and command agents without touching internal math or pipeline classes.
 */
public final class NavMeshAPI {
    private NavMeshAPI() {}

    /**
     * Finds a smooth path asynchronously using the internal thread pool.
     * The callback/future completes on the main server thread.
     *
     * @param world the ServerWorld instance
     * @param start starting world position
     * @param goal  target world position
     * @return CompletableFuture completing with the smoothed NavPath or null if unreachable
     */
    public static CompletableFuture<NavPath> findPathAsync(ServerWorld world, Vec3d start, Vec3d goal) {
        CompletableFuture<NavPath> future = new CompletableFuture<>();
        AsyncPathProcessor processor = AsyncPathProcessor.getInstance();
        if (processor == null) {
            future.complete(null);
            return future;
        }

        processor.requestPath(world, start, goal, future::complete);
        return future;
    }

    /**
     * Finds a smooth path synchronously on the caller thread.
     *
     * @param world the ServerWorld instance
     * @param start starting world position
     * @param goal  target world position
     * @return the smoothed NavPath or null if unreachable
     */
    public static NavPath findPathSync(ServerWorld world, Vec3d start, Vec3d goal) {
        NavMeshManager manager = NavMeshManager.getInstance();
        if (manager == null) return null;

        NavMesh mesh = manager.findMeshForPos(start);
        if (mesh == null) mesh = manager.findMeshForPos(goal);
        if (mesh == null) return null;

        NavMeshPathfinder pathfinder = new NavMeshPathfinder(NpcNavsMod.CONFIG);
        return pathfinder.findPath(world, mesh, start, goal);
    }

    /**
     * Commands an entity to navigate towards a target position using the NavMesh.
     *
     * @param entity the mob entity
     * @param target target position
     * @param speed  movement speed multiplier
     * @return true if the navigation request was accepted
     */
    public static boolean navigateTo(MobEntity entity, Vec3d target, double speed) {
        if (entity.getNavigation() instanceof NavMeshNavigation nav) {
            return nav.navigateTo(target, speed);
        }
        return false;
    }

    /**
     * Returns the baked NavMesh containing the given world position, if any.
     */
    public static NavMesh getMeshAt(Vec3d pos) {
        NavMeshManager manager = NavMeshManager.getInstance();
        return manager != null ? manager.findMeshForPos(pos) : null;
    }

    /**
     * Finds the nearest navigable node across all registered NavMeshes.
     */
    public static NavNode getNearestNode(Vec3d pos, double maxRadius) {
        NavMeshManager manager = NavMeshManager.getInstance();
        return manager != null ? manager.findNearestNodeAnyMesh(pos, maxRadius) : null;
    }

    /**
     * Finds a random walkable node within radius on the NavMesh covering the center position.
     */
    public static NavNode getRandomNodeInRadius(Vec3d center, double maxRadius, java.util.Random random) {
        NavMesh mesh = getMeshAt(center);
        return mesh != null ? mesh.findRandomNode(center, maxRadius, random) : null;
    }

    /**
     * Finds a random walkable node within radius on the NavMesh covering the center position.
     */
    public static NavNode getRandomNodeInRadius(Vec3d center, double maxRadius, net.minecraft.util.math.random.Random random) {
        NavMesh mesh = getMeshAt(center);
        return mesh != null ? mesh.findRandomNode(center, maxRadius, random) : null;
    }

    /**
     * Bakes a NavMesh region asynchronously and registers it into the NavMeshManager.
     */
    public static CompletableFuture<NavMesh> bakeAreaAsync(ServerWorld world, String name, BlockBox bounds) {
        NavMeshBaker baker = new NavMeshBaker(NpcNavsMod.CONFIG);
        return baker.bakeAsync(world, name, bounds).thenApply(mesh -> {
            NavMeshManager manager = NavMeshManager.getInstance();
            if (manager != null) {
                manager.registerMesh(mesh);
            }
            return mesh;
        });
    }

    /**
     * Persists a baked NavMesh to disk.
     */
    public static void saveMesh(String name) throws IOException {
        NavMeshManager manager = NavMeshManager.getInstance();
        if (manager != null) {
            manager.saveMesh(name);
        }
    }

    /**
     * Toggles 3D neon path visualization for debugging.
     */
    public static void setDebugVisualization(boolean enabled) {
        NpcEntity.setDebugPathRendering(enabled);
    }

    /**
     * @return whether path visualization debugging is active
     */
    public static boolean isDebugVisualization() {
        return NpcEntity.isDebugPathRendering();
    }

    /**
     * @return the central NavMeshManager instance
     */
    public static NavMeshManager getManager() {
        return NavMeshManager.getInstance();
    }
}
