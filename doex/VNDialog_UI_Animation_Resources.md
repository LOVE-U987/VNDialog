# VNDialog UI动画与风格修改 — 资料支持文档

> 本资料针对 VNDialog（Minecraft 视觉小说对话引擎模组）的 UI 动画化与风格改造需求整理，涵盖圆角UI、弹窗动画、退出提示、缓动系统等核心技术方案。

---

## 一、核心参考项目（强烈推荐研读源码）

### 1. Animated GUI ⭐ 首选参考
- **GitHub**: https://github.com/dev-limucc/animated-gui
- **特点**: Fabric 1.21、完整的客户端GUI动画系统、MIT协议可自由复用
- **核心贡献**: 
  - 提供 **可复用的UI风格库**（`STYLE.md` 文档完整且可直接复制）
  - 独立的 `Tween` + `Easing` 动画引擎
  - 屏幕打开/关闭动画（Scale/Slide/Fade）
  - 延迟关闭（Deferred Close）实现退出动画
  - 8种缓动曲线：Linear、Smooth(Sine)、Ease-out、Ease-in、Ease-in-out、Overshoot(Back)、Elastic、Bounce

### 2. Modern UI
- **GitHub/Modrinth**: Modern UI by Icyllis Milica
- **特点**: Forge/Fabric 双端、圆角Tooltip、高斯模糊背景、屏幕背景淡入动画
- **核心贡献**:
  - 圆角边框渲染（Rounded Border）
  - 2-Pass 高斯卷积模糊（Gaussian Blur）
  - GUI背景渐变与淡入动画
  - 平滑滚动（Smooth Scrolling）

### 3. Smooth GUI Animations / Smooth Gui / GUI Tween
- **Modrinth/CurseForge**: 搜索 "Smooth GUI Animations" / "GUI Tween"
- **特点**: 轻量级、全界面缩放/滑入动画
- **核心贡献**: 所有GUI统一动画入口的实现思路

### 4. ReBlured / Blur Mod
- **特点**: 为GUI背景添加高斯模糊Shader
- **核心贡献**: 背景模糊Shader的加载与渲染管线集成方案

---

## 二、动画系统核心架构

### 2.1 缓动曲线（Easing）
以下曲线可直接复用（来自 Animated GUI 的 `Easing.java`）：

```java
public enum Easing {
    LINEAR("Linear")        { public float apply(float t) { return t; } },
    SINE("Smooth")          { public float apply(float t) { return -(float)(Math.cos(Math.PI * t) - 1) / 2f; } },
    EASE_OUT("Ease-out")    { public float apply(float t) { float u = 1 - t; return 1 - u * u * u; } },
    EASE_IN("Ease-in")      { public float apply(float t) { return t * t * t; } },
    EASE_IN_OUT("Ease-in-out") {
        public float apply(float t) {
            return t < 0.5f ? 4 * t * t * t : 1 - (float)Math.pow(-2 * t + 2, 3) / 2f;
        }
    },
    BACK("Overshoot") {
        public float apply(float t) {
            final float c1 = 1.70158f, c3 = c1 + 1; float u = t - 1;
            return 1 + c3 * u * u * u + c1 * u * u;
        }
    },
    ELASTIC("Elastic") {
        public float apply(float t) {
            if (t == 0 || t == 1) return t;
            final float c4 = (float)(2 * Math.PI / 3);
            return (float)(Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * c4) + 1);
        }
    },
    BOUNCE("Bounce") {
        public float apply(float t) {
            final float n1 = 7.5625f, d1 = 2.75f;
            if (t < 1 / d1)        return n1 * t * t;
            else if (t < 2 / d1)   { t -= 1.5f / d1;  return n1 * t * t + 0.75f; }
            else if (t < 2.5 / d1) { t -= 2.25f / d1; return n1 * t * t + 0.9375f; }
            else                   { t -= 2.625f / d1; return n1 * t * t + 0.984375f; }
        }
    };

    public abstract float apply(float t);
    public float clampApply(float t) { return apply(t < 0 ? 0 : (t > 1 ? 1 : t)); }
}
```

**选用建议**:
- 弹窗进入: `Ease-out`（自然减速）或 `Overshoot`（弹性弹出）
- 弹窗退出: `Ease-in`（加速离开）
- 立绘切换: `Smooth`（Sine，柔和）
- 按钮悬停: `Ease-out` 或 `Ease-in-out`

### 2.2 Tween 动画引擎
核心类，所有动画值都通过一个 `Tween` 实例管理：

