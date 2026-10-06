package ua.polynav.entity;

import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import ua.polynav.api.INavMeshAgent;
import ua.polynav.navmesh.pathfinding.AsyncPathProcessor;
import ua.polynav.navmesh.pathfinding.NavPath;

public class NavMeshNavigation extends MobNavigation {
    private final MobEntity mob;
    private final INavMeshAgent agent;
    private Vec3d pendingTarget;

    public NavMeshNavigation(MobEntity mob, INavMeshAgent agent, World world) {
        super(mob, world);
        this.mob = mob;
        this.agent = agent;
    }

    public NavMeshNavigation(MobEntity mob, World world) {
        this(mob, (INavMeshAgent) mob, world);
    }

    public NavMeshNavigation(NpcEntity entity, World world) {
        this(entity, entity, world);
    }

    public boolean navigateTo(Vec3d target, double speed) {
        if (!(this.world instanceof ServerWorld serverWorld)) {
            return false;
        }

        this.pendingTarget = target;
        this.speed = speed;

        AsyncPathProcessor processor = AsyncPathProcessor.getInstance();
        if (processor != null) {
            processor.requestPath(serverWorld, mob.getPos(), target, path -> {
                if (path != null && !path.isFinished()) {
                    if (path.getCurrentPoint() != null) {
                        double dSq = mob.squaredDistanceTo(path.getCurrentPoint().getPos());
                        if (dSq < 0.49) { // within 0.7 blocks of start position
                            path.advance();
                        }
                    }
                    agent.setCurrentNavPath(path);
                    mob.setMovementSpeed((float) speed);
                } else {
                    // Fallback or no path found
                    agent.setCurrentNavPath(null);
                }
            });
            return true;
        }
        return false;
    }

    public boolean navigateTo(double x, double y, double z, double speed) {
        return navigateTo(new Vec3d(x, y, z), speed);
    }

    @Override
    public void stop() {
        super.stop();
        agent.setCurrentNavPath(null);
        this.pendingTarget = null;
    }
}
