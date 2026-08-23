package top.yourzi.dialog.ui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import top.yourzi.dialog.audio.DialogAudioManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话框上方的 BGM 控制条：上一首 / 播放暂停 / 下一首 / 停止 / 循环 / 音量±。
 * <p>每个按钮直接驱动 {@link DialogAudioManager}，与 JSON 数据驱动共用同一套 API。
 * <p>仅负责创建按钮与刷新文本；按钮注册到 DialogScreen 由其在 init 中完成（addRenderableWidget 为 protected）。
 */
public class BgmControlBar {

    private final List<Button> buttons = new ArrayList<>();
    private Button playButton;
    private Button loopButton;

    public BgmControlBar() {
    }

    /**
     * 创建并布局按钮（居中，紧贴对话框上边缘）。
     * @param dialogBoxX  对话框左上 X
     * @param dialogBoxY  对话框左上 Y
     * @param dialogWidth 对话框宽
     */
    public void createButtons(int dialogBoxX, int dialogBoxY, int dialogWidth) {
        DialogAudioManager audio = DialogAudioManager.getInstance();
        final int bw = 20;
        final int bh = 20;
        final int gap = 2;

        // 上一首 / 播放暂停 / 下一首 / 停止 / 循环 / 音量- / 音量+
        Button[] ordered;
        this.playButton = buildButton("⏯", this::togglePlayPause, "dialog.ui.bgm.pause");
        this.loopButton = buildButton("🔁", () -> { audio.toggleLoop(); refresh(); }, "dialog.ui.bgm.loop");
        ordered = new Button[]{
                buildButton("⏮", audio::previous, "dialog.ui.bgm.prev"),
                this.playButton,
                buildButton("⏭", audio::next, "dialog.ui.bgm.next"),
                buildButton("⏹", audio::stopBgm, "dialog.ui.bgm.stop"),
                this.loopButton,
                buildButton("－", () -> audio.adjustVolume(-0.1f), "dialog.ui.bgm.vol_down"),
                buildButton("＋", () -> audio.adjustVolume(0.1f), "dialog.ui.bgm.vol_up")
        };

        int total = (bw + gap) * ordered.length - gap;
        int startX = dialogBoxX + (dialogWidth - total) / 2;
        int y = dialogBoxY - bh - 4;

        buttons.clear();
        for (Button btn : ordered) {
            btn.setX(startX);
            btn.setY(y);
            buttons.add(btn);
            startX += bw + gap;
        }
        refresh();
    }

    private Button buildButton(String label, Runnable onClick, String tooltipKey) {
        return Button.builder(Component.literal(label), b -> onClick.run())
                .tooltip(Tooltip.create(Component.translatable(tooltipKey)))
                .build();
    }

    public List<Button> getButtons() { return buttons; }

    /** 刷新播放/暂停与循环按钮文本。 */
    public void refresh() {
        DialogAudioManager audio = DialogAudioManager.getInstance();
        if (playButton != null) {
            boolean paused = audio.isPaused() || !audio.isPlaying();
            playButton.setMessage(Component.literal(paused ? "⏯" : "⏸"));
        }
        if (loopButton != null) {
            loopButton.setMessage(Component.literal(audio.isLoop() ? "🔁" : "🔂"));
        }
    }

    private void togglePlayPause() {
        DialogAudioManager audio = DialogAudioManager.getInstance();
        if (audio.isPlaying() && !audio.isPaused()) {
            audio.pause();
        } else {
            audio.resume();
        }
        refresh();
    }
}