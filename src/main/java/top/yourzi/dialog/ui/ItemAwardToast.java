package top.yourzi.dialog.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 物品获得提示 Toast：在屏幕右上角短暂显示"获得物品 图标 ×数量"。
 */
public class ItemAwardToast implements Toast {
    private static final int WIDTH = 160;
    private static final int HEIGHT = 32;
    private final ItemStack stack;
    private final Component title;
    private final long displayMs = 2600L;

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
        // 半透明背景
        graphics.fill(0, 0, WIDTH, HEIGHT, 0xE0101010);
        // 图标 + 数量
        graphics.renderItem(stack, 8, 8);
        graphics.renderItemDecorations(toastComponent.getMinecraft().font, stack, 8, 8);
        // 文字
        graphics.drawString(toastComponent.getMinecraft().font, title, 30, 10, 0xFFFFFF, true);

        // sessionTimeMillis 为该 Toast 已显示的时长（毫秒）
        return sessionTimeMillis >= displayMs ? Toast.Visibility.HIDE : Toast.Visibility.SHOW;
    }
}