package ua.stubname.navmesh.baker;

import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import ua.stubname.config.NavMeshConfig;
import ua.stubname.navmesh.geometry.NavEdge;
import ua.stubname.navmesh.geometry.NavMesh;
import ua.stubname.navmesh.geometry.NavNode;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class NavMeshBaker {
    private final NavMeshConfig config;

    public NavMeshBaker(NavMeshConfig config) {
        this.config = config;
    }

    public CompletableFuture<NavMesh> bakeAsync(ServerWorld world, String name, BlockBox bounds) {
        return CompletableFuture.supplyAsync(() -> bake(world, name, bounds));
    }

    public NavMesh bake(ServerWorld world, String name, BlockBox bounds) {
        NavMesh navMesh = new NavMesh(name, bounds);

        int minX = bounds.getMinX();
        int maxX = bounds.getMaxX();
        int minY = bounds.getMinY();
        int maxY = bounds.getMaxY();
        int minZ = bounds.getMinZ();
        int maxZ = bounds.getMaxZ();

        List<NavNode> candidateNodes = new ArrayList<>();
        Map<Long, List<NavNode>> blockColumnMap = new HashMap<>();
        int nextNodeId = 1;

        double radius = config.agentRadius;
        double height = config.agentHeight;

        // 1. Scan column by column at block centers (x + 0.5, z + 0.5)
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                double x = bx + 0.5;
                double z = bz + 0.5;

                Double lastWalkableY = null;

                for (int y = maxY; y >= minY; y--) {
                    BlockPos pos = new BlockPos(bx, y, bz);
                    BlockState state = world.getBlockState(pos);

                    // Doors and gates are passages, NOT floors
                    if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock) {
                        continue;
                    }

                    VoxelShape collision = state.getCollisionShape(world, pos);
                    double surfaceY;
                    boolean isWater = false;
                    FluidState fluidState = world.getFluidState(pos);

                    if (fluidState.isIn(FluidTags.WATER)) {
                        if (!config.allowWater && collision.isEmpty()) {
                            continue; // Strict water avoidance: puddles are impassable
                        }
                        surfaceY = collision.isEmpty() ? y + 0.9 : y + collision.getMax(Direction.Axis.Y);
                        isWater = collision.isEmpty();
                    } else if (!collision.isEmpty()) {
                        surfaceY = y + collision.getMax(Direction.Axis.Y);
                    } else {
                        continue;
                    }

                    // Avoid duplicate surfaces on the same column
                    if (lastWalkableY != null && Math.abs(lastWalkableY - surfaceY) < 0.5) {
                        continue;
                    }

                    // Check head clearance
                    if (!isWater && !hasHeadClearance(world, x, surfaceY, z, radius, height)) {
                        continue;
                    }

                    // Determine block under foot for cost calculation and type
                    BlockPos groundPos = BlockPos.ofFloored(x, surfaceY - 0.05, z);
                    BlockState groundState = world.getBlockState(groundPos);
                    String blockId = Registries.BLOCK.getId(groundState.getBlock()).toString();
                    double cost = isWater ? config.waterCost : config.getBlockCost(blockId);

                    NavNode.NodeType nodeType = NavNode.NodeType.NORMAL;
                    if (isWater) {
                        nodeType = NavNode.NodeType.WATER;
                    } else if (groundState.getBlock() instanceof StairsBlock || groundState.getBlock() instanceof SlabBlock) {
                        nodeType = NavNode.NodeType.STEP;
                    }

                    // Check for doors / gates
                    boolean isDoor = false;
                    BlockPos doorPos = null;

                    BlockPos footPos = BlockPos.ofFloored(x, surfaceY + 0.1, z);
                    BlockState footState = world.getBlockState(footPos);
                    BlockPos headPos = BlockPos.ofFloored(x, surfaceY + 1.1, z);
                    BlockState headState = world.getBlockState(headPos);

                    if (footState.getBlock() instanceof DoorBlock) {
                        isDoor = true;
                        doorPos = footState.get(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? footPos.down() : footPos;
                        nodeType = NavNode.NodeType.DOOR;
                    } else if (headState.getBlock() instanceof DoorBlock) {
                        isDoor = true;
                        doorPos = headState.get(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? headPos.down() : headPos;
                        nodeType = NavNode.NodeType.DOOR;
                    } else if (footState.getBlock() instanceof FenceGateBlock) {
                        isDoor = true;
                        doorPos = footPos;
                        nodeType = NavNode.NodeType.DOOR;
                    }

                    NavNode node = new NavNode(nextNodeId++, x, surfaceY, z, cost, nodeType);
                    candidateNodes.add(node);
                    navMesh.addNode(node);

                    long colKey = getColumnKey(bx, bz);
                    blockColumnMap.computeIfAbsent(colKey, k -> new ArrayList<>()).add(node);

                    lastWalkableY = surfaceY;
                }
            }
        }

        // 2. Identify Doorway Axial Alignments
        // For each door node, find its passage axis by examining wall surroundings
        Map<Integer, Direction.Axis> doorAxes = new HashMap<>();
        for (NavNode node : candidateNodes) {
            if (node.getType() == NavNode.NodeType.DOOR) {
                BlockPos dPos = BlockPos.ofFloored(node.getX(), node.getY() + 0.1, node.getZ());
                Direction.Axis axis = determineDoorPassageAxis(world, dPos);
                doorAxes.put(node.getId(), axis);
            }
        }

        // 3. Connect neighbor nodes with edges
        double maxStep = config.maxStepHeight + 0.25;

        for (int i = 0; i < candidateNodes.size(); i++) {
            NavNode nodeA = candidateNodes.get(i);
            int bxA = (int) Math.floor(nodeA.getX());
            int bzA = (int) Math.floor(nodeA.getZ());

            for (int j = i + 1; j < candidateNodes.size(); j++) {
                NavNode nodeB = candidateNodes.get(j);
                int bxB = (int) Math.floor(nodeB.getX());
                int bzB = (int) Math.floor(nodeB.getZ());

                int dbx = Math.abs(bxA - bxB);
                int dbz = Math.abs(bzA - bzB);

                // Only connect adjacent blocks (orthogonal or diagonal)
                if (dbx > 1 || dbz > 1 || (dbx == 0 && dbz == 0)) continue;

                // CORNER CUTTING PREVENTION:
                // Diagonal transitions around corners are forbidden if any orthogonal neighbor is a wall!
                if (dbx == 1 && dbz == 1) {
                    List<NavNode> ortho1 = blockColumnMap.get(getColumnKey(bxA, bzB));
                    List<NavNode> ortho2 = blockColumnMap.get(getColumnKey(bxB, bzA));

                    if (ortho1 == null || ortho1.isEmpty() || ortho2 == null || ortho2.isEmpty()) {
                        continue;
                    }

                    boolean ortho1Valid = ortho1.stream().anyMatch(n -> Math.abs(n.getY() - nodeA.getY()) <= maxStep);
                    boolean ortho2Valid = ortho2.stream().anyMatch(n -> Math.abs(n.getY() - nodeA.getY()) <= maxStep);
                    if (!ortho1Valid || !ortho2Valid) {
                        continue;
                    }
                }

                // STRICT DOORWAY ENTRY RULES:
                // A door node can ONLY connect strictly along its passage axis (orthogonal).
                // All side/diagonal connections to doors are strictly pruned!
                if (nodeA.getType() == NavNode.NodeType.DOOR || nodeB.getType() == NavNode.NodeType.DOOR) {
                    if (dbx != 0 && dbz != 0) {
                        continue; // No diagonals!
                    }

                    NavNode doorNode = nodeA.getType() == NavNode.NodeType.DOOR ? nodeA : nodeB;
                    Direction.Axis passageAxis = doorAxes.getOrDefault(doorNode.getId(), Direction.Axis.Z);

                    if (passageAxis == Direction.Axis.Z && dbx != 0) {
                        continue; // Passage is along Z, cannot enter from X!
                    }
                    if (passageAxis == Direction.Axis.X && dbz != 0) {
                        continue; // Passage is along X, cannot enter from Z!
                    }
                }

                // Step height check
                double dy = Math.abs(nodeA.getY() - nodeB.getY());
                if (dy > maxStep) continue;

                // Clearance check along line
                if (!canTraverse(world, nodeA, nodeB, radius, height)) {
                    continue;
                }

                double distance = nodeA.distanceTo(nodeB.getX(), nodeB.getY(), nodeB.getZ());
                double avgCost = (nodeA.getCostMultiplier() + nodeB.getCostMultiplier()) / 2.0;
                double edgeCost = distance * avgCost;

                boolean isDoorTransition = (nodeA.getType() == NavNode.NodeType.DOOR || nodeB.getType() == NavNode.NodeType.DOOR);
                BlockPos doorPos = nodeA.getType() == NavNode.NodeType.DOOR ? BlockPos.ofFloored(nodeA.getX(), nodeA.getY() + 0.1, nodeA.getZ()) :
                                  (nodeB.getType() == NavNode.NodeType.DOOR ? BlockPos.ofFloored(nodeB.getX(), nodeB.getY() + 0.1, nodeB.getZ()) : null);

                float stepDelta = (float) (nodeB.getY() - nodeA.getY());

                nodeA.addEdge(new NavEdge(nodeB.getId(), edgeCost, isDoorTransition, doorPos, stepDelta));
                nodeB.addEdge(new NavEdge(nodeA.getId(), edgeCost, isDoorTransition, doorPos, -stepDelta));
            }
        }

        navMesh.rebuildSpatialGrid();
        return navMesh;
    }

    private Direction.Axis determineDoorPassageAxis(ServerWorld world, BlockPos doorPos) {
        BlockState state = world.getBlockState(doorPos);
        if (state.getBlock() instanceof DoorBlock) {
            return state.get(DoorBlock.FACING).getAxis();
        }
        if (state.getBlock() instanceof FenceGateBlock) {
            return state.get(FenceGateBlock.FACING).getAxis();
        }

        boolean wallWest = isSolidWall(world, doorPos.west());
        boolean wallEast = isSolidWall(world, doorPos.east());
        boolean wallNorth = isSolidWall(world, doorPos.north());
        boolean wallSouth = isSolidWall(world, doorPos.south());

        if ((wallWest && wallEast) && !(wallNorth && wallSouth)) {
            return Direction.Axis.Z;
        }
        if ((wallNorth && wallSouth) && !(wallWest && wallEast)) {
            return Direction.Axis.X;
        }

        return Direction.Axis.Z;
    }

    private boolean isSolidWall(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir() || state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock) {
            return false;
        }
        return !state.getCollisionShape(world, pos).isEmpty();
    }

    private static long getColumnKey(int bx, int bz) {
        return (((long) bx) << 32) | (bz & 0xFFFFFFFFL);
    }

    private boolean hasHeadClearance(ServerWorld world, double x, double surfaceY, double z, double radius, double height) {
        double r = Math.min(0.28, radius);
        Box clearanceBox = new Box(x - r, surfaceY + 0.05, z - r, x + r, surfaceY + Math.min(height, 1.85), z + r);
        return isBoxClear(world, clearanceBox);
    }

    private boolean canTraverse(ServerWorld world, NavNode a, NavNode b, double radius, double height) {
        double maxFloorY = Math.max(a.getY(), b.getY());
        double ceilY = maxFloorY + Math.min(height, 1.85);
        double r = Math.min(0.28, radius);

        int steps = 4;
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            double midX = a.getX() + (b.getX() - a.getX()) * t;
            double midZ = a.getZ() + (b.getZ() - a.getZ()) * t;

            // Clearance box starts strictly above the highest floor point of the transition.
            // This prevents the step itself (stairs, slabs, layers) from falsely colliding with the body box.
            Box clearanceBox = new Box(midX - r, maxFloorY + 0.05, midZ - r, midX + r, ceilY, midZ + r);
            if (!isBoxClear(world, clearanceBox)) {
                return false;
            }
        }
        return true;
    }

    private boolean isBoxClear(ServerWorld world, Box clearanceBox) {
        BlockPos min = BlockPos.ofFloored(clearanceBox.minX, clearanceBox.minY, clearanceBox.minZ);
        BlockPos max = BlockPos.ofFloored(clearanceBox.maxX, clearanceBox.maxY, clearanceBox.maxZ);

        for (BlockPos pos : BlockPos.iterate(min, max)) {
            // Check liquids: water (if disallowing) and lava are impassable hazards inside clearance space!
            FluidState fluid = world.getFluidState(pos);
            if (!config.allowWater && fluid.isIn(FluidTags.WATER)) {
                return false;
            }
            if (fluid.isIn(FluidTags.LAVA)) {
                return false;
            }

            BlockState state = world.getBlockState(pos);
            if (state.isAir() || state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock) {
                continue;
            }

            VoxelShape shape = state.getCollisionShape(world, pos);
            if (shape.isEmpty()) continue;

            for (Box box : shape.getBoundingBoxes()) {
                if (box.offset(pos).intersects(clearanceBox)) {
                    return false;
                }
            }
        }
        return true;
    }
}
