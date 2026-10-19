package dev.playermodel.client;

import dev.playermodel.model.ModelBundle;
import dev.playermodel.model.NormalizedModel;
import dev.playermodel.runtime.PreparedModel;

import java.nio.file.Path;
import java.util.List;

/** One locally installed model, loaded from a folder under config/playermodel/models. */
public final class LocalModel {
    public final String name;
    public final String displayName;
    public final Path folder;
    public final NormalizedModel model;
    public final byte[] png;
    public final ModelBundle bundle;
    public final List<String> warnings;
    private PreparedModel prepared;

    LocalModel(String name, String displayName, Path folder, NormalizedModel model, byte[] png,
               ModelBundle bundle, List<String> warnings) {
        this.name = name;
        this.displayName = displayName;
        this.folder = folder;
        this.model = model;
        this.png = png;
        this.bundle = bundle;
        this.warnings = warnings;
    }

    public synchronized PreparedModel prepared() {
        if (prepared == null) {
            prepared = new PreparedModel(model);
        }
        return prepared;
    }
}
