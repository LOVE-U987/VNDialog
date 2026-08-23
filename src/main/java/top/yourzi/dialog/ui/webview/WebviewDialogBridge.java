package top.yourzi.dialog.ui.webview;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.DialogManager;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.PortraitInfo;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * VNDialog ↔ WebGUI/MCEF 桥接层（实验分支）。
 *
 * <p>设计要点：<b>不引入任何 WebGUI 编译期依赖</b>，从而本项目在离线/受限环境下仍可
 * 通过 {@code gradlew compileJava --offline} 正常编译。运行时若某环境已装载 WebGUI mod，
 * 则通过反射调用其 {@code WebviewApi} 打开本 mod 打包的「对话历史回顾」HTML 页面；
 * 未安装 WebGUI 时则安全空转，不影响既有原生渲染流程。
 *
 * <p>WebGUI 形态（需在有网络环境对照官方 {@code WebviewApi} 核实签名）：
 * <ul>
 *   <li>{@code WebviewApi.openWebview(url, args)} 打开 HUD/GUI</li>
 *   <li>网页侧 {@code window.webview.postToGame(payload)} 发送消息给 Mod</li>
 * </ul>
 *
 * <p><b>注意：</b>「打开 WebGUI」部分依赖 WebGUI API 的精确签名。当前网络无法拉取
 * WebGUI 制品核对，故实现为候选方法名自动匹配；若运行环境也未匹配上，则警告并安全
 * 回退（不崩溃），留待联网核对后在此定签名。
 */
@OnlyIn(Dist.CLIENT)
public final class WebviewDialogBridge {

    /** WebGUI 对外暴露的 WebviewApi 全限定类名（若官方改名记得更新）。 */
    private static final String WEBGUI_API_CLASS = "com.mcwebgui.api.WebviewApi";

    /** 本 mod 打包到资源的 HTML 页面入口。 */
    private static final String HISTORY_PAGE_RESOURCE = "dialog:web/index.html";

    private static boolean probed = false;
    private static boolean webguiPresent = false;

    private WebviewDialogBridge() {}

    /** 探测 WebGUI 是否在运行时可用（仅一次）。 */
    private static void probeOnce() {
        if (probed) return;
        probed = true;
        try {
            Class.forName(WEBGUI_API_CLASS, false, WebviewDialogBridge.class.getClassLoader());
            webguiPresent = true;
            Dialog.LOGGER.info("[VNDialog/WebUI] 检测到 WebGUI API，Web 历史回顾通道可用。");
        } catch (ClassNotFoundException e) {
            webguiPresent = false;
            Dialog.LOGGER.info("[VNDialog/WebUI] 未检测到 WebGUI API（可选依赖），保持原生对话 UX。");
        }
    }

    /** WebGUI 是否已就绪。 */
    public static boolean isWebguiPresent() {
        probeOnce();
        return webguiPresent;
    }

    /**
     * 打开「对话历史回顾」页面（WebGUI HUD）。
     *
     * @return true 表明已发出打开请求（或已连接）；false 表示 WebGUI 未装或匹配失败。
     */
    public static boolean openHistoryOverlay() {
        probeOnce();
        if (!webguiPresent) return false;
        final String payloadJson = buildJsonPayload();
        final String url = HISTORY_PAGE_RESOURCE;
        try {
            Class<?> api = Class.forName(WEBGUI_API_CLASS);
            Method m = findFirst(api, "openWebview", "openOverlay", "openHud", "open");
            if (m == null) {
                Dialog.LOGGER.warn("[VNDialog/WebUI] 未找到 WebGUI 打开浮层的候选方法，需联网核对 WebviewApi 签名。");
                return false;
            }
            Object target = null;
            if (!Modifier.isStatic(m.getModifiers())) {
                target = getInstanceFactory(api);
                if (target == null) {
                    Dialog.LOGGER.warn("[VNDialog/WebUI] WebviewApi 为实例方法但无法取得实例。");
                    return false;
                }
            }
            invoke(m, target, url, payloadJson);
            Dialog.LOGGER.info("[VNDialog/WebUI] 已调用 WebGUI 打开历史回顾（若参数签名不匹配会在此异常并打印）。");
            return true;
        } catch (Exception e) {
            Dialog.LOGGER.warn("[VNDialog/WebUI] 调用 WebGUI 打开浮层失败：{}", e.getMessage());
            return false;
        }
    }

    /** 将近期对话历史清洗为有序 JSON（供前端渲染）。 */
    public static String buildJsonPayload() {
        JsonArray arr = new JsonArray();
        List<DialogEntry> history;
        try {
            history = DialogManager.getInstance().getDialogHistory();
        } catch (Exception e) {
            return "[]";
        }
        if (history == null || history.isEmpty()) return "[]";

        HolderLookup.Provider lookup = Minecraft.getInstance().level.registryAccess();
        String player = (Minecraft.getInstance().player != null)
                ? Minecraft.getInstance().player.getGameProfile().getName() : "";
        int seq = 0;
        for (DialogEntry de : history) {
            if (de == null) continue;
            try {
                JsonObject o = new JsonObject();
                o.addProperty("seq", ++seq);
                if (de.getSpeaker() != null && !de.getSpeaker(lookup, player).getString().isEmpty()) {
                    o.addProperty("speaker", de.getSpeaker(lookup, player).getString());
                } else {
                    o.addProperty("speaker", "旁白");
                }
                if (de.getText() != null) {
                    o.addProperty("text", de.getText(lookup, player).getString());
                }
                if (de.getSelectedOptionText() != null && !de.getSelectedOptionText().isEmpty()) {
                    o.addProperty("choice", de.getSelectedOptionText());
                }
                List<PortraitInfo> portraits = de.getPortraits();
                if (portraits != null && !portraits.isEmpty() && portraits.get(0).getPath() != null) {
                    o.addProperty("portrait", portraits.get(0).getPath());
                }
                o.addProperty("time", System.currentTimeMillis());
                arr.add(o);
            } catch (Exception ignored) { /* 跳过坏条目 */ }
        }
        return DialogManager.GSON.toJson(arr);
    }

    // ---------- 反射工具 ----------
    private static Method findFirst(Class<?> cls, String... names) {
        for (String n : names) {
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getName().equals(n) && !m.isSynthetic() && !m.isBridge()) return m;
            }
        }
        return null;
    }

    private static Object getInstanceFactory(Class<?> cls) {
        for (String n : new String[]{"getInstance", "api", "get"}) {
            try {
                Method m = cls.getDeclaredMethod(n);
                m.setAccessible(true);
                return m.invoke(null);
            } catch (Exception ignored) { }
        }
        return null;
    }

    private static void invoke(Method m, Object target, Object... fullArgs) throws Exception {
        m.setAccessible(true);
        if (m.getParameterCount() == 0) {
            m.invoke(target);
            return;
        }
        // 截取前需要个数的参数（WebGUI open 方法一般接受 (url, json) 或 (url)）
        Object[] callArgs = new Object[Math.min(fullArgs.length, m.getParameterCount())];
        System.arraycopy(fullArgs, 0, callArgs, 0, callArgs.length);
        m.invoke(target, callArgs);
    }
}