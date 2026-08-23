package top.yourzi.dialog.ui;

import top.yourzi.dialog.model.PortraitAnimationData;
import top.yourzi.dialog.util.Easing;

/**
 * 根据 PortraitAnimationData 配置在给定时间上插值出立绘的位姿。
 * <p>
 * 所有动画默认从构造时起同时播放；move/scale/fade/rotate 互不影响，
 * 以便在 JSON 中并行声明多个动画叠加成复合效果。
 * <p>
 * 结果通过 {@link Pose} 返回：offsetX / offsetY 为像素平移增量，
 * scale 为缩放系数，rotation 为角度，alpha 为透明度，shaking 表示是否有抖动偏移。
 */
public class PortraitAnimator {

    public static class Pose {
        public float offsetX;
        public float offsetY;
        public float scale = 1f;
        public float rotation;
        public float alpha = 1f;
        public boolean animated = false;
        public boolean hasScale = false; // 是否出现了 scale 动画
        public float shakeX;
        public float shakeY;
    }

    private final PortraitAnimationData[] animations;
    private final long startTime;

    public PortraitAnimator(java.util.List<PortraitAnimationData> animations) {
        this.animations = animations == null ? new PortraitAnimationData[0]
                : animations.toArray(new PortraitAnimationData[0]);
        this.startTime = System.currentTimeMillis();
    }

    /**
     * 计算当前时刻的合成位姿。
     */
    public Pose compute(long nowMs) {
        Pose pose = new Pose();
        int time = (int) (nowMs - startTime);

        for (PortraitAnimationData anim : animations) {
            int elapsed = time - anim.getDelayMs();
            if (elapsed < 0) continue;
            int dur = anim.getDurationMs();
            float t = dur <= 0 ? 1f : (float) elapsed / dur;
            t = Math.max(0f, Math.min(1f, t));
            float e = Easing.fromName(anim.getEasing()).apply(t);

            switch (anim.getType().toLowerCase()) {
                case "move" -> {
                    pose.offsetX = lerp(anim.getFromX(), anim.getToX(), e);
                    pose.offsetY = lerp(anim.getFromY(), anim.getToY(), e);
                    pose.animated = true;
                }
                case "scale" -> {
                    pose.scale *= lerp(safeScale(anim.getFromScale()), anim.getToScale(), e);
                    pose.hasScale = true;
                    pose.animated = true;
                }
                case "fade" -> {
                    float from = anim.hasFromAlpha() ? anim.getFromAlpha() : 1f;
                    float to = anim.hasToAlpha() ? anim.getToAlpha() : 1f;
                    pose.alpha *= lerp(from, to, e);
                    pose.animated = true;
                }
                case "rotate" -> {
                    pose.rotation = lerp(anim.getFromRotation(), anim.getToRotation(), e);
                    pose.animated = true;
                }
                case "shake" -> {
                    float decay = 1f - Math.max(0f, Math.min(1f, (float) elapsed / dur));
                    if (decay > 0f) {
                        float raw = (float) Math.sin(elapsed * anim.getFrequency() * 0.1) * anim.getIntensity() * decay;
                        pose.shakeX = raw;
                        pose.shakeY = (float) Math.cos(elapsed * anim.getFrequency() * 0.13) * anim.getIntensity() * decay;
                        pose.animated = true;
                    }
                }
                default -> { /* 未知类型忽略 */ }
            }
        }
        return pose;
    }

    private static float safeScale(float s) {
        return s < 0.001f ? 1f : s;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}