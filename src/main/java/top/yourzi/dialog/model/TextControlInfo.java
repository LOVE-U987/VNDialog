package top.yourzi.dialog.model;

import com.google.gson.annotations.SerializedName;

/**
 * 文本输出控制器：控制对话文本显示后的推进方式。
 * <ul>
 *   <li>wait_for_click : 等待玩家点击继续（默认行为）</li>
 *   <li>wait_for_input : 显示输入框，玩家输入后存储到指定变量，再继续</li>
 *   <li>auto_pause     : 文本显示完自动停顿 durationMs 后继续</li>
 * </ul>
 */
public class TextControlInfo {

    @SerializedName("type")
    private String type = "wait_for_click";

    @SerializedName("input_hint")
    private String inputHint = "";

    @SerializedName("variable")
    private String variable;

    @SerializedName("max_length")
    private int maxLength = 20;

    @SerializedName("duration")
    private int autoPauseMs = 1500;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isWaitForClick() { return type == null || "wait_for_click".equalsIgnoreCase(type); }
    public boolean isWaitForInput() { return "wait_for_input".equalsIgnoreCase(type); }
    public boolean isAutoPause() { return "auto_pause".equalsIgnoreCase(type); }

    public String getInputHint() { return inputHint != null ? inputHint : ""; }
    public void setInputHint(String v) { this.inputHint = v; }

    public String getVariable() { return variable; }
    public void setVariable(String v) { this.variable = v; }

    public int getMaxLength() { return Math.max(1, maxLength); }
    public void setMaxLength(int v) { this.maxLength = v; }

    public int getAutoPauseMs() { return Math.max(0, autoPauseMs); }
    public void setAutoPauseMs(int v) { this.autoPauseMs = v; }
}