package top.yourzi.dialog.model;

import com.google.gson.annotations.SerializedName;

/**
 * 全屏过场配置。作用于对话条目：播放时全屏显示图片若干时间，随后淡出并自动继续。
 * <p>示例：<pre>{"image":"textures/cutscene/title.png","durationMs":1500,"fadeInMs":400,"fadeOutMs":400}</pre>
 */
public class CutsceneInfo {

    @SerializedName("image")
    private String image; // 图片路径，置于 assets/dialog/textures/ 下

    @SerializedName("duration")
    private int durationMs = 1200; // 全屏展示时长

    @SerializedName("fadeIn")
    private int fadeInMs = 400; // 淡入时长

    @SerializedName("fadeOut")
    private int fadeOutMs = 400; // 淡出时长

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public int getDurationMs() { return Math.max(0, durationMs); }
    public void setDurationMs(int v) { this.durationMs = v; }

    public int getFadeInMs() { return Math.max(0, fadeInMs); }
    public void setFadeInMs(int v) { this.fadeInMs = v; }

    public int getFadeOutMs() { return Math.max(0, fadeOutMs); }
    public void setFadeOutMs(int v) { this.fadeOutMs = v; }

    public boolean isValid() {
        return image != null && !image.isEmpty();
    }
}