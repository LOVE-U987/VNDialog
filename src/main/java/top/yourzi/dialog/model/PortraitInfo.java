package top.yourzi.dialog.model;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PortraitInfo {
    @SerializedName("path")
    private String path; // 图片路径

    @SerializedName("position")
    private PortraitPosition position = PortraitPosition.RIGHT; // 立绘显示位置

    @SerializedName("brightness")
    private float brightness = 1.0f; // 立绘亮度

    @SerializedName("animationType")
    private PortraitAnimationType animationType = PortraitAnimationType.NONE; //动画类型

    @SerializedName("size")
    private float size = 1.0f; // 立绘缩放大小，范围0-5，默认为1

    public PortraitInfo() {
    }

    public PortraitInfo(String path, PortraitPosition position, float brightness, PortraitAnimationType animationType) {
        this.path = path;
        this.position = position;
        this.brightness = brightness;
        this.animationType = animationType;
        this.size = 1.0f;
    }

    public PortraitInfo(String path, PortraitPosition position, float brightness, PortraitAnimationType animationType, float size) {
        this.path = path;
        this.position = position;
        this.brightness = brightness;
        this.animationType = animationType;
        this.size = Math.max(0.0f, Math.min(5.0f, size));
    }

}