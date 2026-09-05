package top.yourzi.dialog.util;

import java.util.Locale;
import java.util.function.Function;

/**
 * 缓动函数枚举，用于立绘/过渡动画的曲线控制。
 * 输入 t ∈ [0,1]，返回缓动后的进度（通常也在 [0,1]，部分函数可略微超界）。
 * <p>扩展自 Animated GUI，包含 8 种常用缓动曲线。</p>
 */
public enum Easing {
    LINEAR("Linear", t -> t),
    SINE("Smooth", t -> -(float) (Math.cos(Math.PI * t) - 1) / 2f),
    EASE_OUT("Ease-out", t -> {
        float u = 1 - t;
        return 1 - u * u * u;
    }),
    EASE_IN("Ease-in", t -> t * t * t),
    EASE_IN_OUT("Ease-in-out", t -> {
        return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2f;
    }),
    BACK("Overshoot", t -> {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        float u = t - 1;
        return 1 + c3 * (float) Math.pow(u, 3) + c1 * (float) Math.pow(u, 2);
    }),
    ELASTIC("Elastic", t -> {
        if (t == 0 || t == 1) return t;
        float c4 = (float) (2 * Math.PI / 3);
        return (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * c4) + 1);
    }),
    BOUNCE("Bounce", t -> {
        float n1 = 7.5625f;
        float d1 = 2.75f;
        if (t < 1 / d1) return n1 * t * t;
        else if (t < 2 / d1) {
            t -= 1.5f / d1;
            return n1 * t * t + 0.75f;
        } else if (t < 2.5 / d1) {
            t -= 2.25f / d1;
            return n1 * t * t + 0.9375f;
        } else {
            t -= 2.625f / d1;
            return n1 * t * t + 0.984375f;
        }
    }),
    // 额外添加 Quad 曲线
    EASE_IN_QUAD("Ease-in-Quad", t -> t * t),
    EASE_OUT_QUAD("Ease-out-Quad", t -> 1 - (1 - t) * (1 - t)),
    EASE_IN_OUT_QUAD("Ease-in-out-Quad", t -> t < 0.5 ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2);

    private final String name;
    private final Function<Float, Float> function;

    Easing(String name, Function<Float, Float> function) {
        this.name = name;
        this.function = function;
    }

    public String getName() {
        return name;
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
        // 尝试精确匹配
        try {
            return Easing.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // 尝试去除前后缀的匹配
            String cleaned = name.toUpperCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
            try {
                return Easing.valueOf(cleaned);
            } catch (IllegalArgumentException e2) {
                return LINEAR;
            }
        }
    }
}