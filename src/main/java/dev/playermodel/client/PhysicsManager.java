package dev.playermodel.client;

import dev.playermodel.model.PhysicsConfig;
import dev.playermodel.runtime.CustomModel;
import dev.playermodel.util.AngularMotion;
import dev.playermodel.util.SecondaryForces;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/** Secondary motion integrated once in real time, independently of render poses and static axes. */
public final class PhysicsManager {
    private final Map<UUID, PlayerState> players = new HashMap<>();

    public void update(AbstractClientPlayerEntity player, CustomModel model, long now) {
        PlayerState state = players.computeIfAbsent(player.getUuid(), key -> new PlayerState());
        Vec3d position = player.getEntityPos();
        boolean screen = MinecraftClient.getInstance().currentScreen != null
                && player == MinecraftClient.getInstance().player;
        if (state.model != model || state.position == null
                || position.squaredDistanceTo(state.position) > 16 || now - state.time > 500_000_000L) {
            state.reset(model, position, now);
        }
        float elapsed = (now - state.time) / 1_000_000_000F;
        state.time = now;
        if (elapsed <= 0) return;
        float dt = Math.min(elapsed, .05F);

        PhysicsConfig.Settings global = model.physicsBones().isEmpty()
                ? new PhysicsConfig.Settings() : model.physicsBones().get(0).settings();

        // Estimate translation over a tick-sized window, not a second derivative of render deltas.
        if (now - state.sampleTime >= 50_000_000L) {
            float interval = (now - state.sampleTime) / 1_000_000_000F;
            Vec3d v = position.subtract(state.samplePosition).multiply(1.0 / interval);
            Vector3f next = new Vector3f((float) v.x, (float) -v.y, (float) -v.z);
            state.acceleration.set(next).sub(state.sampleVelocity).div(interval);
            cap(state.acceleration, global.maxAcceleration(30));
            state.sampleVelocity.set(next);
            state.sampleTime = now;
            state.samplePosition = position;
        }
        state.position = position;
        state.velocity.lerp(state.sampleVelocity, blend(global.speedSmoothing(10), dt));
        state.smoothAcceleration.lerp(state.acceleration, blend(global.accelerationSmoothing(8), dt));
        // Minecraft model coordinates have +Y down; body yaw rotates the model outside ModelPart.
        Quaternionf body = new Quaternionf().rotationY((float) Math.toRadians(player.getBodyYaw()));
        Vector3f speed = new Quaternionf(body).invert().transform(new Vector3f(state.velocity));
        Vector3f accel = new Quaternionf(body).invert().transform(new Vector3f(state.smoothAcceleration));
        cap(speed, global.maxSpeed(12.5F));

        List<CustomModel.PhysicsBoneRuntime> ordered = new ArrayList<>();
        Map<String, CustomModel.PhysicsBoneRuntime> byName = new HashMap<>();
        for (var bone : model.physicsBones()) byName.put(key(bone.name()), bone);
        Set<String> visited = new HashSet<>();
        for (var bone : model.physicsBones()) order(bone, byName, visited, ordered);

        // Build animation-only frames BEFORE simulating any bone. A child must never differentiate
        // its parent's secondary motion as a new animation force (positive feedback).
        Map<String, Quaternionf> frames = new HashMap<>();
        for (var bone : ordered) {
            Quaternionf parent = bone.parentPhysics() == null ? model.worldRotation(bone.parent())
                    : frames.get(key(bone.parentPhysics()));
            if (parent == null) parent = model.worldRotation(bone.parent());
            Quaternionf frame = new Quaternionf(parent);
            if (bone.staticRotation() != null) frame.mul(bone.staticRotation());
            frames.put(key(bone.name()), frame);
        }
        for (var bone : ordered) {
            String id = key(bone.name());
            BoneState b = state.bones.computeIfAbsent(id, unused -> new BoneState());
            PhysicsConfig.Settings settings = bone.settings();
            Quaternionf frame = frames.get(id);
            Quaternionf animation = new Quaternionf(body).mul(frame);
            boolean suppressRotation = b.lastFrame == null || screen || state.screen;
            Vector3f worldOmega = suppressRotation ? new Vector3f()
                    : AngularMotion.angularVelocity(b.lastFrame, animation, elapsed);
            b.lastFrame = new Quaternionf(animation);
            cap(worldOmega, settings.maxAnimationSpeed(6));
            Vector3f worldAlpha = suppressRotation ? new Vector3f()
                    : new Vector3f(worldOmega).sub(b.lastOmega).div(elapsed);
            b.lastOmega.set(worldOmega);
            cap(worldAlpha, settings.maxAngularAcceleration(40));
            float angularBlend = blend(settings.forceSmoothing(10), dt);
            if (suppressRotation) { b.smoothOmega.zero(); b.smoothAlpha.zero(); }
            else {
                b.smoothOmega.lerp(worldOmega, angularBlend);
                b.smoothAlpha.lerp(worldAlpha, angularBlend);
            }
            Quaternionf inverseBody = new Quaternionf(body).invert();
            Vector3f omega = inverseBody.transform(new Vector3f(b.smoothOmega));
            Vector3f angularAcceleration = inverseBody.transform(new Vector3f(b.smoothAlpha));

            boolean cloth = "cloth".equalsIgnoreCase(settings.mode);
            float motionScale = settings.motionStrength(1) * legacy(settings.speedGainScale, 1);
            if (cloth) motionScale *= legacy(settings.clothSpeedScale, .8F);
            float animationScale = settings.angularInertia(.7F) * legacy(settings.animationForce, 1);
            Vector3f desired = SecondaryForces.rotation(omega, angularAcceleration,
                    settings.animationGain(.045F) * animationScale,
                    settings.angularAccelerationGain(.004F) * animationScale,
                    settings.viewYawScale(1), settings.viewPitchScale(1));
            desired.y += legacy(settings.wind, 0) * .01F;
            Vector3f lever = b.lever;
            if (lever == null) {
                lever = model.physicsRestLever(bone);
                b.lever = new Vector3f(lever);
            }
            Vector3f r = new Quaternionf(frame).transform(new Vector3f(lever));
            // Complete 3D torque: collinear vertical force remains zero for a vertical lever.
            // A tilted/offset cube reacts to jumping, falling and landing on its actual lever.
            desired.add(SecondaryForces.movement(r, speed, accel,
                    settings.motionGain(.065F) * motionScale,
                    settings.accelerationGain(.015F) * settings.accelerationStrength(.12F)
                            * legacy(settings.inertia, 1),
                    settings.verticalMotionScale(1), settings.verticalAccelerationScale(1)));
            desired.fma(settings.gravityGain(.04F) * legacy(settings.gravity, .8F),
                    SecondaryForces.torque(r, new Vector3f(0, 1, 0)));
            float maxAngleDegrees = Math.min(settings.maxAngleLimit(120), Math.max(1,
                    legacy(settings.maxAngle, 70) * (cloth ? legacy(settings.clothMaxAngleScale, 1.25F) : 1)));
            float maxAngle = (float) Math.toRadians(maxAngleDegrees);
            Vector3f axisMin = null;
            Vector3f axisMax = null;
            if (settings.hasAxisAngleRanges()) {
                float hardLimit = settings.maxAngleLimit(120);
                float legacyX = settings.maxAngleX(maxAngleDegrees);
                float legacyY = settings.maxAngleY(maxAngleDegrees);
                float legacyZ = settings.maxAngleZ(maxAngleDegrees);
                float minX = settings.angleXMin(-legacyX);
                float maxX = settings.angleXMax(legacyX);
                float minY = settings.angleYMin(-legacyY);
                float maxY = settings.angleYMax(legacyY);
                float minZ = settings.angleZMin(-legacyZ);
                float maxZ = settings.angleZMax(legacyZ);
                axisMin = new Vector3f(
                        (float) Math.toRadians(clamp(-hardLimit, hardLimit, Math.min(minX, maxX))),
                        (float) Math.toRadians(clamp(-hardLimit, hardLimit, Math.min(minY, maxY))),
                        (float) Math.toRadians(clamp(-hardLimit, hardLimit, Math.min(minZ, maxZ))));
                axisMax = new Vector3f(
                        (float) Math.toRadians(clamp(-hardLimit, hardLimit, Math.max(minX, maxX))),
                        (float) Math.toRadians(clamp(-hardLimit, hardLimit, Math.max(minY, maxY))),
                        (float) Math.toRadians(clamp(-hardLimit, hardLimit, Math.max(minZ, maxZ))));
                clampAxes(desired, axisMin, axisMax);
            } else {
                cap(desired, maxAngle);
            }
            b.filtered.lerp(desired, blend(settings.forceSmoothing(10), dt));
            if (axisMin != null) clampAxes(b.filtered, axisMin, axisMax);
            float response = settings.restResponse(8) * (cloth ? settings.clothResponseScale(.8F) : 1);
            float air = settings.airResistance(legacy(settings.airDrag, 1.5F));
            b.motion.advance(b.filtered, response, settings.swingDamping(.9F), air,
                    (float) Math.toRadians(settings.maxAngularSpeed(360)), maxAngle, axisMin, axisMax, dt,
                    Math.min(settings.maxSubsteps(32), Math.max(1, settings.substeps(8))));
        }
        // Convert the desired MODEL-space orientation back into each child's actual parent frame.
        // This compensates for inherited parent deflection rather than applying it again per link.
        Map<String, Quaternionf> simulatedFrames = new HashMap<>();
        for (var bone : ordered) {
            BoneState b = state.bones.get(key(bone.name()));
            Quaternionf restFrame = frames.get(key(bone.name()));
            Quaternionf offset = AngularMotion.localOffset(restFrame, b.motion.angle);
            Quaternionf desiredWorld;
            Quaternionf actualParent;
            if (bone.parentPhysics() == null) {
                actualParent = model.worldRotation(bone.parent());
                desiredWorld = new Quaternionf(restFrame).mul(offset);
            } else {
                String parentKey = key(bone.parentPhysics());
                Quaternionf parentRest = frames.get(parentKey);
                actualParent = simulatedFrames.get(parentKey);
                if (parentRest == null) parentRest = new Quaternionf();
                if (actualParent == null) actualParent = new Quaternionf(parentRest);
                // Keep the child's own physics offset, but inherit the parent's simulated
                // rotation. The old code built the child from animation-only rest, then divided
                // by the simulated parent, cancelling the child's visible physics.
                Quaternionf relativeRest = new Quaternionf(parentRest).invert().mul(restFrame).normalize();
                desiredWorld = new Quaternionf(actualParent).mul(relativeRest).mul(offset).normalize();
            }
            Quaternionf local = new Quaternionf(actualParent).invert().mul(desiredWorld).normalize();
            Vector3f euler = local.getEulerAnglesZYX(new Vector3f());
            bone.part().pitch = euler.x;
            bone.part().yaw = euler.y;
            bone.part().roll = euler.z;
            simulatedFrames.put(key(bone.name()), desiredWorld);
        }
        state.screen = screen;
    }

