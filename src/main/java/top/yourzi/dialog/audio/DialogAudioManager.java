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
 * <p>从 JSON 启播后持续播放，直到出现停止 / 切换指令；切换（play/switch/next/prev）使用淡入淡出过渡，
 * 停止同样淡出后结束。淡入淡出进度由界面逐帧调用 {@link #tick()} 推进。
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

    /** 曲名显示用。 */
    private static final String[] TRACK_NAMES = {
            "Theme", "Track 2", "Track 3", "Track 4"
    };

    /** 淡入淡出时长（毫秒）。 */
    private static final int FADE_MS = 900;

    private BgmSoundInstance currentBgm = null;
    private int currentIndex = 0;
    private boolean loop = true;
    private float bgmVolume = 1.0f;
    private boolean playing = false;
    private boolean paused = false;

    /** 当前正在播放的曲目 id（用于幂等判断，避免同一曲目重复触发淡入淡出）。 */
    private String currentBgmId = null;

    // ---- 淡入淡出状态机 ----
    private boolean fading = false;
    private long fadeStartMs = 0L;
    private float fadeFromVol = 0f;
    private float fadeToVol = 0f;
    /** 淡出结束后要淡入的曲目 id；非 null 表示"切歌"（淡出→淡入）。 */
    private String pendingSwitchId = null;
    /** 淡出结束后是否直接停止（stop 指令）。 */
    private boolean pendingStop = false;

    private DialogAudioManager() {}

    public static DialogAudioManager getInstance() { return INSTANCE; }

    // ================= BGM 控制（JSON 数据驱动） =================

    /**
     * 根据 JSON 的 bgm_action 调度 BGM 控制。
     * <p>取值：play/switch、pause、resume、stop、next、prev/previous；缺省且有 bgm 时视作 play。
     * @param action 指令（可 null）
     * @param bgmId  目标曲目（play/switch 使用，可 null）
     */
    public void handleAction(String action, String bgmId) {
        if (action == null || action.isEmpty()) {
            if (bgmId != null && !bgmId.isEmpty()) playBgOrSwitch(bgmId);
            return;
        }
        switch (action.trim().toLowerCase()) {
            case "play":
            case "switch":
            case "start":
                playBgOrSwitch(bgmId);
                break;
            case "pause":
                pause();
                break;
            case "resume":
                resume();
                break;
            case "stop":
                stopBgMusic();
                break;
            case "next":
                next();
                break;
            case "prev":
            case "previous":
                previous();
                break;
            default:
                Dialog.LOGGER.warn("Unknown BGM action '{}' ignored.", action);
                break;
        }
    }

    /**
     * 播放/切换到指定 BGM，并对此状态淡入淡出。
     * <p>若正在播放同一曲目则幂等跳过（不会再次触发淡入）。
     */
    public void playBgOrSwitch(String bgmId) {
        String tid = normalizedId(bgmId);
        if (tid == null) return;
        if (tid.equals(currentBgmId) && currentBgm != null && playing && !fading) {
            return; // 已是同一曲目，无需重复淡入
        }
        currentBgmId = tid;
        if (currentBgm != null && playing) {
            // 已有曲目在播：Fade 淡出当前，之后淡入新曲目
            beginFadeOutSwitch(tid);
        } else {
            beginFadeIn(tid);
        }
    }

    /** 停止 BG Mt，淡出后移除。 */
    public void stopBgMusic() {
        if (currentBgm == null || !playing) { clear(); return; }
        pendingSwitchId = null;
        pendingStop = true;
        fadeFromVol = currentVol();
        fadeToVol = 0f;
        fadeStartMs = System.currentTimeMillis();
        fading = true;
    }

    /** 暂停当前 BGM（置静音，保留位置）。 */
    public void pause() {
        if (currentBgm == null || !playing || paused) return;
        currentBgm.setVolume(0f);
        playing = false;
        paused = true;
    }

    /** 恢复播放。 */
    public void resume() {
        if (currentBgm == null) { beginFadeIn(currentBgmId); return; }
        if (!playing && paused) {
            currentBgm.setVolume(bgmVolume);
            playing = true;
            paused = false;
        }
    }

    /** 切到曲池下一首（淡入淡入）。 */
    public void next() {
        if (TRACKS.length == 0) return;
        currentIndex = (currentIndex + 1) % TRACKS.length;
        playBgOrSwitch(TRACKS[currentIndex]);
    }

    /** 切到曲池上一首（淡入）。 */
    public void previous() {
        if (TRACKS.length == 0) return;
        currentIndex = (currentIndex - 1 + TRACKS.length) % TRACKS.length;
        playBgOrSwitch(TRACKS[currentIndex]);
    }

    /** 音量 +delta（0~1）。 */
    public void adjustVolume(float delta) {
        bgmVolume = clamp(bgmVolume + delta, 0f, 1f);
        if (currentBgm != null && !fading) currentBgm.setVolume(bgmVolume);
    }

    /** 直接设置音量（0~1），JSON 数据驱动。 */
    public void setVolume(float v) {
        bgmVolume = clamp(v, 0f, 1f);
        if (currentBgm != null && !fading) currentBgm.setVolume(bgmVolume);
    }

    public float getVolume() { return bgmVolume; }

    public void toggleLoop() { loop = !loop; }
    public void setLoop(boolean b) { loop = b; }
    public boolean isLoop() { return loop; }

    public boolean isPlaying() { return playing; }
    public boolean isPaused() { return paused; }
    public boolean isFading() { return fading; }

    // ================= 淡入淡出推进 =================

    /**
     * 逐帧（每 tick）推进淡入淡出：调用者（DialogScreen 等）应在 tick 中调用。
     */
    public void tick() {
        if (!fading) return;
        long now = System.currentTimeMillis();
        float t = Math.min(1f, (now - fadeStartMs) / (float) FADE_MS);
        if (currentBgm != null) {
            currentBgm.setVolume(fadeFromVol + (fadeToVol - fadeFromVol) * t);
        }
        if (t >= 1f) {
            if (pendingSwitchId != null) {
                // 淡出完成 → 停止旧曲，淡入新曲
                stopTrack();
                beginFadeIn(pendingSwitchId);
                pendingSwitchId = null;
            } else if (pendingStop) {
                clear();
                pendingStop = false;
            } else {
                // 纯淡入完成
                fading = false;
            }
        }
    }

    // ================= 内部实现 =================

    /** 规整目标 id（null/空 -> 池当前）；未知返回该 id 交给 resolveBgm 处理。 */
    private String normalizedId(String bgmId) {
        if (bgmId == null || bgmId.isEmpty()) return TRACKS[currentIndex];
        return bgmId;
    }

    /** 开始淡入播放指定曲目。 */
    private void beginFadeIn(String id) {
        stopTrack(); // 保证单一实例
        SoundEvent se = resolveBgm(id);
        if (se == null) { currentBgmId = null; return; }
        BgmSoundInstance inst = new BgmSoundInstance(se, loop, 0f);
        Minecraft.getInstance().getSoundManager().play(inst);
        currentBgm = inst;
        playing = true;
        paused = false;
        fadeFromVol = 0f;
        fadeToVol = bgmVolume;
        fadeStartMs = System.currentTimeMillis();
        fading = true;
        pendingSwitchId = null;
        pendingStop = false;
    }

    /** 开始淡出当前，切换playId。 */
    private void beginFadeOutSwitch(String newId) {
        pendingSwitchId = newId;
        fadeFromVol = currentVol();
        fadeToVol = 0f;
        fadeStartMs = System.currentTimeMillis();
        fading = true;
        pendingStop = false;
    }

    /** 当前实际音量（取目标音量，淡入中取原始）。 */
    private float currentVol() {
        if (currentBgm == null) return bgmVolume;
        return bgmVolume; // 维持语义，淡出由 fadeToVol=0 控制
    }

    /** 停止当前实例并清理忽略状态。 */
    private void stopTrack() {
        if (currentBgm != null) {
            Minecraft.getInstance().getSoundManager().stop(currentBgm);
            currentBgm = null;
        }
    }

    /** 硬清理（停止 + 状态重置）。 */
    private void clear() {
        stopTrack();
        playing = false;
        paused = false;
        fading = false;
        currentBgmId = null;
    }

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

    /** 对话结束复位（直接停止，不淡出）。 */
    public void reset() {
        clear();
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

    /** 解析 SE / 语音等音效。未命中返回 null。 */
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

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    /** BGM 专用声音实例：暴露 setVolume 以支持淡入淡出。looping 由构造参数决定。 */
    private static final class BgmSoundInstance extends SimpleSoundInstance {
        BgmSoundInstance(SoundEvent event, boolean looping, float volume) {
            super(event.getLocation(), SoundSource.MUSIC, volume, 1.0F,
                    SoundInstance.createUnseededRandom(), looping, 0,
                    SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
        }
        void setVolume(float vol) {
            this.volume = vol;
        }
    }
}