# BGM 控制增强设计规格

> 日期：当前会话
> 范围：对话 BGM 主动切换 + JSON 数据驱动 + 界面按钮条

## 目标

解决"BGM 控制不充分、无法主动切换"的问题：在对话过程中，玩家可通过界面按钮主动切换/暂停/停止/循环/调节音量；数据作者可通过对话 JSON 的 `sound` 块精确指令 BGM 状态。

## 1. JSON 数据驱动

`SoundInfo` 新增可选字段：

- `bgm_action`：字符串，BGM 指令，取值：
  - `play` / `switch`：播放/切到指定 `bgm` 曲目（缺省等同于仅填 `bgm` 的原行为）
  - `pause` / `resume` / `stop` / `next` / `prev`（或 `previous`）
  进入该对话条目时执行一次；未知指令记警告并忽略。
- `bgm_volume`：本条覆盖的音量（0~1），可选。

兼容性：不填则完全等同旧行为，旧数据包零改动。

## 2. 界面按钮条

对话框上方新增一行控制条：上一首 ⏮ / 播放暂停 ⏯ / 下一首 ⏭ / 停止 ⏹ / 循环 🔁 / 音量- － / 音量+ ＋。

- 由新类 `ui/BgmControlBar` 创建并布局；按钮由 `DialogScreen` 通过 `addRenderableWidget` 注册。
- 按钮实时驱动 `DialogAudioManager`；`tick()` 中刷新播放/暂停与循环图标状态。

## 3. 音频管理器修缮与新增

- 修复既有编译错误：删除非法 `private field int field;`、补 `playing` 字段、`find` 方法、定义 `stopTrack`、修正 `EXPERIENCE_ORB_PICKUP`。
- 新增 `handleAction(action, bgmId)` 指令调度，JSON 与按钮共用。
- BGM 声音改用内部 `DialogMusicSound`（循环 + 可运行时调音量），实现单实例暂停（静音保留位置）。
- 音量 `adjustVolume` / `setVolume` 实时生效。

## 4. 文件清单

修改：
- `model/SoundInfo.java`：新增 `bgm_action` / `bgm_volume`
- `audio/DialogAudioManager.java`：修复 + `handleAction` + `DialogMusicSound`
- `ui/DialogScreen.java`：接入 BGM 控制条与 `bgm_action`
- 语言文件 `zh_cn.json` / `en_us.json`
- 测试数据 `test_dialog.json`

新增：
- `ui/BgmControlBar.java`

## 5. 测试

在 `test_dialog.json` 的条目 1（`switch`）、2（`next`）加入 BGM 自动化示例。