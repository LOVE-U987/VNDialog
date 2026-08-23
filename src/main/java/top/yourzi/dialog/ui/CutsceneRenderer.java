package top.yourzi.dialog.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.model.CutsceneInfo;

import java.io.InputStream;
import java.util.Optional;

/**
 * 全屏过场动画渲染器：覆盖整个屏幕显示一张图，按 fadeIn / duration / fadeOut 控制透明度，
 * 结束后由 DialogScreen 自动继续。由 DialogScreen 驱动 init/render/isFinished。
 */
public class CutsceneRenderer {
    private final CutsceneInfo info;
    private final ResourceLocation tex;
    private long startTime = 0L;

    public CutsceneRenderer(CutsceneInfo info) {
        this.info = info;
        this.tex = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "textures/" + info.getImage());
    }

    /** 开始计时（进入渲染前调用一次）。 */
    public void init() {
        startTime = System.currentTimeMillis();
    }

    /** 是否播放结束（fadeIn + duration + fadeOut 全部过后）。 */
    public boolean isFinished() {
        return System.currentTimeMillis() - startTime >= totalMs();
    }

    /** 当前透明度（0~1）。 */
    public float alpha() {
        long passed = System.currentTimeMillis() - startTime;
        int fadeIn = info.getFadeInMs();
        int holdEnd = fadeIn + info.getDurationMs();
        int total = totalMs();
        if (passed < fadeIn) return clamp01((float) passed / Math.max(1, fadeIn));
        if (passed < holdEnd) return 1f;
        return clamp01(1f - (float) (passed - holdEnd) / Math.max(1, info.getFadeOutMs()));
    }

    private int totalMs() {
        return info.getFadeInMs() + info.getDurationMs() + info.getFadeOutMs();
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    public void render(GuiGraphics g, int screenW, int screenH) {
        float a = alpha();
        if (a <= 0f) return;
        // 若指定的过场图不存在，回退到内置背景图作为占位，保证能看到全屏画面
        boolean resolved = Minecraft.getInstance().getResourceManager().getResource(tex).isPresent();
        ResourceLocation drawTex = resolved ? tex :
                ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "textures/backgrounds/background.png");
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, drawTex);
        RenderSystem.setShaderColor(1f, 1f, 1f, a);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(drawTex, 0, 0, 0f, 0f, screenW, screenH, screenW, screenH);
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }
}