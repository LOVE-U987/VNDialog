package top.yourzi.dialog.util;

/**
 * 核心 Tween 动画引擎，用于所有 UI 动画的平滑插值。
 * <p>每帧调用 {@link #update(long)} 推进动画并获取当前值。</p>
 */
public final class Tween {
    private float start, end, current;
    private long startMs;
    private int durationMs = 1;
    private Easing easing = Easing.LINEAR;
    private boolean active;

    public Tween() {}

    public Tween(float initial) {
        snap(initial);
    }

    /** 瞬间跳转到值（无动画） */
    public void snap(float value) {
        this.start = this.end = this.current = value;
        this.active = false;
    }

    public float current() {
        return current;
    }

    public boolean isActive() {
        return active;
    }

    public int getDurationMs() {
        return durationMs;
    }

    public Easing getEasing() {
        return easing;
    }

    /** 重新设定目标值；如果目标变化，自动从当前值平滑过渡 */
    public void retarget(float target, long now, int durationMs, Easing easing) {
        if (active && Math.abs(target - end) < 1.0e-4f) return;
        if (!active && Math.abs(target - current) < 1.0e-4f) {
            this.end = target;
            return;
        }
        this.start = current;
        this.end = target;
        this.startMs = now;
        this.durationMs = Math.max(1, durationMs);
        this.easing = easing;
        this.active = true;
    }

    /** 每帧调用，推进动画并返回当前值 */
    public float update(long now) {
        if (!active) return current;
        float t = (now - startMs) / (float) durationMs;
        if (t >= 1.0f) {
            current = end;
            active = false;
        } else if (t > 0.0f) {
            current = start + (end - start) * easing.apply(t);
        }
        return current;
    }

    /** 立即完成动画 */
    public void finish() {
        active = false;
        current = end;
    }

    /** 重置为初始状态 */
    public void reset() {
        active = false;
        current = start;
    }
}
