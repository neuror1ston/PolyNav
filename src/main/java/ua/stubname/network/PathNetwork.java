package ua.stubname.network;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.Entity;
import ua.stubname.NpcNavsMod;
import ua.stubname.navmesh.pathfinding.NavPath;
import ua.stubname.navmesh.pathfinding.NavPathPoint;

public class PathNetwork {
    public static final Identifier SYNC_PATH_PACKET = new Identifier(NpcNavsMod.MOD_ID, "sync_path");

    public static void sendPathToClients(Entity entity, NavPath path) {
        if (entity.getWorld().isClient()) return;

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(entity.getId());

        if (path == null || path.isFinished()) {
            buf.writeInt(0); // 0 points means clear path
            buf.writeInt(0);
        } else {
            buf.writeInt(path.getPoints().size());
            buf.writeInt(path.getCurrentIndex());

            for (NavPathPoint pt : path.getPoints()) {
                buf.writeDouble(pt.getX());
                buf.writeDouble(pt.getY());
                buf.writeDouble(pt.getZ());
                buf.writeBoolean(pt.isDoor());
            }
        }

        for (ServerPlayerEntity player : PlayerLookup.tracking(entity)) {
            ServerPlayNetworking.send(player, SYNC_PATH_PACKET, buf);
        }
    }
}
