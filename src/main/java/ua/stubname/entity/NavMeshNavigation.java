package ua.stubname.entity;

import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import ua.stubname.navmesh.pathfinding.AsyncPathProcessor;
import ua.stubname.navmesh.pathfinding.NavPath;

public class NavMeshNavigation extends MobNavigation {
    private final NpcEntity npc;
    private Vec3d pendingTarget;

    public NavMeshNavigation(NpcEntity entity, World world) {
        super(entity, world);
        this.npc = entity;
    }

    public boolean navigateTo(Vec3d target, double speed) {
        if (!(this.world instanceof ServerWorld serverWorld)) {
            return false;
        }

        this.pendingTarget = target;
        this.speed = speed;

        AsyncPathProcessor processor = AsyncPathProcessor.getInstance();
        if (processor != null) {
            processor.requestPath(serverWorld, npc.getPos(), target, path -> {
                if (path != null && !path.isFinished()) {
                    if (path.getCurrentPoint() != null) {
                        double dSq = npc.squaredDistanceTo(path.getCurrentPoint().getPos());
                        if (dSq < 0.49) { // within 0.7 blocks of start position
                            path.advance();
                        }
                    }
                    npc.setCurrentNavPath(path);
                    npc.setMovementSpeed((float) speed);
                } else {
                    // Fallback or no path found
                    npc.setCurrentNavPath(null);
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
        npc.setCurrentNavPath(null);
        this.pendingTarget = null;
    }
}
