package ua.stubname.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import ua.stubname.navmesh.pathfinding.NavPath;
import ua.stubname.navmesh.pathfinding.NavPathPoint;

import java.util.List;

public class NavMeshMoveControl extends MoveControl {
    private final NpcEntity npc;
    private BlockPos doorToClose = null;
    private boolean passedThroughDoor = false;
    private int stuckTicks = 0;

    public NavMeshMoveControl(NpcEntity npc) {
        super(npc);
        this.npc = npc;
    }

    @Override
    public void tick() {
        NavPath path = npc.getCurrentNavPath();
        if (path == null || path.isFinished()) {
            if (passedThroughDoor) {
                checkAndCloseDoorBehind();
            }
            npc.forwardSpeed = 0.0f;
            stuckTicks = 0;
            return;
        }

        NavPathPoint currentTarget = path.getCurrentPoint();
        if (currentTarget == null) {
            return;
        }

        // 1. Scan and open doors well in advance (up to 3.5 blocks forward)
        handleDoorsAhead(path);

        Vec3d targetPos = currentTarget.getPos();

        // 2. Doorway centering: offset away from open door leaf into the clear opening
        if (currentTarget.isDoor() && currentTarget.getDoorPos() != null) {
            targetPos = centerOnDoorway(currentTarget.getDoorPos(), targetPos);
        } else if (path.getNextPoint() != null && path.getNextPoint().isDoor() && path.getNextPoint().getDoorPos() != null) {
            targetPos = centerOnDoorway(path.getNextPoint().getDoorPos(), targetPos);
        }

        double dx = targetPos.x - npc.getX();
        double dy = targetPos.y - npc.getY();
        double dz = targetPos.z - npc.getZ();
        double distSq = dx * dx + dz * dz;

        // Reach distance: 0.38m allows natural, tight waypoint following along curves
        double reachDistance = currentTarget.isDoor() ? 0.35 : 0.38;
        if (distSq < reachDistance * reachDistance) {
            path.advance();
            ua.stubname.network.PathNetwork.sendPathToClients(npc, path);

            if (path.isFinished()) {
                npc.forwardSpeed = 0.0f;
                if (passedThroughDoor) {
                    checkAndCloseDoorBehind();
                }
                return;
            }
            currentTarget = path.getCurrentPoint();
            targetPos = currentTarget.getPos();
            if (currentTarget.isDoor() && currentTarget.getDoorPos() != null) {
                targetPos = centerOnDoorway(currentTarget.getDoorPos(), targetPos);
            }
            dx = targetPos.x - npc.getX();
            dz = targetPos.z - npc.getZ();
        }

        // Track whether NPC reached the doorway threshold
        if (doorToClose != null) {
            double distSqToDoor = npc.squaredDistanceTo(doorToClose.getX() + 0.5, npc.getY(), doorToClose.getZ() + 0.5);
            if (distSqToDoor < 1.44) {
                passedThroughDoor = true;
            }
        }

        // Direct move vector towards goal
        Vec3d moveDir = new Vec3d(dx, 0, dz).normalize();

        // 3. TARGET YAW IS ALWAYS DIRECTLY AT THE GOAL (NO HEAD JITTER!)
        // Head and body orientation calmly faces the target waypoint
        float targetYaw = (float) (MathHelper.atan2(moveDir.z, moveDir.x) * (180.0 / Math.PI)) - 90.0f;
        float angleDiff = Math.abs(MathHelper.wrapDegrees(targetYaw - npc.getYaw()));

        float turnSpeed = angleDiff > 45.0f ? 80.0f : 45.0f;
        npc.setYaw(rotlerp(npc.getYaw(), targetYaw, turnSpeed));
        npc.headYaw = npc.getYaw();
        npc.bodyYaw = npc.getYaw();

        // 4. Intelligent Jump vs Smooth Step:
        // Walk smoothly on stairs, slabs, and layers. Jump only on full blocks when no stairs/slabs exist!
        boolean isFullBlockClimb = false;
        if (dy > 0.45) {
            BlockPos targetGround = BlockPos.ofFloored(targetPos.x, targetPos.y - 0.05, targetPos.z);
            Vec3d lookAhead = moveDir.multiply(0.65);
            BlockPos stepPos = BlockPos.ofFloored(npc.getX() + lookAhead.x, npc.getY() + 0.5, npc.getZ() + lookAhead.z);

            boolean targetIsSmooth = isSmoothWalkable(npc.getWorld(), targetGround);
            boolean stepIsSmooth = isSmoothWalkable(npc.getWorld(), stepPos);

            if (!targetIsSmooth && !stepIsSmooth) {
                isFullBlockClimb = true;
                if (npc.isOnGround() && (distSq < 2.5 || npc.horizontalCollision)) {
                    npc.getJumpControl().setActive();
                }
            }
        }

        // 5. Collision Unstuck Handling (Nudge out of corners without rotating head)
        if (npc.horizontalCollision) {
            stuckTicks++;
            if (stuckTicks >= 2 && !isFullBlockClimb) {
                // Instantly unstick by sliding 0.06m towards goal or away from obstacle
                Vec3d nudge = moveDir.multiply(0.06);
                npc.setPosition(npc.getX() + nudge.x, npc.getY(), npc.getZ() + nudge.z);
                stuckTicks = 0;
            }
        } else {
            stuckTicks = Math.max(0, stuckTicks - 1);
        }

        // 5. Stable forward speed
        double attrSpeed = npc.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        float baseSpeed = (float) (attrSpeed > 0 ? attrSpeed : 0.28f);

        float speedFactor = angleDiff > 45.0f ? 0.70f : 1.0f;
        float forwardSpeed = baseSpeed * speedFactor;

        npc.setMovementSpeed(forwardSpeed);
        npc.forwardSpeed = forwardSpeed;

        // Check if we need to close door behind us
        checkAndCloseDoorBehind();
    }

