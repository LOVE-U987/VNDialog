package top.yourzi.dialog.model;

import com.google.gson.annotations.SerializedName;

/**
 * 对话条目的音效配置：背景音乐 / 音效 / 语音。
 * <p>示例：
 * <pre>{"bgm":"dialog:bgm_theme","se":"dialog:click","voice":"dialog:voice_1"}</pre>
 */
public class SoundInfo {

    @SerializedName("bgm")
    private String bgm; // 背景音乐 SoundEvent id（独占播放）

    @SerializedName("se")
    private String se; // 点击/提示音效

    @SerializedName("voice")
    private String voice; // 语音（说话语音）

    public String getBgm() { return bgm; }
    public void setBgm(String bgm) { this.bgm = bgm; }

    public String getSe() { return se; }
    public void setSe(String se) { this.se = se; }

    public String getVoice() { return voice; }
    public void setVoice(String voice) { this.voice = voice; }

    public boolean hasAny() {
        return (bgm != null && !bgm.isEmpty())
                || (se != null && !se.isEmpty())
                || (voice != null && !voice.isEmpty());
    }
}