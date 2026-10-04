package ua.stubname.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import ua.stubname.network.PathNetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PathRenderer {
    public static class ClientPoint {
        public final double x, y, z;
        public final boolean isDoor;

        public ClientPoint(double x, double y, double z, boolean isDoor) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.isDoor = isDoor;
        }
    }

    public static class ClientPath {
        public final List<ClientPoint> points;
        public final int currentIndex;

        public ClientPath(List<ClientPoint> points, int currentIndex) {
            this.points = points;
            this.currentIndex = currentIndex;
        }
    }

    private static final Map<Integer, ClientPath> PATHS = new ConcurrentHashMap<>();

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(PathNetwork.SYNC_PATH_PACKET, (client, handler, buf, responseSender) -> {
            int entityId = buf.readInt();
            int pointCount = buf.readInt();
            int currentIdx = buf.readInt();

            if (pointCount <= 0) {
                PATHS.remove(entityId);
            } else {
                List<ClientPoint> points = new ArrayList<>(pointCount);
                for (int i = 0; i < pointCount; i++) {
                    double x = buf.readDouble();
                    double y = buf.readDouble();
                    double z = buf.readDouble();
                    boolean isDoor = buf.readBoolean();
                    points.add(new ClientPoint(x, y, z, isDoor));
                }
                PATHS.put(entityId, new ClientPath(points, currentIdx));
            }
        });
    }

    public static void render(WorldRenderContext context) {
        if (PATHS.isEmpty()) return;

        Vec3d cameraPos = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());

        RenderSystem.lineWidth(4.0f);

        for (ClientPath path : PATHS.values()) {
            List<ClientPoint> pts = path.points;
            if (pts.size() < 2) continue;

            for (int i = 0; i < pts.size() - 1; i++) {
                ClientPoint p1 = pts.get(i);
                ClientPoint p2 = pts.get(i + 1);

                float r = 0.0f, g = 0.85f, b = 1.0f, a = 1.0f; // Neon Cyan
                if (p1.isDoor || p2.isDoor) {
                    r = 1.0f; g = 0.65f; b = 0.0f; // Orange for doors
                } else if (i < path.currentIndex) {
                    r = 0.2f; g = 1.0f; b = 0.3f; // Green for active/completed segment
                }

                float nx = (float) (p2.x - p1.x);
                float ny = (float) (p2.y - p1.y);
                float nz = (float) (p2.z - p1.z);
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len > 0.001f) {
                    nx /= len; ny /= len; nz /= len;
                }

                // Render elevated 3D path line segment
                lines.vertex(matrix, (float) p1.x, (float) p1.y + 0.15f, (float) p1.z)
                        .color(r, g, b, a)
                        .normal(matrices.peek().getNormalMatrix(), nx, ny, nz)
                        .next();

                lines.vertex(matrix, (float) p2.x, (float) p2.y + 0.15f, (float) p2.z)
                        .color(r, g, b, a)
                        .normal(matrices.peek().getNormalMatrix(), nx, ny, nz)
                        .next();

                // Vertical waypoint post at each corner
                lines.vertex(matrix, (float) p1.x, (float) p1.y, (float) p1.z)
                        .color(r, g, b, 0.7f)
                        .normal(matrices.peek().getNormalMatrix(), 0, 1, 0)
                        .next();
                lines.vertex(matrix, (float) p1.x, (float) p1.y + 0.4f, (float) p1.z)
                        .color(r, g, b, 0.7f)
                        .normal(matrices.peek().getNormalMatrix(), 0, 1, 0)
                        .next();
            }
        }

        matrices.pop();
    }
}
