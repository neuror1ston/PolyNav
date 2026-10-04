package ua.stubname.navmesh.pathfinding;

import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import ua.stubname.config.NavMeshConfig;

import java.util.ArrayList;
import java.util.List;

public class FunnelSmoother {

    public static List<NavPathPoint> smooth(ServerWorld world, List<NavPathPoint> rawPoints, NavMeshConfig config) {
        if (world == null || rawPoints.size() <= 2) {
            return new ArrayList<>(rawPoints);
        }

        // Anchor Waypoints: Doorways and vertical elevation steps (stairs, slabs, blocks)
        // are strictly immovable anchors.
        boolean[] isAnchor = new boolean[rawPoints.size()];
        for (int i = 0; i < rawPoints.size(); i++) {
            if (rawPoints.get(i).isDoor()) {
                isAnchor[i] = true;
                if (i > 0) isAnchor[i - 1] = true;
                if (i > 1) isAnchor[i - 2] = true;
                if (i + 1 < rawPoints.size()) isAnchor[i + 1] = true;
                if (i + 2 < rawPoints.size()) isAnchor[i + 2] = true;
            }
            // Never skip individual stair treads or vertical ledges
            if (i > 0 && Math.abs(rawPoints.get(i).getY() - rawPoints.get(i - 1).getY()) > 0.25) {
                isAnchor[i] = true;
                isAnchor[i - 1] = true;
            }
        }
        isAnchor[0] = true;
        isAnchor[rawPoints.size() - 1] = true;

        List<NavPathPoint> smoothed = new ArrayList<>();
        smoothed.add(rawPoints.get(0));

        int currentIdx = 0;
        int total = rawPoints.size();

        double agentHeight = config.agentHeight;

        while (currentIdx < total - 1) {
            int furthestVisible = currentIdx + 1;

            if (isAnchor[currentIdx] && currentIdx + 1 < total && isAnchor[currentIdx + 1]) {
                furthestVisible = currentIdx + 1;
            } else {
                int maxLookahead = total - 1;
                for (int test = currentIdx + 1; test < total; test++) {
                    if (isAnchor[test]) {
                        maxLookahead = test;
                        break;
                    }
                }

                for (int testIdx = maxLookahead; testIdx > currentIdx + 1; testIdx--) {
                    // Collinear points must STILL verify safe ground beneath to avoid shortcutting across water puddles!
                    if (isCollinear(rawPoints.get(currentIdx).getPos(), rawPoints.get(currentIdx + 1).getPos(), rawPoints.get(testIdx).getPos())) {
                        if (hasSafeClearance(world, rawPoints.get(currentIdx).getPos(), rawPoints.get(testIdx).getPos(), 0.30, agentHeight)) {
                            furthestVisible = testIdx;
                            break;
                        }
                    }

                    if (hasSafeClearance(world, rawPoints.get(currentIdx).getPos(), rawPoints.get(testIdx).getPos(), 0.60, agentHeight)) {
                        furthestVisible = testIdx;
                        break;
                    }
                }
            }

            NavPathPoint nextPoint = rawPoints.get(furthestVisible);
            smoothed.add(nextPoint);
            currentIdx = furthestVisible;
        }

        return smoothed;
    }

    private static boolean isCollinear(Vec3d a, Vec3d b, Vec3d c) {
        Vec3d ab = b.subtract(a).normalize();
        Vec3d ac = c.subtract(a).normalize();
        return ab.dotProduct(ac) > 0.985;
    }

    private static boolean hasSafeClearance(ServerWorld world, Vec3d from, Vec3d to, double requiredClearance, double height) {
        double dist = from.distanceTo(to);
        int steps = Math.max(2, (int) Math.ceil(dist / 0.25));

        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            double x = from.x + (to.x - from.x) * t;
            double y = from.y + (to.y - from.y) * t;
            double z = from.z + (to.z - from.z) * t;

            if (!isPositionClear(world, x, y, z, requiredClearance, height)) {
                return false;
            }

            if (!hasSolidGroundBeneath(world, x, y, z)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasSolidGroundBeneath(ServerWorld world, double x, double y, double z) {
        BlockPos groundPos = BlockPos.ofFloored(x, y - 0.1, z);
        FluidState fluid = world.getFluidState(groundPos);
        if (fluid.isIn(FluidTags.WATER) || fluid.isIn(FluidTags.LAVA)) {
            return false;
        }

        BlockPos footPos = BlockPos.ofFloored(x, y, z);
        FluidState footFluid = world.getFluidState(footPos);
        if (footFluid.isIn(FluidTags.WATER) || footFluid.isIn(FluidTags.LAVA)) {
            return false;
        }

        BlockState groundState = world.getBlockState(groundPos);
        VoxelShape shape = groundState.getCollisionShape(world, groundPos);
        if (!shape.isEmpty()) {
            return true;
        }

        BlockPos belowPos = groundPos.down();
        BlockState belowState = world.getBlockState(belowPos);
        VoxelShape belowShape = belowState.getCollisionShape(world, belowPos);
        return !belowShape.isEmpty();
    }

    private static boolean isPositionClear(ServerWorld world, double x, double y, double z, double radius, double height) {
        Box box = new Box(x - radius, y + 0.1, z - radius, x + radius, y + height, z + radius);

        BlockPos min = BlockPos.ofFloored(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ);

        for (BlockPos pos : BlockPos.iterate(min, max)) {
            BlockState state = world.getBlockState(pos);
            if (state.isAir() || state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock) {
                continue;
            }

            VoxelShape shape = state.getCollisionShape(world, pos);
            if (shape.isEmpty()) continue;

            for (Box collisionBox : shape.getBoundingBoxes()) {
                if (collisionBox.offset(pos).intersects(box)) {
                    return false;
                }
            }
        }
        return true;
    }
}