    private Vec3d centerOnDoorway(BlockPos doorPos, Vec3d originalTarget) {
        World world = npc.getWorld();
        BlockState state = world.getBlockState(doorPos);
        if (state.getBlock() instanceof DoorBlock) {
            Direction facing = state.get(DoorBlock.FACING);
            DoorHinge hinge = state.get(DoorBlock.HINGE);

            // Door leaf occupies ~0.19m on hinge side. Offset target 0.10m away from hinge
            double hingeOffset = (hinge == DoorHinge.LEFT) ? 0.10 : -0.10;

            if (facing.getAxis() == Direction.Axis.Z) {
                double targetX = doorPos.getX() + 0.5 + (facing == Direction.SOUTH ? -hingeOffset : hingeOffset);
                return new Vec3d(targetX, originalTarget.y, originalTarget.z);
            } else if (facing.getAxis() == Direction.Axis.X) {
                double targetZ = doorPos.getZ() + 0.5 + (facing == Direction.EAST ? -hingeOffset : hingeOffset);
                return new Vec3d(originalTarget.x, originalTarget.y, targetZ);
            }
        }
        return originalTarget;
    }

    private void handleDoorsAhead(NavPath path) {
        World world = npc.getWorld();

        // 1. Scan immediate door ahead (next 2 points)
        for (int i = path.getCurrentIndex(); i < Math.min(path.getPoints().size(), path.getCurrentIndex() + 2); i++) {
            NavPathPoint pt = path.getPoints().get(i);
            if (pt.isDoor() && pt.getDoorPos() != null) {
                tryOpenDoor(pt.getDoorPos());
                return;
            }
        }

        // 2. Also scan directly in front (1.5 blocks)
        Vec3d lookVec = npc.getRotationVec(1.0f).multiply(1.5);
        BlockPos aheadPos = BlockPos.ofFloored(npc.getX() + lookVec.x, npc.getY() + 0.5, npc.getZ() + lookVec.z);
        BlockState aheadState = world.getBlockState(aheadPos);
        if (aheadState.getBlock() instanceof DoorBlock || aheadState.getBlock() instanceof FenceGateBlock) {
            tryOpenDoor(aheadPos);
        }
    }