    private static void order(CustomModel.PhysicsBoneRuntime bone,
                              Map<String, CustomModel.PhysicsBoneRuntime> map,
                              Set<String> visited, List<CustomModel.PhysicsBoneRuntime> result) {
        if (!visited.add(key(bone.name()))) return;
        if (bone.parentPhysics() != null && map.containsKey(key(bone.parentPhysics())))
            order(map.get(key(bone.parentPhysics())), map, visited, result);
        result.add(bone);
    }
    private static String key(String name) { return name.toLowerCase(Locale.ROOT); }
    private static float blend(float rate, float dt) { return -(float) Math.expm1(-rate * dt); }
    private static float legacy(Float value, float fallback) {
        return value == null || !Float.isFinite(value) ? fallback : Math.max(0, value);
    }
    private static void clampAxes(Vector3f v, Vector3f min, Vector3f max) {
        v.set(clamp(min.x, max.x, v.x),
                clamp(min.y, max.y, v.y),
                clamp(min.z, max.z, v.z));
    }
    private static float clamp(float min, float max, float value) {
        return Math.max(min, Math.min(max, value));
    }
    private static void cap(Vector3f v, float length) {
        float size = v.length();
        if (size > length) v.mul(length / size);
    }
    public void reset(UUID uuid) { players.remove(uuid); }
    public void clear() { players.clear(); }
    private static final class BoneState {
        final AngularMotion motion = new AngularMotion();
        final Vector3f filtered = new Vector3f();
        Quaternionf lastFrame;
        final Vector3f lastOmega = new Vector3f();
        final Vector3f smoothOmega = new Vector3f();
        final Vector3f smoothAlpha = new Vector3f();
        Vector3f lever;
    }
    private static final class PlayerState {
        CustomModel model;
        Vec3d position, samplePosition;
        long time, sampleTime;
        boolean screen;
        final Vector3f sampleVelocity = new Vector3f(), velocity = new Vector3f();
        final Vector3f acceleration = new Vector3f(), smoothAcceleration = new Vector3f();
        final Map<String, BoneState> bones = new HashMap<>();
        void reset(CustomModel m, Vec3d pos, long now) {
            model = m; position = samplePosition = pos; time = sampleTime = now;
            sampleVelocity.zero(); velocity.zero(); acceleration.zero(); smoothAcceleration.zero();
            bones.clear(); screen = true;
        }
    }
}
