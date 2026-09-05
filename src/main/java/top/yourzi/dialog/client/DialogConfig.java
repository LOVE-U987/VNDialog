package top.yourzi.dialog.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;
import top.yourzi.dialog.Dialog;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * HUD 对话配置类
 * 管理对话气泡的位置、样式、动画等配置
 * 支持 JSON 配置和热重载
 */
public class DialogConfig {

    private static final DialogConfig INSTANCE = new DialogConfig();
    public static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get()
        .resolve("vndialog")
        .resolve("config.json");
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // 最后修改时间（用于热重载）
    private static long lastModified = 0;

    // ========== 配置字段 ==========

    /** 气泡最大宽度 */
    public int bubbleMaxWidth = 350;

    /** 气泡最小高度 */
    public int bubbleMinHeight = 80;

    /** 气泡水平内边距 */
    public int bubblePaddingX = 20;

    /** 气泡垂直内边距 */
    public int bubblePaddingY = 16;

    /** 头像大小 */
    public int avatarSize = 80;

    /** 头像与气泡的水平间距 */
    public int avatarMargin = 16;

    /** 背景颜色（RGB，不带 Alpha） */
    public int backgroundColor = 0x1E1E2E;

    /** 边框颜色（RGB） */
    public int borderColor = 0x4A4E69;

    /** 文本颜色（RGB） */
    public int textColor = 0xF5F5F5;

    /** 淡入淡出持续时间（秒） */
    public float fadeDuration = 0.3f;

    /** 浮动动画速度 */
    public float floatSpeed = 0.04f;

    /** 浮动动画振幅（像素） */
    public float floatAmplitude = 2.0f;

    /** 打字速度（毫秒/字符） */
    public int charIntervalMs = 30;

    /** 自动超时时间（毫秒） */
    public int autoDismissMs = 5000;

    /** 是否启用浮动动画 */
    public boolean enableFloatAnimation = true;

    /** 是否启用打字机效果 */
    public boolean enableTypewriter = true;

    /** 是否启用淡入淡出效果 */
    public boolean enableFadeEffect = true;

    private DialogConfig() {
        load();
    }

    public static DialogConfig getInstance() {
        return INSTANCE;
    }