```java
public final class Tween {
    private float start, end, current;
    private long startMs;
    private int durationMs = 1;
    private Easing easing = Easing.LINEAR;
    private boolean active;

    public Tween() {}
    public Tween(float initial) { snap(initial); }

    /** 瞬间跳转到值（无动画） */
    public void snap(float value) { 
        this.start = this.end = this.current = value; 
        this.active = false; 
    }

    public float current() { return current; }
    public boolean isActive() { return active; }

    /** 重新设定目标值；如果目标变化，自动从当前值平滑过渡 */
    public void retarget(float target, long now, int durationMs, Easing easing) {
        if (active && Math.abs(target - end) < 1.0e-4f) return;
        if (!active && Math.abs(target - current) < 1.0e-4f) { this.end = target; return; }
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
        if (t >= 1.0f)      { current = end; active = false; }
        else if (t > 0.0f)  { current = start + (end - start) * easing.apply(t); }
        return current;
    }
}
```

**使用模式**（每帧固定套路）：
```java
long now = Util.getMillis();
tween.retarget(goalValue, now, 300, Easing.EASE_OUT); // 目标值、当前时间、持续时间(ms)、曲线
float v = tween.update(now);                          // 获取插值结果用于渲染
```

### 2.3 颜色插值（ARGB通道分别插值）
用于按钮悬停变色、背景过渡等：
```java
private static int lerpColor(int a, int b, float t) {
    t = t < 0 ? 0 : (t > 1 ? 1 : t);
    int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
    int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
    return (Math.round(aa + (ba - aa) * t) << 24) 
         | (Math.round(ar + (br - ar) * t) << 16)
         | (Math.round(ag + (bg - ag) * t) << 8)  
         |  Math.round(ab + (bb - ab) * t);
}
```

---

## 三、圆角UI实现方案

Minecraft原生GUI渲染基于矩形填充（`GuiGraphics.fill()`），不直接支持圆角。以下是几种实现方案：

### 方案A：预渲染圆角纹理（推荐，性能最好）
- 制作一张带圆角的9-patch纹理图（或使用 `nine_slice` 缩放）
- 在 `textures/gui/sprites/` 下放置，通过 `blitSprite` 渲染
- 优点：零运行时开销、兼容性好
- 缺点：圆角半径固定、换色需多张纹理

### 方案B：SDF Shader 圆角矩形（最灵活，适合动态圆角）
使用 Signed Distance Field 算法在片段着色器中裁剪圆角：

```glsl
// 圆角矩形 SDF 函数（可在 RenderPipeline 中使用）
float roundedRectangleSDF(vec2 samplePosition, vec2 halfSize, float radius) {
    vec2 distanceToEdge = abs(samplePosition) - halfSize + radius;
    float outsideDistance = length(max(distanceToEdge, 0.0));
    float insideDistance = min(max(distanceToEdge.x, distanceToEdge.y), 0.0);
    return outsideDistance + insideDistance - radius;
}

// 在片段着色器中
void main() {
    vec2 center = vec2(0.5);
    vec2 halfSize = vec2(width * 0.5, height * 0.5);
    float dist = roundedRectangleSDF(gl_TexCoord[0].xy - center, halfSize, radius);
    float alpha = 1.0 - smoothstep(0.0, 1.0, dist);
    // ... 输出颜色
}
```

**参考**: Modern UI 的圆角Tooltip实现、Unity Shader Graph 的 Rounded Rectangle Node 算法。

### 方案C：多段填充模拟圆角（纯Java，无需Shader）
用多个小矩形+扇形近似圆角，适合简单圆角：
```java
// 绘制圆角矩形主体
graphics.fill(x + radius, y, x + width - radius, y + height, color);      // 中心
graphics.fill(x, y + radius, x + radius, y + height - radius, color);      // 左
graphics.fill(x + width - radius, y + radius, x + width, y + height - radius, color); // 右
// 四个角用较小的矩形或自定义顶点近似（较繁琐，不推荐）
```

**建议**: VNDialog 作为对话引擎，UI元素相对固定，**方案A（预渲染纹理）+ 方案B（关键动态元素用Shader）** 是最佳组合。

---

## 四、弹窗/对话框动画实现

### 4.1 进入动画（Open Animation）
在 `Screen` 的 `init()` 或 `added()` 中启动：

```java
public class DialogScreen extends Screen {
    private final Tween openProgress = new Tween(0.0f);
    private long openStartMs;
    private static final int OPEN_DURATION = 350; // ms

    @Override
    protected void init() {
        super.init();
        openStartMs = Util.getMillis();
        openProgress.snap(0.0f);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        float t = Math.min(1.0f, (now - openStartMs) / (float) OPEN_DURATION);
        float p = Easing.EASE_OUT.clampApply(t); // 已缓动的进度 0→1

        // 应用整体变换：缩放 + 淡入
        graphics.pose().pushMatrix();
        float scale = 0.85f + 0.15f * p; // 从0.85缩放到1.0
        float alpha = p;                  // 透明度 0→1

        // 以屏幕中心为锚点缩放
        float cx = width / 2f, cy = height / 2f;
        graphics.pose().translate(cx, cy);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-cx, -cy);

        // 绘制对话框背景（带透明度）
        int bgColor = (Math.round(alpha * 0xEE) << 24) | 0x0015161B; // 动态alpha
        drawDialogBackground(graphics, bgColor);

        graphics.pose().popMatrix();
    }
}
```

