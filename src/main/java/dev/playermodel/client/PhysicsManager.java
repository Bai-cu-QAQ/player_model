package dev.playermodel.client;

import dev.playermodel.model.PhysicsConfig;
import dev.playermodel.runtime.CustomModel;
import dev.playermodel.runtime.ContactSpring;
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
            boneState.simulate(bone, parent, localAccel, speed, dt);
            boneState.resolveContacts(model, bone, parent, dt);
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
        final ContactSpring contact = new ContactSpring();
        float contactHold;
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
            float blend = 1.0F - (float) Math.exp(-response * dt * 10.0F);
            pitch += (targetPitch - pitch) * blend;
            yaw += (targetYaw - yaw) * blend;
            roll += (targetRoll - roll) * blend;
            applyAngles(bone);
            lastParent.set(parent);
        }

        private void applyAngles(CustomModel.PhysicsBoneRuntime bone) {
            if (bone.staticRotation() == null) {
                bone.part().pitch = (float) Math.toRadians(pitch) + contact.position.x;
                bone.part().yaw = (float) Math.toRadians(yaw) + contact.position.y;
                bone.part().roll = (float) Math.toRadians(roll) + contact.position.z;
            } else {
                Quaternionf offset = new Quaternionf().rotationZYX((float) Math.toRadians(roll) + contact.position.z,
                        (float) Math.toRadians(yaw) + contact.position.y, (float) Math.toRadians(pitch) + contact.position.x);
                Quaternionf composed = new Quaternionf(bone.staticRotation()).mul(offset);
                Vector3f euler = composed.getEulerAnglesZYX(new Vector3f());
                bone.part().pitch = euler.x;
                bone.part().yaw = euler.y;
                bone.part().roll = euler.z;
            }
        }

        /**
         * Compliant angular contact with persistent state and damped release. Contact normals
         * determine the response; no rest-angle snap or per-frame impulse reset is used.
         */
        private void resolveContacts(CustomModel model, CustomModel.PhysicsBoneRuntime bone,
                                     Quaternionf parent, float dt) {
            // A persistent offset survives the next motion update. The old solver discarded its
            // impulse every frame, causing a spring/contact tug-of-war and visible chatter.
            Vector3f target = new Vector3f(contact.position);
            List<Vector3f> rest = model.restPoints(bone);
            List<CustomModel.Contact> contacts = model.contacts(bone, rest);
            if (contacts.isEmpty()) {
                contactHold = Math.max(0.0F, contactHold - dt);
                if (contactHold == 0.0F) target.zero();
            } else {
                contactHold = 0.08F;
                Vector3f correction = new Vector3f();
                Vector3f pivot = model.physicsPivot(bone);
                Quaternionf frame = new Quaternionf(parent);
                if (bone.staticRotation() != null) frame.mul(bone.staticRotation());
                frame.invert();
                for (CustomModel.Contact c : contacts) {
                    Vector3f lever = new Vector3f(c.point()).sub(pivot);
                    Vector3f delta = ContactSpring.correction(lever, c.normal(), c.depth());
                    frame.transform(delta);
                    correction.add(delta);
                }
                // Smooth bounded correction, not an assigned collision angle or a rest-pose snap.
                correction.div(Math.max(1, contacts.size()));
                float length = correction.length();
                if (length > 0.12F) correction.mul(0.12F / length);
                target.add(correction);
            }
            contact.advance(target, Math.min(dt, 0.05F), contacts.isEmpty() ? 14.0F : 35.0F);
            applyAngles(bone);
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
