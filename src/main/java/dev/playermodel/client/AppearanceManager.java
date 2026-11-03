package dev.playermodel.client;

import dev.playermodel.PlayerModelMod;
import dev.playermodel.model.BoneKind;
import dev.playermodel.runtime.Appearance;
import dev.playermodel.runtime.BaseSkeleton;
import dev.playermodel.runtime.CustomModel;
import dev.playermodel.runtime.ModelParts;
import dev.playermodel.runtime.PreparedModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Chooses the model for each player. The live (vanilla/EMF) player model keeps animating; a separate
 * custom model copies its per-part transforms just before rendering, so CEM/EMF animation packs can
 * drive the imported geometry.
 */
public final class AppearanceManager {
    private final ClientConfig config;
    private final ModelRegistry registry;
    private final Map<UUID, Appearance> remote = new HashMap<>();
    private final Map<String, CustomModel> customModels = new HashMap<>();
    private final PhysicsManager physics = new PhysicsManager();
    private final java.util.Set<UUID> debugLogged = new java.util.HashSet<>();
    private boolean serverSyncAvailable;

    public AppearanceManager(ClientConfig config, ModelRegistry registry) {
        this.config = config;
        this.registry = registry;
    }

    public boolean viewEnabled() {
        return config.viewEnabled;
    }

    public void setViewEnabled(boolean enabled) {
        config.viewEnabled = enabled;
        config.save();
    }

    public boolean selfEnabled() {
        return config.selfEnabled;
    }

    public void setSelfEnabled(boolean enabled) {
        config.selfEnabled = enabled;
        config.save();
    }

    public synchronized void setServerSyncAvailable(boolean available) {
        this.serverSyncAvailable = available;
    }

    public boolean serverSyncAvailable() {
        return serverSyncAvailable;
    }

    public synchronized void setRemote(UUID uuid, Appearance appearance) {
        if (appearance == null) {
            remote.remove(uuid);
        } else {
            remote.put(uuid, appearance);
        }
    }

    public synchronized void clearRemote() {
        remote.clear();
    }

    public synchronized Map<UUID, Appearance> remoteSnapshot() {
        return new HashMap<>(remote);
    }

    public String activeHash() {
        return config.activeHash == null || config.activeHash.isBlank() ? null : config.activeHash;
    }

    public void setActive(String hash, String name) {
        config.activeHash = hash == null ? "" : hash;
        config.activeName = name == null ? "" : name;
        config.save();
    }

    public String hashFor(UUID uuid) {
        MinecraftClient client = MinecraftClient.getInstance();
        UUID self = client.player == null ? null : client.player.getUuid();
        if (self != null && self.equals(uuid)) {
            return config.selfEnabled ? activeHash() : null;
        }
        Appearance appearance = remote.get(uuid);
        if (appearance != null) {
            return appearance.enabled() ? appearance.hash() : null;
        }
        if (!serverSyncAvailable && config.offlineApplyToAll) {
            return activeHash();
        }
        return null;
    }

    public PreparedModel preparedFor(UUID uuid) {
        String hash = hashFor(uuid);
        return hash == null ? null : registry.prepared(hash);
    }

    /** Captures the live model's rest pivots before any animation; used to scale pose offsets. */
    public void captureBase(LivingEntity entity, PlayerEntityModel model) {
        if (!(entity instanceof AbstractClientPlayerEntity) || !config.viewEnabled) {
            return;
        }
        BaseSkeleton.capture(ModelParts.of(model));
    }

    /** State-local pose: never retain the cached, mutable CustomModel in a deferred command. */
    public record RenderPose(PlayerEntityModel model, PreparedModel prepared) {}

    private final ThreadLocal<java.util.List<RenderPose>> renderPoses =
            ThreadLocal.withInitial(java.util.ArrayList::new);

    public void pushPose(RenderPose pose) { renderPoses.get().add(pose); }
    public void popPose() {
        var poses = renderPoses.get();
        if (!poses.isEmpty()) poses.remove(poses.size() - 1);
        if (poses.isEmpty()) renderPoses.remove();
    }
    public RenderPose activePose() {
        var poses = renderPoses.get();
        return poses.isEmpty() ? null : poses.get(poses.size() - 1);
    }

    public interface PoseState {
        RenderPose playermodel$getPose();
        void playermodel$setPose(RenderPose pose);
    }

    public static RenderPose pose(net.minecraft.client.render.entity.state.EntityRenderState state) {
        return state instanceof PoseState holder ? holder.playermodel$getPose() : null;
    }

