package dev.playermodel.client;

import dev.playermodel.compile.ModelCompiler;
import dev.playermodel.importing.BbmodelParser;
import dev.playermodel.model.NormalizedModel;
import dev.playermodel.model.RawModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Folder based import/validation/template handling. */
public final class ClientModelService {
    private ClientModelService() {
    }

    /**
     * Copy a .bbmodel into config/playermodel/models/&lt;name&gt;/model.bbmodel and extract its
     * embedded texture as texture.png so the folder can be edited by hand.
     */
    public static ImportResult importFile(Path file) throws Exception {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("文件不存在: " + file);
        }
        String fileName = file.getFileName().toString();
        String baseName = fileName.toLowerCase().endsWith(".bbmodel")
                ? fileName.substring(0, fileName.length() - ".bbmodel".length())
                : fileName;
        String displayName = baseName.isBlank() ? "model" : baseName;
        RawModel raw = BbmodelParser.parse(file, displayName);
        NormalizedModel normalized = ModelCompiler.compile(raw, "model", "model");
        String safeName = uniqueName(displayName);

        Path dir = ModelRegistry.modelsDir().resolve(safeName);
        Files.createDirectories(dir);
        Files.copy(file, dir.resolve("model.bbmodel"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        if (raw.textures.size() <= 1) {
            byte[] png = normalized.texturePng != null ? normalized.texturePng : raw.texture.png();
            Files.write(dir.resolve("texture.png"), png);
        }
        writeMeta(dir, displayName, "model", "model", normalized.textureWidth, normalized.textureHeight,
                normalized.slim);

        PlayerModelClient.registry().scanLocal();
        LocalModel local = PlayerModelClient.registry().localByName(safeName);
        if (local == null) {
            throw new IllegalStateException("导入后重新扫描失败，请查看日志");
        }
        return new ImportResult(local, normalized, raw.warnings);
    }

    public static NormalizedModel validate(Path file) throws Exception {
        RawModel raw = BbmodelParser.parse(file, file.getFileName().toString());
        NormalizedModel normalized = ModelCompiler.compile(raw, "model", "model");
        new dev.playermodel.runtime.PreparedModel(normalized).cuboids(true, java.util.Map.of());
        return normalized;
    }

    /** Writes template folders so they can be edited directly in Blockbench. */
    public static Path extractTemplates() throws IOException {
        Path templates = ModelRegistry.modelsDir();
        Files.createDirectories(templates);
        byte[] texture = readResource("template_texture.png");
        writeTemplate(templates, "template", "template.bbmodel", texture, "model", "原版比例模板", false);
        writeTemplate(templates, "template_slim", "template_slim.bbmodel", texture, "model", "细手臂模板", true);
        writeTemplate(templates, "template_small", "template_small.bbmodel", texture, "model", "小体型模板", false);
        writeTemplate(templates, "example_small", "example_small.bbmodel", texture, "model", "示例小体型模型", false);
        return templates;
    }

    private static void writeTemplate(Path root, String folder, String resource, byte[] texture,
                                      String pivotMode, String displayName, boolean slim) throws IOException {
        Path dir = root.resolve(folder);
        Files.createDirectories(dir);
        byte[] model = readResource(resource);
        if (model == null) {
            throw new IOException("缺少内置资源 " + resource);
        }
        Files.write(dir.resolve("model.bbmodel"), model);
        if (texture != null) {
            Files.write(dir.resolve("texture.png"), texture);
        }
        writeMeta(dir, displayName, "model", pivotMode, 64, 64, slim);
    }

    private static byte[] readResource(String name) throws IOException {
        try (var in = ClientModelService.class.getResourceAsStream("/assets/playermodel/" + name)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    private static void writeMeta(Path dir, String displayName, String textureMode, String pivotMode,
                                  int width, int height, boolean slim) throws IOException {
        ModelRegistry.Meta meta = new ModelRegistry.Meta();
        meta.displayName = displayName;
        meta.textureMode = textureMode;
        meta.pivotMode = pivotMode;
        meta.customAnchors = false;
        meta.slim = slim;
        meta.doubleLayer = true;
        meta.textureWidth = width;
        meta.textureHeight = height;
        Files.writeString(dir.resolve("meta.json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(meta),
                StandardCharsets.UTF_8);
    }

    private static String uniqueName(String base) {
        String safe = ModelRegistry.sanitize(base);
        String name = safe;
        int i = 2;
        while (Files.isDirectory(ModelRegistry.modelsDir().resolve(name))) {
            name = safe + "_" + i++;
        }
        return name;
    }

    public record ImportResult(LocalModel model, NormalizedModel normalized, List<String> warnings) {
    }
}
