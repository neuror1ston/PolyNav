package ua.stubname.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import ua.stubname.NpcNavsMod;
import ua.stubname.entity.NpcEntity;

import java.util.Comparator;
import java.util.List;

public class NpcCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("npc")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("spawn")
                        .executes(ctx -> spawnNpc(ctx.getSource(), "NPC"))
                        .then(CommandManager.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> spawnNpc(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
                        )
                )
                .then(CommandManager.literal("goto")
                        .then(CommandManager.argument("pos", Vec3ArgumentType.vec3())
                                .executes(ctx -> {
                                    Vec3d target = Vec3ArgumentType.getVec3(ctx, "pos");
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

                                    NpcEntity npc = findNearestNpc(player);
                                    if (npc == null) {
                                        ctx.getSource().sendError(Text.literal("§cПоруч (у радіусі 32м) не знайдено NPC!"));
                                        return 0;
                                    }

                                    boolean started = npc.getNavMeshNavigation().navigateTo(target, 0.28);
                                    if (started) {
                                        player.sendMessage(Text.literal("§a[NPC] Відправлено '" + npc.getName().getString() + "' до: " +
                                                String.format("%.1f, %.1f, %.1f", target.x, target.y, target.z)), false);
                                    } else {
                                        player.sendMessage(Text.literal("§c[NPC] Не вдалося прокласти NavMesh шлях до заданої точки!"), false);
                                    }
                                    return 1;
                                })
                        )
                )
                .then(CommandManager.literal("follow")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            NpcEntity npc = findNearestNpc(player);
                            if (npc == null) {
                                ctx.getSource().sendError(Text.literal("§cПоруч не знайдено NPC!"));
                                return 0;
                            }

                            Vec3d target = player.getPos();
                            npc.getNavMeshNavigation().navigateTo(target, 0.28);
                            player.sendMessage(Text.literal("§a[NPC] '" + npc.getName().getString() + "' тепер іде до вас!"), false);
                            return 1;
                        })
                )
        );
    }

    private static int spawnNpc(ServerCommandSource source, String name) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            NpcEntity npc = NpcNavsMod.NPC_ENTITY_TYPE.create(player.getServerWorld());
            if (npc != null) {
                npc.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), 0.0f);
                npc.setCustomName(Text.literal(name));
                npc.setCustomNameVisible(true);
                npc.initialize(player.getServerWorld(), player.getServerWorld().getLocalDifficulty(player.getBlockPos()),
                        SpawnReason.COMMAND, null, null);
                player.getServerWorld().spawnEntity(npc);
                player.sendMessage(Text.literal("§a[NPC] Успішно заспавнено NPC '" + name + "'!"), false);
                return 1;
            }
        } catch (Exception e) {
            source.sendError(Text.literal("§cПомилка створення NPC: " + e.getMessage()));
            e.printStackTrace();
        }
        return 0;
    }

    private static NpcEntity findNearestNpc(ServerPlayerEntity player) {
        Box box = player.getBoundingBox().expand(32.0);
        List<NpcEntity> npcs = player.getServerWorld().getEntitiesByClass(NpcEntity.class, box, e -> e.isAlive());
        return npcs.stream()
                .min(Comparator.comparingDouble(player::squaredDistanceTo))
                .orElse(null);
    }
}
