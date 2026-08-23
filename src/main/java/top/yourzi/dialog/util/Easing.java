package top.yourzi.dialog.util;

import java.util.Locale;
import java.util.function.Function;

/**
 * 缓动函数枚举，用于立绘/过渡动画的曲线控制。
 * 输入 t ∈ [0,1]，返回缓动后的进度（通常也在 [0,1]，部分函数可略微超界）。
 */
public enum Easing {
    LINEAR(t -> t),
    EASE_IN_QUAD(t -> t * t),
    EASE_OUT_QUAD(t -> 1 - (1 - t) * (1 - t)),
    EASE_IN_OUT_QUAD(t -> t < 0.5 ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2),
    EASE_IN_CUBIC(t -> t * t * t),
    EASE_OUT_CUBIC(t -> 1 - (float) Math.pow(1 - t, 3)),
    EASE_IN_OUT_CUBIC(t -> t < 0.5 ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2),
    EASE_OUT_BACK(t -> {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }),
    EASE_OUT_ELASTIC(t -> {
        if (t == 0 || t == 1) return t;
        float p = 0.3f;
        return (float) (Math.pow(2, -10 * t) * Math.sin((t - p / 4) * (2 * Math.PI) / p) + 1);
    }),
    EASE_OUT_BOUNCE(t -> {
        float n1 = 7.5625f;
        float d1 = 2.75f;
        if (t < 1 / d1) return n1 * t * t;
        if (t < 2 / d1) return n1 * (t -= 1.5f / d1) * t + 0.75f;
        if (t < 2.5 / d1) return n1 * (t -= 2.25f / d1) * t + 0.9375f;
        return n1 * (t -= 2.625f / d1) * t + 0.984375f;
    });

    private final Function<Float, Float> function;

    Easing(Function<Float, Float> function) {
        this.function = function;
    }

    public float apply(float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        return function.apply(clamped);
    }

    /**
     * 根据名称获取缓动函数，大小写不敏感；未知名称返回 LINEAR。
     */
    public static Easing fromName(String name) {
        if (name == null) return LINEAR;
        try {
            return Easing.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return LINEAR;
        }
    }
}