    /**
     * 检查并加载配置（支持热重载）
     */
    public static void checkReload() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                long modified = Files.getLastModifiedTime(CONFIG_PATH).toMillis();
                if (modified > lastModified) {
                    INSTANCE.load();
                    lastModified = modified;
                    Dialog.LOGGER.info("VNDialog config reloaded from {}", CONFIG_PATH);
                }
            } catch (IOException e) {
                Dialog.LOGGER.warn("Failed to check config modification time", e);
            }
        }
    }

    /**
     * 重新加载配置
     */
    public void reload() {
        load();
    }

    /**
     * 从文件加载配置
     */
    public void load() {
        if (!Files.exists(CONFIG_PATH)) {
            createDefaultConfig();
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(CONFIG_PATH)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            parseConfig(json);
            Dialog.LOGGER.info("VNDialog config loaded from {}", CONFIG_PATH);
        } catch (IOException e) {
            Dialog.LOGGER.error("Failed to load config from {}", CONFIG_PATH, e);
            createDefaultConfig();
        }
    }

    /**
     * 解析配置 JSON
     */
    private void parseConfig(JsonObject json) {
        if (json.has("bubble_max_width")) {
            bubbleMaxWidth = json.get("bubble_max_width").getAsInt();
        }
        if (json.has("bubble_min_height")) {
            bubbleMinHeight = json.get("bubble_min_height").getAsInt();
        }
        if (json.has("bubble_padding_x")) {
            bubblePaddingX = json.get("bubble_padding_x").getAsInt();
        }
        if (json.has("bubble_padding_y")) {
            bubblePaddingY = json.get("bubble_padding_y").getAsInt();
        }
        if (json.has("avatar_size")) {
            avatarSize = json.get("avatar_size").getAsInt();
        }
        if (json.has("avatar_margin")) {
            avatarMargin = json.get("avatar_margin").getAsInt();
        }
        if (json.has("background_color")) {
            backgroundColor = json.get("background_color").getAsInt();
        }
        if (json.has("border_color")) {
            borderColor = json.get("border_color").getAsInt();
        }
        if (json.has("text_color")) {
            textColor = json.get("text_color").getAsInt();
        }
        if (json.has("fade_duration")) {
            fadeDuration = json.get("fade_duration").getAsFloat();
        }
        if (json.has("float_speed")) {
            floatSpeed = json.get("float_speed").getAsFloat();
        }
        if (json.has("float_amplitude")) {
            floatAmplitude = json.get("float_amplitude").getAsFloat();
        }
        if (json.has("char_interval_ms")) {
            charIntervalMs = json.get("char_interval_ms").getAsInt();
        }
        if (json.has("auto_dismiss_ms")) {
            autoDismissMs = json.get("auto_dismiss_ms").getAsInt();
        }
        if (json.has("enable_float_animation")) {
            enableFloatAnimation = json.get("enable_float_animation").getAsBoolean();
        }
        if (json.has("enable_typewriter")) {
            enableTypewriter = json.get("enable_typewriter").getAsBoolean();
        }
        if (json.has("enable_fade_effect")) {
            enableFadeEffect = json.get("enable_fade_effect").getAsBoolean();
        }
    }

    /**
     * 保存配置到文件
     */
    public void save() {
        try {
            // 确保目录存在
            Path parent = CONFIG_PATH.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            // 写入配置
            JsonObject json = new JsonObject();
            json.addProperty("bubble_max_width", bubbleMaxWidth);
            json.addProperty("bubble_min_height", bubbleMinHeight);
            json.addProperty("bubble_padding_x", bubblePaddingX);
            json.addProperty("bubble_padding_y", bubblePaddingY);
            json.addProperty("avatar_size", avatarSize);
            json.addProperty("avatar_margin", avatarMargin);
            json.addProperty("background_color", backgroundColor);
            json.addProperty("border_color", borderColor);
            json.addProperty("text_color", textColor);
            json.addProperty("fade_duration", fadeDuration);
            json.addProperty("float_speed", floatSpeed);
            json.addProperty("float_amplitude", floatAmplitude);
            json.addProperty("char_interval_ms", charIntervalMs);
            json.addProperty("auto_dismiss_ms", autoDismissMs);
            json.addProperty("enable_float_animation", enableFloatAnimation);
            json.addProperty("enable_typewriter", enableTypewriter);
            json.addProperty("enable_fade_effect", enableFadeEffect);

            try (BufferedWriter writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(json, writer);
                lastModified = System.currentTimeMillis();
                Dialog.LOGGER.info("VNDialog config saved to {}", CONFIG_PATH);
            }
        } catch (IOException e) {
            Dialog.LOGGER.error("Failed to save config to {}", CONFIG_PATH, e);
        }
    }

    /**
     * 创建默认配置
     */
    private void createDefaultConfig() {
        try {
            // 确保目录存在
            Path parent = CONFIG_PATH.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            // 写入默认配置
            JsonObject json = new JsonObject();
            json.addProperty("bubble_max_width", 350);
            json.addProperty("bubble_min_height", 80);
            json.addProperty("bubble_padding_x", 20);
            json.addProperty("bubble_padding_y", 16);
            json.addProperty("avatar_size", 80);
            json.addProperty("avatar_margin", 16);
            json.addProperty("background_color", 0x1E1E2E);
            json.addProperty("border_color", 0x4A4E69);
            json.addProperty("text_color", 0xF5F5F5);
            json.addProperty("fade_duration", 0.3f);
            json.addProperty("float_speed", 0.04f);
            json.addProperty("float_amplitude", 2.0f);
            json.addProperty("char_interval_ms", 30);
            json.addProperty("auto_dismiss_ms", 5000);
            json.addProperty("enable_float_animation", true);
            json.addProperty("enable_typewriter", true);
            json.addProperty("enable_fade_effect", true);

            try (BufferedWriter writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(json, writer);
                lastModified = System.currentTimeMillis();
                Dialog.LOGGER.info("VNDialog default config created at {}", CONFIG_PATH);
            }
        } catch (IOException e) {
            Dialog.LOGGER.error("Failed to create default config", e);
        }
    }

    /**
     * 重置为默认配置
     */
    public void resetToDefaults() {
        loadDefault();
        save();
    }

    private void loadDefault() {
        bubbleMaxWidth = 350;
        bubbleMinHeight = 80;
        bubblePaddingX = 20;
        bubblePaddingY = 16;
        avatarSize = 80;
        avatarMargin = 16;
        backgroundColor = 0x1E1E2E;
        borderColor = 0x4A4E69;
        textColor = 0xF5F5F5;
        fadeDuration = 0.3f;
        floatSpeed = 0.04f;
        floatAmplitude = 2.0f;
        charIntervalMs = 30;
        autoDismissMs = 5000;
        enableFloatAnimation = true;
        enableTypewriter = true;
        enableFadeEffect = true;
    }
}
