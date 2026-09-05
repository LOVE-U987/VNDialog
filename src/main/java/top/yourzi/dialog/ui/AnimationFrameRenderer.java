package top.yourzi.dialog.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.model.AnimationFrameData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * PNG 序列帧动画渲染器。
 * 支持加载多个 PNG 帧并循环播放。
 */
public class AnimationFrameRenderer {

    private final AnimationFrameData data;
    private final List<ResourceLocation> frameTextures;
    private int currentFrameIndex = 0;
    private long frameStartTime = -1;
    private boolean playing = false;
    private boolean finished = false;

    public AnimationFrameRenderer(AnimationFrameData data) {
        this.data = data;
        this.frameTextures = new ArrayList<>();
        loadFrames();
    }

    /**
     * 加载所有帧图片。
     */
    private void loadFrames() {
        if (data.getFrames() == null) return;

        for (String framePath : data.getFrames()) {
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "textures/" + framePath);
            Optional<?> resourceOptional = Minecraft.getInstance().getResourceManager().getResource(rl);
            if (resourceOptional.isPresent()) {
                frameTextures.add(rl);
            } else {
                // 帧不存在时跳过，使用空帧占位
                frameTextures.add(null);
            }
        }

        if (frameTextures.isEmpty()) {
            throw new IllegalArgumentException("No valid frames found for animation: " + data);
        }
    }

    /**
     * 开始播放动画。
     */
    public void start() {
        playing = true;
        finished = false;
        frameStartTime = System.currentTimeMillis() + data.getStartDelayMs();
        currentFrameIndex = 0;
    }

    /**
     * 停止播放动画。
     */
    public void stop() {
        playing = false;
        finished = true;
    }

    /**
     * 更新动画帧。
     */
    public void tick() {
        if (!playing || frameTextures.isEmpty()) return;

        long now = System.currentTimeMillis();

        // 处理启动延迟
        if (frameStartTime > now) {
            return;
        }

        // 检查帧切换
        long elapsedSinceFrameStart = now - frameStartTime;
        int frameDuration = data.getFrameDurationMs();

        if (elapsedSinceFrameStart >= frameDuration) {
            // 切换到下一帧
            currentFrameIndex++;

            if (currentFrameIndex >= frameTextures.size()) {
                if (data.isLoop()) {
                    currentFrameIndex = 0; // 循环
                } else {
                    currentFrameIndex = frameTextures.size() - 1; // 停在最后一帧
                    finished = true;
                    playing = false;
                    return;
                }
            }

            frameStartTime = now;
        }
    }

    /**
     * 渲染当前帧。
     */
    public void render(GuiGraphics graphics, int x, int y, int width, int height) {
        if (!playing || frameTextures.isEmpty() || finished) return;

        ResourceLocation currentTex = frameTextures.get(currentFrameIndex);
        if (currentTex == null) return; // 跳过无效帧

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, currentTex);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        graphics.blit(currentTex, x, y, 0, 0, width, height, width, height);

        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * 是否正在播放。
     */
    public boolean isPlaying() {
        return playing && !finished;
    }

    /**
     * 是否播放结束（非循环模式）。
     */
    public boolean isFinished() {
        return finished;
    }

    /**
     * 获取当前帧索引。
     */
    public int getCurrentFrameIndex() {
        return currentFrameIndex;
    }

    /**
     * 获取总帧数。
     */
    public int getTotalFrames() {
        return frameTextures.size();
    }
}
