package dev.playermodel.model;

import java.util.Locale;

/** Cube face directions, matching Blockbench's face keys. */
public enum FaceDir {
    DOWN,
    UP,
    NORTH,
    SOUTH,
    WEST,
    EAST;

    public static FaceDir byKey(String key) {
        if (key == null) {
            return null;
        }
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "down", "bottom", "bottom_" -> DOWN;
            case "up", "top" -> UP;
            case "north", "front" -> NORTH;
            case "south", "back" -> SOUTH;
            case "west", "left" -> WEST;
            case "east", "right" -> EAST;
            default -> null;
        };
    }
}
