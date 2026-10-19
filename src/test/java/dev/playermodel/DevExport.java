package dev.playermodel;

import dev.playermodel.compile.ModelCompiler;
import dev.playermodel.importing.BbmodelParser;
import dev.playermodel.model.NormalizedModel;
import dev.playermodel.model.RawModel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Dev helper: install the bundled example as a folder model in the client run directory. */
public final class DevExport {
    public static void main(String[] args) throws Exception {
        String name = args.length > 0 ? args[0] : "template";
        Path source = Path.of("src/main/resources/assets/playermodel/" + name + ".bbmodel");
        Path dir = Path.of("run/client/config/playermodel/models/" + name);
        Files.createDirectories(dir);
        Files.copy(source, dir.resolve("model.bbmodel"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Path texture = Path.of("src/main/resources/assets/playermodel/template_texture.png");
        if (Files.isRegularFile(texture)) {
            Files.copy(texture, dir.resolve("texture.png"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        String meta = """
                {
                  "displayName": "%s",
                  "textureMode": "model",
                  "pivotMode": "model",
                  "customAnchors": false,
                  "doubleLayer": true,
                  "slim": %s,
                  "textureWidth": 64,
                  "textureHeight": 64
                }
                """.formatted(name, name.contains("slim"));
        Files.writeString(dir.resolve("meta.json"), meta, StandardCharsets.UTF_8);
        String config = """
                {
                  "viewEnabled": true,
                  "selfEnabled": true,
                  "activeHash": "",
                  "activeName": "%s",
                  "offlineApplyToAll": false,
                  "debug": true
                }
                """.formatted(name);
        Path configPath = Path.of("run/client/config/playermodel/config.json");
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, config, StandardCharsets.UTF_8);

        RawModel raw = BbmodelParser.parse(source, name);
        NormalizedModel model = ModelCompiler.compile(raw, "model", "model");
        System.out.println("installed folder " + dir + " bones=" + model.bones.size());
        System.out.println("anchors=" + model.anchors);
        System.out.println("rigScale=" + new dev.playermodel.runtime.PreparedModel(model).rigScale());
    }
}
