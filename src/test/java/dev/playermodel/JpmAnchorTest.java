package dev.playermodel;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.playermodel.compile.ModelCompiler;
import dev.playermodel.importing.BbmodelParser;
import dev.playermodel.model.Anchor;
import dev.playermodel.model.BoneKind;
import dev.playermodel.model.NormalizedModel;
import dev.playermodel.model.RawModel;
import dev.playermodel.runtime.JpmAnchorTransform;
import dev.playermodel.runtime.JpmFaceFit;
import dev.playermodel.runtime.PreparedModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JpmAnchorTest {
    @TempDir
    Path temp;

    @Test
    void parsesJpmAnchorAndAppliesOffsetAndScale() throws Exception {
        Path source = Path.of("src/test/resources/player_vanilla.bbmodel");
        JsonObject root = JsonParser.parseString(Files.readString(source)).getAsJsonObject();
        JsonArray outliner = root.getAsJsonArray("outliner");
        JsonArray elements = root.getAsJsonArray("elements");
        JsonObject marker = elements.get(0).getAsJsonObject().deepCopy();
        String markerId = UUID.randomUUID().toString();
        marker.addProperty("name", "anchor_jpm_size");
        marker.addProperty("uuid", markerId);
        JsonArray from = new JsonArray();
        JsonArray to = new JsonArray();
        from.add(3); from.add(4); from.add(5);
        to.add(5); to.add(6); to.add(7);
        marker.add("from", from);
        marker.add("to", to);
        marker.add("origin", from.deepCopy());
        elements.add(marker);

        JsonObject group = new JsonObject();
        group.addProperty("uuid", UUID.randomUUID().toString());
        group.addProperty("name", "anchor_jpm");
        JsonArray origin = new JsonArray();
        origin.add(1); origin.add(2); origin.add(3);
        group.add("origin", origin);
        JsonArray rotation = new JsonArray();
        rotation.add(0); rotation.add(0); rotation.add(0);
        group.add("rotation", rotation);
        group.addProperty("visibility", true);
        group.addProperty("export", true);
        JsonArray children = new JsonArray();
        children.add(markerId);
        group.add("children", children);
        outliner.add(group);

        Path file = temp.resolve("anchored.bbmodel");
        Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
        RawModel raw = BbmodelParser.parse(file, "anchored");
        Anchor anchor = raw.anchors.stream().filter(a -> a.name().equals(Anchor.JPM)).findFirst().orElse(null);
        assertNotNull(anchor);
        assertEquals(BoneKind.HEAD, anchor.bone());
        assertEquals(0.5F, anchor.scale(), 1e-5F);
        assertEquals(1.0F, anchor.x(), 1e-5F);

        NormalizedModel normalized = ModelCompiler.compile(raw, "model", "model");
        normalized.faceJpm = """
                {"id":"face","textureSize":[64,64],"submodels":[{
                  "id":"face","boxes":[{"coordinates":[-1,-1,-1,2,2,2],"uvNorth":[0,0,2,2]}]
                }]}
                """;
        PreparedModel prepared = new PreparedModel(normalized);
        float[] offset = JpmAnchorTransform.offset(prepared, anchor, new float[]{0, 0, 0});
        assertEquals(1.0F, offset[0], 1e-5F);
        assertEquals(2.0F, offset[1], 1e-5F);
        assertEquals(3.0F, offset[2], 1e-5F);
        assertEquals(0.5F, JpmAnchorTransform.scale(prepared, anchor), 1e-5F);
    }

    @Test
    void fitsJpmFaceToSmallHead() throws Exception {
        RawModel raw = BbmodelParser.parse(Path.of("src/test/resources/player_small.bbmodel"), "small");
        PreparedModel prepared = new PreparedModel(ModelCompiler.compile(raw, "model", "model"));
        Path jpm = Path.of(".tools/JustExpressions/assets/minecraft/emf/cem/player_face.jpm");
        if (!Files.isRegularFile(jpm)) return;
        var face = dev.playermodel.jpm.JpmParser.parse(jpm);
        assertTrue(JpmFaceFit.scale(prepared, face) < 1.0F);
        assertTrue(JpmFaceFit.scale(prepared, face) > 0.2F);
    }

    @Test
    void generatedTemplateContainsJpmAnchor() throws Exception {
        byte[] source;
        try (var in = JpmAnchorTest.class.getResourceAsStream("/assets/playermodel/template.bbmodel")) {
            assertNotNull(in);
            source = in.readAllBytes();
        }
        Method inject = Class.forName("dev.playermodel.client.ClientModelService")
                .getDeclaredMethod("addJpmAnchor", byte[].class);
        inject.setAccessible(true);
        byte[] generated = (byte[]) inject.invoke(null, (Object) source);
        Path file = temp.resolve("template_with_jpm_anchor.bbmodel");
        Files.write(file, generated);
        RawModel raw = BbmodelParser.parse(file, "template");
        assertTrue(raw.anchors.stream().anyMatch(a -> a.name().equals(Anchor.JPM)));
    }
}
