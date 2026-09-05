package top.yourzi.dialog.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.util.Tween;
import top.yourzi.dialog.util.Easing;
import top.yourzi.dialog.util.UIAnimationUtils;

/**
 * 对话选项按钮（带动画效果的现代深色扁平按钮）
 */
public class OptionButton extends ImageButton {
    // 进入动画：从下方滑入
    private final Tween translateY;
    private final float initialYOffset;
    private long entranceStartTime = -1;
    private int staggerDelay;
    private boolean entered = false;

    // 悬停过渡颜色（现代深色风格）
    private static final int COLOR_BG = 0xFF1B1E28;      // 默认深色背景
    private static final int COLOR_BG_HOVER = 0xFF262B3A; // 悬停背景（略亮）
    private static final int COLOR_BORDER = 0xFF3A3F52;   // 默认边框
    private static final int COLOR_BORDER_HOVER = 0xFF4CAF50; // 悬停边框（强调色）
    private static final int COLOR_TEXT = 0xFFE6E6EA;     // 文字
    private static final int COLOR_TEXT_DISABLED = 0xFF8A8A8A; // 禁用文字
    private static final int COLOR_BG_DISABLED = 0xFF14161E;   // 禁用背景

    public OptionButton(int x, int y, int width, int height, WidgetSprites sprites, OnPress onPress,
            Component message, int staggerDelay) {
        super(x, y, width, height, sprites, onPress, message);
        this.initialYOffset = 20.0f;  // 初始向下偏移 20px
        this.translateY = new Tween(0.0f);
        this.staggerDelay = staggerDelay;
    }

    /**
     * 更新动画状态
     */
    public void updateAnimation(long now) {
        if (this.entranceStartTime < 0) {
            if (this.staggerDelay > 0) {
                // 等待 stagger 时间
                return;
            }
            this.entranceStartTime = now;
        }
        
        long elapsed = now - this.entranceStartTime;
        float progress = Math.min(1.0f, elapsed / 300.0f); // 300ms 进入动画
        
        // 使用 Easing.EASE_OUT 曲线
        float t = Easing.EASE_OUT.apply(progress);
        this.translateY.retarget(t, now, 300, Easing.EASE_OUT);
        
        if (progress >= 1.0f) {
            this.entered = true;
        }
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        long now = System.currentTimeMillis();
        
        // 更新动画状态
        this.updateAnimation(now);
        
        // 计算 Y 偏移
        float yOffset = 0.0f;
        if (!this.entered) {
            float t = this.translateY.update(now);
            yOffset = -this.initialYOffset * (1.0f - t);
        }
        
        // 计算 hover 状态
        boolean isHovered = this.active && mouseX >= this.getX() && mouseX <= this.getX() + this.width &&
                           mouseY >= this.getY() + (int) yOffset && mouseY <= this.getY() + this.height + (int) yOffset;
        
        // 保存当前变换
        guiGraphics.pose().pushPose();
        
        // 应用进入动画平移
        guiGraphics.pose().translate(0, yOffset, 0);
        
        // hover 时轻微缩放（现代扁平风格用更小的缩放）
        if (isHovered) {
            guiGraphics.pose().scale(1.03f, 1.03f, 1.0f);
        }
        
        // 调整渲染位置（考虑缩放）
        float scale = isHovered ? 1.03f : 1.0f;
        int renderX = this.getX();
        int renderY = this.getY() + (int) yOffset;
        
        // 绘制按钮背景（深色扁平圆角矩形 + 细边框）
        int bgColor = this.active ? (isHovered ? COLOR_BG_HOVER : COLOR_BG) : COLOR_BG_DISABLED;
        int borderColor = isHovered ? COLOR_BORDER_HOVER : COLOR_BORDER;
        int alpha = this.active ? 255 : 140;
        
        int buttonX = renderX;
        int buttonY = renderY;
        int buttonW = (int) (this.width / scale);
        int buttonH = (int) (this.height / scale);
        
        // 背景
        UIAnimationUtils.drawRoundedRect(guiGraphics, buttonX, buttonY, buttonW, buttonH, 4, (alpha << 24) | (bgColor & 0xFFFFFF));
        
        // 边框（细线圆角矩形）
        drawBorder(guiGraphics, buttonX, buttonY, buttonW, buttonH, 4, (alpha << 24) | (borderColor & 0xFFFFFF));
        
        // 绘制文字
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        Component message = this.getMessage();
        if (message != Component.EMPTY) {
            int stringWidth = font.width(message);
            int textColor = this.active ? COLOR_TEXT : COLOR_TEXT_DISABLED;
            int textX = buttonX + (buttonW - stringWidth) / 2;
            int textY = buttonY + (buttonH - font.lineHeight) / 2;
            guiGraphics.drawString(font, message, textX, textY, textColor);
        }
        
        // 恢复变换
        guiGraphics.pose().popPose();
    }

    /**
     * 绘制细边框（4 条 1px 直线，覆盖圆角矩形轮廓）。
     */
    private void drawBorder(GuiGraphics guiGraphics, int x, int y, int w, int h, int radius, int color) {
        // 上下边（中间留出圆角）
        guiGraphics.fill(x + radius, y, x + w - radius, y + 1, color);
        guiGraphics.fill(x + radius, y + h - 1, x + w - radius, y + h, color);
        // 左右边（中间留出圆角）
        guiGraphics.fill(x, y + radius, x + 1, y + h - radius, color);
        guiGraphics.fill(x + w - 1, y + radius, x + w, y + h - radius, color);
        // 四个圆角点（近似）
        guiGraphics.fill(x + radius - 1, y + radius - 1, x + radius + 1, y + radius + 1, color);
        guiGraphics.fill(x + w - radius - 1, y + radius - 1, x + w - radius + 1, y + radius + 1, color);
        guiGraphics.fill(x + radius - 1, y + h - radius - 1, x + radius + 1, y + h - radius + 1, color);
        guiGraphics.fill(x + w - radius - 1, y + h - radius - 1, x + w - radius + 1, y + h - radius + 1, color);
    }
}