# VNDialog 非侵入式对话系统 — 知识储备文档

> **目标版本**: NeoForge 1.21.1 (Minecraft 1.21.1)  
> **渲染方式**: 客户端 HUD Overlay（非模态，不阻塞游戏操作）  
> **文档用途**: 本地开发参考，涵盖核心API、架构设计与实现细节

---

## 目录

1. [核心渲染层：HUD Overlay](#1-核心渲染层-hud-overlay)
2. [GuiGraphics 渲染API详解](#2-guigraphics-渲染api详解)
3. [动态头像系统](#3-动态头像系统)
4. [文本处理：自动换行与打字机效果](#4-文本处理自动换行与打字机效果)
5. [配置系统：JSON + 热重载](#5-配置系统json--热重载)
6. [对话队列与状态机](#6-对话队列与状态机)
7. [整体架构设计](#7-整体架构设计)
8. [关键代码速查表](#8-关键代码速查表)

---

## 1. 核心渲染层：HUD Overlay

### 1.1 NeoForge 1.21.1 的 HUD 注册方式

NeoForge 1.21.1 **废弃了 `IGuiOverlay`**，改为通过 `RegisterGuiLayersEvent` 注册 `GuiLayer`。

```java
// 在 Client 侧的 Mod Event Bus 上注册
@SubscribeEvent
public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
    // 注册自定义 HUD 层
    // 参数：layer name（用于排序），render function
    event.registerAbove(
        VanillaGuiLayers.CHAT_OVERLAY,  // 层级：在聊天栏之上
        ResourceLocation.fromNamespaceAndPath("vndialog", "dialog_overlay"),
        (guiGraphics, deltaTracker) -> {
            DialogRenderer.getInstance().render(guiGraphics, deltaTracker);
        }
    );
}
```

**层级常量参考**（`VanillaGuiLayers`）：

| 层级 | 说明 |
|------|------|
| `VIGNETTE_OVERLAY` | 最底层，暗角效果 |
| `SPYGLASS_OVERLAY` | 望远镜遮罩 |
| `HELMET_OVERLAY` | 头盔（南瓜头）遮罩 |
| `FROSTBITE_OVERLAY` | 冻伤效果 |
| `PORTAL_OVERLAY` | 传送门遮罩 |
| `CROSSHAIR` | 准星 |
| `BOSS_OVERLAY` | Boss血条 |
| `ARMOR_LEVEL` | 护甲值 |
| `FOOD_LEVEL` | 饥饿值 |
| `HOTBAR` | 快捷栏 |
| `EXPERIENCE_BAR` | 经验条 |
| `SELECTED_ITEM_NAME` | 选中物品名称 |
| `CHAT_OVERLAY` | 聊天栏 |
| `PLAYER_LIST_OVERLAY` | Tab玩家列表 |
| `DEBUG_OVERLAY` | F3调试信息 |
| `EFFECTS_OVERLAY` | 状态效果图标 |
| `SUBTITLE_OVERLAY` | 字幕 |
| `SCOREBOARD_SIDEBAR` | 计分板侧边栏 |
| `TITLE_OVERLAY` | 标题/副标题 |
| `SLEEP_OVERLAY` | 睡觉遮罩 |
| `OVERLAY_MESSAGE` | 动作栏消息 |

> **建议层级**: `CHAT_OVERLAY` 或 `SELECTED_ITEM_NAME` 之上，确保对话不被其他UI遮挡。

### 1.2 DeltaTracker 与渲染时机

```java
// render 方法签名
void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker)

// deltaTracker 提供：
deltaTracker.getGameTimeDeltaPartialTick(false);  // 部分Tick，用于动画插值
deltaTracker.getGameTimeDeltaTicks();             // 整Tick
```

### 1.3 屏幕坐标系

- 原点 `(0, 0)` 在**屏幕左上角**
- `guiGraphics.guiWidth()` / `guiGraphics.guiHeight()` 获取当前屏幕分辨率
- 坐标不受 GUI 缩放影响（已自动处理）

---

## 2. GuiGraphics 渲染API详解

### 2.1 基础图形绘制

```java
// 填充矩形（纯色）
guiGraphics.fill(x1, y1, x2, y2, color);

// 填充矩形（带透明度）
// color 格式: 0xAARRGGBB，例如 0xFF1A1A2E（不透明深蓝）
guiGraphics.fill(x1, y1, x2, y2, 0xFF1A1A2E);

// 绘制边框
// 需手动绘制四条线，或使用 fill 叠加
// NeoForge 1.21.1 没有内置圆角矩形API，需手动实现或使用 NinePatch

// 绘制渐变矩形
guiGraphics.fillGradient(x1, y1, x2, y2, colorFrom, colorTo);
```

### 2.2 纹理绘制（blit）

```java
// 标准纹理绘制（256x256 PNG）
ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("vndialog", "textures/gui/dialog_bg.png");
guiGraphics.blit(texture, x, y, u, v, width, height);

// 带缩放/部分绘制
guiGraphics.blit(texture, x, y, z, u, v, width, height, textureWidth, textureHeight);

// 精灵图（GUI Texture Atlas，推荐用于小图标）
// 路径相对于 assets/<namespace>/textures/gui/sprites/
// 不需要 .png 后缀
ResourceLocation sprite = ResourceLocation.fromNamespaceAndPath("vndialog", "dialog/avatar_frame");
guiGraphics.blitSprite(sprite, x, y, width, height);
```

### 2.3 文字渲染

```java
// 获取 Font 实例
Font font = Minecraft.getInstance().font;

// 绘制单行文字
guiGraphics.drawString(font, text, x, y, color);

// 绘制带阴影的文字
guiGraphics.drawString(font, text, x, y, color, true);

// 颜色常量参考
0xFFFFFFFF  // 纯白
0xFFE0E0E0  // 浅灰（推荐正文）
0xFFAAAAAA  // 中灰
0xFF1A1A2E  // 深蓝黑（推荐背景）
0xFF16213E  // 深蓝（推荐边框）
0xFF0F3460  // 中蓝（强调色）
0xFFE94560  // 粉红（强调色）
```

### 2.4 启用/禁用混合模式（用于透明度）

```java
// 在渲染半透明内容前启用
RenderSystem.enableBlend();
RenderSystem.defaultBlendFunc();

// 渲染代码...

// 恢复
RenderSystem.disableBlend();
```

### 2.5 PoseStack 变换

```java
// 获取 PoseStack 进行平移/缩放/旋转
PoseStack poseStack = guiGraphics.pose();

poseStack.pushPose();           // 保存当前状态
poseStack.translate(x, y, z);  // 平移
poseStack.scale(sx, sy, 1.0f);  // 缩放
poseStack.mulPose(Axis.ZP.rotationDegrees(angle));  // 旋转

// 渲染代码...

poseStack.popPose();            // 恢复状态
```

---

## 3. 动态头像系统

### 3.1 动态纹理方案对比

| 方案 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| **精灵表 (Sprite Sheet)** | 原生支持，性能最好 | 需要预处理 | 固定循环动画 |
| **GIF/APNG 解析** | 直接加载，方便 | 需自定义解析器，内存占用高 | 复杂动画 |
| **视频纹理 (FFmpeg)** | 效果最丰富 | 依赖重，性能开销大 | 高精度动画 |
| **序列帧 PNG** | 灵活，易控制 | 文件多，管理复杂 | 逐帧控制 |
| **Live2D/Spine** | 专业级效果 | 需额外库，复杂 | 高质量需求 |

### 3.2 精灵表动画（推荐）

Minecraft 原生支持精灵表动画，通过 `.mcmeta` 文件配置：

```json
// assets/vndialog/textures/gui/avatar/character1.png.mcmeta
{
    "animation": {
        "frametime": 5,
        "interpolate": true,
        "frames": [0, 1, 2, 3, 2, 1]
    }
}
```

- 纹理文件为竖直排列的帧（如 64x64 每帧，共8帧 = 64x512）
- `frametime`: 每帧持续多少游戏刻（1 tick = 50ms，默认20fps）
- `interpolate`: 是否插值平滑过渡
- `frames`: 自定义播放顺序，可循环/往返

### 3.3 圆形裁切渲染

```java
// 使用 Stencil Buffer 或 Scissor 实现圆形裁切
// 方案1：使用蒙版纹理（推荐，性能最好）

// 1. 绘制圆形蒙版到 stencil buffer
// 2. 绘制头像纹理（仅 stencil 通过区域显示）
// 3. 清除 stencil

// 方案2：使用预制的圆形边框+中心透明（最简单）
// 头像用正方形，叠加一个圆形边框PNG（中心透明）

// 方案3：使用 shader（最灵活，但复杂）
```

### 3.4 头像浮动动画

```java
// 呼吸/浮动效果
float floatOffset = Mth.sin((gameTime + partialTick) * floatSpeed) * floatAmplitude;
int avatarY = baseY + (int)floatOffset;

// 其中：
// gameTime = Minecraft.getInstance().level.getGameTime()
// partialTick = deltaTracker.getGameTimeDeltaPartialTick(false)
// floatSpeed = 0.05f（建议值）
// floatAmplitude = 3（像素，建议值）
```

---

## 4. 文本处理：自动换行与打字机效果

### 4.1 自动换行

```java
Font font = Minecraft.getInstance().font;
String text = "这是一段需要自动换行的中文对话文本...";
int maxWidth = 280;  // 对话框最大宽度

// Minecraft 原生换行方法
List<FormattedCharSequence> lines = font.split(
    Component.literal(text), 
    maxWidth
);

// 逐行渲染
int lineHeight = font.lineHeight + 2;  // 行间距
for (int i = 0; i < lines.size(); i++) {
    guiGraphics.drawString(font, lines.get(i), textX, textY + i * lineHeight, 0xFFE0E0E0);
}

// 计算文本区域总高度
int totalTextHeight = lines.size() * lineHeight;
```

### 4.2 打字机效果（Typewriter）

```java
public class TypewriterState {
    private final String fullText;
    private final int charIntervalMs;  // 每个字符间隔（毫秒）
    private long startTime;
    private int visibleChars;

    public void start() {
        this.startTime = System.currentTimeMillis();
    }

    public void update() {
        long elapsed = System.currentTimeMillis() - startTime;
        this.visibleChars = Math.min(fullText.length(), (int)(elapsed / charIntervalMs));
    }

    public String getVisibleText() {
        return fullText.substring(0, visibleChars);
    }

    public boolean isComplete() {
        return visibleChars >= fullText.length();
    }

    public void skip() {
        this.visibleChars = fullText.length();
    }
}
```

**进阶：按词组而非字符显示（中文优化）**

```java
// 中文按字符，英文按单词
public String getVisibleTextSmart() {
    // 简单实现：直接按字符截断
    // 高级实现：检测当前截断位置是否在单词中间，智能调整
    return fullText.substring(0, visibleChars);
}
```

### 4.3 打字机 + 自动换行结合

```java
// 先截断可见文本，再对可见文本做换行
String visible = typewriter.getVisibleText();
List<FormattedCharSequence> lines = font.split(Component.literal(visible), maxWidth);

// 注意：每帧重新 split 性能开销很小，可接受
```

---

## 5. 配置系统：JSON + 热重载

### 5.1 配置文件结构

```
config/
└── vndialog/
    ├── hud_layout.json      # HUD位置与样式
    └── dialog_styles/         # 多角色样式（可选）
        └── default.json
```

```json
// config/vndialog/hud_layout.json
{
    "version": 1,
    "dialog": {
        "position": {
            "anchor": "BOTTOM_LEFT",
            "x": 20,
            "y": -120
        },
        "avatar": {
            "size": 64,
            "enabled": true,
            "floatAmplitude": 3.0,
            "floatSpeed": 0.05
        },
        "bubble": {
            "maxWidth": 300,
            "padding": { "left": 16, "right": 16, "top": 12, "bottom": 12 },
            "cornerRadius": 8,
            "backgroundColor": "#1A1A2E",
            "backgroundAlpha": 0.92,
            "borderColor": "#16213E",
            "borderWidth": 2,
            "textColor": "#E0E0E0",
            "shadow": true
        },
        "timing": {
            "charInterval": 30,
            "displayDuration": 2500,
            "fadeInDuration": 200,
            "fadeOutDuration": 300
        }
    }
}
```

### 5.2 配置加载与热重载

```java
public class DialogConfig {
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get()
        .resolve("vndialog").resolve("hud_layout.json");

    private static DialogConfig INSTANCE;
    private static long lastModified = 0;

    // 配置字段...
    public Position position;
    public AvatarConfig avatar;
    public BubbleConfig bubble;
    public TimingConfig timing;

    public static DialogConfig get() {
        // 检查文件修改时间，实现热重载
        if (Files.exists(CONFIG_PATH)) {
            try {
                long modified = Files.getLastModifiedTime(CONFIG_PATH).toMillis();
                if (modified > lastModified) {
                    load();
                    lastModified = modified;
                }
            } catch (IOException e) {
                LOGGER.error("Failed to check config modification time", e);
            }
        }
        return INSTANCE != null ? INSTANCE : loadDefault();
    }

    private static void load() {
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
            INSTANCE = gson.fromJson(reader, DialogConfig.class);
            LOGGER.info("VNDialog config loaded/reloaded");
        } catch (IOException e) {
            LOGGER.error("Failed to load config", e);
        }
    }

    private static DialogConfig loadDefault() {
        // 返回默认配置
        INSTANCE = new DialogConfig();
        // ... 设置默认值
        return INSTANCE;
    }

    // 也可以在 TickEvent.ClientTickEvent 中每N tick检查一次
}
```

### 5.3 坐标锚点系统

```java
public enum Anchor {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT;

    public int resolveX(int screenWidth, int elementWidth, int offsetX) {
        return switch (this) {
            case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> offsetX;
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> (screenWidth - elementWidth) / 2 + offsetX;
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> screenWidth - elementWidth - offsetX;
        };
    }

    public int resolveY(int screenHeight, int elementHeight, int offsetY) {
        return switch (this) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> offsetY;
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> (screenHeight - elementHeight) / 2 + offsetY;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> screenHeight - elementHeight + offsetY;
        };
    }
}
```

---

## 6. 对话队列与状态机

### 6.1 状态机设计

```
┌─────────┐    新对话入队     ┌─────────┐    打字完成      ┌─────────────┐    超时      ┌───────────┐
│  IDLE   │ ───────────────► │ TYPING  │ ──────────────► │ DISPLAYING  │ ──────────► │ FADING_OUT │
└─────────┘                  └─────────┘                 └─────────────┘             └───────────┘
     ▲                                                                                       │
     └───────────────────────────────────────────────────────────────────────────────────────┘
                                       淡出完成
```

```java
public enum DialogState {
    IDLE,           // 空闲，等待新对话
    FADE_IN,        // 淡入中（可选）
    TYPING,         // 打字机效果播放中
    DISPLAYING,     // 完整显示，等待超时
    FADE_OUT        // 淡出中
}
```

### 6.2 对话队列管理器

```java
public class DialogManager {
    private static final DialogManager INSTANCE = new DialogManager();
    private final Queue<DialogEntry> queue = new ArrayDeque<>();

    private DialogState state = DialogState.IDLE;
    private DialogEntry current;
    private TypewriterState typewriter;
    private long displayStartTime;
    private float fadeAlpha = 1.0f;

    // 单例
    public static DialogManager getInstance() { return INSTANCE; }

    // 添加对话到队列
    public void enqueue(Component text, ResourceLocation avatarTexture, int priority) {
        queue.offer(new DialogEntry(text, avatarTexture, priority));
    }

    // 每 tick 更新（注册到 ClientTickEvent）
    public void tick() {
        switch (state) {
            case IDLE -> {
                if (!queue.isEmpty()) {
                    current = queue.poll();
                    typewriter = new TypewriterState(current.text().getString());
                    typewriter.start();
                    state = DialogState.TYPING;
                }
            }
            case TYPING -> {
                typewriter.update();
                if (typewriter.isComplete()) {
                    displayStartTime = System.currentTimeMillis();
                    state = DialogState.DISPLAYING;
                }
            }
            case DISPLAYING -> {
                long elapsed = System.currentTimeMillis() - displayStartTime;
                if (elapsed >= DialogConfig.get().timing.displayDuration) {
                    state = DialogState.FADE_OUT;
                }
            }
            case FADE_OUT -> {
                fadeAlpha -= 0.05f;  // 每 tick 减少透明度
                if (fadeAlpha <= 0) {
                    fadeAlpha = 1.0f;
                    current = null;
                    state = DialogState.IDLE;
                }
            }
        }
    }

    // 渲染调用（注册到 GuiLayer）
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (current == null || state == DialogState.IDLE) return;

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);

        // 计算淡入淡出 alpha
        float alpha = fadeAlpha;
        if (state == DialogState.FADE_OUT) {
            alpha = fadeAlpha;
        }

        DialogRenderer.render(guiGraphics, current, typewriter, alpha, partialTick);
    }
}
```

### 6.3 对话条目数据结构

```java
public record DialogEntry(
    Component text,           // 对话文本（支持样式代码）
    ResourceLocation avatar,  // 头像纹理
    int priority,             // 优先级（数字越小越优先）
    long enqueueTime,         // 入队时间
    @Nullable SoundEvent voiceSound  // 打字音效（可选）
) {}
```

---

## 7. 整体架构设计

### 7.1 包结构

```
com/vndialog/
├── VNDialog.java                    # 主类/Mod入口
├── client/
│   ├── VNDialogClient.java            # 客户端初始化
│   ├── config/
│   │   ├── DialogConfig.java          # 配置数据类
│   │   └── ConfigReloader.java        # 热重载监听器
│   ├── dialog/
│   │   ├── DialogManager.java         # 队列与状态机
│   │   ├── DialogState.java           # 状态枚举
│   │   ├── DialogEntry.java           # 对话数据
│   │   └── TypewriterState.java       # 打字机状态
│   ├── render/
│   │   ├── DialogRenderer.java        # 主渲染器
│   │   ├── AvatarRenderer.java        # 头像渲染
│   │   ├── BubbleRenderer.java        # 气泡渲染
│   │   └── TextRenderer.java          # 文字渲染（换行+打字机）
│   └── event/
│       ├── HudLayerHandler.java       # RegisterGuiLayersEvent
│       └── ClientTickHandler.java     # ClientTickEvent
├── network/
│   ├── VNDialogPacket.java            # 对话数据包
│   └── PacketHandler.java             # 网络处理
└── api/
    └── VNDialogAPI.java               # 对外API
```

### 7.2 事件注册总览

```java
@Mod("vndialog")
public class VNDialog {
    public VNDialog(IEventBus modBus, ModContainer modContainer) {
        // 客户端专用注册
        modBus.addListener(this::onClientSetup);
        modBus.addListener(this::onRegisterGuiLayers);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        // 注册按键绑定（如跳过对话）
        ClientRegistry.registerKeyBinding(SKIP_KEY);
    }

    private void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            VanillaGuiLayers.CHAT_OVERLAY,
            ResourceLocation.fromNamespaceAndPath("vndialog", "dialog"),
            DialogRenderer.getInstance()::render
        );
    }
}

// 单独的事件处理器类
public class ClientEventHandler {
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        DialogManager.getInstance().tick();
        DialogConfig.checkReload();  // 检查配置文件是否修改
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (SKIP_KEY.consumeClick()) {
            DialogManager.getInstance().skipCurrent();
        }
    }
}
```

### 7.3 网络通信（服务端→客户端）

```java
// 定义 Custom Payload
public record ShowDialogPayload(
    Component text,
    ResourceLocation avatar,
    int priority
) implements CustomPacketPayload {
    public static final Type<ShowDialogPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("vndialog", "show_dialog")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ShowDialogPayload> STREAM_CODEC =
        StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, ShowDialogPayload::text,
            ResourceLocation.STREAM_CODEC, ShowDialogPayload::avatar,
            ByteBufCodecs.VAR_INT, ShowDialogPayload::priority,
            ShowDialogPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

// 注册 Payload
modBus.addListener(RegisterPayloadHandlersEvent.class, event -> {
    PayloadRegistrar registrar = event.registrar("1");
    registrar.playToClient(
        ShowDialogPayload.TYPE,
        ShowDialogPayload.STREAM_CODEC,
        (payload, context) -> {
            context.enqueueWork(() -> {
                DialogManager.getInstance().enqueue(payload.text(), payload.avatar(), payload.priority());
            });
        }
    );
});
```

---

## 8. 关键代码速查表

### 8.1 颜色转换

```java
// Hex String -> int（带Alpha）
public static int parseColor(String hex, float alpha) {
    int rgb = Integer.parseInt(hex.replace("#", ""), 16);
    int a = (int)(alpha * 255) << 24;
    return a | rgb;
}

// int -> RGBA 分量
int alpha = (color >> 24) & 0xFF;
int red   = (color >> 16) & 0xFF;
int green = (color >> 8)  & 0xFF;
int blue  = color & 0xFF;
```

### 8.2 淡入淡出插值

```java
// 线性插值
public static float lerp(float start, float end, float progress) {
    return start + (end - start) * progress;
}

// 缓动函数（ease-out）
public static float easeOutCubic(float x) {
    return 1 - (float)Math.pow(1 - x, 3);
}

// 缓动函数（ease-in-out）
public static float easeInOutSine(float x) {
    return -(float)(Math.cos(Math.PI * x) - 1) / 2;
}
```

### 8.3 计算对话框尺寸

```java
public static int[] calculateBubbleSize(Font font, List<FormattedCharSequence> lines, 
                                        int paddingH, int paddingV, int maxWidth) {
    int textWidth = 0;
    for (FormattedCharSequence line : lines) {
        textWidth = Math.max(textWidth, font.width(line));
    }
    int width = Math.min(textWidth + paddingH * 2, maxWidth);
    int height = lines.size() * (font.lineHeight + 2) + paddingV * 2;
    return new int[]{width, height};
}
```

### 8.4 圆形头像裁切（蒙版方案）

```java
// 使用 Scissor 实现简单矩形裁切（非圆形，但性能好）
guiGraphics.enableScissor(x, y, x + size, y + size);
guiGraphics.blit(avatarTexture, x, y, 0, 0, size, size);
guiGraphics.disableScissor();

// 真正的圆形裁切需要 Stencil Buffer 或 Shader
// 简单替代方案：使用圆形边框PNG叠加
```

### 8.5 常用常量

```java
// Minecraft 常量
Minecraft.getInstance().getWindow().getGuiScaledWidth();   // GUI缩放后宽度
Minecraft.getInstance().getWindow().getGuiScaledHeight();  // GUI缩放后高度
Minecraft.getInstance().font;                              // 默认字体
Minecraft.getInstance().getTextureManager();               // 纹理管理器

// 渲染常量
RenderSystem.enableBlend();
RenderSystem.defaultBlendFunc();
RenderSystem.setShaderColor(r, g, b, a);  // 全局着色器颜色（影响后续所有渲染）
RenderSystem.setShaderColor(1, 1, 1, 1);  // 重置
```

---

## 附录A：参考资源

| 资源 | 链接 |
|------|------|
| NeoForge 官方文档 | https://docs.neoforged.net/ |
| NeoForge GitHub Discussions | https://github.com/neoforged/NeoForge/discussions |
| NeoForge JavaDocs | https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.21.x-neoforge/ |
| Minecraft 源码参考 | https://github.com/neoforged/NeoForge |

## 附录B：版本兼容性注意

- **NeoForge 21.1.x** 对应 **Minecraft 1.21.1**
- `ResourceLocation` 构造方式：使用 `ResourceLocation.fromNamespaceAndPath(namespace, path)` 替代旧版构造函数
- 网络包系统：使用 `CustomPacketPayload` + `PayloadRegistrar`（1.20.5+ 新API）
- GUI 渲染：统一使用 `GuiGraphics`，废弃了旧的 `PoseStack` 直接渲染

---

*文档生成时间: 2026-08-31*  
*适用分支: neoforge-1.21.1*
