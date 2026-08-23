# VNDialog 原生渲染核心（方案 B）设计规格

> 日期：2026-01（与知识库文档一致以 MM-DD 计时，实际按会话日期）
> 范围：立绘 / 对话框 / 背景 / 屏幕震动（四模块核心替换）

## 目标

在不引入 MCEF / WebGUI / NanoVG 等外部库的前提下，用 NeoForge 1.21.1 原生渲染实现对
"近似 MCEF 基础对话 UI" 的视觉效果升级。纯 Java，零外部依赖，向后兼容现有数据包格式。

## 范围

本轮实现四个模块：

1. **立绘模块**（JSON 驱动动画配置）
   - `PortraitInfo` 新增可选字段：`x`、`y`、`scale`、`rotation`、`alpha`、`zOrder`、`animations[]`
   - 新增 `PortraitAnimationData` 动画描述 + `PortraitAnimator` 运行时动画器
   - 支持缓动（Easing）与多坐标/缩放/旋转/透明度/震动动画，可并行播放
   - 保持旧字段（`position`/`brightness`/`animationType`）向后兼容

2. 对话框（nine-slice 圆角渲染）
   - 使用 `blitSprite` + nine_slice mcmeta 渲染背景，圆角不拉伸
   - 保留纯色背景 fallback（兼容配置）

3. 背景增强
   - `BackgroundImageInfo` 扩展支持 `gradient` / `color` 类型；保留原 `image` 与 renderOption
   - 渐变用原生填充逐行插值实现；淡入淡出沿用现有逻辑

4. 设置震动（camera shake）
   - 新增 `DialogEffectManager`（trauma 衰减模型）
   - 监听 `ViewportEvent.ComputeCameraAngles` 在客户端生效
   - JSON：对话条目 `effects` 数组（`type/强度/时长`），当展示该条目时触发过场

## 超出范围（本轮不做）

- 全屏视频过场、独显菜单、输入控制、Mini/Nanogui/WebGUI/MCEF 引入
- 可视化节点编辑器
- 声音/音乐通道

## 兼容性

- 所有新增 JSON 字段均为可选，缺省走原逻辑。
- 旧工程样（使用 `path`/`position`/`brightness`/`animationType`）不受影响。
- 网络同步链路（SyncAllDialogsPacket 等）不变，仅新增字段随 JSON 一并同步。

## 文件清单

新增：
- `util/Easing.java`
- `model/PortraitAnimationData.java`
- `ui/PortraitAnimator.java`
- `ui/effects/DialogEffectManager.java`
- `event/ClientRenderEventHandler.java`（注册震动相机事件）
- 资源：`assets/dialog/textures/gui/sprites/dialog_box.png` + `.mcmeta`

修改：
- `model/PortraitInfo.java`（新增字段）
- `model/BackgroundImageInfo.java`（gradient/color）
- `ui/DialogScreen.java`（渲染循环接入）
- 测试脚本 `data/dialog/dialogs/` 新增一个剧情测试 JSON