package top.yourzi.dialog.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.model.SoundInfo;

/**
 * 对话专属音频管理器（客户端单例）。
 * <p>BGM 采用"曲目池"模型：可播放/暂停/停止/切换上一首/下一首，支持音量调节与循环。
 * SE / 语音 / 打字音按条目触发。
 * <p>占位曲目映射到原版内置 SoundEvent，无需自备 .ogg 即可听到。
 * <p>新增：{@link #handleAction(String, String)} 指令调度，供 JSON 数据驱动与界面按钮共用。
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

    /** 曲名字面量（与 TRACKS 一一对应，用于界面显示）。 */
    private static final String[] TRACK_NAMES = {
            "Theme", "Track 2", "Track 3", "Track 4"
    };

    private BgmSoundInstance currentBgm = null;
    private int currentIndex = 0;
    private boolean loop = true;
    private float bgmVolume = 1.0f;
    private boolean playing = false;
    private boolean paused = false;

    private DialogAudioManager() {}

    public static DialogAudioManager getInstance() { return INSTANCE; }

    // ================= BGM 控制 =================

    /**
     * 根据指令字符串调度 BGM 控制（供 JSON 数据驱动与界面按钮共用）。
     * @param action 指令：play/switch、pause、resume、stop、next、prev/previous；null 且 bgmId 非空时视为 play。
     * @param bgmId  目标曲目 id（仅 play/switch 使用，可为 null）。
     */
    public void handleAction(String action, String bgmId) {
        if (action == null || action.isEmpty()) {
            if (bgmId != null && !bgmId.isEmpty()) playBgm(bgmId);
            return;
        }
        switch (action.trim().toLowerCase()) {
            case "play":
            case "switch":
                playBgm(bgmId);
                break;
            case "pause":
                pause();
                break;
            case "resume":
                resume();
                break;
            case "stop":
                stopBgm();
                break;
            case "next":
                next();
                break;
            case "prev":
            case "previous":
                previous();
                break;
            default:
                Dialog.LOGGER.warn("Unknown BGM action: {}. Ignored.", action);
                break;
        }
    }

    /**
     * 播放指定 BGM id（可为 null 则播放曲池当前索引）。
     * <p>若 id 属于曲池则直接定位；否则尝试按任意 SoundEvent 播放。
     */
    public void playBgm(String bgmId) {
        if (bgmId != null && !bgmId.isEmpty()) {
            int idx = find(bgmId);
            if (idx >= 0) {
                currentIndex = idx;
                startCurrent();
            } else {
                playCustom(bgmId);
            }
        } else {
            startCurrent();
        }
    }

    /** 开始播放曲池当前曲目。 */
    private void startCurrent() {
        Minecraft mc = Minecraft.getInstance();
        SoundManager sm = mc.getSoundManager();
        stopTrack();
        SoundEvent se = resolveBgm(TRACKS[currentIndex]);
        if (se == null) return;
        BgmSoundInstance inst = new BgmSoundInstance(se, loop, bgmVolume);
        sm.play(inst);
        currentBgm = inst;
        playing = true;
        paused = false;
    }

    /** 播放任意 BGM SoundEvent（不在曲池也）。 */
    private void playCustom(String id) {
        Minecraft mc = Minecraft.getInstance();
        SoundManager sm = mc.getSoundManager();
        SoundEvent se = resolveBgm(id);
        if (se == null) return;
        stopTrack();
        BgmSoundInstance inst = new BgmSoundInstance(se, loop, bgmVolume);
        sm.play(inst);
        currentBgm = inst;
        playing = true;
        paused = false;
    }

    /** 停止正在播放的 BGM 实例（不改变 playing 状态字段语义，供 start 复用）。 */
    private void stopTrack() {
        if (currentBgm != null) {
            Minecraft.getInstance().getSoundManager().stop(currentBgm);
            currentBgm = null;
        }
        paused = false;
    }

    /** 暂停当前 BGM（通过静音保留位置，不受全局 SoundManager 影响）。 */
    public void pause() {
        if (currentBgm == null || !playing || paused) return;
        currentBgm.setVolume(0f);
        playing = false;
        paused = true;
    }

    /** 恢复播放（还原音量）。 */
    public void resume() {
        if (currentBgm == null) { startCurrent(); return; }
        if (!playing && paused) {
            currentBgm.setVolume(bgmVolume);
            playing = true;
            paused = false;
        }
    }

    /** 停止 BGM。 */
    public void stopBgm() {
        stopTrack();
        playing = false;
        paused = false;
    }

    /** 切换上一首空闲。 */
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

    /** 音量 +delta（0~1），并实时应用。 */
    public void adjustVolume(float delta) {
        bgmVolume = Math.max(0f, Math.min(1f, bgmVolume + delta));
        if (currentBgm != null) currentBgm.setVolume(bgmVolume);
    }

    /** 直接设置音量（0~1），用于 JSON 数据驱动。 */
    public void setVolume(float v) {
        bgmVolume = Math.max(0f, Math.min(1f, v));
        if (currentBgm != null) currentBgm.setVolume(bgmVolume);
    }

    public float getVolume() { return bgmVolume; }

    /** 循环开关。 */
    public void toggleLoop() { loop = !loop; }
    public void setLoop(boolean b) { loop = b; }
    public boolean isLoop() { return loop; }

    public boolean isPlaying() { return playing; }
    public boolean isPaused() { return paused; }
    public int getCurrentTrack() { return currentIndex; }
    public int getTrackCount() { return TRACKS.length; }
    public String getTrackName() { return currentIndex >= 0 && currentIndex < TRACK_NAMES.length ? TRACK_NAMES[currentIndex] : ""; }
    public boolean hasActiveBgm() { return currentBgm != null; }

    // ============ SE / 语音 / 打字 ============

    public void playSe(String seId) {
        SoundEvent se = resolveEffect(seId);
        if (se != null) playUi(se);
    }

    public void playVoice(SoundInfo info) {
        String v = info == null ? null : info.getVoice();
        if (v != null && !v.isEmpty()) {
            SoundEvent se = resolveEffect(v);
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

    /** 解析 BGM 专用 id（曲池占位 + 自定义注册）。未命中返回 null。 */
    private SoundEvent resolveBgm(String id) {
        if (id == null || id.isEmpty()) return null;
        switch (id) {
            case "dialog:bgm_theme": return SoundEvents.MUSIC_DISC_CAT.value();
            case "dialog:bgm_track2": return SoundEvents.MUSIC_DISC_13.value();
            case "dialog:bgm_track3": return SoundEvents.MUSIC_DISC_CAT.value();
            case "dialog:bgm_track4": return SoundEvents.MUSIC_DISC_BLOCKS.value();
            default:
                try {
                    ResourceLocation rl = ResourceLocation.parse(id);
                    SoundEvent se = BuiltInRegistries.SOUND_EVENT.get(rl);
                    return se == null || se.getLocation().getPath().isEmpty() ? null : se;
                } catch (Exception e) {
                    return null;
                }
        }
    }

    /** 解析 SE / 语音等音效。未命中返回 null（避免乱播放）。 */
    private SoundEvent resolveEffect(String id) {
        if (id == null || id.isEmpty()) return null;
        switch (id) {
            case "dialog:se_click":
            case "dialog:se_confirm": return SoundEvents.UI_BUTTON_CLICK.value();
            case "dialog:type_sound": return SoundEvents.NOTE_BLOCK_PLING.value();
            case "dialog:voice_default": return SoundEvents.EXPERIENCE_ORB_PICKUP;
            default: break;
        }
        try {
            ResourceLocation rl = ResourceLocation.parse(id);
            SoundEvent se = BuiltInRegistries.SOUND_EVENT.get(rl);
            return se == null || se.getLocation() == null ? null : se;
        } catch (Exception e) {
            return null;
        }
    }

    private int find(String id) {
        for (int i = 0; i < TRACKS.length; i++) {
            if (TRACKS[i].equals(id)) return i;
        }
        return -1;
    }

    /**
     * BGM 专用声音实例：SimpleSoundInstance 的 volume 字段为 protected 且无公开 setter，
     * 故用子类暴露 setVolume 以便实时调节音量/静音暂停；looping 由构造参数决定。
     */
    private static class BgmSoundInstance extends SimpleSoundInstance {
        /**
         * 构造 BGM 实例（音乐源、无衰减、全局播放）。
         * @param event   目标 SoundEvent
         * @param looping 是否循环
         * @param volume  初始音量（0~1）
         */
        BgmSoundInstance(SoundEvent event, boolean looping, float volume) {
            super(event.getLocation(), SoundSource.MUSIC, volume, 1.0F,
                    SoundInstance.createUnseededRandom(), looping, 0,
                    SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
        }

        /**
         * 设置音量。
         * @param vol 目标音量（0~1）
         */
        void setVolume(float vol) {
            this.volume = vol;
        }
    }
}