    private void tryOpenDoor(BlockPos pos) {
        World world = npc.getWorld();
        double distSq = npc.squaredDistanceTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

        // Open only when within 2.2m of the door
        if (distSq < 5.0) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock doorBlock) {
                boolean isOpen = state.get(DoorBlock.OPEN);
                if (!isOpen) {
                    setDoorState(world, doorBlock, state, pos, true);
                }
                this.doorToClose = getLowerDoorPos(state, pos);
                this.passedThroughDoor = false;
            } else if (state.getBlock() instanceof FenceGateBlock) {
                boolean isOpen = state.get(FenceGateBlock.OPEN);
                if (!isOpen) {
                    world.setBlockState(pos, state.with(FenceGateBlock.OPEN, true), Block.NOTIFY_ALL | Block.REDRAW_ON_MAIN_THREAD);
                    world.emitGameEvent(npc, GameEvent.BLOCK_OPEN, pos);
                }
                this.doorToClose = pos;
                this.passedThroughDoor = false;
            }
        }
    }

    private BlockPos getLowerDoorPos(BlockState state, BlockPos pos) {
        if (state.contains(DoorBlock.HALF) && state.get(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            return pos.down();
        }
        return pos;
    }

    private void setDoorState(World world, DoorBlock doorBlock, BlockState state, BlockPos pos, boolean open) {
        BlockPos lowerPos = getLowerDoorPos(state, pos);
        BlockPos upperPos = lowerPos.up();

        BlockState lowerState = world.getBlockState(lowerPos);
        BlockState upperState = world.getBlockState(upperPos);

        if (lowerState.isOf(doorBlock)) {
            world.setBlockState(lowerPos, lowerState.with(DoorBlock.OPEN, open), Block.NOTIFY_ALL | Block.REDRAW_ON_MAIN_THREAD);
        }
        if (upperState.isOf(doorBlock)) {
            world.setBlockState(upperPos, upperState.with(DoorBlock.OPEN, open), Block.NOTIFY_ALL | Block.REDRAW_ON_MAIN_THREAD);
        }

        net.minecraft.sound.SoundEvent sound = open ? doorBlock.getBlockSetType().doorOpen() : doorBlock.getBlockSetType().doorClose();
        world.playSound(null, lowerPos, sound, net.minecraft.sound.SoundCategory.BLOCKS, 1.0f, world.getRandom().nextFloat() * 0.1f + 0.9f);
        world.emitGameEvent(npc, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, lowerPos);
    }

    private void checkAndCloseDoorBehind() {
        if (doorToClose == null || !passedThroughDoor) return;

        World world = npc.getWorld();
        double distSq = npc.squaredDistanceTo(doorToClose.getX() + 0.5, npc.getY(), doorToClose.getZ() + 0.5);

        // Only close after the NPC has passed through and walked at least 2.2m away on the other side
        if (distSq > 4.84) {
            BlockState state = world.getBlockState(doorToClose);
            if (state.getBlock() instanceof DoorBlock doorBlock && state.get(DoorBlock.OPEN)) {
                setDoorState(world, doorBlock, state, doorToClose, false);
            } else if (state.getBlock() instanceof FenceGateBlock && state.get(FenceGateBlock.OPEN)) {
                world.setBlockState(doorToClose, state.with(FenceGateBlock.OPEN, false), Block.NOTIFY_ALL | Block.REDRAW_ON_MAIN_THREAD);
                world.emitGameEvent(npc, GameEvent.BLOCK_CLOSE, doorToClose);
            }
            this.doorToClose = null;
            this.passedThroughDoor = false;
        }
    }

    private boolean isSmoothWalkable(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir() || state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock) {
            return true;
        }
        if (state.getBlock() instanceof StairsBlock || state.getBlock() instanceof SlabBlock) {
            return true;
        }
        VoxelShape shape = state.getCollisionShape(world, pos);
        if (shape.isEmpty()) {
            return true;
        }
        // Sub-block layers <= 0.6 (e.g. Conquest Reforged layers, snow layers, carpets)
        return shape.getMax(Direction.Axis.Y) <= 0.6;
    }

    private float rotlerp(float from, float to, float maxDelta) {
        float diff = MathHelper.wrapDegrees(to - from);
        if (diff > maxDelta) diff = maxDelta;
        if (diff < -maxDelta) diff = -maxDelta;
        return from + diff;
    }
}
