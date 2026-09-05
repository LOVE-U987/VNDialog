package top.yourzi.dialog.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.util.Easing;
import top.yourzi.dialog.util.Tween;
import top.yourzi.dialog.util.UIAnimationUtils;

/**
 * 关闭对话确认屏（现代深色简洁风格）。
 * <p>半透明暗色遮罩 + 居中深色圆角面板 + 确认/取消两个扁平按钮。</p>
 */
public class DialogConfirmScreen extends Screen {
    // 面板尺寸
    private static final int PANEL_W = 320;
    private static final int PANEL_H = 130;
    private static final int RADIUS = 10;

    // 主题色（现代深色）
    private static final int PANEL_COLOR = 0xF0181B24;   // 深色面板
    private static final int PANEL_BORDER = 0xFF3A3F52;  // 面板边框
    private static final int TITLE_COLOR = 0xFFE6E6EA;   // 标题文字
    private static final int MSG_COLOR = 0xFFA0A5B5;     // 消息文字

    private static final int BTN_BG = 0xFF1B1E28;
    private static final int BTN_BG_HOVER = 0xFF262B3A;
    private static final int BTN_BORDER = 0xFF3A3F52;
    private static final int BTN_BORDER_CONFIRM = 0xFF4CAF50; // 确认按钮强调色
    private static final int BTN_TEXT = 0xFFE6E6EA;

    private final java.util.function.Consumer<Boolean> callback;
    private final Component titleText;
    private final Component messageText;
    private final String confirmLabel;
    private final String cancelLabel;

    // 进入动画：面板淡入 + 轻微上移
    private final Tween fade = new Tween(0.0f);
    private final Tween rise = new Tween(0.0f);
    private long startMs = -1;
    private static final int ANIM_DURATION = 200;

    private Button confirmButton;
    private Button cancelButton;

    public DialogConfirmScreen(java.util.function.Consumer<Boolean> callback,
                               Component title, Component message) {
        this(callback, title, message, "确认", "取消");
    }

    public DialogConfirmScreen(java.util.function.Consumer<Boolean> callback,
                               Component title, Component message,
                               String confirmLabel, String cancelLabel) {
        super(title);
        this.callback = callback;
        this.titleText = title;
        this.messageText = message;
        this.confirmLabel = confirmLabel;
        this.cancelLabel = cancelLabel;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int cy = this.height / 2;
        int btnW = 130;
        int btnH = 24;
        int btnGap = 12;
        int btnY = cy + PANEL_H / 2 - btnH - 16;

        this.confirmButton = Button.builder(Component.literal(confirmLabel), b -> {
            this.callback.accept(true);
        }).bounds(cx - btnW - btnGap / 2, btnY, btnW, btnH).build();
        this.cancelButton = Button.builder(Component.literal(cancelLabel), b -> {
            this.callback.accept(false);
        }).bounds(cx + btnGap / 2, btnY, btnW, btnH).build();

        this.addRenderableWidget(this.confirmButton);
        this.addRenderableWidget(this.cancelButton);

        this.startMs = System.currentTimeMillis();
        this.fade.snap(0.0f);
        this.rise.snap(0.0f);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        // 遮罩由 render() 自绘，不绘制原版背景
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        long now = System.currentTimeMillis();
        if (this.startMs < 0) this.startMs = now;

        // 进入动画
        fade.retarget(1.0f, startMs, ANIM_DURATION, Easing.EASE_OUT);
        rise.retarget(1.0f, startMs, ANIM_DURATION, Easing.EASE_OUT);
        float a = fade.update(now);
        float r = rise.update(now);

        // 遮罩（随进入淡入淡出）
        int overlayAlpha = (int) (a * 0xA0);
        guiGraphics.fill(0, 0, this.width, this.height, (overlayAlpha << 24));

        int cx = this.width / 2;
        int cy = this.height / 2;
        int px = cx - PANEL_W / 2;
        int py = cy - PANEL_H / 2 + Math.round((1.0f - r) * 16); // 轻微上移进入

        // 面板背景（随进入淡入）
        int panelAlpha = (int) (a * ((PANEL_COLOR >>> 24) & 0xFF));
        int panelColor = (panelAlpha << 24) | (PANEL_COLOR & 0xFFFFFF);
        UIAnimationUtils.drawRoundedRect(guiGraphics, px, py, PANEL_W, PANEL_H, RADIUS, panelColor);
        // 面板边框
        drawBorder(guiGraphics, px, py, PANEL_W, PANEL_H, RADIUS, PANEL_BORDER);

        // 标题
        guiGraphics.drawCenteredString(this.font, this.titleText, cx, py + 22, TITLE_COLOR);
        // 消息
        guiGraphics.drawCenteredString(this.font, this.messageText, cx, py + 46, MSG_COLOR);

        // 渲染按钮（深色扁平风格）
        renderFlatButton(guiGraphics, this.confirmButton, mouseX, mouseY, true);
        renderFlatButton(guiGraphics, this.cancelButton, mouseX, mouseY, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (this.confirmButton.isMouseOver(mouseX, mouseY)) {
                this.callback.accept(true);
                return true;
            }
            if (this.cancelButton.isMouseOver(mouseX, mouseY)) {
                this.callback.accept(false);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            this.callback.accept(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 以深色扁平风格绘制按钮，替代原版按钮纹理。
     */
    private void renderFlatButton(GuiGraphics guiGraphics, Button btn, int mouseX, int mouseY, boolean isConfirm) {
        boolean hovered = mouseX >= btn.getX() && mouseX <= btn.getX() + btn.getWidth()
                && mouseY >= btn.getY() && mouseY <= btn.getY() + btn.getHeight();
        int bg = hovered ? BTN_BG_HOVER : BTN_BG;
        int border = isConfirm ? BTN_BORDER_CONFIRM : BTN_BORDER;
        if (!hovered) border = BTN_BORDER;
        UIAnimationUtils.drawRoundedRect(guiGraphics, btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight(), 4, bg);
        drawBorder(guiGraphics, btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight(), 4, border);
        Component msg = btn.getMessage();
        int tw = this.font.width(msg);
        int tx = btn.getX() + (btn.getWidth() - tw) / 2;
        int ty = btn.getY() + (btn.getHeight() - this.font.lineHeight) / 2;
        guiGraphics.drawString(this.font, msg, tx, ty, BTN_TEXT);
    }

    private void drawBorder(GuiGraphics guiGraphics, int x, int y, int w, int h, int radius, int color) {
        guiGraphics.fill(x + radius, y, x + w - radius, y + 1, color);
        guiGraphics.fill(x + radius, y + h - 1, x + w - radius, y + h, color);
        guiGraphics.fill(x, y + radius, x + 1, y + h - radius, color);
        guiGraphics.fill(x + w - 1, y + radius, x + w, y + h - radius, color);
        guiGraphics.fill(x + radius - 1, y + radius - 1, x + radius + 1, y + radius + 1, color);
        guiGraphics.fill(x + w - radius - 1, y + radius - 1, x + w - radius + 1, y + radius + 1, color);
        guiGraphics.fill(x + radius - 1, y + h - radius - 1, x + radius + 1, y + h - radius + 1, color);
        guiGraphics.fill(x + w - radius - 1, y + h - radius - 1, x + w - radius + 1, y + h - radius + 1, color);
    }
}