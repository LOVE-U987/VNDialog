package top.yourzi.dialog.ui.effects;

/**
 * 管理过场视觉特效的客户端单例。
 * <ul>
 *   <li>shake : 屏幕震动（trauma 衰减模型，幅度很小，仅作轻微提醒）。</li>
 *   <li>overlay：全屏覆盖层（flash 白闪 / fade 黑场 / tint 常驻色）。</li>
 * </ul>
 */
public class DialogEffectManager {
    private static final DialogEffectManager INSTANCE = new DialogEffectManager();

    /** 震动最大单轴幅度（度）。刻意很小。 */
    private static final float MAX_SHAKE_DEG = 0.5f;

    // ---- shake ----
    private float trauma = 0f;
    private float decayPerSecond = 1.0f;
    private long lastShakeMs = 0L;

    // ---- overlay ----
    private final java.util.List<Item> overlayQueue = new java.util.ArrayList<>();
    private int overlayColor = 0xFFFFFF; // RGB，不含 alpha
    private float overlayPeak = 1f;      // 峰值 alpha (0~1)
    private long overlayStartMs = 0L;
    private long overlayDurationMs = 400;
    private boolean overlayActive = false;

    private static final class Item {
        final int rgb; final float peak; final long dur;
        Item(int rgb, float peak, long dur) { this.rgb=rgb; this.peak=peak; this.dur=dur; }
    }

    private DialogEffectManager() {}

    public static DialogEffectManager getInstance() { return INSTANCE; }

    /** 将一个覆盖层特效入队（按顺序播放，避免同帧互相覆盖）。 */
    public void triggerOverlay(int rgb, float peak, long durationMs) {
        overlayQueue.add(new Item(rgb, peak, durationMs));
        if (!overlayActive) { startNextOverlay(); }
    }

    private void startNextOverlay() {
        if (overlayQueue.isEmpty()) { overlayActive = false; return; }
        Item it = overlayQueue.remove(0);
        this.overlayColor = it.rgb & 0xFFFFFF;
        this.overlayPeak = clamp01(it.peak);
        this.overlayDurationMs = Math.max(30, it.dur);
        this.overlayStartMs = System.currentTimeMillis();
        this.overlayActive = true;
    }

    public boolean isOverlayActive() { return overlayActive; }

    /** 当前覆盖层的 ARGB；无覆盖层或已结束返回 0，并自动开始下一个队列项。 */
    public int getOverlayColorARGB() {
        if (!overlayActive) { startNextIfAny(); return 0; }
        float t = (System.currentTimeMillis() - overlayStartMs) / (float) overlayDurationMs;
        if (t >= 1f) { overlayActive = false; startNextIfAny(); return 0; }
        float a;
        if (t < 0.4f) a = t / 0.4f;
        else if (t < 0.6f) a = 1f;
        else a = 1f - (t - 0.6f) / 0.4f;
        a = clamp01(a * overlayPeak);
        int alpha = (int) (a * 255);
        return (alpha << 24) | overlayColor;
    }

    private void startNextIfAny() {
        if (!overlayQueue.isEmpty()) { startNextOverlay(); }
    }

    public void reset() {
        overlayQueue.clear();
        this.overlayActive = false;
        this.lastShakeMs = 0L;
        this.trauma = 0f;
    }

    // ---- shake ----

    public void triggerShake(float intensity, int durationMs) {
        float v = Math.max(0f, Math.min(1f, intensity));
        float seconds = Math.max(0.05f, durationMs / 1000f);
        this.decayPerSecond = 1.0f / seconds;
        this.trauma = Math.min(1.0f, v);
        this.lastShakeMs = System.currentTimeMillis();
    }

    public boolean isShaking() { return trauma > 0.0001f; }

    public void tick() {
        if (!isShaking()) { lastShakeMs = 0L; return; }
        if (lastShakeMs == 0L) { lastShakeMs = System.currentTimeMillis(); return; }
        long now = System.currentTimeMillis();
        float delta = (now - lastShakeMs) / 1000f;
        lastShakeMs = now;
        trauma = Math.max(0f, trauma - decayPerSecond * delta);
    }

    public float getShakeOffset(int seed, float time) {
        if (!isShaking()) return 0f;
        float mag = (float) Math.pow(trauma, 2.0f) * MAX_SHAKE_DEG;
        return (float) Math.sin(time * 3.5f + seed * 1.7f) * mag;
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}