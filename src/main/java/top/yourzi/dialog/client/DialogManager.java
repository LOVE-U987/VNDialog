package top.yourzi.dialog.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import top.yourzi.dialog.Dialog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话管理器
 * 管理对话队列和状态机
 */
@OnlyIn(Dist.CLIENT)
public class DialogManager {

    private static final DialogManager INSTANCE = new DialogManager();

    // 对话队列（按优先级排序）
    private final List<DialogEntry> queue = new ArrayList<>();

    // 当前对话
    private DialogEntry currentEntry;
    private TypewriterState typewriter;

    // 状态
    private DialogState state = DialogState.IDLE;
    private long displayStartTime;

    // 配置
    private static final int DEFAULT_CHAR_INTERVAL_MS = 30;

    private DialogManager() {}

    public static DialogManager getInstance() {
        return INSTANCE;
    }

    /**
     * 添加对话到队列（带头像）
     * @param text 对话文本
     * @param avatar 头像纹理路径
     * @param priority 优先级（数字越小越优先）
     */
    public void enqueue(Component text, ResourceLocation avatar, int priority) {
        queue.add(new DialogEntry(text, avatar, priority));
        // 按优先级排序
        queue.sort((a, b) -> Integer.compare(a.priority(), b.priority()));
        
        Dialog.LOGGER.debug("Dialog enqueued: {}, priority: {}, queue size: {}", 
            text.getString(), priority, queue.size());
    }

    /**
     * 添加对话到队列（带头像，默认优先级）
     */
    public void enqueue(Component text, ResourceLocation avatar) {
        enqueue(text, avatar, 0);
    }

    /**
     * 添加对话到队列（不带头像）
     */
    public void enqueue(Component text, int priority) {
        enqueue(text, null, priority);
    }

    /**
     * 添加对话到队列（默认优先级）
     */
    public void enqueue(Component text) {
        enqueue(text, (ResourceLocation) null);
    }

    /**
     * 更新状态机（每 tick 调用）
     */
    public void tick() {
        // 检查配置热重载
        DialogConfig.checkReload();

        // 如果没有当前对话且有队列，添加新对话
        if (currentEntry == null && !queue.isEmpty()) {
            currentEntry = queue.remove(0);
            typewriter = new TypewriterState(currentEntry.text(), DialogConfig.getInstance().charIntervalMs);
            typewriter.start();
            setState(DialogState.FADE_IN);
            return;
        }

        // 状态机
        switch (state) {
            case FADE_IN -> {
                // 淡入由 DialogRenderer 处理
            }
            case TYPING -> {
                if (DialogConfig.getInstance().enableTypewriter) {
                    typewriter.update();
                    if (typewriter.isComplete()) {
                        displayStartTime = System.currentTimeMillis();
                        setState(DialogState.DISPLAYING);
                    }
                } else {
                    displayStartTime = System.currentTimeMillis();
                    setState(DialogState.DISPLAYING);
                }
            }
            case DISPLAYING -> {
                long elapsed = System.currentTimeMillis() - displayStartTime;
                if (elapsed >= DialogConfig.getInstance().autoDismissMs) {
                    setState(DialogState.FADE_OUT);
                }
            }
            case FADE_OUT -> {
                if (typewriter != null && typewriter.getProgress() >= 1.0f) {
                    reset();
                }
            }
            case IDLE -> {
                // 保持空闲
            }
        }
    }

    /**
     * 重置管理器状态
     */
    private void reset() {
        currentEntry = null;
        typewriter = null;
        displayStartTime = -1;
        setState(DialogState.IDLE);
    }

    /**
     * 跳过当前对话（立即完成打字机效果）
     */
    public void skip() {
        if (typewriter != null && state != DialogState.IDLE) {
            typewriter.skip();
            displayStartTime = System.currentTimeMillis();
            setState(DialogState.DISPLAYING);
        }
    }

    /**
     * 立即显示（跳过淡入和打字机）
     */
    public void showImmediately() {
        if (currentEntry != null) {
            typewriter = new TypewriterState(currentEntry.text(), DEFAULT_CHAR_INTERVAL_MS);
            typewriter.skip();
            displayStartTime = System.currentTimeMillis();
            setState(DialogState.DISPLAYING);
        }
    }

    // ========== Getters ==========

    public DialogState getState() {
        return state;
    }

    public void setState(DialogState state) {
        this.state = state;
    }

    public DialogEntry getCurrentEntry() {
        return currentEntry;
    }

    public Component getCurrentText() {
        return currentEntry != null ? currentEntry.text() : Component.empty();
    }

    public TypewriterState getTypewriter() {
        return typewriter;
    }

    /**
     * 获取当前头像纹理
     */
    public ResourceLocation getCurrentAvatar() {
        return currentEntry != null ? currentEntry.avatar() : null;
    }

    public boolean hasCurrentEntry() {
        return currentEntry != null;
    }

    public int getQueueSize() {
        return queue.size();
    }

    /**
     * 清空队列
     */
    public void clearQueue() {
        queue.clear();
    }

    /**
     * 清空当前对话
     */
    public void clearCurrent() {
        if (currentEntry != null) {
            reset();
        }
    }

    /**
     * 获取队列中对话文本列表（用于调试）
     */
    public List<String> getQueueTexts() {
        List<String> texts = new ArrayList<>();
        for (DialogEntry entry : queue) {
            texts.add(entry.text().getString());
        }
        return texts;
    }
}

/**
 * 对话条目记录
 */
@OnlyIn(Dist.CLIENT)
record DialogEntry(Component text, ResourceLocation avatar, int priority, long enqueueTime) {
    public DialogEntry(Component text, ResourceLocation avatar, int priority) {
        this(text, avatar, priority, System.currentTimeMillis());
    }
}