### 4.2 退出动画（Close Animation）— 关键难点
Minecraft 默认关闭Screen是瞬间的，要实现退出动画需要**延迟关闭**：

```java
public class DialogScreen extends Screen {
    private boolean closing = false;
    private long closeStartMs;
    private static final int CLOSE_DURATION = 250;

    @Override
    public boolean shouldCloseOnEsc() { return !closing; } // 动画期间阻止ESC直接关闭

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !closing) {
            startCloseAnimation();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void startCloseAnimation() {
        closing = true;
        closeStartMs = Util.getMillis();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        float p = 1.0f;

        if (closing) {
            float t = Math.min(1.0f, (now - closeStartMs) / (float) CLOSE_DURATION);
            p = 1.0f - Easing.EASE_IN.clampApply(t); // 1→0
            if (t >= 1.0f) {
                super.onClose(); // 动画结束后真正关闭
                return;
            }
        } else {
            // 进入动画逻辑...
        }

        // 使用 p 渲染缩放/透明度递减的对话框
        graphics.pose().pushMatrix();
        float scale = 0.9f + 0.1f * p;
        // ... 变换与渲染
        graphics.pose().popMatrix();
    }
}
```

### 4.3 弹窗类型动画效果参考

| 效果 | 进入 | 退出 | 适用场景 |
|------|------|------|----------|
| **Scale Pop** | Scale 0.8→1.0 + Overshoot | Scale 1.0→0.9 + Fade | 选项弹窗、确认框 |
| **Slide Up** | TranslateY 30px→0 + Fade | TranslateY 0→-20px + Fade | 底部对话框 |
| **Fade Center** | Opacity 0→1 + Scale 0.96→1 | Opacity 1→0 | 提示Toast |
| **Backdrop Blur** | Blur半径 0→8 + 暗度 0→40% | 反向 | 全屏模态框 |

---

## 五、圆角退出提示（Toast/Notification）

### 5.1 设计要点
- 圆角矩形背景（使用预渲染纹理或Shader）
- 从屏幕边缘滑入/滑出
- 自动消失倒计时动画（进度条或整体淡出）
- 支持手动提前关闭

### 5.2 实现骨架
```java
public class RoundedToast {
    private final Tween yOffset = new Tween();      // 垂直位移
    private final Tween alpha = new Tween();        // 透明度
    private final Tween progress = new Tween();     // 倒计时进度
    private long showStartMs;
    private static final int SLIDE_DURATION = 300;
    private static final int DISPLAY_DURATION = 3000;

    public void show() {
        long now = Util.getMillis();
        yOffset.retarget(0, now, SLIDE_DURATION, Easing.EASE_OUT);
        alpha.retarget(1, now, SLIDE_DURATION, Easing.EASE_OUT);
        progress.retarget(1, now, DISPLAY_DURATION, Easing.LINEAR);
        showStartMs = now;
    }

    public void dismiss() {
        long now = Util.getMillis();
        yOffset.retarget(30, now, 200, Easing.EASE_IN); // 向下滑出
        alpha.retarget(0, now, 200, Easing.EASE_IN);
    }

    public void render(GuiGraphics g, int screenWidth, int screenHeight) {
        long now = Util.getMillis();
        float y = yOffset.update(now);
        float a = alpha.update(now);
        float prog = progress.update(now);

        if (a <= 0.01f) return;

        int toastW = 240, toastH = 48;
        int x = (screenWidth - toastW) / 2;
        int yPos = screenHeight - 80 + Math.round(y); // 底部居中 + 偏移

        // 绘制圆角背景（使用预渲染纹理 blitSprite）
        int bgColor = (Math.round(a * 0xDD) << 24) | 0x0015151B;
        drawRoundedBackground(g, x, yPos, toastW, toastH, 8, bgColor);

        // 倒计时进度条（底部细线）
        int barWidth = Math.round(toastW * prog);
        int barColor = 0xFF3A6EA5; // 强调色
        g.fill(x, yPos + toastH - 2, x + barWidth, yPos + toastH, 
               (Math.round(a * 0xFF) << 24) | (barColor & 0x00FFFFFF));

        // 文字（带透明度）
        String msg = "按 ESC 退出对话";
        int textColor = (Math.round(a * 0xFF) << 24) | 0x00E6E6EA;
        g.drawString(Minecraft.getInstance().font, msg, 
                     x + 16, yPos + (toastH - 8) / 2, textColor);
    }
}
```

