package dev.playermodel.client;

import dev.playermodel.model.PhysicsConfig;
import dev.playermodel.runtime.CustomModel;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side secondary motion for {@code _physics} bones. The parent core bones already contain
 * EMF/vanilla animation; this only adds a local spring/cloth offset on top of them.
 */
public final class PhysicsManager {
    private static final float MAX_DT = 0.05F;
    private final Map<UUID, PlayerState> players = new HashMap<>();

    public void update(AbstractClientPlayerEntity player, CustomModel model, long nowNanos) {
        PlayerState state = players.computeIfAbsent(player.getUuid(), key -> new PlayerState());
        if (state.model != model) {
            state.reset(model);
        }
        Vec3d pos = player.getPos();
        if (state.lastPos != null && pos.squaredDistanceTo(state.lastPos) > 16.0) {
            state.reset(model);
        }

        long last = state.lastNanos;
        state.lastNanos = nowNanos;
        if (last == 0L) {
            state.captureParents(model);
            state.lastVelocity = Vec3d.ZERO;
            state.lastPos = pos;
            return;
        }
        float dt = (nowNanos - last) / 1_000_000_000.0F;
        if (dt <= 0.0F) {
            return;
        }
        dt = Math.min(dt, MAX_DT);

        Vec3d previousPos = state.lastPos == null ? pos : state.lastPos;
        Vec3d velocity = pos.subtract(previousPos).multiply(1.0 / dt);
        state.lastPos = pos;
        Vec3d delta = velocity.subtract(state.lastVelocity);
        state.lastVelocity = velocity;
        Vector3f accel = new Vector3f((float) (delta.x * 0.5 / dt),
                (float) (delta.y * 0.5 / dt), (float) (delta.z * 0.5 / dt));
        if (accel.lengthSquared() > 900.0F) {
            accel.normalize(30.0F);
        }
        // 0.05 turns blocks/second into a reasonable spring offset while walking.
        Vector3f speed = new Vector3f((float) (velocity.x * 0.05), 0.0F,
                (float) (velocity.z * 0.05));

        for (CustomModel.PhysicsBoneRuntime bone : model.physicsBones()) {
            BoneState boneState = state.bones.computeIfAbsent(bone.name().toLowerCase(java.util.Locale.ROOT),
                    key -> new BoneState());
            Quaternionf parent = model.worldRotation(bone.parent());
            boneState.simulate(bone, parent, accel, speed, dt);
        }
    }

    public void reset(UUID uuid) {
        players.remove(uuid);
    }

    public void clear() {
        players.clear();
    }

    private static final class PlayerState {
        CustomModel model;
        long lastNanos;
        Vec3d lastVelocity = Vec3d.ZERO;
        Vec3d lastPos;
        final Map<String, BoneState> bones = new HashMap<>();

        void reset(CustomModel newModel) {
            this.model = newModel;
            this.lastNanos = 0L;
            this.lastVelocity = Vec3d.ZERO;
            this.lastPos = null;
            this.bones.clear();
        }

        void captureParents(CustomModel model) {
            for (CustomModel.PhysicsBoneRuntime bone : model.physicsBones()) {
                BoneState state = bones.computeIfAbsent(bone.name().toLowerCase(java.util.Locale.ROOT),
                        key -> new BoneState());
                state.lastParent = model.worldRotation(bone.parent());
                state.initialized = true;
            }
        }
    }

    private static final class BoneState {
        float pitch;
        float yaw;
        float roll;
        float vPitch;
        float vYaw;
        float vRoll;
        Quaternionf lastParent;
        boolean initialized;

        void simulate(CustomModel.PhysicsBoneRuntime bone, Quaternionf parent, Vector3f accel,
                      Vector3f speed, float dt) {
            if (!initialized || lastParent == null) {
                lastParent = new Quaternionf(parent);
                initialized = true;
            }
            PhysicsConfig.Settings settings = bone.settings();
            boolean cloth = "cloth".equalsIgnoreCase(settings.mode);
            float stiffness = settings.stiffness(90.0F);
            float damping = settings.damping(14.0F);
            float gravity = settings.gravity(0.6F);
            float inertia = settings.inertia(0.8F);
            float maxAngle = settings.maxAngle(55.0F);
            float wind = settings.wind(0.0F);
            if (cloth) {
                // Cloth chains use a softer, more damped spring at every segment. Each child also
                // inherits its parent's simulated rotation through ModelPart hierarchy.
                stiffness *= 0.42F;
                damping *= 0.78F;
                maxAngle *= 1.25F;
            }
            maxAngle = Math.min(150.0F, maxAngle);

            Vector3f omega = parentAngularVelocity(parent, lastParent, dt);
            Vector3f gravityLocal = new Vector3f(0.0F, -1.0F, 0.0F);
            new Quaternionf(parent).invert().transform(gravityLocal);
            Vector3f accelLocal = new Vector3f(accel);
            new Quaternionf(parent).invert().transform(accelLocal);
            Vector3f speedLocal = new Vector3f(speed);
            new Quaternionf(parent).invert().transform(speedLocal);

            float aPitch = -stiffness * pitch - damping * vPitch
                    + gravity * gravityLocal.z
                    - inertia * omega.x
                    - 2.5F * accelLocal.z
                    + 60.0F * speedLocal.z;
            float aYaw = -stiffness * yaw - damping * vYaw
                    - inertia * omega.y
                    + wind
                    + 1.5F * accelLocal.x
                    + 3.0F * speedLocal.x;
            float aRoll = -stiffness * roll - damping * vRoll
                    - gravity * gravityLocal.x
                    - inertia * omega.z
                    - 2.5F * accelLocal.x
                    + 60.0F * speedLocal.x;

            vPitch = clamp(vPitch + aPitch * dt, -18.0F, 18.0F);
            vYaw = clamp(vYaw + aYaw * dt, -18.0F, 18.0F);
            vRoll = clamp(vRoll + aRoll * dt, -18.0F, 18.0F);
            pitch = clamp(pitch + vPitch * dt, -maxAngle, maxAngle);
            yaw = clamp(yaw + vYaw * dt, -maxAngle, maxAngle);
            roll = clamp(roll + vRoll * dt, -maxAngle, maxAngle);
            if (Math.abs(vPitch) < 0.002F) {
                vPitch = 0.0F;
            }
            if (Math.abs(vYaw) < 0.002F) {
                vYaw = 0.0F;
            }
            if (Math.abs(vRoll) < 0.002F) {
                vRoll = 0.0F;
            }

            bone.part().pitch = (float) Math.toRadians(pitch);
            bone.part().yaw = (float) Math.toRadians(yaw);
            bone.part().roll = (float) Math.toRadians(roll);
            lastParent.set(parent);
        }

        private static Vector3f parentAngularVelocity(Quaternionf current, Quaternionf last, float dt) {
            Quaternionf delta = new Quaternionf(current).mul(new Quaternionf(last).invert());
            float w = clamp(delta.w, -1.0F, 1.0F);
            float angle = 2.0F * (float) Math.acos(w);
            float sin = (float) Math.sqrt(Math.max(0.0F, 1.0F - w * w));
            if (angle < 1.0E-4F || sin < 1.0E-5F) {
                return new Vector3f();
            }
            Vector3f axis = new Vector3f(delta.x / sin, delta.y / sin, delta.z / sin);
            new Quaternionf(current).invert().transform(axis);
            return axis.mul(angle / dt);
        }

        private static float clamp(float value, float min, float max) {
            return MathHelper.clamp(value, min, max);
        }
    }
}
