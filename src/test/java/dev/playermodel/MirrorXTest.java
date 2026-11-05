package dev.playermodel;

import dev.playermodel.compile.ModelCompiler;
import dev.playermodel.importing.BbmodelParser;
import dev.playermodel.model.RawModel;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MirrorXTest {
    @Test
    void mirrorFlagIsCarriedIntoNormalizedModel() throws Exception {
        RawModel raw = BbmodelParser.parse(Path.of("src/test/resources/player_vanilla.bbmodel"), "mirror");
        assertFalse(ModelCompiler.compile(raw, "model", "model").mirrorX);
        raw.mirrorX = true;
        assertTrue(ModelCompiler.compile(raw, "model", "model").mirrorX);
    }
}