---

## 六、背景模糊（Gaussian Blur）集成

### 6.1 参考实现
Modern UI 和 Blur Mod 都使用 **2-Pass Gaussian Blur**：
1. 将当前帧缓冲（Framebuffer）内容复制到纹理
2. 第一遍：水平方向高斯模糊
3. 第二遍：垂直方向高斯模糊
4. 将结果作为GUI背景渲染

### 6.2 简化方案（适用于对话背景）
如果不需要全屏模糊，可在对话开启时：
- 截屏并降低亮度/饱和度作为静态背景
- 使用半透明暗色遮罩（`0xAA000000`）+ 轻微模糊纹理
- VNDialog 已有 `background_image` 支持，可扩展为支持 `blur_amount` 字段

---

## 七、VNDialog 具体改造建议

### 7.1 JSON配置扩展（向后兼容）
在对话JSON中新增动画配置字段：
```json
{
  "id": "hello_world",
  "animation": {
    "dialog_enter": "SCALE_POP",
    "dialog_exit": "FADE_OUT",
    "option_slide": true,
    "portrait_transition": "FADE_IN",
    "background_blur": 0.5
  },
  "entries": [...]
}
```

### 7.2 立绘动画增强
现有 `animationType` 可扩展更多类型：
- `SLIDE_IN_FROM_LEFT` / `SLIDE_IN_FROM_RIGHT`
- `SCALE_POP`（弹性放大出现）
- `FLIP_IN`（Y轴翻转进入）
- `BLUR_IN`（从模糊到清晰）

### 7.3 选项按钮动画
- 选项列表整体 `Slide Up` 进入
- 单个选项 `Stagger` 错开出现（每个延迟50ms）
- 悬停时 `Scale 1.0→1.02` + 颜色插值

### 7.4 退出提示框
- 圆角矩形背景（8px圆角）
- 从底部 `Slide Up` 进入
- 3秒倒计时进度条
- 按ESC或点击后 `Fade Out` + `Slide Down` 消失

---

## 八、文件组织建议

```
client/
├── animation/
│   ├── Easing.java          # 缓动曲线枚举
│   ├── Tween.java           # 核心插值引擎
│   └── AnimationType.java   # VNDialog专用动画类型
├── gui/
│   ├── dialog/
│   │   ├── DialogScreen.java
│   │   └── DialogRenderer.java
│   ├── widget/
│   │   ├── FlatButton.java      # 扁平按钮（来自Animated GUI）
│   │   ├── RoundedPanel.java    # 圆角面板渲染器
│   │   └── ToastNotification.java # 退出提示
│   └── shader/
│       ├── rounded_rect.fsh     # 圆角片段着色器
│       └── gaussian_blur.fsh    # 高斯模糊（可选）
```

---

## 九、关键注意事项

1. **帧率独立**: 始终使用 `Util.getMillis()` 驱动动画，不要用 `tick` 或 `frame` 计数
2. **Retarget安全**: `Tween.retarget()` 可在每帧调用，目标不变时无开销；目标突变时自动从当前值平滑过渡，不会跳跃
3. **延迟关闭**: 退出动画必须通过延迟关闭实现（拦截 `onClose()`，等动画结束再真正关闭Screen）
4. **Scissor裁剪**: 内容滑动进入时，用 `graphics.enableScissor()` 限制在面板内，防止溢出
5. **降级兼容**: 提供 `animation_enabled` 配置项，低性能设备可关闭动画回退到瞬间切换
6. **颜色格式**: Minecraft使用 `0xAARRGGBB` 格式，注意Alpha通道在最高字节

---

## 十、快速开始检查清单

- [ ] 复制 `Easing.java` + `Tween.java` 到项目（来自 Animated GUI，MIT协议）
- [ ] 为 `DialogScreen` 添加 `openProgress` / `closeProgress` Tween
- [ ] 拦截ESC键，实现延迟关闭动画
- [ ] 制作圆角对话框背景纹理（9-patch 或 nine_slice）
- [ ] 为选项按钮添加悬停缩放+颜色插值
- [ ] 实现底部圆角Toast提示（带倒计时进度条）
- [ ] 在JSON配置中新增 `animation` 字段（可选，默认启用）
- [ ] 添加客户端配置项关闭动画（兼容低配）

---

*资料整理时间: 2026-08-30*
*核心参考: Animated GUI (dev-limucc), Modern UI (Icyllis Milica), Smooth GUI Animations*
