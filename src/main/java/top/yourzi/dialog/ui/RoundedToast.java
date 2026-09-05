package top.yourzi.dialog.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.util.Easing;
import top.yourzi.dialog.util.Tween;
import top.yourzi.dialog.util.UIAnimationUtils;

/**
 * 圆角退出提示 Toast：在屏幕底部居中滑入滑出显示，带倒计时进度条。
 */
public class RoundedToast implements Toast {
    private static final int WIDTH = 280;
    private static final int HEIGHT = 48;
    private static final int RADIUS = 8;

    private final Component message;
    private final long displayDuration = 4000L; // 显示 4 秒
    private long showStartMs = -1;

    // 动画状态
    private final Tween yOffset = new Tween();      // 垂直位移
    private final Tween alpha = new Tween();        // 透明度
    private final Tween progress = new Tween();     // 倒计时进度
    private boolean visible = true;
    private boolean hiding = false;

    public RoundedToast(Component message) {
        this.message = message;
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return HEIGHT;
    }

    @Override
    public Toast.Visibility render(GuiGraphics graphics, ToastComponent toastComponent, long sessionTimeMillis) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return Toast.Visibility.HIDE;

        long now = System.currentTimeMillis();

        // 初始化动画
        if (showStartMs == -1) {
            showStartMs = now;
            yOffset.snap(30);      // 初始位置（底部下方）
            alpha.snap(0);         // 初始透明
            progress.snap(1);      // 进度 100%
        }

        float elapsed = Math.max(0, now - showStartMs);
        float t = elapsed / displayDuration;

        if (!hiding) {
            // 显示阶段
            if (t < 0.3f) {
                // 滑入 + 淡入 (0→0.3s)
                float slideT = t / 0.3f;
                yOffset.retarget(0, now, 300, Easing.EASE_OUT);
                alpha.retarget(1, now, 300, Easing.EASE_OUT);
            } else if (t >= 1.0f) {
                // 开始淡出
                hiding = true;
                yOffset.retarget(30, now, 200, Easing.EASE_IN);
                alpha.retarget(0, now, 200, Easing.EASE_IN);
                progress.retarget(0, now, 200, Easing.LINEAR);
            } else {
                // 保持显示（进度条递减）
                // 前 300ms 用于滑入，之后 70% 的时间用于倒计时
                float countdownT = (t - 0.3f) / 0.7f;
                float countdownProgress = 1.0f - Math.min(1.0f, countdownT);
                progress.retarget(countdownProgress, now, (int) (displayDuration * 0.7f), Easing.LINEAR);
            }
        }

        // 更新动画值
        float y = yOffset.update(now);
        float a = alpha.update(now);
        float prog = progress.update(now);

        if (a <= 0.01f) {
            return Toast.Visibility.HIDE;
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int x = (screenWidth - WIDTH) / 2;
        int yPos = screenHeight - 80 + (int) y; // 底部居中 + 偏移

        // 绘制圆角背景
        int bgColor = UIAnimationUtils.lerpColor(0xFF101010, 0xFF1A1A2E, a);
        drawRoundedBackground(graphics, x, yPos, WIDTH, HEIGHT, RADIUS, bgColor);

        // 倒计时进度条（底部细线）
        int barWidth = (int) (WIDTH * prog);
        int barColor = 0xFF3A6EA5; // 强调色
        int barAlpha = (int) (a * 0xFF);
        int barColorWithAlpha = (barAlpha << 24) | (barColor & 0x00FFFFFF);
        graphics.fill(x, yPos + HEIGHT - 3, x + barWidth, yPos + HEIGHT, barColorWithAlpha);

        // 文字（带透明度）
        String text = message.getString();
        int textColor = (int) (a * 0xFF) << 24;
        graphics.drawString(mc.font, text, x + 16, yPos + (HEIGHT - 8) / 2, textColor | 0x00E6E6EA, true);

        // 返回显示状态
        return visible ? Toast.Visibility.SHOW : Toast.Visibility.HIDE;
    }

    /**
     * 手动隐藏 Toast（调用后立即停止渲染）。
     */
    public void dismiss() {
        visible = false;
        hiding = true;
        long now = System.currentTimeMillis();
        yOffset.retarget(30, now, 200, Easing.EASE_IN);
        alpha.retarget(0, now, 200, Easing.EASE_IN);
    }

    /**
     * 绘制圆角矩形背景（手动实现，避免依赖 Shader）。
     */
    private void drawRoundedBackground(GuiGraphics graphics, int x, int y, int w, int h, int radius, int color) {
        // 使用简化版圆角绘制（四段直线 + 四个扇形近似）
        UIAnimationUtils.drawRoundedRect(graphics, x, y, w, h, radius, color);
    }
}
