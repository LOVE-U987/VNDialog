package top.yourzi.dialog.event;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.minecraft.resources.ResourceLocation;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.client.DialogRenderer;
import top.yourzi.dialog.client.DialogManager;

import org.lwjgl.glfw.GLFW;

/**
 * HUD 层注册处理器
 * 负责注册 VNDialog 的 HUD Overlay 层
 */
@EventBusSubscriber(modid = Dialog.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class HudLayerHandler {

    // 跳过对话按键
    public static final KeyMapping SKIP_KEY = new KeyMapping(
        "key.vndialog.skip",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_V,  // 默认按 V 键跳过
        "category.vndialog"
    );

    /**
     * 注册自定义对话 HUD 层
     * 在聊天栏之上渲染，确保对话不会被其他 UI 遮挡
     */
    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        // 在文本覆盖层之上注册对话层
        event.registerAbove(
            net.neoforged.neoforge.client.gui.VanillaGuiLayers.TEXT_OVERLAY,
            ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "dialog_overlay"),
            (guiGraphics, deltaTracker) -> {
                DialogRenderer.getInstance().render(guiGraphics, deltaTracker);
            }
        );
    }

    /**
     * 客户端 tick 事件处理器
     * 每 tick 更新对话管理器的状态机
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        DialogManager.getInstance().tick();
    }

    /**
     * 输入事件处理器（按键绑定）
     */
    @SubscribeEvent
    public static void onInputEvent(InputEvent.Key event) {
        if (SKIP_KEY.consumeClick()) {
            DialogManager.getInstance().skip();
        }
    }
}
