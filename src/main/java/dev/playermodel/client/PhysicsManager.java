package dev.playermodel.client;

import dev.playermodel.model.PhysicsConfig;
import dev.playermodel.runtime.CustomModel;
import dev.playermodel.util.AngularMotion;
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
        Vec3d position = player.getPos();
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

        // Estimate translation over a tick-sized window, not a second derivative of render deltas.
        if (now - state.sampleTime >= 50_000_000L) {
            float interval = (now - state.sampleTime) / 1_000_000_000F;
            Vec3d v = position.subtract(state.samplePosition).multiply(1.0 / interval);
            Vector3f next = new Vector3f((float) v.x, (float) -v.y, (float) -v.z);
            state.acceleration.set(next).sub(state.sampleVelocity).div(interval);
            cap(state.acceleration, 30);
            state.sampleVelocity.set(next);
            state.sampleTime = now;
            state.samplePosition = position;
        }
        state.position = position;
        state.velocity.lerp(state.sampleVelocity, blend(10, dt));
        state.smoothAcceleration.lerp(state.acceleration, blend(8, dt));
        // Minecraft model coordinates have +Y down; body yaw rotates the model outside ModelPart.
        Quaternionf body = new Quaternionf().rotationY((float) Math.toRadians(player.getBodyYaw()));
        Vector3f speed = new Quaternionf(body).invert().transform(new Vector3f(state.velocity));
        Vector3f accel = new Quaternionf(body).invert().transform(new Vector3f(state.smoothAcceleration));
        cap(speed, 12.5F);

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
            Vector3f omega = b.lastFrame == null || screen || state.screen
                    ? new Vector3f() : AngularMotion.angularVelocity(b.lastFrame, animation, elapsed);
            b.lastFrame = new Quaternionf(animation);
            // Move the world-frame angular vector back to the body/model frame.
            new Quaternionf(body).invert().transform(omega);
            cap(omega, 6);

            boolean cloth = "cloth".equalsIgnoreCase(settings.mode);
            float motionScale = settings.motionStrength(1) * legacy(settings.speedGainScale, 1);
            if (cloth) motionScale *= legacy(settings.clothSpeedScale, .8F);
            Vector3f desired = new Vector3f(-speed.z, 0, speed.x).mul(.065F * motionScale);
            desired.add(new Vector3f(-accel.z, 0, accel.x)
                    .mul(.015F * settings.accelerationStrength(.12F) * legacy(settings.inertia, 1)));
            desired.fma(-.045F * settings.angularInertia(.7F) * legacy(settings.animationForce, 1), omega);
            desired.y += legacy(settings.wind, 0) * .01F;
            // Rest-space lever from actual cube centers; no double application of static rotation.
            Vector3f lever = b.lever;
            if (lever == null) {
                lever = model.physicsRestLever(bone);
                b.lever = new Vector3f(lever);
            }
            Vector3f r = new Quaternionf(frame).transform(new Vector3f(lever));
            float radius = r.length();
            if (radius > 1e-5F) {
                // +Y is down in model space. Gravity is not a hand-flipped pitch/roll coefficient.
                Vector3f gravityTorque = r.cross(new Vector3f(0, 1, 0));
                desired.fma(.04F * legacy(settings.gravity, .8F) / radius, gravityTorque);
            }
            float maxAngle = (float) Math.toRadians(Math.min(120, Math.max(1,
                    legacy(settings.maxAngle, 70) * (cloth ? legacy(settings.clothMaxAngleScale, 1.25F) : 1))));
            cap(desired, maxAngle);
            b.filtered.lerp(desired, blend(settings.forceSmoothing(10), dt));
            float response = settings.restResponse(8) * (cloth ? settings.clothResponseScale(.8F) : 1);
            float air = settings.airResistance(legacy(settings.airDrag, 1.5F));
            b.motion.advance(b.filtered, response, settings.swingDamping(.9F), air,
                    (float) Math.toRadians(settings.maxAngularSpeed(360)), maxAngle, dt,
                    Math.min(32, Math.max(1, settings.substeps(8))));
        }
        // Convert the desired MODEL-space orientation back into each child's actual parent frame.
        // This compensates for inherited parent deflection rather than applying it again per link.
        Map<String, Quaternionf> simulatedFrames = new HashMap<>();
        for (var bone : ordered) {
            BoneState b = state.bones.get(key(bone.name()));
            Quaternionf restFrame = frames.get(key(bone.name()));
            Quaternionf offset = AngularMotion.localOffset(restFrame, b.motion.angle);
            Quaternionf desiredWorld = new Quaternionf(restFrame).mul(offset);
            Quaternionf actualParent = bone.parentPhysics() == null ? model.worldRotation(bone.parent())
                    : simulatedFrames.get(key(bone.parentPhysics()));
            if (actualParent == null) actualParent = model.worldRotation(bone.parent());
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
