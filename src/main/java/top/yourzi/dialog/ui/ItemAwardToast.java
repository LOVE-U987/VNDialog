package top.yourzi.dialog.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import top.yourzi.dialog.util.Tween;
import top.yourzi.dialog.util.Easing;
import top.yourzi.dialog.util.UIAnimationUtils;

/**
 * 物品获得提示 Toast（沉浸式中心弹窗）：在屏幕中心显示动画弹窗。
 */
public class ItemAwardToast implements Toast {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 80;
    private static final int RADIUS = 12;
    private final ItemStack stack;
    private final Component title;
    private final long displayMs = 3000L;

    // 动画状态
    private final Tween scale = new Tween(0.0f);
    private final Tween alpha = new Tween(0.0f);
    private final Tween progress = new Tween(1.0f);
    private long showStartMs = -1;
    private boolean visible = true;

    public ItemAwardToast(ItemStack stack) {
        this.stack = stack;
        this.title = Component.translatable("dialog.toast.award", stack.getHoverName());
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
            scale.snap(0.0f);
            alpha.snap(0.0f);
            progress.snap(1.0f);
        }

        float elapsed = Math.max(0, now - showStartMs);
        float t = elapsed / displayMs;

        if (t < 0.3f) {
            // 进入动画（0→0.3s）：缩放 + 淡入
            float entryT = t / 0.3f;
            scale.retarget(1.0f, now, 300, Easing.EASE_OUT);
            alpha.retarget(1.0f, now, 300, Easing.EASE_OUT);
        } else if (t >= 1.0f) {
            // 开始退出
            visible = false;
            scale.retarget(0.0f, now, 200, Easing.EASE_IN);
            alpha.retarget(0.0f, now, 200, Easing.EASE_IN);
        } else {
            // 保持显示（进度条递减）
            progress.retarget(1.0f - (t - 0.3f) / 0.7f, now, (int) (displayMs * 0.7f), Easing.LINEAR);
        }

        // 更新动画值
        float s = scale.update(now);
        float a = alpha.update(now);
        float prog = progress.update(now);

        if (a <= 0.01f || !visible) {
            return Toast.Visibility.HIDE;
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int x = (screenWidth - WIDTH) / 2;
        int y = (screenHeight - HEIGHT) / 2;

        // 绘制圆角背景（带动画透明度）
        int bgColor = UIAnimationUtils.lerpColor(0xFF101010, 0xFF1A1A2E, a);
        drawRoundedBackground(graphics, x, y, WIDTH, HEIGHT, RADIUS, bgColor, s);

        // 物品图标（居中显示）
        int iconSize = 48;
        int iconX = x + 20;
        int iconY = y + 16;
        graphics.renderItem(stack, iconX, iconY);
        graphics.renderItemDecorations(mc.font, stack, iconX, iconY);

        // 文字（带透明度）
        String text = title.getString();
        int textColor = (int) (a * 0xFF) << 24 | 0x00E6E6EA;
        graphics.drawString(mc.font, text, x + 80, y + 24, textColor, true);

        // 进度条（底部）
        int barWidth = (int) (WIDTH - 40);
        int barHeight = 4;
        int barX = x + 20;
        int barY = y + HEIGHT - 12;
        int barColor = 0xFF3A6EA5;
        int barFilledWidth = (int) (barWidth * prog);
        int barColorWithAlpha = (int) (a * 0xFF) << 24 | (barColor & 0x00FFFFFF);

        // 进度条背景
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, (int) (a * 0.3f * 0xFF) << 24 | 0x000000);
        // 进度条填充
        graphics.fill(barX, barY, barX + barFilledWidth, barY + barHeight, barColorWithAlpha);

        return visible ? Toast.Visibility.SHOW : Toast.Visibility.HIDE;
    }

    /** 手动隐藏 Toast */
    public void dismiss() {
        visible = false;
        long now = System.currentTimeMillis();
        scale.retarget(0.0f, now, 200, Easing.EASE_IN);
        alpha.retarget(0.0f, now, 200, Easing.EASE_IN);
    }

    /** 绘制圆角背景 */
    private void drawRoundedBackground(GuiGraphics graphics, int x, int y, int w, int h, int radius, int color, float scale) {
        // 应用缩放（以中心为锚点）
        graphics.pose().pushPose();
        float centerX = x + w / 2f;
        float centerY = y + h / 2f;
        graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.pose().translate(-centerX, -centerY, 0);

        UIAnimationUtils.drawRoundedRect(graphics, x, y, w, h, radius, color);

        graphics.pose().popPose();
    }
}