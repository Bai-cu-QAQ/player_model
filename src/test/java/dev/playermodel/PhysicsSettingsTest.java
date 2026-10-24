package dev.playermodel;

import com.google.gson.Gson;
import dev.playermodel.model.PhysicsConfig;
import dev.playermodel.model.PhysicsConfig.Settings;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class PhysicsSettingsTest {
    private record Parameter(String name, float defaultValue, float min, float max) {
        void set(Settings settings, Float value) throws ReflectiveOperationException {
            Settings.class.getField(name).set(settings, value);
        }

        float get(Settings settings, float fallback) throws ReflectiveOperationException {
            return (float) Settings.class.getMethod(name, float.class).invoke(settings, fallback);
        }
    }

    private static final Parameter[] PARAMETERS = {
            new Parameter("motionStrength", 1.0F, 0.0F, 10.0F),
            new Parameter("accelerationStrength", 0.12F, 0.0F, 10.0F),
            new Parameter("restResponse", 8.0F, 0.1F, 40.0F),
            new Parameter("swingDamping", 0.9F, 0.1F, 4.0F),
            new Parameter("clothResponseScale", 0.8F, 0.1F, 2.0F),
            new Parameter("airResistance", 1.5F, 0.0F, 30.0F),
            new Parameter("maxAngularSpeed", 360.0F, 1.0F, 1440.0F),
            new Parameter("forceSmoothing", 10.0F, 0.1F, 60.0F),
            new Parameter("angularInertia", 0.7F, 0.0F, 2.0F),
            new Parameter("verticalMotionScale", 1F, 0F, 10F),
            new Parameter("verticalAccelerationScale", 1F, 0F, 10F),
            new Parameter("viewYawScale", 1F, 0F, 10F),
            new Parameter("viewPitchScale", 1F, 0F, 10F),
            new Parameter("angularAccelerationGain", .004F, 0F, 1F),
            new Parameter("maxAngularAcceleration", 40F, 0F, 300F)
    };

    @Test
    void absentValuesRemainNullableAndUseSanitizedFallbacks() throws Exception {
        Settings settings = new Settings();
        assertNull(settings.mode);
        for (Parameter parameter : PARAMETERS) {
            assertNull(Settings.class.getField(parameter.name()).get(settings), parameter.name());
            assertEquals(parameter.defaultValue(), parameter.get(settings, parameter.defaultValue()), parameter.name());
            assertEquals(parameter.min(), parameter.get(settings, -100.0F), parameter.name());
            assertEquals(parameter.max(), parameter.get(settings, Float.MAX_VALUE), parameter.name());
            assertEquals(parameter.defaultValue(), parameter.get(settings, Float.NaN), parameter.name());
            assertEquals(parameter.defaultValue(), parameter.get(settings, Float.POSITIVE_INFINITY), parameter.name());
            float customFallback = (parameter.min() + parameter.max()) * 0.5F;
            assertEquals(customFallback, parameter.get(settings, customFallback), parameter.name());
        }
    }

    @Test
    void gettersRejectNonFiniteValuesAndClampFiniteBounds() throws Exception {
        Settings settings = new Settings();
        for (Parameter parameter : PARAMETERS) {
            for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
                parameter.set(settings, invalid);
                assertEquals(parameter.defaultValue(), parameter.get(settings, parameter.defaultValue()), parameter.name());
                assertEquals(parameter.defaultValue(), parameter.get(settings, Float.NaN), parameter.name());
                assertEquals(parameter.min(), parameter.get(settings, -100.0F), parameter.name());
            }
            parameter.set(settings, -Float.MAX_VALUE);
            assertEquals(parameter.min(), parameter.get(settings, parameter.defaultValue()), parameter.name());
            parameter.set(settings, Float.MAX_VALUE);
            assertEquals(parameter.max(), parameter.get(settings, parameter.defaultValue()), parameter.name());
            parameter.set(settings, parameter.min());
            assertEquals(parameter.min(), parameter.get(settings, parameter.defaultValue()), parameter.name());
            parameter.set(settings, parameter.max());
            assertEquals(parameter.max(), parameter.get(settings, parameter.defaultValue()), parameter.name());
        }
    }

    @Test
    void copyAndApplyRetainEveryNullableCompatibilityAndNewField() throws Exception {
        Settings source = new Settings();
        source.mode = "cloth";
        // Exercise all fields, including legacy airDrag, without silently missing future additions.
        for (Field field : Settings.class.getFields()) {
            if (field.getType() == Float.class) field.set(source, 0.75F);
            if (field.getType() == Integer.class) field.set(source, 4);
        }
        Settings copy = source.copy();
        Settings applied = new Settings();
        applied.apply(source);
        assertNotSame(source, copy);
        for (Field field : Settings.class.getFields()) {
            assertEquals(field.get(source), field.get(copy), "copy " + field.getName());
            assertEquals(field.get(source), field.get(applied), "apply " + field.getName());
        }
        copy.motionStrength = 2.0F;
        assertEquals(Float.valueOf(0.75F), source.motionStrength);
        applied.apply(new Settings());
        applied.apply(null);
        for (Field field : Settings.class.getFields()) {
            assertEquals(field.get(source), field.get(applied), "null override " + field.getName());
        }
    }

    @Test
    void partialClothOverrideInheritsModeAndSupportsLegacyAirDrag() {
        PhysicsConfig config = new Gson().fromJson("""
                {
                  "defaultSettings": {
                    "mode": "cloth", "motionStrength": 1.4, "restResponse": 9,
                    "airResistance": 1.5, "airDrag": 2.0
                  },
                  "bones": {
                    "hair_physics": {"swingDamping": 0.7, "airDrag": 3.5},
                    "skirt_physics": {"airResistance": 2.5},
                    "rigid_physics": {"mode": "pendulum"}
                  }
                }
                """, PhysicsConfig.class);
        Settings hair = config.resolved("HAIR_PHYSICS");
        assertEquals("cloth", hair.mode);
        assertTrue(config.clothRequested("HAIR_PHYSICS"));
        assertTrue(config.clothRequested("skirt_physics"));
        assertTrue(config.clothRequested("missing_physics"));
        assertFalse(config.clothRequested("rigid_physics"));
        assertEquals(1.4F, hair.motionStrength(1.0F));
        assertEquals(9.0F, hair.restResponse(8.0F));
        assertEquals(0.7F, hair.swingDamping(0.9F));
        assertEquals(3.5F, hair.airDrag(2.0F));
        assertEquals(1.5F, hair.airResistance(1.5F));
        assertEquals(2.5F, config.resolved("skirt_physics").airResistance(1.5F));
        assertEquals(2.0F, config.resolved("skirt_physics").airDrag(0.0F));
        hair.mode = "pendulum";
        hair.motionStrength = 9.0F;
        assertTrue(config.clothRequested("hair_physics"));
        assertEquals(1.4F, config.defaultSettings.motionStrength(1.0F));
    }

    @Test
    void missingContainersResolveSafelyWithoutInventingModeOverrides() {
        PhysicsConfig config = new PhysicsConfig();
        assertNull(config.resolved(null).mode);
        assertFalse(config.clothRequested(null));
        config.defaultSettings = null;
        config.bones = null;
        Settings resolved = config.resolved("missing_physics");
        assertNull(resolved.mode);
        assertEquals(8.0F, resolved.restResponse(8.0F));
        assertFalse(config.clothRequested("missing_physics"));
        assertFalse(config.hasBoneOverride("missing_physics"));
    }
}
