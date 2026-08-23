package top.yourzi.dialog.event;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.ui.DialogScreen;
import top.yourzi.dialog.ui.effects.DialogEffectManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * 客户端渲染相关事件：为对话过场提供屏幕震动（相机角度偏移）。
 * <p>仅在当前打开的是 {@link DialogScreen} 时生效；当对话关闭或切换屏幕时立即停止震动。
 */
@EventBusSubscriber(modid = Dialog.MODID, value = Dist.CLIENT)
public class ClientRenderEventHandler {

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        DialogEffectManager fx = DialogEffectManager.getInstance();

        // 仅当当前屏幕是 DialogScreen（且不在历史记录/非空）时应用震动；否则复位并返回
        if (!(screen instanceof DialogScreen ds) || ds.isShowingHistory()) {
            fx.reset();
            return;
        }

        fx.tick();
        if (!fx.isShaking()) {
            return;
        }

        float partialTick = (float) event.getPartialTick();
        float time = 0f;
        if (mc.level != null) {
            time = mc.level.getGameTime() + partialTick;
        }
        event.setYaw(event.getYaw() + fx.getShakeOffset(1, time));
        event.setPitch(event.getPitch() + fx.getShakeOffset(2, time));
        event.setRoll(event.getRoll() + fx.getShakeOffset(3, time));
    }
}