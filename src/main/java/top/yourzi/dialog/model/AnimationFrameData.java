package top.yourzi.dialog.model;

import com.google.gson.annotations.SerializedName;

/**
 * 序列帧动画数据：用于 PNG 序列帧动画配置。
 */
public class AnimationFrameData {

    @SerializedName("frames")
    private String[] frames; // 帧图片路径数组（相对于 textures/ 目录）

    @SerializedName("frame_duration")
    private int frameDurationMs = 100; // 每帧持续时间（毫秒）

    @SerializedName("loop")
    private boolean loop = true; // 是否循环

    @SerializedName("start_delay")
    private int startDelayMs = 0; // 启动延迟（毫秒）

    public String[] getFrames() {
        return frames;
    }

    public void setFrames(String[] frames) {
        this.frames = frames;
    }

    public int getFrameDurationMs() {
        return Math.max(10, frameDurationMs);
    }

    public void setFrameDurationMs(int frameDurationMs) {
        this.frameDurationMs = frameDurationMs;
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public int getStartDelayMs() {
        return Math.max(0, startDelayMs);
    }

    public void setStartDelayMs(int startDelayMs) {
        this.startDelayMs = startDelayMs;
    }

    public boolean isValid() {
        return frames != null && frames.length > 0;
    }
}
