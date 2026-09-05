package top.yourzi.dialog.util;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * UI 动画辅助工具类。
 * 提供颜色插值、圆角矩形渲染等常用功能。
 */
public class UIAnimationUtils {

    /**
     * 线性插值两个颜色（ARGB 格式）。
     * @param a 起始颜色 0xAARRGGBB
     * @param b 结束颜色 0xAARRGGBB
     * @param t 插值因子 0~1
     * @return 插值后的颜色
     */
    public static int lerpColor(int a, int b, float t) {
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24)
             | (Math.round(ar + (br - ar) * t) << 16)
             | (Math.round(ag + (bg - ag) * t) << 8)
             | Math.round(ab + (bb - ab) * t);
    }

    /**
     * 线性插值两个 int 值。
     */
    public static int lerp(int a, int b, float t) {
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        return a + (int) ((b - a) * t);
    }

    /**
     * 线性插值两个 float 值。
     */
    public static float lerp(float a, float b, float t) {
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        return a + (b - a) * t;
    }

    /**
     * 限制值在范围内。
     */
    public static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    /**
     * 限制 float 值在 0~1 范围内。
     */
    public static float clamp01(float val) {
        return Math.max(0f, Math.min(1f, val));
    }

    /**
     * 渲染圆角矩形（使用四段直线 + 四个小扇形近似）。
     * 性能优于 Shader，适合简单圆角需求。
     *
     * @param graphics GuiGraphics 实例
     * @param x 左上角 X
     * @param y 左上角 Y
     * @param width 宽度
     * @param height 高度
     * @param radius 圆角半径
     * @param color 颜色 0xAARRGGBB
     */
    public static void drawRoundedRect(GuiGraphics graphics, int x, int y, int width, int height, int radius, int color) {
        radius = Math.min(radius, Math.min(width / 2, height / 2));

        int alpha = (color >>> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        // 中心矩形
        if (width > radius * 2 && height > radius * 2) {
            graphics.fill(x + radius, y, x + width - radius, y + height, color);
        }

        // 左右矩形
        if (width > radius * 2) {
            graphics.fill(x, y + radius, x + radius, y + height - radius, color);
            graphics.fill(x + width - radius, y + radius, x + width, y + height - radius, color);
        }

        // 上下矩形
        if (height > radius * 2) {
            graphics.fill(x + radius, y, x + width - radius, y + radius, color);
            graphics.fill(x + radius, y + height - radius, x + width - radius, y + height, color);
        }

        // 四个角的扇形近似（用多个小矩形模拟）
        drawCornerSector(graphics, x + radius, y, radius, color, 0);      // 左上
        drawCornerSector(graphics, x + width - radius, y, radius, color, 1); // 右上
        drawCornerSector(graphics, x + radius, y + height - radius, radius, color, 2); // 左下
        drawCornerSector(graphics, x + width - radius, y + height - radius, radius, color, 3); // 右下
    }

    /**
     * 绘制圆角扇形（近似）。
     * @param corner 角落类型：0=左上，1=右上，2=左下，3=右下
     */
    private static void drawCornerSector(GuiGraphics graphics, int x, int y, int radius, int color, int corner) {
        int segments = 8; // 分段数，越多越圆滑
        for (int i = 0; i < segments; i++) {
            double angle1 = getCornerAngle(i, corner);
            double angle2 = getCornerAngle(i + 1, corner);
            float rad1 = (float) (Math.PI / 2 * angle1);
            float rad2 = (float) (Math.PI / 2 * angle2);

            int x1 = x + (int) (radius * Math.cos(rad1));
            int y1 = y + (int) (radius * Math.sin(rad1));
            int x2 = x + (int) (radius * Math.cos(rad2));
            int y2 = y + (int) (radius * Math.sin(rad2));

            // 绘制小三角形近似扇形
            int centerX = corner % 2 == 0 ? x : x + radius;
            int centerY = corner / 2 == 0 ? y : y + radius;

            if (corner == 0) { // 左上
                graphics.fill(centerX, y, x1, y1, color);
                graphics.fill(x1, y1, x2, y2, color);
            } else if (corner == 1) { // 右上
                graphics.fill(centerX - radius, y, x1, y1, color);
                graphics.fill(x1, y1, x2, y2, color);
            } else if (corner == 2) { // 左下
                graphics.fill(centerX, y + radius, x1, y1, color);
                graphics.fill(x1, y1, x2, y2, color);
            } else { // 右下
                graphics.fill(centerX - radius, y + radius, x1, y1, color);
                graphics.fill(x1, y1, x2, y2, color);
            }
        }
    }

    private static double getCornerAngle(int segment, int corner) {
        switch (corner) {
            case 0: return 2.0 - segment * 0.5 / 4;    // 左上：270°→180°
            case 1: return 0.5 + segment * 0.5 / 4;    // 右上：90°→0°
            case 2: return 1.5 - segment * 0.5 / 4;    // 左下：180°→270°
            case 3: return 1.0 + segment * 0.5 / 4;    // 右下：0°→90°
            default: return 0;
        }
    }

    /**
     * 绘制实心圆（近似圆形，用水平扫描线填充）。
     * @param graphics GuiGraphics 实例
     * @param cx 圆心 X
     * @param cy 圆心 Y
     * @param radius 半径
     * @param color 颜色 0xAARRGGBB
     */
    public static void drawCircle(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        radius = Math.max(1, radius);
        // 逐水平扫描线填充圆内部（行宽 = 2*sqrt(r²-y²)）
        int top = cy - radius;
        int bottom = cy + radius;
        for (int y = top; y <= bottom; y++) {
            int dy = y - cy;
            int dx = (int) Math.sqrt(radius * radius - dy * dy);
            int x0 = cx - dx;
            int x1 = cx + dx;
            graphics.fill(x0, y, x1 + 1, y + 1, color);
        }
    }

    /**
     * 渲染圆角矩形（使用 Minecraft 的 blit 系统，需要预渲染纹理）。
     * 这是更高效的方案，适合频繁渲染的场景。
     */
    public static void drawRoundedRectSprite(GuiGraphics graphics, int x, int y, int width, int height,
                                             int radius, int color, net.minecraft.resources.ResourceLocation texture) {
        // 简化实现：直接使用标准 blit，真正的 9-patch 需要预分割纹理
        graphics.blit(texture, x, y, 0, 0, width, height, 256, 256);
    }
}
