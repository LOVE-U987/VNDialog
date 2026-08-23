package top.yourzi.dialog.model;

import com.google.gson.annotations.SerializedName;

/**
 * 对话条目的音效配置：背景音乐 / 音效 / 语音。
 * <p>示例：
 * <pre>{"bgm":"dialog:bgm_theme","se":"dialog:click","voice":"dialog:voice_1"}</pre>
 */
public class SoundInfo {

    @SerializedName("bgm")
    private String bgm; // 背景音乐 SoundEvent id（配合 bgm_action 使用）

    @SerializedName("bgm_action")
    private String bgmAction; // BGM 指令：play/switch、pause、resume、stop、next、prev；缺省=有 bgm 则播放

    @SerializedName("bgm_volume")
    private Float bgmVolume; // 本条覆盖的音量（0~1），可选

    @SerializedName("se")
    private String se; // 点击/提示音效

    @SerializedName("voice")
    private String voice; // 语音（说话语音）

    public String getBgm() { return bgm; }
    public void setBgm(String bgm) { this.bgm = bgm; }

    public String getBgmAction() { return bgmAction; }
    public void setBgmAction(String bgmAction) { this.bgmAction = bgmAction; }

    public Float getBgmVolume() { return bgmVolume; }
    public void setBgmVolume(Float bgmVolume) { this.bgmVolume = bgmVolume; }

    public String getSe() { return se; }
    public void setSe(String se) { this.se = se; }

    public String getVoice() { return voice; }
    public void setVoice(String voice) { this.voice = voice; }

    public boolean hasAny() {
        return (bgm != null && !bgm.isEmpty())
                || (bgmAction != null && !bgmAction.isEmpty())
                || (se != null && !se.isEmpty())
                || (voice != null && !voice.isEmpty());
    }
}