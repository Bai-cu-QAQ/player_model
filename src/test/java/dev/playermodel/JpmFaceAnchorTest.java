package dev.playermodel;

import dev.playermodel.compile.ModelCompiler;
import dev.playermodel.importing.BbmodelParser;
import dev.playermodel.model.Anchor;
import dev.playermodel.model.BoneKind;
import dev.playermodel.model.NormalizedModel;
import dev.playermodel.model.RawModel;
import dev.playermodel.runtime.JpmAnchorTransform;
import dev.playermodel.runtime.PreparedModel;
import net.minecraft.client.model.ModelPart;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JpmFaceAnchorTest {
    @Test
    void anchorMovesAndScalesFaceRelativeToHead() throws Exception {
        NormalizedModel model = compile();
        var head = model.bone(BoneKind.HEAD);
        float hx = head == null ? 0 : head.pivotX();
        float hy = head == null ? 0 : head.pivotY();
        float hz = head == null ? 0 : head.pivotZ();
        model.anchors = List.of(new Anchor(Anchor.JPM, BoneKind.HEAD,
                hx + 1, hy + 2, hz + 3, 0.75F));
        PreparedModel prepared = new PreparedModel(model);
        ModelPart face = new ModelPart(List.of(), Map.of());
        JpmAnchorTransform.apply(prepared, face);
        assertEquals(1, face.originX, 1e-6);
        assertEquals(2, face.originY, 1e-6);
        assertEquals(3, face.originZ, 1e-6);
        assertEquals(0.75F, face.xScale, 1e-6);
        assertEquals(0.75F, face.yScale, 1e-6);
        assertEquals(0.75F, face.zScale, 1e-6);
    }

    @Test
    void noAnchorKeepsIdentityTransform() throws Exception {
        NormalizedModel model = compile();
        model.anchors = List.of();
        PreparedModel prepared = new PreparedModel(model);
        ModelPart face = new ModelPart(List.of(), Map.of());
        JpmAnchorTransform.apply(prepared, face);
        assertEquals(0, face.originX + face.originY + face.originZ, 1e-6);
        assertEquals(1, face.xScale, 1e-6);
        assertEquals(1, face.yScale, 1e-6);
        assertEquals(1, face.zScale, 1e-6);
    }

    private static NormalizedModel compile() throws Exception {
        RawModel raw = BbmodelParser.parse(Path.of("src/test/resources/player_vanilla.bbmodel"), "anchor");
        return ModelCompiler.compile(raw, "model", "model");
    }
}