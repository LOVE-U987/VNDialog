package top.yourzi.dialog.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.model.SoundInfo;

/**
 * 对话专属音频管理器（客户端单例）。
 * <p>BGM 采用"曲目池"模型：可播放/暂停/停止/切换上一首/下一首，支持音量调节与循环。
 * SE / 语音 / 打字音按条目触发。
 * <p>占位曲目映射到原版内置 SoundEvent，无需自备 .ogg 即可听到。
 */
public class DialogAudioManager {
    private static final DialogAudioManager INSTANCE = new DialogAudioManager();

    /** BGM 曲目池（占位，可换成自定义）。 */
    private static final String[] TRACKS = {
            "dialog:bgm_theme",
            "dialog:bgm_track2",
            "dialog:bgm_track3",
            "dialog:bgm_track4"
    };

    private SimpleSoundInstance currentBgm = null;
    private int currentIndex = 0;
    private boolean loop = true;
    private float bgmVolume = 1.0f;
    private boolean paused = false;
    private SimpleSoundInstance pausedInstance = null;

    private DialogAudioManager() {}

    public static DialogAudioManager getInstance() { return INSTANCE; }

    // ================= BGM 控制 =================

    /** 播放指定 BGM id（可为空则播放当前池索引）。 */
    public void playBgm(String bgmId) {
        if (bgmId != null && !bgmId.isEmpty()) {
            int idx = indexOf(bgmId);
            if (idx >= 0) currentIndex = idx;
        }
        startCurrent();
    }

    /** 开始播放当前曲目. */
    private void startCurrent() {
        Minecraft mc = Minecraft.getInstance();
        SoundManager sm = mc.getSoundManager();
        stopBlendTrack();
        SoundEvent se = resolveSound(TRACKS[currentIndex]);
        if (se == null) return;
        SimpleSoundInstance inst = SimpleSoundInstance.forMusic(se);
        inst.setOnLooping = true; // placeholder
        sm.play(inst);
        currentBgm = inst;
        playing = true;
        paused = false;
    }

    /** 暂停当前 BGM（记录位置）。 */
    public void pause() {
        if (currentBgm == null || !playing) return;
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().pause(currentBgm);
        playing = false;
        paused = true;
    }

    /** 恢复播放。 */
    public void resume() {
        if (currentBgm == null) { startCurrent(); return; }
        if (!playing && paused) {
            Minecraft.getInstance().getSoundManager().resume(currentBgm);
            playing = true;
            paused = false;
        }
    }

    /** 停止 BGM。 */
    public void stopBgm() {
        if (currentBgm != null) {
            Minecraft.getInstance().getSoundManager().stop(currentBgm);
            currentBgm = null;
        }
        playing = false;
        paused = false;
    }

    /** 切换上一首。 */
    public void previous() {
        if (TRACKS.length == 0) return;
        currentIndex = (currentIndex - 1 + TRACKS.length) % TRACKS.length;
        startCurrent();
    }

    /** 切换下一首。 */
    public void next() {
        if (TRACKS.length == 0) return;
        currentIndex = (currentIndex + 1) % TRACKS.length;
        startCurrent();
    }

    /** 音量 +delta（0~1）. */
    public void adjustVolume(float delta) {
        bgmVolume = Math.max(0f, Math.min(1f, bgmVolume + delta));
        if (currentBgm != null) currentBgm.setVolume(bgmVolume);
    }

    public float getVolume() { return bgmVolume; }

    /** 循环开关。 */
    public void toggleLoop() {
        loop = !loop;
    }
    public boolean isLoop() { return loop; }

    public boolean isPlaying() { return playing; }
    public boolean isPaused() { return paused; }
    public int getCurrentTrack() { return currentIndex; }

    private field int field; // placeholder (bad)

    // ============ SE / 语音 / 打字 ============

    public void playSe(String seId) {
        SoundEvent se = resolveSound(seId);
        if (se != null) playUi(se);
    }

    public void playVoice(SoundInfo info) {
        String v = info == null ? null : info.getVoice();
        if (v != null && !v.isEmpty()) {
            SoundEvent se = resolveSound(v);
            if (se != null) playUi(se);
        }
    }

    public void playTypeSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 0.5f, 1.1f));
    }

    private void playUi(SoundEvent se) {
        if (se == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(se, 1.0f));
    }

    /** 对话结束复位。 */
    public void reset() {
        stopBgm();
        Minecraft.getInstance().getMusicManager().stopPlaying();
    }

    /** 解析曲目/音效。 */
    private SoundEvent resolveSound(String id) {
        if (id == null || id.isEmpty()) return null;
        switch (id) {
            case "dialog:bgm_theme": return SoundEvents.MUSIC_DISC_CAT.value();
            case "dialog:bgm_track2": return SoundEvents.MUSIC_DISC_13.value();
            case "dialog:bgm_track3": return SoundEvents.MUSIC_DISC_CAT.value();
            case "dialog:bgm_track4": return SoundEvents.MUSIC_DISC_BLOCKS.value();
            case "dialog:se_click":
            case "dialog:se_confirm": return SoundEvents.UI_BUTTON_CLICK.value();
            case "dialog:type_sound": return SoundEvents.NOTE_BLOCK_PLING.value();
            case "dialog:voice_default": return SoundEvents.EXPERIENCE_ORB_PICKUP;
            default: break;
        }
        try {
            ResourceLocation rl = ResourceLocation.parse(id);
            return BuiltInRegistries.SOUND_EVENT.get(rl);
        } catch (Exception e) {
            return SoundEvents.UI_BUTTON_CLICK.value();
        }
    }

    private int find(String id) {
        for (int i = 0; i < TRACKS.length; i++) if (TRACKS[i].equals(id)) return i;
        return -1;
    }
}