package top.yourzi.dialog.client;

/**
 * 对话状态枚举
 */
public enum DialogState {
    IDLE,           // 空闲，等待新对话
    FADE_IN,        // 淡入中
    TYPING,         // 打字机效果播放中
    DISPLAYING,     // 完整显示，等待超时
    FADE_OUT        // 淡出中
}
