package ua.polynav.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;

public class NavMeshWandItem extends Item {
    public NavMeshWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (!context.getWorld().isClient() && context.getPlayer() instanceof ServerPlayerEntity player) {
            BlockPos pos = context.getBlockPos();
            PlayerSelectionManager.Selection sel = PlayerSelectionManager.getOrCreate(player.getUuid());
            sel.setPos2(pos);

            String message = "§b[NavMesh Wand] §aPos2 §7встановлено на: §f" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
            if (sel.isComplete()) {
                BlockBox box = sel.toBlockBox();
                int dx = box.getBlockCountX();
                int dy = box.getBlockCountY();
                int dz = box.getBlockCountZ();
                message += " §8(Розмір: §e" + dx + "x" + dy + "x" + dz + "§8, об'єм: §e" + (dx * dy * dz) + "§8)";
            }
            player.sendMessage(Text.literal(message), false);
            return ActionResult.SUCCESS;
        }
        return ActionResult.SUCCESS;
    }

    public static void setPos1(ServerPlayerEntity player, BlockPos pos) {
        PlayerSelectionManager.Selection sel = PlayerSelectionManager.getOrCreate(player.getUuid());
        sel.setPos1(pos);

        String message = "§b[NavMesh Wand] §6Pos1 §7встановлено на: §f" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        if (sel.isComplete()) {
            BlockBox box = sel.toBlockBox();
            int dx = box.getBlockCountX();
            int dy = box.getBlockCountY();
            int dz = box.getBlockCountZ();
            message += " §8(Розмір: §e" + dx + "x" + dy + "x" + dz + "§8, об'єм: §e" + (dx * dy * dz) + "§8)";
        }
        player.sendMessage(Text.literal(message), false);
    }
}
