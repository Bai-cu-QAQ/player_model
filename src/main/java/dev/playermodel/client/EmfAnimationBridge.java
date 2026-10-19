package dev.playermodel.client;

import dev.playermodel.PlayerModelMod;
import net.minecraft.client.render.entity.model.EntityModel;

import java.lang.reflect.Method;

/**
 * EMF applies its CEM animations from EMFModelPartWithState.render, which we bypass when drawing
 * the custom model. Invoke the same root hooks (oneTimeRunnable + animate) before copying the pose.
 * Kept reflective so EMF stays a soft, optional dependency.
 */
public final class EmfAnimationBridge {
    private static boolean initialized;
    private static boolean available;
    private static Method getRoot;
    private static Method oneTimeRunnable;
    private static Method animate;
    private static boolean warned;

    private EmfAnimationBridge() {
    }

    public static void animate(EntityModel<?> model) {
        if (!init()) {
            return;
        }
        try {
            Object root = getRoot.invoke(model);
            if (root == null) {
                return;
            }
            oneTimeRunnable.invoke(root);
            animate.invoke(root);
        } catch (Throwable t) {
            available = false;
            if (!warned) {
                warned = true;
                PlayerModelMod.LOG.warn("EMF animation bridge disabled", t);
            }
        }
    }

    private static synchronized boolean init() {
        if (initialized) {
            return available;
        }
        initialized = true;
        try {
            Class<?> rootClass = Class.forName("traben.entity_model_features.models.parts.EMFModelPartRoot");
            getRoot = Class.forName("traben.entity_model_features.models.IEMFModel")
                    .getMethod("emf$getEMFRootModel");
            oneTimeRunnable = rootClass.getMethod("oneTimeRunnable");
            animate = rootClass.getMethod("animate");
            available = true;
            PlayerModelMod.LOG.info("EMF animation bridge initialized");
        } catch (ClassNotFoundException ignored) {
            available = false;
        } catch (Throwable t) {
            available = false;
            PlayerModelMod.LOG.warn("EMF animation bridge unavailable", t);
        }
        return available;
    }
}
