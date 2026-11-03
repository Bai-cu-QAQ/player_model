package dev.playermodel;

import dev.playermodel.importing.BbmodelParser;
import dev.playermodel.importing.ImportException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class DiagnosticsTest {
    @TempDir
    Path temp;

    @Test
    void faceErrorNamesElementUuidFaceAndField() throws Exception {
        Path file = temp.resolve("bad_face.bbmodel");
        Files.writeString(file, """
                {"meta":{"model_format":"free"},"resolution":{"width":64,"height":64},
                 "elements":[{"name":"bad_cube","uuid":"cube-uuid","from":[0,0,0],"to":[8,8,8],
                 "faces":{"north":{"uv":[0,0,8,8],"rotation":45}}}]}
                """);
        ImportException error = expectImport(file);
        String message = error.getMessage();
        assertTrue(message.contains("elements[0]"), message);
        assertTrue(message.contains("bad_cube"), message);
        assertTrue(message.contains("cube-uuid"), message);
        assertTrue(message.contains("faces.north"), message);
        assertTrue(message.contains("field rotation"), message);
        assertTrue(message.startsWith(file.toString()), message);
    }

    @Test
    void malformedJsonReportsLineAndColumn() throws Exception {
        Path file = temp.resolve("broken.bbmodel");
        Files.writeString(file, "{\n  \"meta\": {\n    \"model_format\": \"free\",\n  }\n}\n");
        ImportException error = expectImport(file);
        String message = error.getMessage();
        assertTrue(message.contains("JSON 语法错误"), message);
        assertTrue(message.contains("第 4 行"), message);
        assertTrue(message.startsWith(file.toString()), message);
    }

    @Test
    void outlinerPointsToExactEntryAndMissingUuid() throws Exception {
        Path file = temp.resolve("bad_outliner.bbmodel");
        Files.writeString(file, """
                {"meta":{"model_format":"free"},"resolution":{"width":64,"height":64},
                 "elements":[{"name":"body_cube","uuid":"known","from":[0,0,0],"to":[8,8,8],
                 "faces":{"north":{"uv":[0,0,8,8]}}}],
                 "outliner":["missing-uuid"]}
                """);
        ImportException error = expectImport(file);
        String message = error.getMessage();
        assertTrue(message.contains("outliner[0]"), message);
        assertTrue(message.contains("missing-uuid"), message);
        assertTrue(message.contains("field uuid"), message);
    }

    @Test
    void hiddenAnchorGroupDoesNotOrphanItsMarkerCube() throws Exception {
        Path source = Path.of("src/test/resources/player_vanilla.bbmodel");
        JsonObject root = JsonParser.parseString(Files.readString(source)).getAsJsonObject();
        JsonObject marker = new JsonObject();
        marker.addProperty("name", "anchor_helmet_size");
        marker.addProperty("uuid", "hidden-marker");
        JsonArray from = new JsonArray();
        from.add(0); from.add(0); from.add(0);
        JsonArray to = new JsonArray();
        to.add(4); to.add(4); to.add(4);
        marker.add("from", from);
        marker.add("to", to);
        root.getAsJsonArray("elements").add(marker);

        JsonObject hidden = new JsonObject();
        hidden.addProperty("name", "anchor_helmet");
        hidden.addProperty("visibility", false);
        JsonArray children = new JsonArray();
        children.add("hidden-marker");
        hidden.add("children", children);
        root.getAsJsonArray("outliner").add(hidden);

        Path file = temp.resolve("hidden_anchor.bbmodel");
        Files.writeString(file, root.toString());
        var model = BbmodelParser.parse(file, "hidden-anchor");
        assertTrue(model.bones.size() >= 6, "normal player bones must still load");
        assertTrue(model.physicsBones.isEmpty(), "hidden anchor must not become a physics/root cube");
    }

    private ImportException expectImport(Path file) throws Exception {
        try {
            BbmodelParser.parse(file, "test");
            fail("expected import to fail");
            return null;
        } catch (ImportException expected) {
            return expected;
        }
    }
}
