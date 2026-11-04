package dev.playermodel.runtime;

import net.minecraft.client.model.ModelPart;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FaceSnapshotTransformTest {
    @Test
    void faceWrapperReproducesRootAndHeadTransforms() {
        ModelPart root = new ModelPart(List.of(), Map.of());
        root.setOrigin(0, 24, 0);
        root.yaw = 0.25F;

        ModelPart head = new ModelPart(List.of(), Map.of());
        head.setOrigin(0, -24, 0);
        head.pitch = 0.5F;
        head.zScale = 1.5F;

        ModelPart face = new ModelPart(List.of(), Map.of());
        face.setOrigin(1, 2, 3);
        face.yaw = 0.1F;

        ModelPart wrapped = CustomModel.wrapTransform(root, CustomModel.wrapTransform(head, face));

        assertEquals(0.25F, wrapped.yaw, 1e-6, "outermost wrapper must carry the model root rotation");
        assertEquals(24, wrapped.originY, 1e-6);
        ModelPart headWrapper = wrapped.children.get("pm_face_child");
        assertEquals(0.5F, headWrapper.pitch, 1e-6, "inner wrapper must carry head rotation");
        assertEquals(-24, headWrapper.originY, 1e-6);
        assertEquals(1.5F, headWrapper.zScale, 1e-6);
        ModelPart faceNode = headWrapper.children.get("pm_face_child");
        assertEquals(1, faceNode.originX, 1e-6);
        assertEquals(0.1F, faceNode.yaw, 1e-6);
    }
}