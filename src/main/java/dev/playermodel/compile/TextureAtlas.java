package dev.playermodel.compile;

import dev.playermodel.model.RawBone;
import dev.playermodel.model.RawElement;
import dev.playermodel.model.RawFace;
import dev.playermodel.model.RawModel;
import dev.playermodel.model.RawTexture;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/** Packs all textures referenced by a model into one atlas and maps face UVs into it. */
final class TextureAtlas {
    private static final int PADDING = 1;
    private static final int MAX_SIZE = 8192;

    private final Map<Integer, Placement> placements;
    private final int width;
    private final int height;
    private final byte[] png;
    private final int textureWidth;
    private final int textureHeight;

    private TextureAtlas(Map<Integer, Placement> placements, int width, int height, byte[] png,
                         int textureWidth, int textureHeight) {
        this.placements = placements;
        this.width = width;
        this.height = height;
        this.png = png;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
    }

    static TextureAtlas prepare(RawModel raw) throws ModelCompiler.CompileException {
        TreeSet<Integer> used = new TreeSet<>();
        for (RawBone bone : raw.bones) {
            for (RawElement element : bone.elements) {
                if (element.faces.isEmpty()) {
                    used.add(raw.selectedTextureIndex);
                } else {
                    for (RawFace face : element.faces.values()) {
                        used.add(face.textureIndex());
                    }
                }
            }
        }
        if (used.isEmpty()) {
            used.add(raw.selectedTextureIndex);
        }

        if (used.size() == 1) {
            RawTexture texture = raw.textureAt(used.first());
            if (texture == null) {
                throw new ModelCompiler.CompileException("模型缺少贴图");
            }
            return new TextureAtlas(Map.of(), 0, 0, texture.png(), texture.width(), texture.height());
        }

        Map<Integer, Placement> placements = new LinkedHashMap<>();
        int width = 0;
        int height = PADDING;
        for (int index : used) {
            RawTexture texture = raw.textureAt(index);
            if (texture == null) {
                throw new ModelCompiler.CompileException("模型引用了不存在的贴图 #" + index);
            }
            BufferedImage image = decode(texture);
            int w = image.getWidth();
            int h = image.getHeight();
            width = Math.max(width, w);
            placements.put(index, new Placement(image, PADDING, height, w, h));
            height += h + PADDING;
        }
        int atlasWidth = width + PADDING * 2;
        int atlasHeight = height;
        if (atlasWidth > MAX_SIZE || atlasHeight > MAX_SIZE) {
            throw new ModelCompiler.CompileException("合并后的贴图图集过大: " + atlasWidth + "x" + atlasHeight);
        }
        BufferedImage atlas = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = atlas.createGraphics();
        try {
            for (Placement placement : placements.values()) {
                graphics.drawImage(placement.image, placement.x, placement.y, null);
            }
        } finally {
            graphics.dispose();
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(atlas, "png", out);
            return new TextureAtlas(placements, atlasWidth, atlasHeight, out.toByteArray(), atlasWidth, atlasHeight);
        } catch (IOException e) {
            throw new ModelCompiler.CompileException("生成图集 PNG 失败: " + e.getMessage());
        }
    }

    boolean hasAtlas() {
        return !placements.isEmpty();
    }

    float mapU(int textureIndex, float u) {
        if (placements.isEmpty()) {
            return u;
        }
        Placement placement = placements.get(textureIndex);
        return placement == null ? u : (placement.x + u * placement.width) / width;
    }

    float mapV(int textureIndex, float v) {
        if (placements.isEmpty()) {
            return v;
        }
        Placement placement = placements.get(textureIndex);
        return placement == null ? v : (placement.y + v * placement.height) / height;
    }

    byte[] png() {
        return png;
    }

    int textureWidth() {
        return textureWidth;
    }

    int textureHeight() {
        return textureHeight;
    }

    private static BufferedImage decode(RawTexture texture) throws ModelCompiler.CompileException {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(texture.png()));
            if (image == null) {
                throw new ModelCompiler.CompileException("无法解码贴图 " + texture.name());
            }
            return image;
        } catch (IOException e) {
            throw new ModelCompiler.CompileException("读取贴图失败 " + texture.name() + ": " + e.getMessage());
        }
    }

    private record Placement(BufferedImage image, int x, int y, int width, int height) {
    }
}
