package ua.polynav.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;
import ua.polynav.api.INavMeshAgent;
import ua.polynav.navmesh.pathfinding.NavPath;
import ua.polynav.navmesh.pathfinding.NavPathPoint;

public class NpcEntity extends PathAwareEntity implements INavMeshAgent {
    private static boolean debugPathRendering = false;

    private final NavMeshNavigation navMeshNavigation;
    private final NavMeshMoveControl navMeshMoveControl;
    private NavPath currentNavPath;
    private int pathDebugTick = 0;

    public NpcEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
        this.navMeshMoveControl = new NavMeshMoveControl(this);
        this.moveControl = this.navMeshMoveControl;
        this.navMeshNavigation = new NavMeshNavigation(this, world);
        this.navigation = this.navMeshNavigation;

        // Standard 0.6 step height: smooth traversal of stairs, slabs, and Conquest layers without jumping.
        // Full blocks will require an intentional jump!
        this.setStepHeight(0.6f);
    }

    public static DefaultAttributeContainer.Builder createNpcAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0);
    }

    public NavMeshNavigation getNavMeshNavigation() {
        return navMeshNavigation;
    }

    public NavPath getCurrentNavPath() {
        return currentNavPath;
    }

    public void setCurrentNavPath(NavPath path) {
        this.currentNavPath = path;
        ua.polynav.network.PathNetwork.sendPathToClients(this, path);
    }

    public static void setDebugPathRendering(boolean enabled) {
        debugPathRendering = enabled;
    }

    public static boolean isDebugPathRendering() {
        return debugPathRendering;
    }

    @Override
    public void tick() {
        super.tick();

        // Server-side path debug particle visualization
        if (!this.getWorld().isClient() && debugPathRendering && currentNavPath != null && !currentNavPath.isFinished()) {
            if (++pathDebugTick % 5 == 0 && this.getWorld() instanceof ServerWorld serverWorld) {
                renderPathParticles(serverWorld, currentNavPath);
            }
        }
    }

    private void renderPathParticles(ServerWorld world, NavPath path) {
        DustParticleEffect activePointColor = new DustParticleEffect(new Vector3f(0.1f, 1.0f, 0.2f), 1.0f); // Bright green
        DustParticleEffect doorPointColor = new DustParticleEffect(new Vector3f(1.0f, 0.8f, 0.1f), 1.2f);   // Orange for doors
        DustParticleEffect futurePointColor = new DustParticleEffect(new Vector3f(0.2f, 0.6f, 1.0f), 0.8f); // Cyan

        for (int i = path.getCurrentIndex(); i < path.getPoints().size(); i++) {
            NavPathPoint pt = path.getPoints().get(i);
            DustParticleEffect color = pt.isDoor() ? doorPointColor : (i == path.getCurrentIndex() ? activePointColor : futurePointColor);
            world.spawnParticles(color, pt.getX(), pt.getY() + 0.15, pt.getZ(), 1, 0.02, 0.02, 0.02, 0.0);
        }
    }
}
