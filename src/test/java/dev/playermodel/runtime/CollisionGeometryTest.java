package dev.playermodel.runtime;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CollisionGeometryTest {
    private static List<List<Vector3f>> cube() {
        Vector3f a = new Vector3f(-1, -1, -1), b = new Vector3f(1, -1, -1);
        Vector3f c = new Vector3f(1, 1, -1), d = new Vector3f(-1, 1, -1);
        Vector3f e = new Vector3f(-1, -1, 1), f = new Vector3f(1, -1, 1);
        Vector3f g = new Vector3f(1, 1, 1), h = new Vector3f(-1, 1, 1);
        return List.of(List.of(a, b, c, d), List.of(e, f, g, h),
                List.of(a, b, f, e), List.of(d, c, g, h),
                List.of(a, d, h, e), List.of(b, c, g, f));
    }

    @Test
    void samplesUniqueCornersEdgesAndFacesInStableOrder() {
        List<Vector3f> samples = CollisionGeometry.surfaceSamples(cube());
        assertEquals(26, samples.size());
        assertEquals(samples, CollisionGeometry.surfaceSamples(cube()));
        assertTrue(samples.contains(new Vector3f(0, 0, -1)));
        assertTrue(samples.contains(new Vector3f(0, -1, -1)));
        assertFalse(samples.contains(new Vector3f()));
    }

    @Test
    void faceCenterDetectsIntersectionWhenAllCornersMiss() {
        CollisionGeometry.Box box = CollisionGeometry.Box.fromFaces(cube(), new Matrix4f().scale(0.3F));
        List<List<Vector3f>> surface = List.of(List.of(new Vector3f(-2, -2, 0),
                new Vector3f(2, -2, 0), new Vector3f(2, 2, 0), new Vector3f(-2, 2, 0)));
        assertTrue(surface.getFirst().stream().allMatch(p -> box.penetration(p).depth() < 0));
        assertTrue(CollisionGeometry.surfaceSamples(surface).stream()
                .anyMatch(p -> box.penetration(p).depth() > 0));
    }

    @Test
    void rotationDoesNotCreateAggregateAabbFalsePositive() {
        Matrix4f transform = new Matrix4f().rotateZ((float) Math.PI / 4).scale(2, 0.2F, 1);
        CollisionGeometry.Box box = CollisionGeometry.Box.fromFaces(cube(), transform);
        assertTrue(box.penetration(new Vector3f(1, -1, 0)).depth() < 0);
        Vector3f point = transform.transformPosition(new Vector3f(0, 0.5F, 0));
        CollisionGeometry.Penetration hit = box.penetration(point);
        assertEquals(0.1F, hit.depth(), 1.0E-5F);
        assertEquals(-Math.sqrt(0.5), hit.normal().x, 1.0E-5);
        assertEquals(Math.sqrt(0.5), hit.normal().y, 1.0E-5);
        assertEquals(1, hit.normal().length(), 1.0E-5);
    }

    @Test
    void bakedElementRotationIsRecoveredFromActualFaces() {
        Matrix4f baked = new Matrix4f().translate(4, 2, -3).rotateY(0.7F).scale(2, 0.4F, 0.2F);
        List<List<Vector3f>> faces = cube().stream().map(face -> face.stream()
                .map(p -> baked.transformPosition(new Vector3f(p))).toList()).toList();
        CollisionGeometry.Box box = CollisionGeometry.Box.fromFaces(faces, new Matrix4f());
        assertNotNull(box);
        assertEquals(0.1F, box.penetration(baked.transformPosition(new Vector3f(0, 0, 0.5F))).depth(), 1.0E-5);
        assertTrue(box.penetration(baked.transformPosition(new Vector3f(0, 0, 1.1F))).depth() < 0);
    }

    @Test
    void independentCuboidsLeaveGapEmpty() {
        CollisionGeometry.Box left = CollisionGeometry.Box.fromFaces(cube(), new Matrix4f().translate(-3, 0, 0));
        CollisionGeometry.Box right = CollisionGeometry.Box.fromFaces(cube(), new Matrix4f().translate(3, 0, 0));
        assertTrue(left.penetration(new Vector3f()).depth() < 0);
        assertTrue(right.penetration(new Vector3f()).depth() < 0);
    }

    @Test
    void restDepthAndToleranceArePreserved() {
        assertEquals(0, CollisionGeometry.excessDepth(0.31F, 0.3F));
        assertEquals(0, CollisionGeometry.excessDepth(0.2F, 0.3F));
        assertEquals(0.1F, CollisionGeometry.excessDepth(0.4F, 0.3F), 1.0E-6F);
        assertEquals(0.1F, CollisionGeometry.excessDepth(0.1F, -1), 1.0E-6F);
        assertEquals(0, CollisionGeometry.excessDepth(0.01F, -1));
        assertEquals(0, CollisionGeometry.excessDepth(-1, -1));
    }

    @Test
    void staticRestRotationIsNotIdentityAndDoesNotMutateQuaternion() {
        org.joml.Quaternionf rotation = new org.joml.Quaternionf().rotationZ((float) Math.PI / 2);
        org.joml.Quaternionf original = new org.joml.Quaternionf(rotation);
        Matrix4f parent = new Matrix4f().translate(1, 0, 0);
        Matrix4f rest = CollisionGeometry.restTransform(parent, new Vector3f(2, 0, 0),
                rotation, new Vector3f(1));
        assertEquals(1, parent.m30(), 1.0E-5F);
        Vector3f point = rest.transformPosition(new Vector3f(2, 0, 0));
        assertEquals(3, point.x, 1.0E-5F);
        assertEquals(2, point.y, 1.0E-5F);
        assertEquals(original, rotation);
    }

    @Test
    void singularBodyTransformIsIgnored() {
        assertNull(CollisionGeometry.Box.fromFaces(cube(), new Matrix4f().scale(0, 1, 1)));
    }
}
