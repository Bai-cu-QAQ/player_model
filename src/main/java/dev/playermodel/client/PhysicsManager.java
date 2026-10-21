package dev.playermodel.client;

import dev.playermodel.model.PhysicsConfig;
import dev.playermodel.runtime.CustomModel;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
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
            state.samplePos = pos;
            state.sampleNanos = nowNanos;
            return;
        }
        float dt = (nowNanos - last) / 1_000_000_000.0F;
        if (dt <= 0.0F) {
            return;
        }
        dt = Math.min(dt, MAX_DT);

        Vec3d previousPos = state.lastPos == null ? pos : state.lastPos;
        Vec3d rawVelocity = pos.subtract(previousPos).multiply(1.0 / dt);
        state.lastPos = pos;

        // Sample over 50 ms. Per-frame position delta is zero on most render frames because the
        // integrated server updates position at 20 Hz; a short window keeps a steady walking force.
        if (state.sampleNanos == 0L) {
            state.sampleNanos = nowNanos;
            state.samplePos = pos;
        } else if (nowNanos - state.sampleNanos >= 50_000_000L) {
            double seconds = (nowNanos - state.sampleNanos) / 1_000_000_000.0;
            Vec3d sampled = pos.subtract(state.samplePos).multiply(1.0 / seconds);
            Vector3f target = new Vector3f((float) (sampled.x * 0.1), (float) (sampled.y * 0.1),
                    (float) (sampled.z * 0.1));
            state.smoothedSpeed.lerp(target, 0.35F);
            float speedLength = state.smoothedSpeed.length();
            if (speedLength > 1.25F) {
                state.smoothedSpeed.normalize(1.25F);
            }
            state.samplePos = pos;
            state.sampleNanos = nowNanos;
        }
        Vec3d delta = rawVelocity.subtract(state.lastVelocity);
        state.lastVelocity = rawVelocity;
        Vector3f accel = new Vector3f((float) (delta.x * 0.5 / dt),
                (float) (delta.y * 0.5 / dt), (float) (delta.z * 0.5 / dt));
        if (accel.lengthSquared() > 900.0F) {
            accel.normalize(30.0F);
        }
        // Entity yaw is applied by the renderer outside the model. Convert world movement into the
        // entity model frame before applying part-local physics.
        float yaw = (float) Math.toRadians(player.getBodyYaw());
        float cos = (float) Math.cos(yaw);
        float sin = (float) Math.sin(yaw);
        Vector3f speed = new Vector3f(
                state.smoothedSpeed.x * cos + state.smoothedSpeed.z * sin,
                state.smoothedSpeed.y,
                -state.smoothedSpeed.x * sin + state.smoothedSpeed.z * cos);
        Vector3f localAccel = new Vector3f(
                accel.x * cos + accel.z * sin,
                accel.y,
                -accel.x * sin + accel.z * cos);
        model.beginPhysicsFrame();
        for (CustomModel.PhysicsBoneRuntime bone : model.physicsBones()) {
            BoneState boneState = state.bones.computeIfAbsent(bone.name().toLowerCase(java.util.Locale.ROOT),
                    key -> new BoneState());
            Quaternionf parent = model.worldRotation(bone.parent());
            // One closed-form step with 10x time gives the same target smoothing as the previous
            // twenty half-steps without running the per-bone simulation twenty times.
            // Integrate the motion at a stable fixed response rate. Collision is solved once after
            // the pose update so it cannot fight the same pose four times in one render frame.
            for (int step = 0; step < 4; step++) {
                boneState.simulate(bone, parent, localAccel, speed, dt * 2.5F, model, false);
            }
            boneState.resolveContacts(model, bone, parent, dt, 70.0F);
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
        Vec3d samplePos;
        long sampleNanos;
        long lastLogNanos;
        Vector3f smoothedSpeed = new Vector3f();
        final Map<String, BoneState> bones = new HashMap<>();

        void reset(CustomModel newModel) {
            this.model = newModel;
            this.lastNanos = 0L;
            this.lastVelocity = Vec3d.ZERO;
            this.lastPos = null;
            this.samplePos = null;
            this.sampleNanos = 0L;
            this.lastLogNanos = 0L;
            this.smoothedSpeed = new Vector3f();
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
                      Vector3f speed, float dt, CustomModel model, boolean applyCollision) {
            if (!initialized || lastParent == null) {
                lastParent = new Quaternionf(parent);
                initialized = true;
            }
            PhysicsConfig.Settings settings = bone.settings();
            boolean cloth = "cloth".equalsIgnoreCase(settings.mode);
            float stiffness = settings.stiffness(75.0F);
            float damping = settings.damping(9.0F);
            float gravity = settings.gravity(0.8F) * 20.0F;
            float inertia = settings.inertia(0.8F);
            float maxAngle = settings.maxAngle(70.0F);
            float wind = settings.wind(0.0F);
            if (cloth) {
                // Cloth chains use a softer, more damped spring at every segment. Each child also
                // inherits its parent's simulated rotation through ModelPart hierarchy.
                stiffness *= 0.65F;
                damping *= 0.9F;
                maxAngle *= 1.25F;
            }
            maxAngle = Math.min(150.0F, maxAngle);

            Vector3f omega = parentAngularVelocity(parent, lastParent, dt);
            omega.mul(57.29578F);
            Vector3f gravityLocal = new Vector3f(0.0F, -1.0F, 0.0F);
            new Quaternionf(parent).invert().transform(gravityLocal);
            Vector3f accelLocal = new Vector3f(accel);
            new Quaternionf(parent).invert().transform(accelLocal);
            Vector3f speedLocal = new Vector3f(speed);
            new Quaternionf(parent).invert().transform(speedLocal);

            float gain = 3000.0F / Math.max(5.0F, stiffness);
            float response = 8.0F + damping * 2.0F;
            if (cloth) {
                gain *= 0.8F;
                response *= 0.75F;
            }
            // Vertical motion has no horizontal component: rising keeps the cloth hanging down,
            // falling lifts it upward. Horizontal speed is only an additional trail force.
            float rise = speedLocal.y;
            float lift = rise < 0.0F ? Math.min(maxAngle, -rise * (maxAngle / 1.25F)) : 0.0F;
            float targetPitch = clamp(speedLocal.z * gain
                    - clamp(omega.x, -90.0F, 90.0F) * 0.6F
                    + gravity * gravityLocal.z / Math.max(5.0F, stiffness)
                    + lift, -maxAngle, maxAngle);
            float targetYaw = clamp(-clamp(omega.y, -90.0F, 90.0F) * 0.4F + wind, -maxAngle, maxAngle);
            float targetRoll = clamp(speedLocal.x * gain
                    - clamp(omega.z, -90.0F, 90.0F) * 0.6F
                    - gravity * gravityLocal.x / Math.max(5.0F, stiffness), -maxAngle, maxAngle);
            // 按骨骼关闭指定轴：只把该轴目标归零，其他轴和碰撞仍保留。
            if (settings.disablePitch(false)) {
                targetPitch = 0.0F;
            }
            if (settings.disableYaw(false)) {
                targetYaw = 0.0F;
            }
            if (settings.disableRoll(false)) {
                targetRoll = 0.0F;
            }
            float blend = 1.0F - (float) Math.exp(-response * dt);
            pitch += (targetPitch - pitch) * blend;
            yaw += (targetYaw - yaw) * blend;
            roll += (targetRoll - roll) * blend;
            vPitch = 0.0F;
            vYaw = 0.0F;
            vRoll = 0.0F;
            applyAngles(bone);
            if (applyCollision) {
                resolveContacts(model, bone, parent, dt, maxAngle);
            }
            lastParent.set(parent);
        }

        private void applyAngles(CustomModel.PhysicsBoneRuntime bone) {
            if (bone.staticRotation() == null) {
                bone.part().pitch = (float) Math.toRadians(pitch);
                bone.part().yaw = (float) Math.toRadians(yaw);
                bone.part().roll = (float) Math.toRadians(roll);
            } else {
                Quaternionf offset = new Quaternionf().rotationZYX((float) Math.toRadians(roll),
                        (float) Math.toRadians(yaw), (float) Math.toRadians(pitch));
                Quaternionf composed = new Quaternionf(bone.staticRotation()).mul(offset);
                Vector3f euler = composed.getEulerAnglesZYX(new Vector3f());
                bone.part().pitch = euler.x;
                bone.part().yaw = euler.y;
                bone.part().roll = euler.z;
            }
        }

        /**
         * Impulse-based collision: contacts create torque around the bone pivot, integrated as an
         * angular velocity. No target angle is forced or scaled down.
         */
        private void resolveContacts(CustomModel model, CustomModel.PhysicsBoneRuntime bone,
                                     Quaternionf parent, float dt, float maxAngle) {
            float collisionDt = Math.min(dt, 0.05F);
            List<Vector3f> restPoints = model.restPoints(bone);
            for (int iteration = 0; iteration < 2; iteration++) {
                List<CustomModel.Contact> contacts = model.contacts(bone, restPoints);
                if (contacts.isEmpty()) {
                    return;
                }
                Vector3f pivot = model.physicsPivot(bone);
                Quaternionf inverseParent = new Quaternionf(parent).invert();
                for (CustomModel.Contact contact : contacts) {
                    Vector3f lever = new Vector3f(contact.point()).sub(pivot);
                    Vector3f torque = lever.cross(contact.normal());
                    inverseParent.transform(torque);
                    float impulse = Math.min(0.25F, contact.depth()) * 1800.0F;
                    vPitch += torque.x * impulse;
                    vYaw += torque.y * impulse;
                    vRoll += torque.z * impulse;
                }
                vPitch = clamp(vPitch, -120.0F, 120.0F);
                vYaw = clamp(vYaw, -120.0F, 120.0F);
                vRoll = clamp(vRoll, -120.0F, 120.0F);
                pitch = clamp(pitch + vPitch * collisionDt, -maxAngle, maxAngle);
                yaw = clamp(yaw + vYaw * collisionDt, -maxAngle, maxAngle);
                roll = clamp(roll + vRoll * collisionDt, -maxAngle, maxAngle);
                applyAngles(bone);
                vPitch *= 0.35F;
                vYaw *= 0.35F;
                vRoll *= 0.35F;
            }
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
