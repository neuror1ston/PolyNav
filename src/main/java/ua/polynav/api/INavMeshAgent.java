package ua.polynav.api;

import net.minecraft.entity.mob.MobEntity;
import ua.polynav.navmesh.pathfinding.NavPath;

/**
 * Interface representing any entity capable of navigating along a NavMesh.
 */
public interface INavMeshAgent {
    /**
     * @return the active navigation path, or null if idle
     */
    NavPath getCurrentNavPath();

    /**
     * Assigns a new navigation path to this agent.
     *
     * @param path the computed NavPath
     */
    void setCurrentNavPath(NavPath path);

    /**
     * Returns this agent as a MobEntity for movement and world interactions.
     */
    default MobEntity asMob() {
        if (this instanceof MobEntity mob) {
            return mob;
        }
        throw new IllegalStateException("INavMeshAgent must be implemented by a MobEntity or override asMob()");
    }
}