    public RenderPose capturePose(PlayerEntityModel live,
                                  net.minecraft.client.render.entity.state.PlayerEntityRenderState state) {
        AbstractClientPlayerEntity player = PlayerModelClient.playerFor(state);
        if (player == null || !config.viewEnabled) return null;
        PreparedModel prepared = preparedFor(player.getUuid());
        if (prepared == null) return null;
        try {
            captureBase(player, live);
            live.setAngles(state);
            EmfAnimationBridge.animate(live);
            CustomModel custom = customModelFor(prepared);
            custom.copyPose(live, prepared);
            physics.update(player, custom, System.nanoTime());
            return new RenderPose(new PlayerEntityModel(copyPart(custom.model().getRootPart()), prepared.slim()), prepared);
        } catch (Throwable t) {
            if (config.debug) PlayerModelMod.LOG.warn("Failed to capture custom player pose", t);
            return null;
        }
    }

    /** Deep-copy the transform tree; immutable cuboid geometry can be shared. */
    public static ModelPart copyPart(ModelPart source) {
        Map<String, ModelPart> children = new HashMap<>();
        source.children.forEach((name, child) -> children.put(name, copyPart(child)));
        ModelPart copy = new ModelPart(java.util.List.copyOf(source.cuboids), children);
        copy.setTransform(source.getTransform());
        // getTransform() omits live scale in 1.21.11; copy it explicitly.
        copy.xScale = source.xScale;
        copy.yScale = source.yScale;
        copy.zScale = source.zScale;
        copy.setDefaultTransform(source.getDefaultTransform());
        copy.visible = source.visible;
        copy.hidden = source.hidden;
        return copy;
    }

    /** Queue playback calls setAngles again: this model deliberately keeps the captured pose. */
    public static EntityModel<net.minecraft.client.render.entity.state.EntityRenderState> frozenModel(ModelPart root) {
        return new EntityModel<net.minecraft.client.render.entity.state.EntityRenderState>(copyPart(root)) {
            @Override
            public void setAngles(net.minecraft.client.render.entity.state.EntityRenderState ignored) {}
        };
    }

    private CustomModel customModelFor(PreparedModel prepared) {
        String hash = prepared.data().displayName + "#" + System.identityHashCode(prepared);
        return customModels.computeIfAbsent(hash, key -> new CustomModel(prepared));
    }

    public CustomModel customModelFor(AbstractClientPlayerEntity player) {
        if (!config.viewEnabled) {
            return null;
        }
        PreparedModel prepared = preparedFor(player.getUuid());
        return prepared == null ? null : customModelFor(prepared);
    }

    public float featureScale(AbstractClientPlayerEntity player) {
        PreparedModel prepared = preparedFor(player.getUuid());
        return prepared == null ? 1.0F : prepared.rigScale();
    }

    /** Render layer for the base player body; null means use the renderer's normal layer. */
    public RenderLayer renderLayer(AbstractClientPlayerEntity player) {
        Identifier texture = textureFor(player);
        return texture == null ? null : net.minecraft.client.render.RenderLayers.entityTranslucent(texture);
    }

    /** Texture to use for the player model, or null to keep the vanilla skin. */
    public Identifier textureFor(AbstractClientPlayerEntity player) {
        if (!config.viewEnabled) {
            return null;
        }
        String hash = hashFor(player.getUuid());
        if (hash == null) {
            return null;
        }
        PreparedModel prepared = registry.prepared(hash);
        if (prepared == null || "skin".equalsIgnoreCase(prepared.textureMode())) {
            return null;
        }
        return registry.texture(hash);
    }

    public boolean hasCustomModel(AbstractClientPlayerEntity player) {
        if (!config.viewEnabled) {
            return false;
        }
        String hash = hashFor(player.getUuid());
        return hash != null && registry.prepared(hash) != null;
    }

    public boolean usesSkinTexture(AbstractClientPlayerEntity player) {
        String hash = hashFor(player.getUuid());
        if (hash == null) {
            return false;
        }
        PreparedModel prepared = registry.prepared(hash);
        return prepared != null && "skin".equalsIgnoreCase(prepared.textureMode());
    }

    public ClientConfig config() {
        return config;
    }

    public ModelRegistry registry() {
        return registry;
    }

    public String describeSelf() {
        String hash = activeHash();
        if (hash == null) {
            return "none";
        }
        return registry.nameForHash(hash) + " (" + hash.substring(0, 8) + ")";
    }

    public int cubeCount(String hash) {
        PreparedModel prepared = registry.prepared(hash);
        if (prepared == null) {
            return 0;
        }
        int count = 0;
        for (BoneKind kind : BoneKind.VALUES) {
            var bone = prepared.data().bone(kind);
            if (bone != null) {
                count += bone.cubes().size();
            }
        }
        return count;
    }
}
