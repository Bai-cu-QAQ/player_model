package dev.playermodel.model;

import java.util.Map;

/** A parsed Blockbench cube before it is compiled into vanilla cuboids. */
public final class RawElement {
    public String uuid;
    public String name;
    public float[] from = {0, 0, 0};
    public float[] to = {0, 0, 0};
    public float[] origin = {0, 0, 0};
    public float[] rotation = {0, 0, 0};
    public float inflate;
    public boolean mirrorUv;
    public boolean boxUv;
    public float[] uvOffset = {0, 0};
    public Map<FaceDir, RawFace> faces = Map.of();
}
