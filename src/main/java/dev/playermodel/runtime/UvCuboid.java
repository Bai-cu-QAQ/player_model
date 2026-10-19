package dev.playermodel.runtime;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Set;

/**
 * A Cuboid that renders its own per-face quads. Sodium replaces Cuboid.renderCuboid with a fast path
 * built from the constructor's box-UV arguments, which would discard the per-face UVs of imported
 * models. Overriding the method keeps Sodium (and vanilla) on the accurate path.
 */
final class UvCuboid extends ModelPart.Cuboid {
    UvCuboid(float x, float y, float z, float sizeX, float sizeY, float sizeZ,
             ModelPart.Quad[] sides) {
        super(0, 0, x, y, z, sizeX, sizeY, sizeZ, 0, 0, 0, false, 1, 1,
                Set.of(Direction.values()));
        this.sides = sides;
    }

    @Override
    public void renderCuboid(MatrixStack.Entry entry, VertexConsumer consumer, int light, int overlay, int color) {
        Matrix4f matrix = entry.getPositionMatrix();
        Vector3f transformedNormal = new Vector3f();
        Vector3f edgeA = new Vector3f();
        Vector3f edgeB = new Vector3f();
        Vector3f localNormal = new Vector3f();
        for (ModelPart.Quad quad : this.sides) {
            ModelPart.Vertex v0 = quad.vertices[0];
            ModelPart.Vertex v1 = quad.vertices[1];
            ModelPart.Vertex v2 = quad.vertices[2];
            localNormal.set(v1.pos.x() - v0.pos.x(), v1.pos.y() - v0.pos.y(), v1.pos.z() - v0.pos.z());
            edgeB.set(v2.pos.x() - v0.pos.x(), v2.pos.y() - v0.pos.y(), v2.pos.z() - v0.pos.z());
            edgeA.set(localNormal);
            localNormal.set(edgeA).cross(edgeB);
            if (localNormal.lengthSquared() < 1e-8F) {
                localNormal.set(quad.direction.x, quad.direction.y, quad.direction.z);
            } else {
                localNormal.normalize();
                if (localNormal.dot(quad.direction.x, quad.direction.y, quad.direction.z) < 0) {
                    localNormal.negate();
                }
            }
            Vector3f normal = entry.transformNormal(localNormal, transformedNormal);
            float nx = normal.x();
            float ny = normal.y();
            float nz = normal.z();
            for (ModelPart.Vertex vertex : quad.vertices) {
                float x = vertex.pos.x() / 16.0F;
                float y = vertex.pos.y() / 16.0F;
                float z = vertex.pos.z() / 16.0F;
                Vector3f position = matrix.transformPosition(x, y, z, transformedNormal);
                consumer.vertex(position.x(), position.y(), position.z(), color,
                        vertex.u, vertex.v, overlay, light, nx, ny, nz);
            }
        }
    }
}
