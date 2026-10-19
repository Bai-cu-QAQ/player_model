package dev.playermodel.model;

/** A texture embedded in a .bbmodel file. */
public record RawTexture(String name, byte[] png, int width, int height, int uvWidth, int uvHeight) {
}
