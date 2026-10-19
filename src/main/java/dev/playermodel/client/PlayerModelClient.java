package dev.playermodel.client;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.playermodel.PlayerModelMod;
import dev.playermodel.runtime.TextureCache;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.command.CommandSource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class PlayerModelClient implements net.fabricmc.api.ClientModInitializer {
    private static ClientConfig config;
    private static ModelRegistry registry;
    private static AppearanceManager appearanceManager;
    private static KeyBinding toggleViewKey;
    private static boolean texturePrimed;
    private static dev.playermodel.runtime.CustomModel.Installed liveRestore;
    private static net.minecraft.client.network.AbstractClientPlayerEntity renderingPlayer;

    public static void setRenderingPlayer(net.minecraft.client.network.AbstractClientPlayerEntity player) {
        renderingPlayer = player;
    }

    public static net.minecraft.client.network.AbstractClientPlayerEntity renderingPlayer() {
        return renderingPlayer;
    }

    public static void setLiveRestore(dev.playermodel.runtime.CustomModel.Installed restore) {
        liveRestore = restore;
    }

    public static void restoreLive() {
        if (liveRestore != null) {
            liveRestore.restore();
            liveRestore = null;
        }
    }

    public static ClientConfig config() {
        ensureInit();
        return config;
    }

    public static ModelRegistry registry() {
        ensureInit();
        return registry;
    }

    public static AppearanceManager appearanceManager() {
        ensureInit();
        return appearanceManager;
    }

    private static synchronized void ensureInit() {
        if (registry != null) {
            return;
        }
        config = ClientConfig.load();
        registry = new ModelRegistry();
        appearanceManager = new AppearanceManager(config, registry);
        registry.scanLocal();
        resolveActive();
    }

    private static void resolveActive() {
        String hash = registry.resolveActiveHash(config.activeName, config.activeHash);
        if (!hash.equals(config.activeHash)) {
            config.activeHash = hash;
            if (!hash.isEmpty() && (config.activeName == null || config.activeName.isBlank())) {
                config.activeName = registry.nameForHash(hash);
            }
            config.save();
        }
        if (hash.isEmpty()) {
            config.activeName = "";
            config.activeHash = "";
            config.save();
        }
    }

    @Override
    public void onInitializeClient() {
        ensureInit();
        registerCommands();
        registerKeybind();
        PlayerModelClientNetworking.register();
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return Identifier.of(PlayerModelMod.MOD_ID, "reload");
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        TextureCache.clear();
                    }
                });
        PlayerModelMod.LOG.info("Player Model Importer client ready, {} folder model(s)", registry.namedModels().size());
    }

    private void registerKeybind() {
        toggleViewKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.playermodel.toggle_view", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F6, "category.playermodel"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!texturePrimed && client.getWindow() != null && !config.activeHash.isBlank()) {
                texturePrimed = true;
                var bundle = registry.bundle(config.activeHash);
                if (bundle != null) {
                    TextureCache.ensure(bundle.hash, bundle.png);
                }
            }
            while (toggleViewKey.wasPressed()) {
                boolean enabled = !appearanceManager.viewEnabled();
                appearanceManager.setViewEnabled(enabled);
                if (client.player != null) {
                    client.player.sendMessage(Text.translatable(enabled
                            ? "message.playermodel.view_on" : "message.playermodel.view_off"), true);
                }
            }
        });
    }

    private static final SuggestionProvider<FabricClientCommandSource> MODEL_NAMES =
            (context, builder) -> CommandSource.suggestMatching(registry().namedModels().keySet(), builder);

    private static final SuggestionProvider<FabricClientCommandSource> BOOL_VALUES =
            (context, builder) -> {
                builder.suggest("true");
                builder.suggest("false");
                return builder.buildFuture();
            };

    private static final SuggestionProvider<FabricClientCommandSource> BB_MODEL_PATHS =
            (context, builder) -> {
                List<String> suggestions = new ArrayList<>();
                try (Stream<Path> stream = Files.walk(ModelRegistry.modelsDir(), 2)) {
                    stream.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".bbmodel"))
                            .forEach(path -> {
                                String value = path.toAbsolutePath().toString();
                                suggestions.add(value.contains(" ") ? "\"" + value + "\"" : value);
                                suggestions.add(path.getFileName().toString());
                            });
                } catch (Exception ignored) {
                }
                for (String suggestion : suggestions) {
                    builder.suggest(suggestion);
                }
                return builder.buildFuture();
            };

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
                ClientCommandManager.literal("pmodel")
                        .then(ClientCommandManager.literal("import")
                                .then(ClientCommandManager.argument("path", StringArgumentType.greedyString())
                                        .suggests(BB_MODEL_PATHS)
                                        .executes(ctx -> importModel(ctx.getSource(), StringArgumentType.getString(ctx, "path")))))
                        .then(ClientCommandManager.literal("validate")
                                .then(ClientCommandManager.argument("path", StringArgumentType.greedyString())
                                        .suggests(BB_MODEL_PATHS)
                                        .executes(ctx -> validateModel(ctx.getSource(), StringArgumentType.getString(ctx, "path")))))
                        .then(ClientCommandManager.literal("use")
                                .then(ClientCommandManager.argument("model", StringArgumentType.greedyString())
                                        .suggests(MODEL_NAMES)
                                        .executes(ctx -> useModel(ctx.getSource(), StringArgumentType.getString(ctx, "model")))))
                        .then(ClientCommandManager.literal("self")
                                .then(ClientCommandManager.argument("enabled", BoolArgumentType.bool())
                                        .suggests(BOOL_VALUES)
                                        .executes(ctx -> setSelf(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled")))))
                        .then(ClientCommandManager.literal("view")
                                .then(ClientCommandManager.argument("enabled", BoolArgumentType.bool())
                                        .suggests(BOOL_VALUES)
                                        .executes(ctx -> setView(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled")))))
                        .then(ClientCommandManager.literal("list").executes(ctx -> list(ctx.getSource())))
                        .then(ClientCommandManager.literal("reload").executes(ctx -> reload(ctx.getSource())))
                        .then(ClientCommandManager.literal("status").executes(ctx -> status(ctx.getSource())))
                        .then(ClientCommandManager.literal("template").executes(ctx -> template(ctx.getSource())))
                        .then(ClientCommandManager.literal("folder").executes(ctx -> folder(ctx.getSource())))
        ));
    }

    private int importModel(FabricClientCommandSource source, String rawPath) {
        Path path = resolve(rawPath);
        try {
            ClientModelService.ImportResult result = ClientModelService.importFile(path);
            LocalModel model = result.model();
            appearanceManager.setActive(model.bundle.hash, model.name);
            appearanceManager.setSelfEnabled(true);
            TextureCache.ensure(model.bundle.hash, model.png);
            source.sendFeedback(Text.translatable("message.playermodel.imported", model.name,
                    model.bundle.shortHash(), model.model.bones.size()));
            source.sendFeedback(Text.literal("  目录: " + model.folder));
            for (String warning : result.warnings()) {
                source.sendFeedback(Text.literal("  ! " + warning));
            }
            PlayerModelClientNetworking.onLocalModelChanged();
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("导入失败: " + e.getMessage()));
            PlayerModelMod.LOG.warn("Import failed for {}", path, e);
            return 0;
        }
    }

    private int validateModel(FabricClientCommandSource source, String rawPath) {
        Path path = resolve(rawPath);
        try {
            var model = ClientModelService.validate(path);
            source.sendFeedback(Text.translatable("message.playermodel.valid", model.bones.size(),
                    model.textureWidth, model.textureHeight));
            for (String warning : model.warnings) {
                source.sendFeedback(Text.literal("  ! " + warning));
            }
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("校验失败: " + e.getMessage()));
            return 0;
        }
    }

    private int useModel(FabricClientCommandSource source, String nameOrHash) {
        String hash = registry.hashForName(nameOrHash.trim());
        if (hash == null) {
            source.sendError(Text.literal("找不到模型: " + nameOrHash + "（用 /pmodel list 查看）"));
            return 0;
        }
        String name = registry.nameForHash(hash);
        appearanceManager.setActive(hash, name);
        appearanceManager.setSelfEnabled(true);
        var bundle = registry.bundle(hash);
        if (bundle != null) {
            TextureCache.ensure(hash, bundle.png);
        }
        source.sendFeedback(Text.translatable("message.playermodel.using", name, hash.substring(0, 8)));
        PlayerModelClientNetworking.onLocalModelChanged();
        return 1;
    }

    private int setSelf(FabricClientCommandSource source, boolean enabled) {
        appearanceManager.setSelfEnabled(enabled);
        source.sendFeedback(Text.translatable(enabled
                ? "message.playermodel.self_on" : "message.playermodel.self_off"));
        PlayerModelClientNetworking.onLocalModelChanged();
        return 1;
    }

    private int setView(FabricClientCommandSource source, boolean enabled) {
        appearanceManager.setViewEnabled(enabled);
        source.sendFeedback(Text.translatable(enabled
                ? "message.playermodel.view_on" : "message.playermodel.view_off"));
        return 1;
    }

    private int list(FabricClientCommandSource source) {
        Map<String, String> models = registry.namedModels();
        if (models.isEmpty()) {
            source.sendFeedback(Text.translatable("message.playermodel.empty"));
        } else {
            source.sendFeedback(Text.translatable("message.playermodel.list_header", models.size()));
            models.forEach((name, hash) -> source.sendFeedback(Text.literal("  " + name + "  [" + hash.substring(0, 8) + "]")));
        }
        for (String error : registry.errors()) {
            source.sendFeedback(Text.literal("  ! 加载失败: " + error));
        }
        return 1;
    }

    private int reload(FabricClientCommandSource source) {
        registry.scanLocal();
        TextureCache.clear();
        resolveActive();
        texturePrimed = false;
        source.sendFeedback(Text.translatable("message.playermodel.reloaded", registry.namedModels().size()));
        for (String error : registry.errors()) {
            source.sendFeedback(Text.literal("  ! " + error));
        }
        PlayerModelClientNetworking.onLocalModelChanged();
        return 1;
    }

    private int status(FabricClientCommandSource source) {
        source.sendFeedback(Text.translatable("message.playermodel.status",
                appearanceManager.describeSelf(),
                appearanceManager.viewEnabled() ? "on" : "off",
                appearanceManager.selfEnabled() ? "on" : "off",
                appearanceManager.serverSyncAvailable() ? "yes" : "no"));
        return 1;
    }

    private int template(FabricClientCommandSource source) {
        try {
            Path dir = ClientModelService.extractTemplates();
            registry.scanLocal();
            resolveActive();
            source.sendFeedback(Text.literal("模板文件夹已写出到: " + dir));
            source.sendFeedback(Text.literal("用 /pmodel list 查看，/pmodel use <名字> 切换"));
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("写出模板失败: " + e.getMessage()));
            return 0;
        }
    }

    private int folder(FabricClientCommandSource source) {
        source.sendFeedback(Text.literal("模型目录: " + ModelRegistry.modelsDir()));
        return 1;
    }

    private static Path resolve(String raw) {
        String value = raw.trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
        }
        Path path = Path.of(value);
        if (!path.isAbsolute()) {
            path = MinecraftClient.getInstance().runDirectory.toPath().resolve(path);
        }
        return path.normalize();
    }
}
