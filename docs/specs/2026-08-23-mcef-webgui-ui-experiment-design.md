# VNDialog MCEF + WebGUI UI 改进实验 —— 设计文档（草稿，待评审）

> 日期：2026-08-23
> 基分支：`neoforge-1.21.1` → 实验分支：`experiment/mcef-webgui-ui`
> 状态：**DRAFT —— 尚未实现，待用户评审与确认**
> 关联文档：`docs/specs/2026-01-01-native-render-core-design.md`（现有原生渲染架构）

---

## 1. 背景与目标

VNDialog 当前 UI 采用 **纯 NeoForge 原生渲染（方案 B）**，没有引入 MCEF/WebGUI/NanoVG。
本项目为一项**实验性** UI 增强：按知识库 `VNDialog_Extension_Development_Guide.md` 的
**MCEF + WebGUI** 技术路线，额外实现一项 Web 技术栈（HTML/CSS/JS）的 UI 改进，并在独立
GitHub 测试分支上验证，**不影响主分支 `neoforge-1.21.1` 的既有实现**。

目标：
1. 建立可回退/可并列的独立测试分支。
2. 在分支上集成 WebGUI（其底层依赖 MCEF），通过 HTML/CSS/JS 实现一项**原生难以优雅实现**的 UI 改进。
3. 提供 JS ↔ Java 的双向通信桥。
4. 输出设计文档 + 完整实现（让位于有网络环境的机器可完成在线编译验证）。

---

## 2. 关键技术决策

### 2.1 选型：WebGUI（上层框架）+ 其底层 MCEF（Chromium 引擎）

依据知识库：
- **MCEF**：将真实 Chromium 嵌入客户端，支持完整 Web 技术栈（CSS 动画/WebGL/WebSocket/Canvas/Web Audio）；
  默认为从 `maven.cinemamod.com` 拉依赖（本机无法解析，见 §3），Chromium 二进制 `~150MB` 首次启动下载。
- **WebGUI**：基于 MCEF 的 Web UI 框架，提供透明 HUD Overlay + 全屏 GUI 两种模式，支持 NeoForge 1.21.1（Supported），
  提供 `WebviewApi` 供其他 mod 编程打开 overlay，页面通过 `window.webgui.postMessage`（知识库示例为 `window.webgui.postToGame`）
  与 Mod 双向通信 [cite:c41c40d3-2][cite:e52e59d4-4][cite:e52e59d4-8]。

### 2.2 集成形态：VNDialog 作为「依赖 WebGUI 的外部 mod」

- `build.gradle` 声明 WebGUI 的编译期/运行期依赖，并旁列 MCEF。
- 通过 `WebviewApi` 在 VNDialog 需要时打开一个 **HUD overlay 或 GUI**，加载随 Mod 打包的 HTML/CSS/JS 页面。
- 呈现目标使用 Mod 现有 DialogManager 数据，通过通信桥注入页面。

---

## 3. 环境约束与影响（当前机器）

经勘察，本机网络与缓存现状：

| 资源 | 可达性 | 影响 |
|---|---|---|
| Maven Central (`repo1.maven.org`) | ✅ | 可用 |
| `webgui.space` | ✅ | 版权/文档参考可用 |
| NeoForge Maven、GitHub | ❌ 超时 | 无法在线取 Neoforge 新依赖/Git push |
| **`maven.cinemamod.com`（MCEF Maven）** | ❌ 域名无法解析 | **无法拉取 MCEF/Webview Api 依赖** |
| 本地 `~/.gradle` 缓存 | 仅含现有 Native 构建依赖 | 无 mcef/webgui 依赖 |

**结论**：本机**无法在线引入 MCEF/WebviewApi 依赖并完成在线编译**。因此本实验在本机内产出：
1. 完整 Java + HTML/CSS/JS 实现源码；
2. `build.gradle` 依赖改动与说明；
3. 使用/构建文档。

（运行、编译验证需在可访问 WebGUI Modrinth Maven 与 `maven.cinemamod.com` 的环境进行。）

---

## 4. UI 改进目标（**已实现：对话历史回顾 UI**）

知识库原生方案效果清单里标注为「难」或需外部库项的，是原生不适合的候选，在 Web 里天然可做：

1. **对话历史记录回顾 UI** ✅ **已实现**——原生是简单列表+滚动，Web 端做成卡片/时间线 + 立绘缩略 + 打字机动画 + 多端 CSS 动效。
2. **角色立绘 Live2D / Spine 动态立绘**（Web 可用 Live2D SDK 骨骼动画）——留存为后续目标。
3. **选项选择动效**（Web 端 CSS 弹簧/辉光/滑入、文字描边）——可复用同一桥接通道追加。
4. **Typewriter + 逐字音效 / 文字渐变描边**——本次历史回顾正文已内置打字机（typewriter）动画。

> 依据原始诉求「额外实现一下 UI 改进」+ 分支验证导向，本实验选定 **历史记录回顾 UI** 并完成了可独立运行的 HTML/CSS/JS 页面与 Java 反射桥骨架。

