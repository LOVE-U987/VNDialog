package top.yourzi.dialog.model;

import com.google.gson.annotations.SerializedName;

/**
 * 立绘的一条动画配置，由 JSON 驱动。
 * type 支持：
 *   - move   : 从 (fromX,fromY) -> (toX,toY) 平移
 *   - scale  : 缩放系数 fromScale -> toScale（相对基准缩放）
 *   - fade   : 透明度 fromAlpha -> toAlpha
 *   - rotate : 旋转角（度）fromRotation -> toRotation
 *   - shake  : 围绕当前坐标做缩放为 intensity 的抖动
 */
public class PortraitAnimationData {

    @SerializedName("type")
    private String type = "move";

    // 平移
    @SerializedName("fromX")
    private float fromX;
    @SerializedName("fromY")
    private float fromY;
    @SerializedName("toX")
    private float toX;
    @SerializedName("toY")
    private float toY;

    // 缩放 / 透明度 / 旋转
    // JSON 可用类型专属名（fromScale/toScale/...）或通用简写 "from"/"to"，
    // 由 PortraitAnimationDataDeserializer 根据 type 字段映射到正确字段。
    @SerializedName("fromScale")
    private float fromScale;
    @SerializedName("toScale")
    private float toScale;
    @SerializedName("fromAlpha")
    private float fromAlpha = Float.NaN;
    @SerializedName("toAlpha")
    private float toAlpha = Float.NaN;
    @SerializedName("fromRotation")
    private float fromRotation;
    @SerializedName("toRotation")
    private float toRotation;

    // shake 参数
    @SerializedName("intensity")
    private float intensity = 4f;
    @SerializedName("frequency")
    private float frequency = 0.08f;

    // 通用
    @SerializedName("duration")
    private int durationMs = 500;
    @SerializedName("delay")
    private int delayMs = 0;
    @SerializedName("easing")
    private String easing = "EASE_OUT_CUBIC";

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public float getFromX() { return fromX; }
    public float getFromY() { return fromY; }
    public float getToX() { return toX; }
    public float getToY() { return toY; }
    public float getFromScale() { return fromScale; }
    public float getToScale() { return toScale; }

    public boolean hasFromAlpha() { return !Float.isNaN(fromAlpha); }
    public float getFromAlpha() { return fromAlpha; }
    public boolean hasToAlpha() { return !Float.isNaN(toAlpha); }
    public float getToAlpha() { return toAlpha; }

    public float getFromRotation() { return fromRotation; }
    public float getToRotation() { return toRotation; }
    public float getIntensity() { return intensity; }
    public float getFrequency() { return frequency; }
    public int getDurationMs() { return Math.max(1, durationMs); }
    public int getDelayMs() { return delayMs; }
    public String getEasing() { return easing; }

    public void setFromX(float v) { this.fromX = v; }
    public void setFromY(float v) { this.fromY = v; }
    public void setToX(float v) { this.toX = v; }
    public void setToY(float v) { this.toY = v; }
    public void setFromScale(float v) { this.fromScale = v; }
    public void setToScale(float v) { this.toScale = v; }
    public void setFromAlpha(float v) { this.fromAlpha = v; }
    public void setToAlpha(float v) { this.toAlpha = v; }
    public void setFromRotation(float v) { this.fromRotation = v; }
    public void setToRotation(float v) { this.toRotation = v; }
    public void setIntensity(float v) { this.intensity = v; }
    public void setFrequency(float v) { this.frequency = v; }
    public void setDurationMs(int v) { this.durationMs = v; }
    public void setDelayMs(int v) { this.delayMs = v; }
    public void setEasing(String v) { this.easing = v; }
}