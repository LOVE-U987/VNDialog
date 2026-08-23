package top.yourzi.dialog.model;

import com.google.gson.annotations.SerializedName;

/**
 * 封装背景信息。支持三种类型：
 *   - image   : 图片，使用 path + renderOption（沿用原逻辑）
 *   - gradient: 线性渐变，使用 color（顶部）与 colorBottom（底部）
 *   - color   : 纯色，使用 color
 * 类型缺省视为 image，保持向后兼容。
 */
public class BackgroundImageInfo {
    @SerializedName("type")
    private String type = "image"; // image / gradient / color

    @SerializedName("path")
    private String path;

    @SerializedName("renderOption")
    private BackgroundRenderOption renderOption;

    @SerializedName("color")
    private String colorTop = "#1a1a2e";

    @SerializedName("colorBottom")
    private String colorBottom;

    public BackgroundImageInfo() {}

    public BackgroundImageInfo(String path, BackgroundRenderOption renderOption) {
        this.path = path;
        this.renderOption = renderOption;
        this.type = "image";
    }

    public boolean isGradient() { return "gradient".equalsIgnoreCase(type); }
    public boolean isColor() { return "color".equalsIgnoreCase(type); }
    public boolean isImage() { return !isGradient() && !isColor(); }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public BackgroundRenderOption getRenderOption() { return renderOption; }
    public void setRenderOption(BackgroundRenderOption renderOption) { this.renderOption = renderOption; }

    public String getColorTop() { return colorTop; }
    public void setColorTop(String colorTop) { this.colorTop = colorTop; }

    public String getColorBottom() { return colorBottom != null ? colorBottom : colorTop; }
    public void setColorBottom(String colorBottom) { this.colorBottom = colorBottom; }
}