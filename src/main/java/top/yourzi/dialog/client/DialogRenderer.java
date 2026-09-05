package top.yourzi.dialog.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.List;

/**
 * 对话渲染器
 * 负责渲染非侵入式对话气泡和头像
 */
@OnlyIn(Dist.CLIENT)
public class DialogRenderer {

    private static final DialogRenderer INSTANCE = new DialogRenderer();

    // 渲染配置常量
    private static final int BUBBLE_MAX_WIDTH = 400;
    private static final int BUBBLE_MIN_HEIGHT = 80;
    private static final int BUBBLE_PADDING_X = 20;
    private static final int BUBBLE_PADDING_Y = 16;
    private static final int AVATAR_SIZE = 80;
    private static final int AVATAR_MARGIN = 16;
    private static final int CORNER_RADIUS = 8;

    // 颜色配置（深色主题 - 参考效果图）
    private static final int BG_COLOR = 0xFF1E1E2E;
    private static final int BORDER_COLOR = 0xFF4A4E69;
    private static final int TEXT_COLOR = 0xFFF5F5F5;
    private static final int SUBTEXT_COLOR = 0xFFAAAAAA;

    // 动画配置
    private static final float FADE_IN_DURATION = 0.3f;
    private static final float FADE_OUT_DURATION = 0.3f;
    private static final float FLOAT_SPEED = 0.04f;
    private static final float FLOAT_AMPLITUDE = 2.0f;

    // 状态
    private float fadeAlpha = 1.0f;
    private float floatOffset = 0.0f;
    private float currentFadeAlpha = 1.0f;
    private long lastRenderTime = 0;
    private String previousText = "";

    private DialogRenderer() {}

    public static DialogRenderer getInstance() {
        return INSTANCE;
    }

    /**
     * 渲染对话
     */
    public void render(GuiGraphics guiGraphics, net.minecraft.client.DeltaTracker deltaTracker) {
        DialogManager manager = DialogManager.getInstance();
        
        // 如果没有活跃的对话，跳过渲染
        if (manager.getCurrentEntry() == null) {
            return;
        }

        // 检查文字变化，重置淡入效果
        String currentText = manager.getCurrentText().getString();
        if (!currentText.equals(previousText)) {
            previousText = currentText;
            fadeAlpha = 0.0f;
        }

        // 计算渲染时间
        long now = System.currentTimeMillis();
        if (lastRenderTime == 0) {
            lastRenderTime = now;
        }
        float partialTick = (now - lastRenderTime) / 1000.0f;
        lastRenderTime = now;

        // 更新浮动动画
        long gameTime = Minecraft.getInstance().level.getGameTime();
        float floatFactor = Mth.sin((gameTime + partialTick * 20) * FLOAT_SPEED) * FLOAT_AMPLITUDE;
        this.floatOffset = floatFactor;

        // 更新淡入淡出
        updateFadeEffect(deltaTracker);

        // 渲染
        renderBubble(guiGraphics, manager);
    }

    /**
     * 更新淡入淡出效果
     */
    private void updateFadeEffect(net.minecraft.client.DeltaTracker deltaTracker) {
        DialogState state = DialogManager.getInstance().getState();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);

