package top.yourzi.dialog.model;

import com.google.gson.annotations.SerializedName;

/**
 * 对话条目的过场特效配置。
 * type 支持：
 *   - shake : 屏幕震动（沿用旧）
 *   - flash : 全屏闪光（白色或指定颜色），快速淡入淡出
 *   - fade  : 全屏短暂闪黑/闪暗（过场黑场）
 *   - tint  : 全屏叠加一层颜色（常驻 tint，淡入）
 */
public class DialogEffect {
    @SerializedName("type")
    private String type = "shake";

    @SerializedName("intensity")
    private float intensity = 0.5f;

    @SerializedName("duration")
    private int durationMs = 400;

    @SerializedName("color")
    private String color; // 仅 flash/tint 使用（十六进制 #rrggbb）

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public float getIntensity() { return intensity; }
    public void setIntensity(float intensity) { this.intensity = intensity; }
    public int getDurationMs() { return Math.max(1, durationMs); }
    public void setDurationMs(int durationMs) { this.durationMs = durationMs; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public boolean isShake() { return "shake".equalsIgnoreCase(type); }
    public boolean isFlash() { return "flash".equalsIgnoreCase(type); }
    public boolean isFade() { return "fade".equalsIgnoreCase(type); }
    public boolean isTint() { return "tint".equalsIgnoreCase(type); }
}