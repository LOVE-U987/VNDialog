package top.yourzi.dialog.client;

import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 打字机效果状态
 * 控制文本逐个字符显示
 */
@OnlyIn(Dist.CLIENT)
public class TypewriterState {
    
    private final String fullText;
    private final int charIntervalMs;
    private long startTime;
    private int visibleChars;

    public TypewriterState(Component text, int charIntervalMs) {
        this.fullText = text.getString();
        this.charIntervalMs = Math.max(charIntervalMs, 1); // 至少 1ms
        this.visibleChars = 0;
    }

    public TypewriterState(String text, int charIntervalMs) {
        this.fullText = text;
        this.charIntervalMs = Math.max(charIntervalMs, 1);
        this.visibleChars = 0;
    }

    /**
     * 启动打字机效果
     */
    public void start() {
        this.startTime = System.currentTimeMillis();
    }

    /**
     * 更新状态
     */
    public void update() {
        long elapsed = System.currentTimeMillis() - startTime;
        int expectedChars = (int) (elapsed / charIntervalMs);
        this.visibleChars = Math.min(fullText.length(), expectedChars);
    }

    /**
     * 获取可见文本
     */
    public String getVisibleText() {
        if (visibleChars >= fullText.length()) {
            return fullText;
        }
        try {
            return fullText.substring(0, visibleChars);
        } catch (StringIndexOutOfBoundsException e) {
            return fullText;
        }
    }

    /**
     * 是否完成
     */
    public boolean isComplete() {
        return visibleChars >= fullText.length();
    }

    /**
     * 跳过（立即完成）
     */
    public void skip() {
        this.visibleChars = fullText.length();
    }

    /**
     * 获取进度（0.0 - 1.0）
     */
    public float getProgress() {
        return (float) visibleChars / fullText.length();
    }

    /**
     * 获取可见字符数
     */
    public int getVisibleChars() {
        return visibleChars;
    }

    /**
     * 获取总字符数
     */
    public int getTotalChars() {
        return fullText.length();
    }

    /**
     * 重置
     */
    public void reset() {
        this.visibleChars = 0;
        this.startTime = 0;
    }
}