        switch (state) {
            case FADE_IN -> {
                fadeAlpha = Mth.clamp(fadeAlpha + partialTick / FADE_IN_DURATION, 0.0f, 1.0f);
                if (fadeAlpha >= 1.0f) {
                    DialogManager.getInstance().setState(DialogState.TYPING);
                }
            }
            case FADE_OUT -> {
                fadeAlpha = Mth.clamp(fadeAlpha - partialTick / FADE_OUT_DURATION, 0.0f, 1.0f);
                if (fadeAlpha <= 0.0f) {
                    DialogManager.getInstance().setState(DialogState.IDLE);
                    fadeAlpha = 1.0f;
                }
            }
            default -> {
                // 其他状态保持当前透明度
            }
        }
    }

    /**
     * 渲染对话气泡
     */
    private void renderBubble(GuiGraphics guiGraphics, DialogManager manager) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Component text = manager.getCurrentText();
        TypewriterState typewriter = manager.getTypewriter();
        ResourceLocation avatarTexture = manager.getCurrentAvatar();

        if (typewriter == null) {
            return;
        }

        // 获取可见文本
        String visibleText = typewriter.getVisibleText();
        Component textComponent = Component.literal(visibleText);

        // 计算文本大小
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(textComponent, BUBBLE_MAX_WIDTH - BUBBLE_PADDING_X * 2);
        int textWidth = 0;
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            textWidth = Math.max(textWidth, font.width(line));
        }

        int bubbleWidth = Math.max(textWidth + BUBBLE_PADDING_X * 2, BUBBLE_MAX_WIDTH / 2);
        int bubbleHeight = lines.size() * (font.lineHeight + 2) + BUBBLE_PADDING_Y * 2;
        bubbleHeight = Math.max(bubbleHeight, BUBBLE_MIN_HEIGHT);

        // 计算位置（屏幕底部居中）
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int bubbleX = (screenWidth - bubbleWidth) / 2;
        int bubbleY = screenHeight - bubbleHeight - 100 - (int) floatOffset;

        // 头像位置（左侧）
        int avatarX = bubbleX - AVATAR_SIZE - AVATAR_MARGIN;
        int avatarY = bubbleY + (bubbleHeight - AVATAR_SIZE) / 2;

        // 开始渲染
        guiGraphics.pose().pushPose();

        // 绘制背景
        renderBubbleBackground(guiGraphics, bubbleX, bubbleY, bubbleWidth, bubbleHeight);

        // 绘制头像（如果有）
        if (avatarTexture != null && DialogConfig.getInstance().avatarSize > 0) {
            renderAvatar(guiGraphics, avatarTexture, avatarX, avatarY, AVATAR_SIZE);
        }

        // 绘制文本
        renderBubbleText(guiGraphics, font, lines, bubbleX + BUBBLE_PADDING_X, bubbleY + BUBBLE_PADDING_Y);

        guiGraphics.pose().popPose();
    }

    /**
     * 渲染头像（带圆形裁切效果）
     */
    private void renderAvatar(GuiGraphics guiGraphics, ResourceLocation avatarTexture, int x, int y, int size) {
        Minecraft mc = Minecraft.getInstance();
        
        // 启用混合模式
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 计算圆形裁切的边界框
        int radius = size / 2;
        
        // 使用 Scissor 实现矩形裁切
        guiGraphics.enableScissor(x, y, x + size, y + size);

        // 绘制头像纹理
        guiGraphics.blit(avatarTexture, x, y, 0, 0, size, size);

        // 禁用裁切
        guiGraphics.disableScissor();

        // 绘制圆形边框
        renderCircularBorder(guiGraphics, x, y, size);

        // 恢复混合模式
        RenderSystem.disableBlend();
    }

    /**
     * 绘制圆形边框
     */
    private void renderCircularBorder(GuiGraphics guiGraphics, int x, int y, int size) {
        int borderColor = withAlpha(BORDER_COLOR, currentFadeAlpha);
        int borderWidth = 3;

        // 绘制圆形边框（四边）
        guiGraphics.fill(x - borderWidth, y - borderWidth, x + size + borderWidth, y - borderWidth + 3, borderColor);
        guiGraphics.fill(x - borderWidth, y + size - 2, x + size + borderWidth, y + size + borderWidth, borderColor);
        guiGraphics.fill(x - borderWidth, y, x - borderWidth + 3, y + size, borderColor);
        guiGraphics.fill(x + size - 2, y, x + size + borderWidth, y + size, borderColor);
    }

    /**
     * 渲染气泡背景（带圆角）
     */
    private void renderBubbleBackground(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        // 填充背景色
        int bgColor = withAlpha(BG_COLOR, currentFadeAlpha);
        
        // 绘制圆角矩形（用多个矩形拼接）
        int radius = CORNER_RADIUS;
        
        // 上边
        guiGraphics.fill(x, y, x + width, y + radius, bgColor);
        // 下边
        guiGraphics.fill(x, y + height - radius, x + width, y + height, bgColor);
        // 左边
        guiGraphics.fill(x, y + radius, x + radius, y + height - radius, bgColor);
        // 右边
        guiGraphics.fill(x + width - radius, y + radius, x + width, y + height - radius, bgColor);
        // 中心
        guiGraphics.fill(x + radius, y + radius, x + width - radius, y + height - radius, bgColor);

        // 绘制边框（圆角效果）
        int borderColor = withAlpha(BORDER_COLOR, currentFadeAlpha);
        int borderWidth = 1;
        
        // 上边框
        guiGraphics.fill(x, y, x + width, y + borderWidth, borderColor);
        // 下边框
        guiGraphics.fill(x, y + height - borderWidth, x + width, y + height, borderColor);
        // 左边框
        guiGraphics.fill(x, y, x + borderWidth, y + height, borderColor);
        // 右边框
        guiGraphics.fill(x + width - borderWidth, y, x + width, y + height, borderColor);
    }

    /**
     * 渲染气泡文本
     */
    private void renderBubbleText(GuiGraphics guiGraphics, Font font, 
                                   List<net.minecraft.util.FormattedCharSequence> lines, 
                                   int x, int y) {
        int lineHeight = font.lineHeight + 2;
        int textColor = withAlpha(TEXT_COLOR, currentFadeAlpha);

        for (int i = 0; i < lines.size(); i++) {
            guiGraphics.drawString(font, lines.get(i), x, y + i * lineHeight, textColor, true);
        }
    }

    /**
     * 为颜色添加透明度
     */
    private int withAlpha(int color, float alpha) {
        int a = (int) (alpha * 255);
        return (a << 24) | (color & 0xFFFFFF);
    }
}
