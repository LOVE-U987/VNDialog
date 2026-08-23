# BGM 控制增强设计规格（纯 JSON 驱动）

> 日期：当前会话
> 范围：对话 BGM 主动切换 + JSON 数据驱动 + 淡入淡出

## 目标

BGM 完全由对话 JSON 数据驱动，不引入界面按钮。在某个对话条目中"启用" BGM 后持续播放，直到出现"停止"标志；切换时使用淡入淡出过渡。

## 1. JSON 数据驱动

`SoundInfo` 可选字段：

- `bgm_action`：字符串，取值：
  - `start` / `play` / `switch`：开启/切换到指定 `bgm` 曲目（淡入）
  - `pause` / `resume`
  - `stop`：停止 BGM（淡出）
  - `next` / `prev`（或 `previous`）：曲池切换（淡入淡出）
  进入该对话条目时执行一次；未知指令记警告忽略。
- `bgm_volume`：本条覆盖音量（0~1），可选。

兼容性：仅有 `bgm` 字段时按 `play` 处理；不填则继续当前 BGM（持续播放）。

## 2. 淡入淡出

`DialogAudioManager` 内置淡入淡出状态机，`FADE_MS=900ms`：

- play/start/switch：若已有曲目在播 → 先淡出当前，再淡入新曲目（切换淡入淡出）；否则直接淡入。
- stop：淡出后停止。
- 同一曲目重复开启 → 幂等跳过，不重复淡入。
- 进度由对话界面逐帧调用 `tick()` 推进；暂停/恢复为瞬态（静音/复原）。

## 3. 回退按钮条

- 删除界面按钮条 `ui/BgmControlBar` 及其注册；移除相关语言键。
- BGM 现在仅能由 JSON 驱动，无手动界面控制。

## 4. 音频管理器修缮

- 修复原编译错误；新增 `handleAction(action, bgmId)` 指令调度（JSON/命令共用）。
- BGM 声音用内部 `BgmSoundInstance`（暴露 setVolume 支持淡入淡出与暂停静音）。

## 5. 文件清单

修改：
- `model/SoundInfo.java`：`bgm_action` / `bgm_volume`
- `audio/DialogAudioManager.java`：修复 + `handleTextAction` + 淡入淡出状态机
- `ui/DialogScreen.java`：JSON 触发 + tick 推进淡入淡出
- 语言文件 `zh_cn.json` / `en_us.json`
- 测试数据 `test_dialog.json`（start / switch / stop 示例）

新增：无（UI 条已移除）

## 6. 测试

`test_dialog.json`：条目1 `start` 开始；条目2 `switch` 切曲（淡入淡出）；末尾 `end` 用 `stop` 停止。