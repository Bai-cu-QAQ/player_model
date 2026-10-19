package dev.playermodel.model;

import java.util.ArrayList;
import java.util.List;

/** A Blockbench outliner group. */
public final class RawGroup {
    public String name;
    public float[] origin = {0, 0, 0};
    public float[] rotation = {0, 0, 0};
    public float[] scale = {1, 1, 1};
    public float anchorScale;
    public boolean anchor;
    public List<RawElement> elements = new ArrayList<>();
    public List<RawGroup> children = new ArrayList<>();
}
