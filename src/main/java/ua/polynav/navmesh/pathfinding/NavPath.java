package ua.polynav.navmesh.pathfinding;

import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.List;

public class NavPath {
    private final List<NavPathPoint> points;
    private final double totalCost;
    private int currentIndex = 0;

    public NavPath(List<NavPathPoint> points, double totalCost) {
        this.points = points != null ? points : Collections.emptyList();
        this.totalCost = totalCost;
    }

    public List<NavPathPoint> getPoints() {
        return points;
    }

    public int getLength() {
        return points.size();
    }

    public double getTotalCost() {
        return totalCost;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int index) {
        this.currentIndex = Math.max(0, Math.min(index, points.size()));
    }

    public boolean isFinished() {
        return currentIndex >= points.size();
    }

    public NavPathPoint getCurrentPoint() {
        if (isFinished()) return null;
        return points.get(currentIndex);
    }

    public NavPathPoint getNextPoint() {
        if (currentIndex + 1 >= points.size()) return null;
        return points.get(currentIndex + 1);
    }

    public void advance() {
        if (currentIndex < points.size()) {
            currentIndex++;
        }
    }

    public Vec3d getEndPos() {
        if (points.isEmpty()) return null;
        return points.get(points.size() - 1).getPos();
    }
}
