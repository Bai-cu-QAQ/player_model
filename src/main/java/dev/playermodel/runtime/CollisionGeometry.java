package dev.playermodel.runtime;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure geometry in model units; deliberately independent of Minecraft model classes. */
final class CollisionGeometry {
    private CollisionGeometry() {
    }

    static final float REST_TOLERANCE = 0.02F;

    /** Stable, unique corners, edge midpoints and face centers, including thin/flat surfaces. */
    static List<Vector3f> surfaceSamples(List<List<Vector3f>> faces) {
        Set<Vector3f> samples = new LinkedHashSet<>();
        for (List<Vector3f> face : faces) {
            if (face.isEmpty()) {
                continue;
            }
            Vector3f center = new Vector3f();
            for (int i = 0; i < face.size(); i++) {
                Vector3f vertex = face.get(i);
                samples.add(new Vector3f(vertex));
                samples.add(new Vector3f(vertex).add(face.get((i + 1) % face.size())).mul(0.5F));
                center.add(vertex);
            }
            samples.add(center.div(face.size()));
        }
        return new ArrayList<>(samples);
    }

    static Matrix4f restTransform(Matrix4f parent, Vector3f pivot, org.joml.Quaternionf rotation,
                                  Vector3f scale) {
        return new Matrix4f(parent).translate(pivot).rotate(rotation).scale(scale);
    }

    static float excessDepth(float currentDepth, float restDepth) {
        float allowedDepth = Math.max(0.0F, restDepth);
        return currentDepth > 0.0F && currentDepth > allowedDepth + REST_TOLERANCE
                ? currentDepth - allowedDepth : 0.0F;
    }

    record Penetration(Vector3f normal, float depth) {
    }

    /**
     * A cuboid in its own oriented coordinates. Quad edges retain imported element rotation;
     * the inverse transform also retains core/ancestor rotation and nonuniform scale.
     */
    static final class Box {
        private final Matrix4f localFromWorld;
        private final Vector3f min;
        private final Vector3f max;
        private final Vector3f[] normals;
        private final float[] normalLengths;

        private Box(Matrix4f worldFromLocal, Vector3f min, Vector3f max) {
            this.localFromWorld = new Matrix4f(worldFromLocal).invert();
            this.min = min;
            this.max = max;
            // Rows of the inverse are inverse-transpose transformed local face normals.
            normals = new Vector3f[]{
                    new Vector3f(localFromWorld.m00(), localFromWorld.m10(), localFromWorld.m20()),
                    new Vector3f(localFromWorld.m01(), localFromWorld.m11(), localFromWorld.m21()),
                    new Vector3f(localFromWorld.m02(), localFromWorld.m12(), localFromWorld.m22())};
            normalLengths = new float[3];
            for (int axis = 0; axis < 3; axis++) {
                normalLengths[axis] = normals[axis].length();
                normals[axis].div(normalLengths[axis]);
            }
        }

        static Box fromFaces(List<List<Vector3f>> faces, Matrix4f worldFromPart) {
            Vector3f x = null;
            Vector3f y = null;
            for (List<Vector3f> face : faces) {
                if (face.size() < 3) {
                    continue;
                }
                Vector3f a = new Vector3f(face.get(1)).sub(face.get(0));
                Vector3f b = new Vector3f(face.get(face.size() - 1)).sub(face.get(0));
                if (a.lengthSquared() > 1.0E-12F && b.lengthSquared() > 1.0E-12F
                        && new Vector3f(a).cross(b).lengthSquared() > 1.0E-12F) {
                    x = a.normalize();
                    y = b.sub(new Vector3f(x).mul(b.dot(x))).normalize();
                    break;
                }
            }
            if (x == null) {
                return null;
            }
            Vector3f z = new Vector3f(x).cross(y).normalize();
            Matrix4f partFromLocal = new Matrix4f()
                    .m00(x.x).m01(x.y).m02(x.z)
                    .m10(y.x).m11(y.y).m12(y.z)
                    .m20(z.x).m21(z.y).m22(z.z);
            Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
            Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
            for (List<Vector3f> face : faces) {
                for (Vector3f vertex : face) {
                    Vector3f local = new Vector3f(vertex.dot(x), vertex.dot(y), vertex.dot(z));
                    min.min(local);
                    max.max(local);
                }
            }
            Matrix4f worldFromLocal = new Matrix4f(worldFromPart).mul(partFromLocal);
            if (max.x - min.x <= 1.0E-6F || max.y - min.y <= 1.0E-6F
                    || max.z - min.z <= 1.0E-6F || !Float.isFinite(worldFromLocal.determinant())
                    || Math.abs(worldFromLocal.determinant()) < 1.0E-12F) {
                return null;
            }
            return new Box(worldFromLocal, min, max);
        }

        Penetration penetration(Vector3f worldPoint) {
            Vector3f point = localFromWorld.transformPosition(new Vector3f(worldPoint));
            if (point.x < min.x || point.x > max.x || point.y < min.y || point.y > max.y
                    || point.z < min.z || point.z > max.z) {
                return new Penetration(new Vector3f(), -1.0F);
            }
            float best = Float.POSITIVE_INFINITY;
            Vector3f normal = new Vector3f();
            for (int axis = 0; axis < 3; axis++) {
                float lower = (point.get(axis) - min.get(axis)) / normalLengths[axis];
                float upper = (max.get(axis) - point.get(axis)) / normalLengths[axis];
                if (lower < best) {
                    best = lower;
                    normal.set(normals[axis]).negate();
                }
                if (upper < best) {
                    best = upper;
                    normal.set(normals[axis]);
                }
            }
            return new Penetration(normal, best);
        }
    }
}
