package ua.polynav.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import ua.polynav.NpcNavsMod;
import ua.polynav.entity.NpcEntity;
import ua.polynav.item.PlayerSelectionManager;
import ua.polynav.navmesh.NavMeshManager;
import ua.polynav.navmesh.baker.NavMeshBaker;
import ua.polynav.navmesh.geometry.NavMesh;

public class NavMeshCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("navmesh")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("wand")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            player.giveItemStack(new ItemStack(NpcNavsMod.NAVMESH_WAND));
                            player.sendMessage(Text.literal("§b[NavMesh] §aВидано паличку виділення! (ЛКМ - Pos1, ПКМ - Pos2)"), false);
                            return 1;
                        })
                )
                .then(CommandManager.literal("pos1")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            BlockPos pos = player.getBlockPos();
                            PlayerSelectionManager.Selection sel = PlayerSelectionManager.getOrCreate(player.getUuid());
                            sel.setPos1(pos);
                            player.sendMessage(Text.literal("§b[NavMesh] §6Pos1 §7встановлено на: §f" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), false);
                            return 1;
                        })
                        .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                .executes(ctx -> {
                                    BlockPos pos = BlockPosArgumentType.getBlockPos(ctx, "pos");
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    PlayerSelectionManager.Selection sel = PlayerSelectionManager.getOrCreate(player.getUuid());
                                    sel.setPos1(pos);
                                    player.sendMessage(Text.literal("§b[NavMesh] §6Pos1 §7встановлено на: §f" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), false);
                                    return 1;
                                })
                        )
                )
                .then(CommandManager.literal("pos2")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            BlockPos pos = player.getBlockPos();
                            PlayerSelectionManager.Selection sel = PlayerSelectionManager.getOrCreate(player.getUuid());
                            sel.setPos2(pos);
                            player.sendMessage(Text.literal("§b[NavMesh] §aPos2 §7встановлено на: §f" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), false);
                            return 1;
                        })
                        .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                .executes(ctx -> {
                                    BlockPos pos = BlockPosArgumentType.getBlockPos(ctx, "pos");
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    PlayerSelectionManager.Selection sel = PlayerSelectionManager.getOrCreate(player.getUuid());
                                    sel.setPos2(pos);
                                    player.sendMessage(Text.literal("§b[NavMesh] §aPos2 §7встановлено на: §f" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), false);
                                    return 1;
                                })
                        )
                )
                .then(CommandManager.literal("bake")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    PlayerSelectionManager.Selection sel = PlayerSelectionManager.get(player.getUuid());

                                    if (sel == null || !sel.isComplete()) {
                                        ctx.getSource().sendError(Text.literal("§cСпочатку виділіть Pos1 та Pos2 за допомогою палички або команд /navmesh pos1/pos2!"));
                                        return 0;
                                    }

                                    BlockBox box = sel.toBlockBox();
                                    player.sendMessage(Text.literal("§b[NavMesh] §eЗапікання сітки '" + name + "' розпочато у фоновому потоці..."), false);
                                    long startTime = System.currentTimeMillis();

                                    NavMeshBaker baker = new NavMeshBaker(NpcNavsMod.CONFIG);
                                    baker.bakeAsync(player.getServerWorld(), name, box).thenAccept(mesh -> {
                                        long elapsed = System.currentTimeMillis() - startTime;
                                        NavMeshManager.getInstance().registerMesh(mesh);
                                        try {
                                            NavMeshManager.getInstance().saveMesh(name);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }

                                        player.sendMessage(Text.literal("§b[NavMesh] §aУспішно запечено '" + name + "'!"), false);
                                        player.sendMessage(Text.literal("§7Вузлів: §e" + mesh.getNodeCount() + "§7, час: §e" + elapsed + " мс"), false);
                                    }).exceptionally(ex -> {
                                        player.sendMessage(Text.literal("§cПомилка запікання: " + ex.getMessage()), false);
                                        ex.printStackTrace();
                                        return null;
                                    });

                                    return 1;
                                })
                        )
                )
                .then(CommandManager.literal("list")
                        .executes(ctx -> {
                            var meshes = NavMeshManager.getInstance().getAllMeshes();
                            if (meshes.isEmpty()) {
                                ctx.getSource().sendMessage(Text.literal("§7Немає завантажених сіток NavMesh."));
                                return 1;
                            }
                            ctx.getSource().sendMessage(Text.literal("§b[NavMesh] Завантажені сітки:"));
                            for (NavMesh m : meshes) {
                                ctx.getSource().sendMessage(Text.literal("§8 - §e" + m.getName() + " §7(Вузлів: " + m.getNodeCount() + ")"));
                            }
                            return 1;
                        })
                )
                .then(CommandManager.literal("debug")
                        .then(CommandManager.literal("path")
                                .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                            NpcEntity.setDebugPathRendering(enabled);
                                            ctx.getSource().sendMessage(Text.literal("§b[NavMesh] §7Дебаг відображення шляху частинками: §e" + (enabled ? "Увімкнено" : "Вимкнено")));
                                            return 1;
                                        })
                                )
                        )
                        .then(CommandManager.literal("show")
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    var meshes = NavMeshManager.getInstance().getAllMeshes();
                                    if (meshes.isEmpty()) {
                                        player.sendMessage(Text.literal("§cНемає завантажених сіток! Спочатку запечіть сітку через /navmesh bake <name>"), false);
                                        return 0;
                                    }

                                    int count = 0;
                                    net.minecraft.particle.DustParticleEffect normalColor = new net.minecraft.particle.DustParticleEffect(new org.joml.Vector3f(0.2f, 1.0f, 0.2f), 0.8f);
                                    net.minecraft.particle.DustParticleEffect doorColor = new net.minecraft.particle.DustParticleEffect(new org.joml.Vector3f(1.0f, 0.6f, 0.0f), 1.2f);
                                    net.minecraft.particle.DustParticleEffect stepColor = new net.minecraft.particle.DustParticleEffect(new org.joml.Vector3f(0.1f, 0.5f, 1.0f), 1.0f);
                                    net.minecraft.particle.DustParticleEffect waterColor = new net.minecraft.particle.DustParticleEffect(new org.joml.Vector3f(0.1f, 0.2f, 0.9f), 0.8f);

                                    for (NavMesh mesh : meshes) {
                                        for (ua.polynav.navmesh.geometry.NavNode node : mesh.getNodes().values()) {
                                            if (node.distanceSquaredTo(player.getX(), player.getY(), player.getZ()) <= 35.0 * 35.0) {
                                                net.minecraft.particle.DustParticleEffect color;
                                                if (node.getType() == ua.polynav.navmesh.geometry.NavNode.NodeType.DOOR) {
                                                    color = doorColor;
                                                } else if (node.getType() == ua.polynav.navmesh.geometry.NavNode.NodeType.WATER) {
                                                    color = waterColor;
                                                } else if (node.getType() == ua.polynav.navmesh.geometry.NavNode.NodeType.STEP) {
                                                    color = stepColor;
                                                } else {
                                                    color = normalColor;
                                                }

                                                player.getServerWorld().spawnParticles(player, color, true,
                                                        node.getX(), node.getY() + 0.15, node.getZ(),
                                                        1, 0.0, 0.0, 0.0, 0.0);
                                                count++;
                                            }
                                        }
                                    }

                                    player.sendMessage(Text.literal("§b[NavMesh] §aВізуалізовано §e" + count + " §aвузлів навколо вас!"), false);
                                    player.sendMessage(Text.literal("§7(§aЗелені§7: звичайні, §6Помаранчеві§7: двері, §9Сині§7: сходинки/шари)"), false);
                                    return 1;
                                })
                        )
                )
        );
    }
}
