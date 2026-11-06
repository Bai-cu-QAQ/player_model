package dev.playermodel;

import dev.playermodel.model.Anchor;
import dev.playermodel.model.BoneKind;
import dev.playermodel.model.FaceDir;
import dev.playermodel.model.RawBone;
import dev.playermodel.model.RawElement;
import dev.playermodel.model.RawFace;
import dev.playermodel.model.RawModel;
import dev.playermodel.model.RawPhysicsBone;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RawModelXAxisTest {
    @Test
    void inversionIncludesGeometryPhysicsRotationsAndAnchors() {
        RawModel model = new RawModel();
        RawBone head = new RawBone(BoneKind.HEAD, new float[]{2, 3, 4}, new float[]{10, 20, 30});
        RawElement cube = cube();
        head.elements.add(cube);
        model.bones.add(head);
        RawPhysicsBone physics = new RawPhysicsBone("hair_physics", BoneKind.HEAD, null,
                new float[]{5, 6, 7}, new float[]{40, 50, 60});
        RawElement physicsCube = cube();
        physics.elements.add(physicsCube);
        model.physicsBones.add(physics);
        model.anchors.add(new Anchor(Anchor.JPM, BoneKind.HEAD, 8, 9, 10, 0.75F));

        model.invertXAxis();

        assertArrayEquals(new float[]{-2, 3, 4}, head.pivot);
        assertArrayEquals(new float[]{10, -20, -30}, head.rotation);
        assertArrayEquals(new float[]{-5, 6, 7}, physics.pivot);
        assertArrayEquals(new float[]{40, -50, -60}, physics.rotation);
        for (RawElement element : new RawElement[]{cube, physicsCube}) {
            assertArrayEquals(new float[]{-4, 2, 3}, element.from);
            assertArrayEquals(new float[]{-1, 5, 6}, element.to);
            assertArrayEquals(new float[]{-2, 3, 4}, element.origin);
            assertArrayEquals(new float[]{10, -20, -30}, element.rotation);
            assertEquals(new RawFace(8, 5, 6, 7, 0, 0), element.faces.get(FaceDir.EAST));
            assertEquals(new RawFace(3, 2, 1, 4, 0, 0), element.faces.get(FaceDir.WEST));
        }
        assertEquals(new Anchor(Anchor.JPM, BoneKind.HEAD, -8, 9, 10, 0.75F), model.anchors.get(0));
    }

    @Test
    void applyingInversionTwiceRestoresSourceData() {
        RawModel model = new RawModel();
        RawBone bone = new RawBone(BoneKind.LEFT_ARM, new float[]{2, 3, 4}, new float[]{10, 20, 30});
        RawElement element = cube();
        Map<FaceDir, RawFace> faces = element.faces;
        bone.elements.add(element);
        model.bones.add(bone);
        model.anchors.add(new Anchor(Anchor.LEFT_HAND, BoneKind.LEFT_ARM, 8, 9, 10, 1));

        model.invertXAxis();
        model.invertXAxis();

        assertArrayEquals(new float[]{2, 3, 4}, bone.pivot);
        assertArrayEquals(new float[]{10, 20, 30}, bone.rotation);
        assertArrayEquals(new float[]{1, 2, 3}, element.from);
        assertArrayEquals(new float[]{4, 5, 6}, element.to);
        assertEquals(faces, element.faces);
        assertEquals(8, model.anchors.get(0).x());
    }

    private static RawElement cube() {
        RawElement element = new RawElement();
        element.from = new float[]{1, 2, 3};
        element.to = new float[]{4, 5, 6};
        element.origin = new float[]{2, 3, 4};
        element.rotation = new float[]{10, 20, 30};
        element.faces = Map.of(FaceDir.EAST, new RawFace(1, 2, 3, 4, 0, 0),
                FaceDir.WEST, new RawFace(6, 5, 8, 7, 0, 0));
        return element;
    }
}
