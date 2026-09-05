package top.yourzi.dialog.kubergs;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * KUBEGS 联动监听器。
 * 提供键鼠操作监听、对话控制、玩家 JSON 数据读取等功能。
 * <p>
 * KUBEGS (Kube's Game State) 是一个游戏状态管理协议，允许外部工具读写玩家数据。
 * 本监听器通过 HTTP API 或本地文件实现联动。
 */
public class KUBERGSListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("KUBERGSListener");
    private static final KUBERGSListener INSTANCE = new KUBERGSListener();

    // KUBEGS API 端点（默认本地）
    private static String KUBEGS_API_URL = "http://127.0.0.1:8080/kubergs";

    // 是否启用 KUBEGS 联动
    private boolean enabled = true;

    // 玩家 JSON 数据缓存
    private JsonObject playerDataCache;
    private long lastCacheTime = 0;
    private static final int CACHE_DURATION_MS = 1000; // 缓存 1 秒

    private KUBERGSListener() {}

    public static KUBERGSListener getInstance() {
        return INSTANCE;
    }

    /**
     * 检查 KUBEGS 服务是否可用。
     */
    public boolean isServiceAvailable() {
        if (!enabled) return false;
        try {
            URL url = new URL(KUBEGS_API_URL + "/status");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(500);
            conn.setReadTimeout(500);
            int responseCode = conn.getResponseCode();
            return responseCode == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 监听键鼠操作：检查是否有键被按下以关闭/打开对话。
     * @param showDialogOnPress 按下的键是否打开对话（true=打开，false=关闭）
     * @return 是否触发对话开关
     */
    public boolean listenKeyInput(boolean showDialogOnPress) {
        if (!enabled) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return false;

        // 检查 ESC 键（关闭对话）
        if (showDialogOnPress && GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
            return true; // 按 ESC 关闭对话
        }

        // 检查 F1 键（打开对话，用于测试）
        if (!showDialogOnPress && GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_F1) == GLFW.GLFW_PRESS) {
            return true; // 按 F1 打开对话
        }

        return false;
    }

    /**
     * 监听鼠标点击：检查是否点击了对话区域以交互。
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param dialogX 对话框 X 位置
     * @param dialogY 对话框 Y 位置
     * @param dialogWidth 对话框宽度
     * @param dialogHeight 对话框高度
     * @return 是否点击了对话框
     */
    public boolean listenMouseClick(double mouseX, double mouseY, int dialogX, int dialogY, int dialogWidth, int dialogHeight) {
        if (!enabled) return false;

        return mouseX >= dialogX && mouseX <= dialogX + dialogWidth &&
               mouseY >= dialogY && mouseY <= dialogY + dialogHeight;
    }

    /**
     * 读取玩家 JSON 数据。
     * @param key 数据键（如 "health", "inventory", "position"）
     * @return 对应值（可以是 String、Number、Boolean 等）
     */
    public Object getPlayerData(String key) {
        if (!enabled) return null;

        // 检查缓存
        long now = System.currentTimeMillis();
        if (playerDataCache != null && (now - lastCacheTime) < CACHE_DURATION_MS) {
            JsonObject data = playerDataCache;
            if (data.has(key)) {
                return data.get(key);
            }
            return null;
        }

        // 从 KUBEGS API 获取数据
        try {
            URL url = new URL(KUBEGS_API_URL + "/player");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            playerDataCache = JsonParser.parseString(response.toString()).getAsJsonObject();
            lastCacheTime = now;

            if (playerDataCache.has(key)) {
                return playerDataCache.get(key);
            }
        } catch (Exception e) {
            // 获取失败，返回缓存数据或 null
            LOGGER.warn("Failed to get player data from KUBEGS: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 修改玩家 JSON 数据。
     * @param key 数据键
     * @param value 新值
     * @return 是否成功
     */
    public boolean setPlayerData(String key, Object value) {
        if (!enabled) return false;

        try {
            // 获取当前数据
            Object currentData = getPlayerData(key);
            JsonObject data = new JsonObject();
            if (currentData instanceof JsonObject) {
                data = (JsonObject) currentData;
            }

            // 设置新值
            if (value instanceof String) {
                data.addProperty(key, (String) value);
            } else if (value instanceof Number) {
                data.addProperty(key, ((Number) value).doubleValue());
            } else if (value instanceof Boolean) {
                data.addProperty(key, (Boolean) value);
            } else if (value instanceof JsonObject) {
                data.add(key, (JsonObject) value);
            }

            // 发送回 KUBEGS API
            URL url = new URL(KUBEGS_API_URL + "/player");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            OutputStream os = conn.getOutputStream();
            os.write(data.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            return conn.getResponseCode() == 200;
        } catch (Exception e) {
            LOGGER.error("Failed to set player data in KUBEGS: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 打开对话（通过 KUBEGS 协议）。
     * @param dialogId 对话 ID
     */
    public void showDialog(String dialogId) {
        if (!enabled) return;

        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("action", "show_dialog");
            payload.addProperty("dialog_id", dialogId);

            URL url = new URL(KUBEGS_API_URL + "/command");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            OutputStream os = conn.getOutputStream();
            os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();
        } catch (Exception e) {
            LOGGER.warn("Failed to show dialog via KUBEGS: {}", e.getMessage());
        }
    }

    /**
     * 关闭对话（通过 KUBEGS 协议）。
     */
    public void closeDialog() {
        if (!enabled) return;

        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("action", "close_dialog");

            URL url = new URL(KUBEGS_API_URL + "/command");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            OutputStream os = conn.getOutputStream();
            os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();
        } catch (Exception e) {
            LOGGER.warn("Failed to close dialog via KUBEGS: {}", e.getMessage());
        }
    }

    /**
     * 启用/禁用 KUBEGS 联动。
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 是否启用。
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置 KUBEGS API URL。
     */
    public void setApiUrl(String url) {
        KUBERGSListener.KUBEGS_API_URL = url;
    }
}
