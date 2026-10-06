package ua.polynav.item;

import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSelectionManager {
    public static class Selection {
        private BlockPos pos1;
        private BlockPos pos2;

        public BlockPos getPos1() {
            return pos1;
        }

        public void setPos1(BlockPos pos1) {
            this.pos1 = pos1;
        }

        public BlockPos getPos2() {
            return pos2;
        }

        public void setPos2(BlockPos pos2) {
            this.pos2 = pos2;
        }

        public boolean isComplete() {
            return pos1 != null && pos2 != null;
        }

        public BlockBox toBlockBox() {
            if (!isComplete()) return null;
            return BlockBox.create(pos1, pos2);
        }
    }

    private static final Map<UUID, Selection> SELECTIONS = new ConcurrentHashMap<>();

    public static Selection getOrCreate(UUID uuid) {
        return SELECTIONS.computeIfAbsent(uuid, k -> new Selection());
    }

    public static Selection get(UUID uuid) {
        return SELECTIONS.get(uuid);
    }
}
