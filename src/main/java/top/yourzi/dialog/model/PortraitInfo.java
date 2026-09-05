package top.yourzi.dialog.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class PortraitInfo {
    @SerializedName("path")
    private String path; // 图片路径

    @SerializedName("position")
    private PortraitPosition position = PortraitPosition.RIGHT; // 立绘显示位置（兼容旧字段）

    @SerializedName("brightness")
    private float brightness = 1.0f; // 立绘亮度

    @SerializedName("animationType")
    private PortraitAnimationType animationType = PortraitAnimationType.NONE; // 旧版单动画类型（兼容）

    // ---- 新增：原生渲染核心字段（全部可选，向后兼容）----

    @SerializedName("x")
    private Float x; // 覆盖基准 x（像素，锚点为 anchor）为空则按 position 计算

    @SerializedName("y")
    private Float y; // 覆盖基准 y（像素）

    @SerializedName("scale")
    private Float scale = 1.0f; // 基准缩放

    @SerializedName("rotation")
    private Float rotation = 0f; // 基准旋转（度）

    @SerializedName("alpha")
    private Float alpha = 1.0f; // 基准透明度

    @SerializedName("zOrder")
    private Integer zOrder = 0; // 绘制层级（越大越靠上）

    @SerializedName("anchor")
    private String anchor = "bottom_center"; // bottom_center / center / bottom_left / bottom_right

    @SerializedName("animations")
    private List<PortraitAnimationData> animations; // JSON 驱动动画列表

    @SerializedName("animation_frames")
    private AnimationFrameData animationFrameData; // 序列帧动画数据

    public PortraitInfo() {
    }

    public PortraitInfo(String path, PortraitPosition position, float brightness, PortraitAnimationType animationType) {
        this.path = path;
        this.position = position;
        this.brightness = brightness;
        this.animationType = animationType;
    }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public PortraitPosition getPosition() { return position; }
    public void setPosition(PortraitPosition position) { this.position = position; }
    public float getBrightness() { return brightness; }
    public void setBrightness(float brightness) { this.brightness = brightness; }
    public PortraitAnimationType getAnimationType() { return animationType; }
    public void setAnimationType(PortraitAnimationType animationType) { this.animationType = animationType; }

    public Float getX() { return x; }
    public void setX(Float x) { this.x = x; }
    public Float getY() { return y; }
    public void setY(Float y) { this.y = y; }
    public Float getScale() { return scale != null ? scale : 1.0f; }
    public void setScale(Float scale) { this.scale = scale; }
    public Float getRotation() { return rotation != null ? rotation : 0f; }
    public void setRotation(Float rotation) { this.rotation = rotation; }
    public Float getAlpha() { return alpha != null ? alpha : 1.0f; }
    public void setAlpha(Float alpha) { this.alpha = alpha; }
    public Integer getZOrder() { return zOrder != null ? zOrder : 0; }
    public void setZOrder(Integer zOrder) { this.zOrder = zOrder; }
    public String getAnchor() { return anchor != null ? anchor : "bottom_center"; }
    public void setAnchor(String anchor) { this.anchor = anchor; }
    public List<PortraitAnimationData> getAnimations() { return animations; }
    public void setAnimations(List<PortraitAnimationData> animations) { this.animations = animations; }
    public AnimationFrameData getAnimationFrameData() { return animationFrameData; }
    public void setAnimationFrameData(AnimationFrameData animationFrameData) { this.animationFrameData = animationFrameData; }
}