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
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
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
    public void captureBase(LivingEntity entity, PlayerEntityModel<?> model) {
        if (!(entity instanceof AbstractClientPlayerEntity) || !config.viewEnabled) {
            return;
        }
        BaseSkeleton.capture(ModelParts.of(model));
    }

    /**
     * Called from the LivingEntityRenderer model-render redirect. Returns true when the custom model
     * was rendered instead of the live model.
     */
    public boolean renderCustom(EntityModel<?> live, MatrixStack matrices, VertexConsumer vertices,
                                int light, int overlay, int color, LivingEntity entity) {
        if (!(entity instanceof AbstractClientPlayerEntity player) || !config.viewEnabled) {
            return false;
        }
        if (!(live instanceof PlayerEntityModel<?> playerModel)) {
            return false;
        }
        PreparedModel prepared = preparedFor(player.getUuid());
        if (prepared == null) {
            return false;
        }
        try {
            EmfAnimationBridge.animate(live);
            CustomModel custom = customModelFor(prepared);
            custom.copyPose(playerModel, prepared);
            custom.model().render(matrices, vertices, light, overlay, color);
            return true;
        } catch (Throwable t) {
            if (config.debug) {
                PlayerModelMod.LOG.warn("Failed to render custom player model", t);
            }
            return false;
        }
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
        return texture == null ? null : RenderLayer.getEntityTranslucentCull(texture);
    }

    /** Texture to use for the player model, or null to keep the vanilla skin. */
    public Identifier textureFor(AbstractClientPlayerEntity player) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (player == client.player && client.options.getPerspective().isFirstPerson()) {
            // First-person arm keeps the vanilla skin texture.
            return null;
        }
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
