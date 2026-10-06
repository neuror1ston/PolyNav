package ua.polynav.navmesh.storage;

import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import ua.polynav.navmesh.geometry.NavEdge;
import ua.polynav.navmesh.geometry.NavMesh;
import ua.polynav.navmesh.geometry.NavNode;

import java.io.*;

public class NavMeshStorage {
    private static final int MAGIC = 0x4E41564D; // "NAVM"
    private static final int VERSION = 1;

    public static void save(NavMesh mesh, File file) throws IOException {
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeUTF(mesh.getName());

            BlockBox b = mesh.getBounds();
            out.writeInt(b.getMinX());
            out.writeInt(b.getMinY());
            out.writeInt(b.getMinZ());
            out.writeInt(b.getMaxX());
            out.writeInt(b.getMaxY());
            out.writeInt(b.getMaxZ());

            out.writeInt(mesh.getNodes().size());
            for (NavNode node : mesh.getNodes().values()) {
                out.writeInt(node.getId());
                out.writeDouble(node.getX());
                out.writeDouble(node.getY());
                out.writeDouble(node.getZ());
                out.writeDouble(node.getCostMultiplier());
                out.writeByte(node.getType().ordinal());

                out.writeInt(node.getEdges().size());
                for (NavEdge edge : node.getEdges()) {
                    out.writeInt(edge.getTargetNodeId());
                    out.writeDouble(edge.getDistanceCost());
                    out.writeBoolean(edge.isDoor());
                    if (edge.isDoor() && edge.getDoorPos() != null) {
                        out.writeInt(edge.getDoorPos().getX());
                        out.writeInt(edge.getDoorPos().getY());
                        out.writeInt(edge.getDoorPos().getZ());
                    } else if (edge.isDoor()) {
                        out.writeInt(0);
                        out.writeInt(0);
                        out.writeInt(0);
                    }
                    out.writeFloat(edge.getHeightDelta());
                }
            }
        }
    }

    public static NavMesh load(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new IOException("Invalid NavMesh file format!");
            }
            int version = in.readInt();
            if (version != VERSION) {
                throw new IOException("Unsupported NavMesh version: " + version);
            }

            String name = in.readUTF();
            int minX = in.readInt();
            int minY = in.readInt();
            int minZ = in.readInt();
            int maxX = in.readInt();
            int maxY = in.readInt();
            int maxZ = in.readInt();

            BlockBox bounds = new BlockBox(minX, minY, minZ, maxX, maxY, maxZ);
            NavMesh mesh = new NavMesh(name, bounds);

            int nodeCount = in.readInt();
            NavNode.NodeType[] nodeTypes = NavNode.NodeType.values();

            for (int i = 0; i < nodeCount; i++) {
                int id = in.readInt();
                double x = in.readDouble();
                double y = in.readDouble();
                double z = in.readDouble();
                double cost = in.readDouble();
                byte typeOrdinal = in.readByte();
                NavNode.NodeType type = (typeOrdinal >= 0 && typeOrdinal < nodeTypes.length) ? nodeTypes[typeOrdinal] : NavNode.NodeType.NORMAL;

                NavNode node = new NavNode(id, x, y, z, cost, type);
                int edgeCount = in.readInt();
                for (int j = 0; j < edgeCount; j++) {
                    int targetId = in.readInt();
                    double edgeCost = in.readDouble();
                    boolean isDoor = in.readBoolean();
                    BlockPos doorPos = null;
                    if (isDoor) {
                        int dx = in.readInt();
                        int dy = in.readInt();
                        int dz = in.readInt();
                        if (dx != 0 || dy != 0 || dz != 0) {
                            doorPos = new BlockPos(dx, dy, dz);
                        }
                    }
                    float heightDelta = in.readFloat();
                    node.addEdge(new NavEdge(targetId, edgeCost, isDoor, doorPos, heightDelta));
                }

                mesh.addNode(node);
            }

            mesh.rebuildSpatialGrid();
            return mesh;
        }
    }
}
