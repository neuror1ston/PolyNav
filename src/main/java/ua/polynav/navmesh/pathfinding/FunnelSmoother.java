package ua.polynav.navmesh.pathfinding;

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
import ua.polynav.config.NavMeshConfig;

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

        // Pass 2: Round corners with hitbox verification for natural smooth turning
        return roundCorners(world, smoothed, config);
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

    private static List<NavPathPoint> roundCorners(ServerWorld world, List<NavPathPoint> points, NavMeshConfig config) {
        if (points.size() < 3) return points;

        double agentRadius = Math.max(0.35, config.agentRadius);
        double agentHeight = config.agentHeight;

        List<NavPathPoint> result = new ArrayList<>();
        result.add(points.get(0));

        int i = 1;
        while (i < points.size() - 1) {
            NavPathPoint prev = points.get(i - 1);
            NavPathPoint curr = points.get(i);
            NavPathPoint next = points.get(i + 1);

            // Never round doorways or vertical elevation changes (stairs, slabs, blocks)
            if (curr.isDoor() || prev.isDoor() || next.isDoor() ||
                Math.abs(curr.getY() - prev.getY()) > 0.15 || Math.abs(next.getY() - curr.getY()) > 0.15) {
                result.add(curr);
                i++;
                continue;
            }

            Vec3d v1 = curr.getPos().subtract(prev.getPos());
            Vec3d v2 = next.getPos().subtract(curr.getPos());
            double len1 = Math.sqrt(v1.x * v1.x + v1.z * v1.z);
            double len2 = Math.sqrt(v2.x * v2.x + v2.z * v2.z);

            if (len1 < 0.6 || len2 < 0.6) {
                result.add(curr);
                i++;
                continue;
            }

            Vec3d dir1 = new Vec3d(v1.x / len1, 0, v1.z / len1);
            Vec3d dir2 = new Vec3d(v2.x / len2, 0, v2.z / len2);
            double dot = dir1.dotProduct(dir2);

            // Only round corners (angle between ~25 deg and 150 deg)
            if (dot > 0.90 || dot < -0.85) {
                result.add(curr);
                i++;
                continue;
            }

            // Fillet radius along the incoming and outgoing legs
            double maxFillet = Math.min(0.65, Math.min(len1, len2) * 0.45);
            List<NavPathPoint> curvePoints = null;

            // Try candidate scales: largest smooth curve to smallest safe curve
            double[] candidateScales = {1.0, 0.70, 0.45};
            for (double scale : candidateScales) {
                double s = maxFillet * scale;
                if (s < 0.20) break;

                Vec3d pA = curr.getPos().subtract(dir1.multiply(s));
                Vec3d pB = curr.getPos().add(dir2.multiply(s));

                // 3 intermediate Bezier curve points
                Vec3d q1 = evalBezier(pA, curr.getPos(), pB, 0.25);
                Vec3d q2 = evalBezier(pA, curr.getPos(), pB, 0.50);
                Vec3d q3 = evalBezier(pA, curr.getPos(), pB, 0.75);

                Vec3d[] testPoints = {pA, q1, q2, q3, pB};
                boolean valid = true;

                for (Vec3d pt : testPoints) {
                    if (!isPositionClear(world, pt.x, pt.y, pt.z, agentRadius, agentHeight) ||
                        !hasSolidGroundBeneath(world, pt.x, pt.y, pt.z)) {
                        valid = false;
                        break;
                    }
                }

                if (valid) {
                    curvePoints = new ArrayList<>();
                    curvePoints.add(new NavPathPoint(pA, false, null, 0f));
                    curvePoints.add(new NavPathPoint(q1, false, null, 0f));
                    curvePoints.add(new NavPathPoint(q2, false, null, 0f));
                    curvePoints.add(new NavPathPoint(q3, false, null, 0f));
                    curvePoints.add(new NavPathPoint(pB, false, null, 0f));
                    break;
                }
            }

            if (curvePoints != null) {
                result.addAll(curvePoints);
            } else {
                result.add(curr);
            }
            i++;
        }

        result.add(points.get(points.size() - 1));
        return result;
    }

    private static Vec3d evalBezier(Vec3d a, Vec3d ctrl, Vec3d b, double t) {
        double u = 1.0 - t;
        double x = u * u * a.x + 2 * u * t * ctrl.x + t * t * b.x;
        double y = u * u * a.y + 2 * u * t * ctrl.y + t * t * b.y;
        double z = u * u * a.z + 2 * u * t * ctrl.z + t * t * b.z;
        return new Vec3d(x, y, z);
    }

    private static boolean isPositionClear(ServerWorld world, double x, double y, double z, double radius, double height) {
        Box box = new Box(x - radius, y + 0.1, z - radius, x + radius, y + height, z + radius);

        BlockPos min = BlockPos.ofFloored(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ);

        for (BlockPos pos : BlockPos.iterate(min, max)) {
            FluidState fluid = world.getFluidState(pos);
            if (fluid.isIn(FluidTags.WATER) || fluid.isIn(FluidTags.LAVA)) {
                return false;
            }

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