1. **对话历史记录回顾 UI**（原生现为列表+滚动，Web 端可做漂亮的卡片/时间线 + 立绘缩略 + CSS 动画）。
2. **角色立绘 Live2D / Spine 动态立绘**（原生做精灵图/序列帧有限，Web 可用 Live2D SDK 骨骼动画）。
3. **选项选择动效**（原生 `Button` 无动画；Web 端 CSS 弹簧/辉光/滑入、文字描边）。
4. **Typewriter + 逐字音效 / 文字渐变描边**（Web CSS/Canvas 远胜原生）。

> 初步推荐：**方案 1（历史记录 UI** / **方案 3（选项动效）**，原因：可控范围内、数据链路最短、无需外部 3D/骨骼资源，易在分支验证。

---

## 5. 审阅设计架构（目标完整实现时）

```
┌──────────────────────────── VNDialog (Mod, Java) ────────────────────────────┐
│  DialogManager ──DialogSequence/Entry 数据──────────────────────────┐       │
│  UI 改进控制器 (WebviewBridge.java)                                   │      │
│     • 打开/关闭 Webview (WebviewApi.openWebview / openWebMenu)        │      │
│     • 定 序构建待办; 向页面 postMessage(dialog_data, state)          │      │
│     • 监听页面 postMessage → 转译成 DialogManager 调用              │      │
└──────────────▲───────────────────────────────────────────────┬──────────────┘
               │ CustomPacketPayload / 跨线程               │ 窗口.webgui / postMessage
               └─────────────────────────── WebGUI / MCEF Browser ─┘
                        resources/dialog/web/ (HTML/CSS/JS 打包)
```

数据流：
1. 服务端 → `SendDialogDataPacket`/`SyncAllDialogsPacket` → `DialogManager` 缓存；
2. 改进 UI 需要时，bridge 读取缓存数据 → 组装 JSON → `webview.postMessage` 给页面；
3. 页面交互（选项/关闭）→ `window.webview.postToGame({channel,...})` → bridge → `DialogManager` 执行跳转/命令。

---

## 6. 已实现文件清单

```
src/main/resources/assets/dialog/web/            # Web 前端资源（已实现，可独立运行）
  ├─ index.html                                  # 历史回顾入口（HUD/GUI 加载）
  ├─ preview.html                               # 独立预览页（浏览器直接打开测试）
  ├─ preview-data.js                             # 脱离游戏的演示数据
  ├─ css/style.css                              # 卡片/时间线/打字机/caret 动画
  └─ js/ui.js                                   # 桥接抽象 + 渲染 + typewriter + 消息监听
src/main/java/top/yourzi/dialog/ui/webview/      # Java 桥接层（已实现，无 WebGUI 编译依赖）
  └─ WebviewDialogBridge.java       # 反射式打开 WebGUI 浮层 + 数据序列化 (buildJsonPayload)
build.gradle                      # 已加入【可选/注释】的 Modrinth + Cinemamod 依赖段
docs/… (本文件)                      # 设计与使用说明
```

> Java 侧采用**反射**，不 `import` 任何 WebGUI 包 → 离线可编译；仅在运行环境装了 WebGUI 时才反射打开页面。

---

## 7. 测试/验收

- 分支独立，不影响 `neoforge-1.21.1`（实验代码仅存在于 `experiment/mcef-webgui-ui`）。
- ✅ **本机已验证**：`gradlew compileJava --offline --no-daemon` → `BUILD SUCCESSFUL`（包含新增 `WebviewDialogBridge`，未装 WebGUI 也能编译）。
- ✅ **前端已可独立测试**：浏览器直接打开 `src/main/resources/assets/dialog/web/preview.html` 即可查看历史回顾卡片效果。
- ⏳ **联网环境待验证**：`gradlew build` + 装 WebGUI/MCEF mod 后，游戏内打开 WebGUI 页面、数据/交互双向链路。

---

## 8. 风险与待确认

1. WebGUI / `WebviewApi` 的精确 API 名与调用方式 —— 当前环境无法拉取依赖，需在有网络环境核对官方源码/文档后才能最终确定。
2. Chromium 二进制体积与首次启动下载（`~150MB`）—— 由 MCEF 负责下载；发布时最好在 mod 包内/文档中捆绑说明。
3. MCEF 与现有 NeoForge 原生渲染（方案 B）是否会互相遮挡/影响性能 —— 页面需透明背景，避免阻塞游戏画面。

---

## 后续说明（已按分支自主推进的首版实现）
- ✅ UI 改进已选定并在分支实现：**对话历史回顾 UI**（HTML/CSS/JS 可独立预览 + Java 反射桥）。
- ✅ 采用「VNDialog 依赖/桥接外部 WebGUI」思路，Java 侧零 WebGUI 编译依赖，离线可编译。
- ✅ 本机已通过 `compileJava --offline`；完整联网编译/游戏内联测留待有网络环境。