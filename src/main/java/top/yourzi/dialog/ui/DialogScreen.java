package top.yourzi.dialog.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.yourzi.dialog.Config;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.DialogManager;
import top.yourzi.dialog.model.BackgroundImageInfo;
import top.yourzi.dialog.model.BackgroundRenderOption;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogEffect;
import top.yourzi.dialog.model.DialogOption;
import top.yourzi.dialog.model.SoundInfo;
import top.yourzi.dialog.audio.DialogAudioManager;
import top.yourzi.dialog.model.DialogSequence;
import top.yourzi.dialog.model.PortraitAnimationType;
import top.yourzi.dialog.model.PortraitInfo;
import top.yourzi.dialog.model.PortraitPosition;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.Resource;

import java.util.HashMap;
import java.util.Optional;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.WidgetSprites;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.ConfirmScreen;
import top.yourzi.dialog.util.STBBackendImage;
import top.yourzi.dialog.ui.effects.DialogEffectManager;
import top.yourzi.dialog.model.DisplayItemInfo;

/**
 * 对话界面，用于显示对话框和立绘
 */
public class DialogScreen extends Screen {
    //存储每个立绘的显示数据
    private static class PortraitDisplayData {
        ResourceLocation resourceLocation;
        int actualWidth;
        int actualHeight;
        float brightness = 1.0f;
        PortraitPosition position;
        PortraitAnimationType animationType = PortraitAnimationType.NONE;
        long animationStartTime = -1;
        boolean loadedSuccessfully = false;

        // ---- 原生渲染核心新增字段 ----
        Float customX;
        Float customY;
        float baseScale = 1f;
        float baseRotation = 0f;
        float baseAlpha = 1f;
        int zOrder = 0;
        String anchor = "bottom_center";
        PortraitAnimator animator;

        private final static HashMap<ResourceLocation, BufferedImage> CACHED = new HashMap<>();

        PortraitDisplayData(PortraitInfo pInfo) {
            String path = pInfo.getPath();
            if (path != null && !path.isEmpty()) {
                this.resourceLocation = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, String.format("textures/portraits/%s", path));
                this.brightness = pInfo.getBrightness();
                this.position = pInfo.getPosition() != null ? pInfo.getPosition() : PortraitPosition.RIGHT; // 位置
                this.animationType = pInfo.getAnimationType() != null ? pInfo.getAnimationType() : PortraitAnimationType.NONE; // 动画类型
                // 新增可配置字段
                this.customX = pInfo.getX();
                this.customY = pInfo.getY();
                this.baseScale = pInfo.getScale();
                this.baseRotation = pInfo.getRotation();
                this.baseAlpha = pInfo.getAlpha();
                this.zOrder = pInfo.getZOrder();
                this.anchor = pInfo.getAnchor();
                if (pInfo.getAnimations() != null && !pInfo.getAnimations().isEmpty()) {
                    this.animator = new PortraitAnimator(pInfo.getAnimations());
                }
                loadDimensions();
                if (Config.ENABLE_PORTRAIT_ANIMATIONS.get() && loadedSuccessfully && this.animationType != PortraitAnimationType.NONE) {
                    this.animationStartTime = System.currentTimeMillis();
                }
            } else {
                Dialog.LOGGER.warn("Portrait path is null or empty. Cannot load portrait.");
            }
        }

        private void loadDimensions() {
            if (this.resourceLocation == null) return;
            var target_bufferedimage = CACHED.get(this.resourceLocation);
            if (target_bufferedimage == null) {
                try {
                    Optional<Resource> resourceOptional = Minecraft.getInstance().getResourceManager().getResource(this.resourceLocation);
                    if (resourceOptional.isPresent()) {
                        try (final var inputStream = resourceOptional.get().open()) {
                            target_bufferedimage = STBBackendImage.read(inputStream);
                            this.actualWidth = target_bufferedimage.getWidth();
                            this.actualHeight = target_bufferedimage.getHeight();
                            this.loadedSuccessfully = true;
                            CACHED.put(this.resourceLocation, target_bufferedimage);
                        }
                    } else {
                        Dialog.LOGGER.warn("Portrait resource not found: {}.", this.resourceLocation);
                    }
                } catch (IOException e) {
                    Dialog.LOGGER.error("Error reading portrait image {}: {}.", this.resourceLocation, e.getMessage());
                } catch (Exception e) {
                    Dialog.LOGGER.error("Unexpected error loading portrait image {}: {}.", this.resourceLocation, e.getMessage());
                }
            } else {
                this.actualWidth = target_bufferedimage.getWidth();
                this.actualHeight = target_bufferedimage.getHeight();
                this.loadedSuccessfully = true;
            }
        }
    }

    // 对话序列和当前对话条目
    private final DialogSequence dialogSequence;
    private final DialogEntry dialogEntry;

    // 对话框位置和大小
    private int dialogBoxX;
    private int dialogBoxY;
    private int dialogBoxWidth;
    private int dialogBoxHeight;

    // 选项按钮列表
    private final List<OptionButton> optionButtons = new ArrayList<>();

    private final String playerName;

    //立绘数据列表
    private final List<PortraitDisplayData> portraitDisplayList = new ArrayList<>();

    private static final int ANIMATION_DURATION_MS = 300; // 动画持续时间，单位毫秒

    // 文本动画相关
    private int currentCharIndex = 0;
    private long lastCharTime = 0;
    private boolean textFullyDisplayed = false;

    // 快速跳过相关
    private int fastForwardCooldown = 0;
    private boolean optionButtonsCreated = false; // 标记选项按钮是否已为当前条目创建

    // 对话历史记录界面相关
    private boolean showingHistory = false;
    private int historyScrollOffset = 0;
    private List<DialogEntry> historyEntries = new ArrayList<>();
    private Button closeHistoryButton; // 关闭历史记录按钮
    private Button viewHistoryButton; // 查看历史按钮
    private Button autoPlayButton; // 自动播放按钮

    // 背景图片相关
    private BackgroundImageDisplayData backgroundImageDisplayData;

    // 需要在对话中显示的物品列表
    private final List<ItemStack> displayItemStacks = new ArrayList<>();

    // 滚动条相关
    private int totalHistoryContentHeight = 0;
    private boolean canScrollHistoryDown = false;
    private boolean canScrollHistoryUp = false;

    // 全屏过场动画
    private CutsceneRenderer cutsceneRenderer = null;
    private boolean cutsceneInit = false;

    // 文本交互控制
    private net.minecraft.client.gui.components.EditBox inputBox = null;
    private boolean waitingForInput = false;
    private boolean inputConsumed = false;
    private long inputStartMs = -1;

    public DialogScreen(DialogSequence dialogSequence, DialogEntry dialogEntry, String playerName) {

        super(dialogEntry.getSpeaker() != null ? dialogEntry.getSpeaker(Minecraft.getInstance().level.registryAccess(), playerName) : Component.empty());
        this.dialogSequence = dialogSequence;
        this.dialogEntry = dialogEntry;
        this.playerName = playerName;
        this.font = Minecraft.getInstance().font;

        // 加载背景资源（图片 / 渐变 / 纯色）
        if (dialogEntry.getBackgroundImage() != null) {
            BackgroundImageInfo bg = dialogEntry.getBackgroundImage();
            // 图片类型：必须要有 path 才创建；gradient/color 类型无需 path
            if (bg.isImage() && bg.getPath() != null && !bg.getPath().isEmpty()) {
                this.backgroundImageDisplayData = new BackgroundImageDisplayData(bg);
            } else if (!bg.isImage()) {
                this.backgroundImageDisplayData = new BackgroundImageDisplayData(bg);
            }
        }

        // 加载多个立绘资源
        if (dialogEntry.getPortraits() != null && !dialogEntry.getPortraits().isEmpty()) {
            for (top.yourzi.dialog.model.PortraitInfo portraitInfo : dialogEntry.getPortraits()) {
                if (portraitInfo.getPath() != null && !portraitInfo.getPath().isEmpty()) {
                    PortraitDisplayData displayData = new PortraitDisplayData(portraitInfo);
                    if (displayData.loadedSuccessfully) {
                        this.portraitDisplayList.add(displayData);
                    }
                } else {
                    Dialog.LOGGER.warn("Encountered a portrait info with null or empty path.");
                }
            }
        } else {
            Dialog.LOGGER.warn("No portrait configurations found in DialogEntry or the list is empty.");
        }

                // 加载需要在对话中显示的物品
                if (dialogEntry.getDisplayItems() != null && !dialogEntry.getDisplayItems().isEmpty()) {
                    for (top.yourzi.dialog.model.DisplayItemInfo itemInfo : dialogEntry.getDisplayItems()) {
                        if (itemInfo.getItemId() != null && !itemInfo.getItemId().isEmpty()) {
                            try {
                                ResourceLocation itemRl = ResourceLocation.withDefaultNamespace(itemInfo.getItemId());
                                Item item = BuiltInRegistries.ITEM.get(itemRl);
                                if (item != null && item != Items.AIR) {
                                    ItemStack itemStack = new ItemStack(item, itemInfo.getCount() > 0 ? itemInfo.getCount() : 1);
                                    if (itemInfo.getNbt() != null && !itemInfo.getNbt().isEmpty()) {
                                        try {
                                            CompoundTag nbtTag = TagParser.parseTag(itemInfo.getNbt());
                                            itemStack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbtTag));
                                        } catch (Exception e) {
                                            Dialog.LOGGER.error("Error parsing NBT or setting custom data for display item {}: {}. NBT: '{}'", itemInfo.getItemId(), e.getMessage(), itemInfo.getNbt());
                                        }
                                    }
                                    this.displayItemStacks.add(itemStack);
                                } else {
                                    Dialog.LOGGER.warn("Item not found or is AIR: {}. Skipping display item.", itemInfo.getItemId());
                                }
                            } catch (Exception e) {
                                Dialog.LOGGER.error("Error creating ItemStack for display item {}: {}", itemInfo.getItemId(), e.getMessage());
                            }
                        } else {
                            Dialog.LOGGER.warn("Encountered a display item with null or empty itemId.");
                        }
                    }
                }

        // 检查是否由快速跳过触发
        if (DialogManager.isFastForwardingNext()) {
            this.fastForwardCooldown = 5;
            DialogManager.setFastForwardingNext(false); // 重置标记
        }
    }

        // 管理背景显示数据（图片 / 渐变 / 纯色）
        private static class BackgroundImageDisplayData {
            private ResourceLocation imageLocation;
            private BackgroundRenderOption renderOption;
            private BackgroundImageInfo info; // 原始背景信息（渐变/纯色需要）
            private STBBackendImage image;
            private boolean loadedSuccessfully = false;
            private int imageWidth;
            private int imageHeight;
            // 添加动画相关字段
            private boolean fadeInStarted = false;
            private boolean fadeOutStarted = false;
            private long fadeInStartTime = -1;
            private long fadeOutStartTime = -1;
            public static final int FADE_DURATION_MS = 500; // 淡入淡出持续时间，0.5秒
    
            public BackgroundImageDisplayData(BackgroundImageInfo backgroundImageInfo) {
                this.info = backgroundImageInfo;
                this.renderOption = backgroundImageInfo.getRenderOption();
                if (backgroundImageInfo.isImage()) {
                    this.imageLocation = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "textures/backgrounds/" + backgroundImageInfo.getPath());
                    loadResource();
                    if (loadedSuccessfully) {
                        startFadeIn();
                    }
                } else {
                    // gradient / color 无需加载图片，直接视为加载成功
                    this.loadedSuccessfully = true;
                    startFadeIn();
                }
            }

            public boolean isGradient() { return info != null && info.isGradient(); }
            public boolean isColor() { return info != null && info.isColor(); }
            public BackgroundImageInfo getInfo() { return info; }
            
            // 开始淡入动画
            public void startFadeIn() {
                this.fadeInStarted = true;
                this.fadeOutStarted = false;
                this.fadeInStartTime = System.currentTimeMillis();
            }
            
            // 开始淡出动画
            public void startFadeOut() {
                this.fadeOutStarted = true;
                this.fadeInStarted = false;
                this.fadeOutStartTime = System.currentTimeMillis();
            }
            
            // 获取当前透明度
            public float getCurrentAlpha() {
                long currentTime = System.currentTimeMillis();
                float alpha = 1.0f;
                
                if (fadeInStarted && fadeInStartTime != -1) {
                    long elapsedTime = currentTime - fadeInStartTime;
                    if (elapsedTime < FADE_DURATION_MS) {
                        // 淡入过程中，透明度从0逐渐增加到1
                        alpha = (float) elapsedTime / FADE_DURATION_MS;
                    } else {
                        // 淡入完成
                        fadeInStarted = false;
                    }
                } else if (fadeOutStarted && fadeOutStartTime != -1) {
                    long elapsedTime = currentTime - fadeOutStartTime;
                    if (elapsedTime < FADE_DURATION_MS) {
                        // 淡出过程中，透明度从1逐渐减少到0
                        alpha = 1.0f - (float) elapsedTime / FADE_DURATION_MS;
                    } else {
                        // 淡出完成
                        fadeOutStarted = false;
                        alpha = 0.0f;
                    }
                }
                
                return alpha;
            }
    
            private void loadResource() {
                try {
                    Optional<Resource> resourceOptional = Minecraft.getInstance().getResourceManager().getResource(imageLocation);
                    if (resourceOptional.isPresent()) {
                        try (InputStream inputStream = resourceOptional.get().open()) {
                            this.image = STBBackendImage.read(inputStream);
                            this.imageWidth = image.getWidth();
                            this.imageHeight = image.getHeight();
                            this.loadedSuccessfully = true;
                        } catch (IOException e) {
                            Dialog.LOGGER.error("Failed to load background image: {}", imageLocation, e);
                        }
                    } else {
                        Dialog.LOGGER.warn("Background image resource not found: {}", imageLocation);
                    }
                } catch (Exception e) {
                    Dialog.LOGGER.error("Error accessing background image resource: {}", imageLocation, e);
                }
            }
    
            public void close() {
                if (image != null) {
                    image.close();
                }
            }
        }

    @Override
    protected void init() {
        super.init();
        
        // 设置对话框位置和大小
        dialogBoxWidth = Config.DIALOG_BOX_WIDTH.get();
        dialogBoxHeight = Config.DIALOG_BOX_HEIGHT.get();
        dialogBoxX = (width - dialogBoxWidth) / 2;
        dialogBoxY = height - dialogBoxHeight - 20;

        // 初始化查看历史按钮 (位于对话框右下角)
        int historyButtonWidth = 20;
        int historyButtonHeight = 20;
        int historyButtonPadding = 5;
        this.viewHistoryButton = Button.builder(Component.literal("▲"), (button) -> {
            toggleHistoryScreen();
        }).bounds(dialogBoxX + dialogBoxWidth - historyButtonWidth - historyButtonPadding, 
                  dialogBoxY + dialogBoxHeight - historyButtonHeight - historyButtonPadding, 
                  historyButtonWidth, historyButtonHeight).build();
        this.addRenderableWidget(this.viewHistoryButton);

        // 初始化自动播放按钮 (位于历史记录按钮左侧)
        int autoPlayButtonWidth = 20;
        int autoPlayButtonHeight = 20;
        this.autoPlayButton = Button.builder(Component.literal("▶"), (button) -> {
            toggleAutoPlay();
        }).bounds(dialogBoxX + dialogBoxWidth - historyButtonWidth - historyButtonPadding - autoPlayButtonWidth - historyButtonPadding, 
                  dialogBoxY + dialogBoxHeight - autoPlayButtonHeight - historyButtonPadding, 
                  autoPlayButtonWidth, autoPlayButtonHeight).build();
        this.addRenderableWidget(this.autoPlayButton);
        updateAutoPlayButtonText(); // 初始化按钮文本
        
        // 如果此对话条目有选项，预先停止自动播放
        if (dialogEntry.hasOptions()) {
            if (DialogManager.isAutoPlaying()) {
                DialogManager.stopAutoPlay();
                updateAutoPlayButtonText(); // 更新按钮文本以反映自动播放已停止
            }
        }
        this.optionButtonsCreated = false; // 初始化选项按钮创建标记

        // 初始化关闭历史记录按钮 (用于关闭历史查看界面)
        this.closeHistoryButton = Button.builder(Component.literal("▼"), (button) -> {
            toggleHistoryScreen();
        }).bounds(this.width / 2 - 50, this.height - 30, 100, 20).build();

        // 触发当前对话条目的过场特效（如屏幕震动）
        triggerEntryEffects();
        // 触发当前对话条目的音频（BGM / SE / 语音）
        triggerEntryAudio();
        // 初始化全屏过场
        if (dialogEntry.getCutscene() != null && dialogEntry.getCutscene().isValid()) {
            this.cutsceneRenderer = new CutsceneRenderer(dialogEntry.getCutscene());
            this.cutsceneInit = false;
        }
        // 给予物品 + 获得弹窗
        triggerItemAwards();
        // 初始化文本交互控制
        setupTextControl();
    }

    /**
     * 若当前条目有带 give:true 的物品，则执行给予命令并弹出收获 Toast。
     */
    private void triggerItemAwards() {
        if (dialogEntry.getDisplayItems() == null || dialogEntry.getDisplayItems().isEmpty()) return;
        if (this.minecraft == null) return;
        for (DisplayItemInfo itemInfo : dialogEntry.getDisplayItems()) {
            if (!itemInfo.isGive()) continue;
            if (itemInfo.getItemId() == null || itemInfo.getItemId().isEmpty()) continue;
            int count = Math.max(1, itemInfo.getCount());
            // 通过既有命令通道给予（单身生存模式有效）
            DialogManager.getInstance().executeCommands(this.minecraft.player,
                    java.util.Collections.singletonList("give @s " + itemInfo.getItemId() + " " + count));
            // 弹窗
            try {
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        net.minecraft.resources.ResourceLocation.parse(itemInfo.getItemId()));
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                    ItemStack st = new ItemStack(item, count);
                    this.minecraft.getToasts().addToast(new ItemAwardToast(st));
                }
            } catch (Exception e) {
                Dialog.LOGGER.warn("Fail to toast award item {}", itemInfo.getItemId());
            }
        }
    }

    /**
     * 播放当前对话条目的音频配置（BGM 支持 JSON 指令驱动）。
     */
    private void triggerEntryAudio() {
        SoundInfo sound = dialogEntry.getSound();
        if (sound == null || !sound.hasAny()) return;
        DialogAudioManager audio = DialogAudioManager.getInstance();
        String action = sound.getBgmAction();
        // 指令驱动的 BGM 控制（start/play/switch/pause/resume/stop/next/prev）
        if (action != null && !action.isEmpty()) {
            audio.handleAction(action, sound.getBgm());
        } else if (sound.getBgm() != null && !sound.getBgm().isEmpty()) {
            // 向后兼容：仅有 bgm 字段时视作 play
            audio.handleAction("play", sound.getBgm());
        }
        if (sound.getBgmVolume() != null) {
            audio.setVolume(sound.getBgmVolume());
        }
        if (sound.getSe() != null && !sound.getSe().isEmpty()) {
            audio.playSe(sound.getSe());
        }
        audio.playVoice(sound);
    }

    /**
     * 根据当前对话条目的 effects 配置触发过场特效（客户端本地）。
     */
    private void triggerEntryEffects() {
        if (dialogEntry.getEffects() != null && !dialogEntry.getEffects().isEmpty()) {
            for (DialogEffect effect : dialogEntry.getEffects()) {
                try {
                    if (effect.isShake()) {
                        DialogEffectManager.getInstance().triggerShake(effect.getIntensity(), effect.getDurationMs());
                    } else if (effect.isFlash()) {
                        int rgb = parseHexColor(effect.getColor() != null ? effect.getColor() : "#ffffff");
                        DialogEffectManager.getInstance().triggerOverlay(rgb, clampIntensity(effect.getIntensity()), effect.getDurationMs());
                    } else if (effect.isTint()) {
                        int rgb = parseHexColor(effect.getColor() != null ? effect.getColor() : "#ffffff");
                        DialogEffectManager.getInstance().triggerOverlay(rgb, clampIntensity(effect.getIntensity()), effect.getDurationMs());
                    } else if (effect.isFade()) {
                        int rgb = effect.getColor() != null ? parseHexColor(effect.getColor()) : 0x000000;
                        DialogEffectManager.getInstance().triggerOverlay(rgb, clampIntensity(effect.getIntensity() > 0 ? effect.getIntensity() : 0.8f), effect.getDurationMs());
                    }
                } catch (Exception e) {
                    Dialog.LOGGER.warn("Effect trigger failed for type {}: {}", effect.getType(), e.getMessage());
                }
            }
        }
    }

    private float clampIntensity(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    // ===== 文本交互控制 =====

    /** 初始化文本交互控制（在 init 调用）。 */
    private void setupTextControl() {
        var tc = dialogEntry.getTextControl();
        if (tc != null && tc.isWaitForInput()) {
            this.waitingForInput = true;
            this.inputConsumed = false;
            this.inputStartMs = -1;
            int bx = dialogBoxX + Config.DIALOG_BOX_PADDING.get();
            int by = dialogBoxY + dialogBoxHeight - 26;
            this.inputBox = new net.minecraft.client.gui.components.EditBox(font, bx, by, 200, 16, Component.literal(""));
            this.inputBox.setMaxLength(tc.getMaxLength());
            this.inputBox.setHint(Component.literal(tc.getInputHint()));
            this.inputBox.setFocused(true); // 关键：不聚焦则无法键入
            this.inputBox.setCanLoseFocus(false);
        } else {
            this.waitingForInput = false;
        }
    }

    /** 提交输入并继续对话。 */
    private void submitAndContinue() {
        this.inputConsumed = true;
        this.waitingForInput = false;
        var tc = this.dialogEntry.getTextControl();
        if (tc != null && tc.getVariable() != null && !tc.getVariable().isEmpty()) {
            String value = this.inputBox != null ? this.inputBox.getValue() : "";
            DialogManager.getInstance().setDialogVariable(tc.getVariable(), value);
        }
        if (dialogEntry.getCommand() != null && !dialogEntry.getCommand().isEmpty()) {
            DialogManager.getInstance().executeCommands(this.minecraft.player, dialogEntry.getCommand());
        }
        DialogManager.getInstance().showNextDialog();
    }

    /** 将文本中的 {@var} 占位替换为对话变量值。 */
    private String applyVariables(String text) {
        if (text == null) return "";
        if (text.indexOf('{') < 0) return text;
        try {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            while (i < text.length()) {
                int open = text.indexOf('{', i);
                if (open < 0) { sb.append(text, i, text.length()); break; }
                int close = text.indexOf('}', open);
                sb.append(text, i, open);
                if (close < 0) { sb.append(text, open, text.length()); break; }
                String varName = text.substring(open + 1, close);
                String val = DialogManager.getInstance().getDialogVariable(varName);
                sb.append(val != null ? val : "");
                i = close + 1;
            }
            return sb.toString();
        } catch (Exception e) {
            return text;
        }
    }
    
    /**
     * 创建对话选项按钮
     */
    private void createOptionButtons() {
        optionButtons.clear();
        
        DialogOption[] options = dialogEntry.getOptions();
        if (options == null || options.length == 0) {
            return;
        }
        
        int buttonWidth = 200;
        int buttonHeight = 20;
        WidgetSprites sprites;

        if (Config.USE_CUSTOM_BUTTON_TEXTURE.get()) {
        // 使用自定义纹理
        ResourceLocation buttonTexture = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "widget/button");
        ResourceLocation buttonHighlightTexture = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "widget/button_highlighted");
        ResourceLocation buttonDisabledTexture = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "widget/button_disabled");

        sprites = new WidgetSprites(
            buttonTexture,           // enabled
            buttonDisabledTexture,   // disabled  
            buttonHighlightTexture,  // highlighted
            buttonHighlightTexture   // focused
            );

        }else{

        // 使用 Minecraft 默认的按钮纹理
        ResourceLocation buttonTexture = ResourceLocation.withDefaultNamespace("widget/button");
        ResourceLocation buttonHighlightTexture = ResourceLocation.withDefaultNamespace("widget/button_highlighted");
        ResourceLocation buttonDisabledTexture = ResourceLocation.withDefaultNamespace("widget/button_disabled");

        sprites = new WidgetSprites(
            buttonTexture,           // enabled
            buttonDisabledTexture,   // disabled  
            buttonHighlightTexture,  // highlighted
            buttonHighlightTexture   // focused
            );
        }



            int buttonSpacing = 5;
            int totalHeight = options.length * (buttonHeight + buttonSpacing) - buttonSpacing;
            int startY = dialogBoxY - totalHeight - 10;

        for (int i = 0; i < options.length; i++) {
            DialogOption option = options[i];
            int buttonY = startY + i * (buttonHeight + buttonSpacing);
            
            OptionButton button = new OptionButton(
                    (width - buttonWidth) / 2, // x
                    buttonY,                   // y
                    buttonWidth,               // width
                    buttonHeight,              // height
                    sprites, // WidgetSprites - 您需要根据需要提供合适的 WidgetSprites
                    b -> {                     // OnPress
                        // 执行选项指令（如果存在）
                        if (option.getCommand() != null && !option.getCommand().isEmpty()) {
                            DialogManager.getInstance().executeCommands(this.getMinecraft().player, option.getCommand());
                        }
                        DialogManager.getInstance().recordChoiceForCurrentDialog(option.getText(Minecraft.getInstance().level.registryAccess(), playerName).getString());
                        DialogManager.getInstance().jumpToDialog(option.getTargetId());
                    },
                    option.getText(Minecraft.getInstance().level.registryAccess(), playerName) // Component message
            );
            
            optionButtons.add(button);
            addRenderableWidget(button);
        }
    }
    
    @Override
    public void renderBackground(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
    //模糊效果也干了
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {

        // 全屏过场：播放期间只显示过场图，不渲染对话 UI
        if (this.cutsceneRenderer != null) {
            if (!this.cutsceneInit) {
                this.cutsceneRenderer.init();
                this.cutsceneInit = true;
            }
            if (!this.cutsceneRenderer.isFinished()) {
                // 渲染黑底 + 过场图
                guiGraphics.fill(0, 0, this.width, this.height, 0xFF000000);
                this.cutsceneRenderer.render(guiGraphics, this.width, this.height);
                return; // 不渲染其它内容
            }
            // 播放结束，清理并继续正常渲染
            this.cutsceneRenderer = null;
        }

        // 首先渲染背景 (如果存在)
        if (this.backgroundImageDisplayData != null) {
            if (this.backgroundImageDisplayData.isGradient()) {
                renderBackgroundGradient(guiGraphics, this.backgroundImageDisplayData);
            } else if (this.backgroundImageDisplayData.isColor()) {
                renderBackgroundColor(guiGraphics, this.backgroundImageDisplayData);
            } else if (this.backgroundImageDisplayData.loadedSuccessfully) {
                renderBackgroundImage(guiGraphics, this.backgroundImageDisplayData);
            }
        }

        // 如果正在显示历史记录，则渲染历史记录界面
        if (showingHistory) {
            renderHistoryScreen(guiGraphics, mouseX, mouseY, partialTicks);
            // 渲染历史记录界面的关闭按钮
            this.closeHistoryButton.render(guiGraphics, mouseX, mouseY, partialTicks);
            return; // 不渲染对话框和立绘
        }


        // 渲染立绘（按 zOrder 排序，先画层级低的）
        if (!portraitDisplayList.isEmpty()) {
            List<PortraitDisplayData> ordered = new ArrayList<>(portraitDisplayList);
            ordered.sort(java.util.Comparator.comparingInt(d -> d.zOrder));

            for (PortraitDisplayData displayData : ordered) {
                if (displayData.loadedSuccessfully && displayData.resourceLocation != null && displayData.actualWidth > 0 && displayData.actualHeight > 0) {
                    RenderSystem.setShader(GameRenderer::getPositionTexShader);
                    RenderSystem.setShaderTexture(0, displayData.resourceLocation);
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();

                    int portraitRenderHeight = (int) (this.height * 0.7); // 固定高度
                    float aspectRatio = (float) displayData.actualWidth / displayData.actualHeight;
                    int portraitRenderWidth = (int) (portraitRenderHeight * aspectRatio); // 等比例计算宽度

                    // 动画合成
                    float currentScale = 1.0f;
                    float currentAlpha = 1.0f;
                    float xOffset = 0;
                    float yOffset = 0;
                    float rotationDeg = 0f;
                    float shakeX = 0;
                    float shakeY = 0;
                    boolean hasScaleAnim = false;

                    long currentTime = System.currentTimeMillis();
                    float progress = 1.0f;

                    // ---- 新动画系统（JSON 驱动）----
                    if (displayData.animator != null && Config.ENABLE_PORTRAIT_ANIMATIONS.get()) {
                        PortraitAnimator.Pose pose = displayData.animator.compute(currentTime);
                        xOffset += pose.offsetX;
                        yOffset += pose.offsetY;
                        currentScale *= pose.scale;
                        if (pose.hasScale) hasScaleAnim = true;
                        currentAlpha *= pose.alpha;
                        rotationDeg += pose.rotation;
                        shakeX += pose.shakeX;
                        shakeY += pose.shakeY;
                    }

                    // ---- 旧版动画类型（兼容）----
                    if (Config.ENABLE_PORTRAIT_ANIMATIONS.get() && displayData.animationType != PortraitAnimationType.NONE && displayData.animationStartTime != -1) {
                        long elapsedTime = currentTime - displayData.animationStartTime;
                        if (elapsedTime < ANIMATION_DURATION_MS) {
                            progress = (float) elapsedTime / ANIMATION_DURATION_MS;
                        } else {
                            displayData.animationStartTime = -1;
                        }
                        switch (displayData.animationType) {
                            case FADE_IN:
                                currentAlpha *= Mth.lerp(progress, 0f, 1f);
                                break;
                            case SLIDE_IN_FROM_BOTTOM:
                            case SLIDE_IN_TOP:
                                {
                                    float fromY = (displayData.animationType == PortraitAnimationType.SLIDE_IN_FROM_BOTTOM) ? 60f : -60f;
                                    yOffset += Mth.lerp(progress, fromY, 0f);
                                }
                                break;
                            case SLIDE_IN_LEFT:
                                xOffset += Mth.lerp(progress, -120f, 0f);
                                break;
                            case SLIDE_IN_RIGHT:
                                xOffset += Mth.lerp(progress, 120f, 0f);
                                break;
                            case SCALE_UP:
                                currentScale *= Mth.lerp(progress, 0.3f, 1f);
                                break;
                            case BOUNCE:
                                if (progress < 0.5f) {
                                    yOffset += Mth.lerp(progress * 2, 0f, -20f);
                                } else {
                                    yOffset += Mth.lerp((progress - 0.5f) * 2, -20f, 0f);
                                }
                                break;
                            case NONE:
                            default:
                                break;
                        }
                    }

                    // 基准缩放 / 透明度 / 旋转（新增 JSON 字段）
                    // scale 动画的 from/to 定义为"相对基准尺寸的最终倍率"，
                    // 因此在存在 scale 动画时直接用动画结果作为最终缩放，
                    // 避免与 baseScale 重复相乘导致立绘被过度缩小（缩成角落小点）。
                    float totalScale = hasScaleAnim ? currentScale : currentScale * displayData.baseScale;
                    currentAlpha *= displayData.baseAlpha;
                    rotationDeg += displayData.baseRotation;

                    // 高度、底色
                    float totalAlpha = Mth.clamp(currentAlpha, 0f, 1f);
                    if (displayData.brightness == 0.0f) {
                        RenderSystem.setShaderColor(0.0f, 0.0f, 0.0f, totalAlpha);
                    } else {
                        RenderSystem.setShaderColor(displayData.brightness, displayData.brightness, displayData.brightness, totalAlpha);
                    }

                    // ===== 固定缩放/旋转原点（默认底部中心）=====
                    // 基准像素尺寸（scale=1）用于计算一个固定的锚点，缩放/旋转围绕它进行，
                    // 避免缩放时立绘向角落漂移或缩到屏幕外。
                    int baseW = portraitRenderWidth;
                    int baseH = portraitRenderHeight;

                    // 1) 计算立绘的"底部中点"固定锚点（屏幕坐标），与缩放无关
                    int anchorX, anchorY;
                    int pad = Math.max(14, baseW / 8);
                    switch (displayData.position) {
                        case LEFT:
                            anchorX = pad + baseW / 2;
                            anchorY = this.height;
                            break;
                        case RIGHT:
                            anchorX = this.width - pad - baseW / 2;
                            anchorY = this.height;
                            break;
                        case CENTER:
                        default:
                            anchorX = this.width / 2;
                            anchorY = this.height;
                            break;
                    }
                    // 自定义锚点：anchor="bottom_x/center..." 语义 —— (customX,customY) 作为该锚点底部的中心点
                    if (displayData.customX != null || displayData.customY != null) {
                        int ax = displayData.customX != null ? displayData.customX.intValue() : anchorX;
                        int ay = displayData.customY != null ? displayData.customY.intValue() : anchorY;
                        int[] p = anchorCenterPoint(displayData.anchor, ax, ay, baseW);
                        anchorX = p[0];
                        anchorY = p[1];
                    }

                    // 2) 依据当前缩放后的尺寸，围绕固定锚点放置（锚点即立绘底边中心）
                    int scaledWidth = (int) (baseW * totalScale);
                    int scaledHeight = (int) (baseH * totalScale);
                    if (scaledWidth < 1) scaledWidth = 1;
                    if (scaledHeight < 1) scaledHeight = 1;

                    int anchorCenterX = anchorX; // 底部中心锚
                    int anchorBottomY = anchorY;

                    // 3) 应用立绘内动画位移 + 抖动
                    // 底部中心锚点 → 左上角：(锚点X - 宽/2, 锚点Y - 高)
                    int finalX = anchorCenterX - scaledWidth / 2 + (int) xOffset + (int) shakeX;
                    int finalY = anchorBottomY - scaledHeight + (int) yOffset + (int) shakeY;

                    // 4) 旋转（绕锚点——底部中心）以保持紧贴画面
                    if (Math.abs(rotationDeg) % 360f < 0.01f) {
                        guiGraphics.blit(displayData.resourceLocation, finalX, finalY, 0, 0, scaledWidth, scaledHeight, scaledWidth, scaledHeight);
                    } else {
                        renderRotated(guiGraphics, displayData.resourceLocation, anchorCenterX, anchorBottomY, scaledWidth, scaledHeight, rotationDeg);
                    }
                    RenderSystem.disableBlend();
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F); // 重置颜色
                }
            }
        }

        // 渲染对话框背景
        int dialogBgColor = Config.DIALOG_BACKGROUND_COLOR.get();
        int dialogBgOpacity = Config.DIALOG_BACKGROUND_OPACITY.get();

        // 1) 若存在对话框背景图片，则拉伸绘制
        try {
            ResourceLocation dialogBgRl = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "textures/dialog_background/background.png");
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, dialogBgRl);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            guiGraphics.blit(dialogBgRl, dialogBoxX, dialogBoxY, 0, 0.0F, 0.0F, dialogBoxWidth, dialogBoxHeight, dialogBoxWidth, dialogBoxHeight);
            RenderSystem.disableBlend();
        } catch (Exception e) {
            // 忽略，继续走底色
        }

        // 2) 叠加半透明面板底色（带轻微垂直渐变，增强层次感）
        int baseAlpha = Mth.clamp(dialogBgOpacity, 0, 255);
        int panelBase = (baseAlpha << 24) | (dialogBgColor & 0xFFFFFF);
        int panelTop = (Mth.clamp((int) (baseAlpha * 0.5f), 0, 255) << 24) | (dialogBgColor & 0xFFFFFF);
        guiGraphics.fillGradient(dialogBoxX, dialogBoxY, dialogBoxX + dialogBoxWidth, dialogBoxY + dialogBoxHeight, panelTop, panelBase);

        // 3) 顶部细亮边
        guiGraphics.fill(dialogBoxX, dialogBoxY, dialogBoxX + dialogBoxWidth, dialogBoxY + 1, 0x55222222);

        // 如果自动播放开启且无选项，显示提示
        if (DialogManager.isAutoPlaying() && !dialogEntry.hasOptions()) {
            Component autoPlayText = Component.literal("[AUTO]");
            int autoPlayTextWidth = this.font.width(autoPlayText);
            // 将提示显示在对话框的右上角外部一点或者左上角，避免遮挡按钮
            guiGraphics.drawString(this.font, autoPlayText, dialogBoxX + dialogBoxWidth - autoPlayTextWidth - 5, dialogBoxY - 15, 0xFFFFFF);
        }
        
        // 渲染对话文本
        int padding = Config.DIALOG_BOX_PADDING.get();
        int textX = dialogBoxX + padding;
        int textY = dialogBoxY + padding;
        
        // 如果显示说话者名称且有说话者
        Component speakerComponent = dialogEntry.getSpeaker(Minecraft.getInstance().level.registryAccess(), playerName);
        if (Config.SHOW_SPEAKER_NAME.get() && speakerComponent != null && !speakerComponent.getString().isEmpty()) {
            guiGraphics.drawString(font, Component.literal("[").append(speakerComponent).append("]"), textX, textY, 0xFFFFFF);
            textY += font.lineHeight + 5;
        }
        
        // 渲染对话文本
        String rawText = applyVariables(dialogEntry.getText(Minecraft.getInstance().level.registryAccess(), playerName).getString());
        if (rawText != null && !rawText.isEmpty()) {
            int maxWidth = dialogBoxWidth - (padding * 2);
            int textAnimationSpeed = Config.TEXT_ANIMATION_SPEED.get(); // 每秒字符数

            if (textAnimationSpeed <= 0) { // 立即显示
                textFullyDisplayed = true;
                currentCharIndex = rawText.length();
            }

            if (!textFullyDisplayed) {
                long currentTime = System.currentTimeMillis();
                if (lastCharTime == 0) { // 首次渲染或重置
                    lastCharTime = currentTime;
                }
                // 计算每字符间隔时间 (毫秒)
                long charInterval = (textAnimationSpeed > 0) ? (1000 / textAnimationSpeed) : 0;
                
                if (currentTime - lastCharTime >= charInterval) {
                    currentCharIndex++;
                    // 打字音效（每打出1个可见字符播放一次）
                    DialogAudioManager.getInstance().playTypeSound();
                    lastCharTime = currentTime;
                    if (currentCharIndex >= rawText.length()) {
                        textFullyDisplayed = true;
                        currentCharIndex = rawText.length(); // 确保索引不超过长度
                        lastCharTime = System.currentTimeMillis(); // 记录文本完全显示的时间点，用于自动播放计时
                    }
                }
            }

            // 渲染对话中展示的物品
            if (!this.displayItemStacks.isEmpty() && textFullyDisplayed) {
                int itemSize = 16;
                int itemPadding = 4;
                int totalItemWidth = (this.displayItemStacks.size() * itemSize) + (Math.max(0, this.displayItemStacks.size() - 1) * itemPadding);
            
                int startX = dialogBoxX + (dialogBoxWidth - totalItemWidth) / 2;
                int itemY = dialogBoxY - itemSize - 5;
            
                for (ItemStack itemStack : this.displayItemStacks) {
            
                    guiGraphics.renderItem(itemStack, startX, itemY);
            
                    if (mouseX >= startX && mouseX < startX + itemSize && mouseY >= itemY && mouseY < itemY + itemSize) {
                        guiGraphics.fill(startX, itemY, startX + itemSize, itemY + itemSize, 0x80000000);
                    }
            
                    guiGraphics.renderItemDecorations(this.font, itemStack, startX, itemY);
            
                    startX += itemSize + itemPadding;
                }
            
                startX = dialogBoxX + (dialogBoxWidth - totalItemWidth) / 2;
                for (ItemStack itemStack : this.displayItemStacks) {
                    if (mouseX >= startX && mouseX < startX + itemSize && mouseY >= itemY && mouseY < itemY + itemSize) {
                        guiGraphics.renderTooltip(this.font, itemStack, mouseX, mouseY);
                    }
                    startX += itemSize + itemPadding;
                }
            }

            // 文本交互：等待输入 → 渲染输入框
            if (this.waitingForInput && textFullyDisplayed && this.inputBox != null && !this.inputConsumed) {
                if (this.inputStartMs == -1) this.inputStartMs = System.currentTimeMillis();
                this.inputBox.setY(dialogBoxY + dialogBoxHeight - 26);
                this.inputBox.render(guiGraphics, mouseX, mouseY, partialTicks);
            }

            // 文本交互：auto_pause 自动停顿后推进
            var textCtrl = dialogEntry.getTextControl();
            if (textFullyDisplayed && !dialogEntry.hasOptions() && !this.waitingForInput
                    && textCtrl != null && textCtrl.isAutoPause()) {
                if (this.inputStartMs == -1) this.inputStartMs = System.currentTimeMillis();
                if (System.currentTimeMillis() - this.inputStartMs > textCtrl.getAutoPauseMs()) {
                    if (dialogEntry.getCommand() != null && !dialogEntry.getCommand().isEmpty()) {
                        DialogManager.getInstance().executeCommands(this.getMinecraft().player, dialogEntry.getCommand());
                    }
                    DialogManager.getInstance().showNextDialog();
                    return;
                }
            }

            // 如果自动播放开启，且文本完全显示，且没有选项，则延迟后自动前进
            if (DialogManager.isAutoPlaying() && textFullyDisplayed && !dialogEntry.hasOptions()) {
                if (System.currentTimeMillis() - lastCharTime > Config.AUTO_ADVANCE_DELAY.get()) { // lastCharTime 在文本完全显示后更新
                    DialogManager.getInstance().showNextDialog();
                    // 执行当前对话条目的指令
                    if (dialogEntry.getCommand() != null && !dialogEntry.getCommand().isEmpty()) {
                        DialogManager.getInstance().executeCommands(this.getMinecraft().player, dialogEntry.getCommand());
                    }
                    return;
                }
            }
            
            List<net.minecraft.util.FormattedCharSequence> lines;
            if (textFullyDisplayed) {
                lines = font.split(dialogEntry.getText(Minecraft.getInstance().level.registryAccess(), playerName), maxWidth);
            } else {
                String animatedString = rawText.substring(0, Math.min(currentCharIndex, rawText.length()));
                if (animatedString.isEmpty()) {
                    lines = java.util.Collections.emptyList();
                } else {
                    Component animatedTextComponent = Component.literal(animatedString);
                    lines = font.split(animatedTextComponent, maxWidth);
                }
            }
            
            for (net.minecraft.util.FormattedCharSequence line : lines) {
                guiGraphics.drawString(font, line, textX, textY, Config.DIALOG_TEXT_COLOR.get());
                textY += font.lineHeight;
            }
        }

        // 在文本完全显示后，并且有选项时，才创建和显示选项按钮
        if (textFullyDisplayed && dialogEntry.hasOptions()) {
            if (!this.optionButtonsCreated) {
                createOptionButtons();
                this.optionButtonsCreated = true;
            }
        }
        if (this.minecraft != null && this.minecraft.gameRenderer.currentEffect() != null && this.minecraft.gameRenderer.currentEffect().getName().equals("minecraft:shaders/post/blur.json")) {
            this.minecraft.gameRenderer.shutdownEffect();
        }
        // 渲染按钮和其他UI元素
        super.render(guiGraphics, mouseX, mouseY, partialTicks);

        // 全屏覆盖层（闪光 / 黑场 / 色调）—— 盖在最上层
        try {
            int overlay = DialogEffectManager.getInstance().getOverlayColorARGB();
            if (overlay != 0) {
                guiGraphics.fill(0, 0, this.width, this.height, overlay);
            }
        } catch (Exception e) {
            Dialog.LOGGER.warn("Overlay render error: {}", e.getMessage());
        }


        // 悬浮文本提示
        if (this.viewHistoryButton.isMouseOver(mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font, Component.translatable("dialog.ui.history"), mouseX, mouseY);
        }
        if (this.autoPlayButton.isMouseOver(mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font, Component.translatable("dialog.ui.auto_play"), mouseX, mouseY);
        }

        // 处理快速跳过
        boolean isCtrlPressed = Minecraft.getInstance().getWindow() != null &&
                                (GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                                 GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS);

        // 如果按下Ctrl键快速跳过，则关闭自动播放
        if (isCtrlPressed && DialogManager.isAutoPlaying()) {
            DialogManager.stopAutoPlay();
            updateAutoPlayButtonText();
        }

        if (isCtrlPressed && !dialogEntry.hasOptions()) {
            if (fastForwardCooldown > 0) {
                fastForwardCooldown--;
            } else {
                DialogManager.setFastForwardingNext(true);
                DialogManager.getInstance().showNextDialog();
                return; // 立即跳到下一条，避免渲染当前帧的剩余部分
            }
        } else {
            // 如果Ctrl未按下或有选项，则清除快速跳过标记，确保正常流程
            DialogManager.setFastForwardingNext(false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return Config.IS_PAUSE_SCREEN.get();
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.waitingForInput && !this.inputConsumed && this.inputBox != null && textFullyDisplayed) {
            return this.inputBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (this.showingHistory) {
                // 如果在历史记录界面，ESC键返回对话界面
                toggleHistoryScreen();
                return true; // 事件已处理
            } else {
                // 如果在对话界面，ESC键弹出确认关闭的提示
                this.minecraft.setScreen(new ConfirmScreen(
                    this::confirmCloseDialog,
                    Component.translatable("dialog.ui.esc"), // 确认框标题
                    Component.translatable("dialog.ui.confirm_esc") // 确认框消息
                ));
                return true; // 事件已处理
            }
        }

        // 处理其他键的通用行为
        if (this.showingHistory) {return false;}

        // 等待输入状态：将输入交给输入框，回车提交
        if (this.waitingForInput && !this.inputConsumed && this.inputBox != null && textFullyDisplayed) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitAndContinue();
                return true;
            }
            if (this.inputBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            return false; // 输入模式吞掉其它键（除空格等避免误推进）
        }

        // 当文本完全显示，且没有选项时，按空格键可以手动前进
        if (textFullyDisplayed && !dialogEntry.hasOptions() && (keyCode == GLFW.GLFW_KEY_SPACE)) {
            if (DialogManager.isAutoPlaying()) {
                DialogManager.stopAutoPlay();
                updateAutoPlayButtonText();
            }
            if (dialogEntry.getCommand() != null && !dialogEntry.getCommand().isEmpty()) {
                DialogManager.getInstance().executeCommands(this.getMinecraft().player, dialogEntry.getCommand());
            }
            DialogManager.getInstance().showNextDialog();
            return true;
        }

        // 如果文本未完全显示，按空格则立即显示全部文本
        if (!textFullyDisplayed && (keyCode == GLFW.GLFW_KEY_SPACE)) {
            if (DialogManager.isAutoPlaying()) {
                DialogManager.stopAutoPlay();
                updateAutoPlayButtonText();
            }
            textFullyDisplayed = true;
            currentCharIndex = dialogEntry.getText(Minecraft.getInstance().level.registryAccess(), playerName).getString().length();
            lastCharTime = System.currentTimeMillis();
            return true;
        }

        // 对于其他未处理的按键，调用父类的处理方法
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    //处理关闭对话确认的回调方法
    private void confirmCloseDialog(boolean confirmed) {
        if (confirmed) {
            closeScreenWithFadeOut(); // 使用淡出效果关闭屏幕
        } else {
            // 如果用户选择"否"，则重新显示当前对话界面
            if (this.minecraft != null) {
                this.minecraft.setScreen(this);
            }
        }
    }
    
    // 使用淡出效果关闭屏幕
    private void closeScreenWithFadeOut() {
        // 在关闭对话框前启动背景图片淡出动画
        if (this.backgroundImageDisplayData != null && this.backgroundImageDisplayData.loadedSuccessfully) {
            this.backgroundImageDisplayData.startFadeOut();
            // 延迟关闭对话框，等待淡出动画完成
            new Thread(() -> {
                try {
                    Thread.sleep(BackgroundImageDisplayData.FADE_DURATION_MS);
                    Minecraft.getInstance().execute(() -> {
                        DialogManager.getInstance().stopAutoPlay();
                        super.onClose();
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // 如果线程被中断，直接关闭对话框
                    Minecraft.getInstance().execute(() -> {
                        DialogManager.getInstance().stopAutoPlay();
                        super.onClose();
                    });
                }
            }).start();
        } else {
            // 如果没有背景图片，直接关闭对话框
            DialogManager.getInstance().stopAutoPlay();
            super.onClose();
        }
    }
    
    @Override
    public void onClose() {
        // 复位震动与音频等过场特效
        DialogEffectManager.getInstance().reset();
        DialogAudioManager.getInstance().reset();
        // 使用淡出效果关闭屏幕，而不是直接调用 super.onClose()
        closeScreenWithFadeOut();
    }

    /** 是否存在当前显示（供客户端事件判断是否仍需震动）。 */
    public boolean isShowingHistory() {
        return showingHistory;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 如果点击，则关闭自动播放
        if (DialogManager.isAutoPlaying()) {
            DialogManager.stopAutoPlay();
            updateAutoPlayButtonText();
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // 等待输入：点击输入框区域则聚焦；点击别处不推进，避免误跳
        if (this.waitingForInput && !this.inputConsumed && this.inputBox != null && textFullyDisplayed) {
            if (button == 0 && this.inputBox.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return true; // 输入模式下吞掉点击
        }

        // 如果没有显示历史记录且没有 widget 处理点击事件，
        // 则检查是否点击了对话框区域以推进文本/对话。
        if (!showingHistory) {
            // 检查点击是否在对话框边界内
            boolean clickedInDialogBox = button == 0 &&
                                         dialogBoxX <= mouseX && mouseX <= dialogBoxX + dialogBoxWidth &&
                                         dialogBoxY <= mouseY && mouseY <= dialogBoxY + dialogBoxHeight;

            if (clickedInDialogBox) {
                if (!textFullyDisplayed) {
                    // 如果文本未完全显示，点击使其完全显示
                    textFullyDisplayed = true;
                    currentCharIndex = dialogEntry.getText(Minecraft.getInstance().level.registryAccess(), playerName).getString().length();
                    lastCharTime = 0; // 重置动画或自动播放的时间
                    return true; // 消费点击事件
                } else {
                    // 文本已完全显示
                    if (!dialogEntry.hasOptions()) {
                        // 如果没有选项，则推进对话
                        // 执行当前对话条目的指令（如果存在）
                        if (dialogEntry.getCommand() != null && !dialogEntry.getCommand().isEmpty()) {
                            DialogManager.getInstance().executeCommands(this.getMinecraft().player, dialogEntry.getCommand());
                        }
                        DialogManager.getInstance().showNextDialog();
                        return true; // 消费点击事件
                    }
                }
            }
        }
        
        return false; // 除了 widgets 或对话推进之外没有自定义处理
    }

    /**
     * 切换对话历史记录界面的显示状态
     */
    public void toggleHistoryScreen() {
        this.showingHistory = !this.showingHistory;
    }

    private void toggleAutoPlay() {
        DialogManager.setAutoPlaying(!DialogManager.isAutoPlaying());
        updateAutoPlayButtonText();
    }

    private void updateAutoPlayButtonText() {
        if (this.autoPlayButton != null) {
            this.autoPlayButton.setMessage(Component.literal(DialogManager.isAutoPlaying() ? "⏸" : "▶"));
        }
    }

    @Override
    public void tick() {
        super.tick();
        updateAutoPlayButtonText(); 
        // 推进 BGM 淡入/淡出过渡
        DialogAudioManager.getInstance().tick(); 


    
        if (this.showingHistory) {
            this.historyEntries = DialogManager.getInstance().getDialogHistory();
            // 禁用主对话界面按钮
            this.optionButtons.forEach(b -> b.active = false);
            if (this.viewHistoryButton != null) { // 确保按钮已初始化
                this.viewHistoryButton.active = false;
            }
            
            // 激活并添加关闭历史按钮
            if (!this.children().contains(this.closeHistoryButton)) {
                 this.addRenderableWidget(this.closeHistoryButton);
            }
            this.closeHistoryButton.active = true;

        } else {
            // 恢复主对话界面按钮
            this.optionButtons.forEach(b -> b.active = true);
            if (this.viewHistoryButton != null) { // 确保按钮已初始化
                this.viewHistoryButton.active = true;
            }
            
            // 移除关闭历史按钮
            if (this.children().contains(this.closeHistoryButton)) {
                this.removeWidget(this.closeHistoryButton);
            }
            this.closeHistoryButton.active = false; 
        }
    }

    /**
     * 渲染对话历史记录界面
     */
    private void renderHistoryScreen(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 渲染背景
        guiGraphics.fill(0, 0, this.width, this.height, 0xCC000000); // 半透明黑色背景

        int currentY = (int) (this.height * 0.1);
        final int textPaddingLeft = 50;
        final int optionPaddingLeft = textPaddingLeft + 5;
        final int extraEmptyLineHeight = font.lineHeight;
        final int historyAreaTopY = (int) (this.height * 0.1);
        final int historyAreaBottomY = this.height - 40; // 底部留出空间给关闭按钮等
        final int historyAreaHeight = historyAreaBottomY - historyAreaTopY;

        // 计算最大宽度
        final int dialogTextMaxWidth = Math.max(1, this.width - textPaddingLeft - 20 - 15); // 20 右缩进, 15 为滚动条宽度和间距
        final int optionTextMaxWidth = Math.max(1, this.width - optionPaddingLeft - 20 - 15);

        // 重新计算内容总高度
        totalHistoryContentHeight = 0;
        for (DialogEntry entry : historyEntries) {
            Component currentEntrySpeaker = entry.getSpeaker(Minecraft.getInstance().level.registryAccess(), playerName);
            Component dialogText = entry.getText(Minecraft.getInstance().level.registryAccess(), playerName);
            Component lineToRender;
            if (dialogText == null) dialogText = Component.empty();
            if (currentEntrySpeaker != null && !currentEntrySpeaker.getString().isEmpty()) {
                lineToRender = Component.literal("[").append(currentEntrySpeaker).append("] ").append(dialogText);
            } else {
                lineToRender = dialogText;
            }
            if (lineToRender != null) {
                List<net.minecraft.util.FormattedCharSequence> wrappedDialogLines = font.split(lineToRender, dialogTextMaxWidth);
                if (wrappedDialogLines.isEmpty() && !lineToRender.getString().isEmpty()) {
                    totalHistoryContentHeight += font.lineHeight + 2;
                } else {
                    for (net.minecraft.util.FormattedCharSequence line : wrappedDialogLines) {
                        totalHistoryContentHeight += font.lineHeight + 2;
                    }
                }
            } else {
                totalHistoryContentHeight += font.lineHeight + 2;
            }
            totalHistoryContentHeight += 5; // 条目间距
            if (entry.getSelectedOptionText() != null && !entry.getSelectedOptionText().isEmpty()) {
                Component optionComponent = Component.literal(" -> " + entry.getSelectedOptionText());
                List<net.minecraft.util.FormattedCharSequence> wrappedOptionLines = font.split(optionComponent, optionTextMaxWidth);
                if (wrappedOptionLines.isEmpty() && !optionComponent.getString().isEmpty()) {
                    totalHistoryContentHeight += font.lineHeight + 2;
                } else {
                    for (net.minecraft.util.FormattedCharSequence line : wrappedOptionLines) {
                        totalHistoryContentHeight += font.lineHeight + 2;
                    }
                }
                totalHistoryContentHeight += extraEmptyLineHeight; // 选项后间距
            }
        }

        // 渲染实际可见内容
        currentY = historyAreaTopY - historyScrollOffset; // 应用滚动偏移

        for (DialogEntry entry : historyEntries) {


            Component currentEntrySpeaker = entry.getSpeaker(Minecraft.getInstance().level.registryAccess(), playerName);
            Component dialogText = entry.getText(Minecraft.getInstance().level.registryAccess(), playerName);
            Component lineToRender;

            if (dialogText == null) {
                dialogText = Component.empty();
            }

            if (currentEntrySpeaker != null && !currentEntrySpeaker.getString().isEmpty()) {
                lineToRender = Component.literal("[").append(currentEntrySpeaker).append("] ").append(dialogText);
            } else {
                lineToRender = dialogText;
            }
            
            int entryStartY = currentY;
            int entryHeight = 0;

            if (lineToRender != null) {
                List<net.minecraft.util.FormattedCharSequence> wrappedDialogLines = font.split(lineToRender, dialogTextMaxWidth);
                if (wrappedDialogLines.isEmpty() && !lineToRender.getString().isEmpty()) {
                    if (currentY + font.lineHeight > historyAreaTopY && currentY < historyAreaBottomY) {
                        guiGraphics.drawString(font, lineToRender, textPaddingLeft, currentY, 0xFFFFFF);
                    }
                    currentY += font.lineHeight + 2;
                    entryHeight += font.lineHeight + 2;
                } else {
                    for (net.minecraft.util.FormattedCharSequence line : wrappedDialogLines) {
                        if (currentY + font.lineHeight > historyAreaTopY && currentY < historyAreaBottomY) {
                            guiGraphics.drawString(font, line, textPaddingLeft, currentY, 0xFFFFFF);
                        }
                        currentY += font.lineHeight + 2;
                        entryHeight += font.lineHeight + 2;
                    }
                }
            } else {
                 currentY += font.lineHeight + 2;
                 entryHeight += font.lineHeight + 2;
            }
            currentY += 5;
            entryHeight += 5;

            // 显示选择的选项
            if (entry.getSelectedOptionText() != null && !entry.getSelectedOptionText().isEmpty()) {
                Component optionComponent = Component.literal(" -> " + entry.getSelectedOptionText());
                currentY += 5;
                entryHeight += 5;

                List<net.minecraft.util.FormattedCharSequence> wrappedOptionLines = font.split(optionComponent, optionTextMaxWidth);
                if (wrappedOptionLines.isEmpty() && !optionComponent.getString().isEmpty()) {
                    if (currentY + font.lineHeight > historyAreaTopY && currentY < historyAreaBottomY) {
                        guiGraphics.drawString(font, optionComponent, optionPaddingLeft, currentY, 0xAAAAAA);
                    }
                    currentY += font.lineHeight + 2;
                    entryHeight += font.lineHeight + 2;
                } else {
                    for (net.minecraft.util.FormattedCharSequence line : wrappedOptionLines) {
                        if (currentY + font.lineHeight > historyAreaTopY && currentY < historyAreaBottomY) {
                            guiGraphics.drawString(font, line, optionPaddingLeft, currentY, 0xAAAAAA);
                        }
                        currentY += font.lineHeight + 2;
                        entryHeight += font.lineHeight + 2;
                    }
                }
                currentY += extraEmptyLineHeight;
                entryHeight += extraEmptyLineHeight;
            }
            // 如果条目的任何部分在可视区域之上，并且其结束部分在可视区域之下，则认为该条目是（部分）可见的
        }

        // 更新滚动状态
        canScrollHistoryUp = historyScrollOffset > 0;
        canScrollHistoryDown = totalHistoryContentHeight > historyAreaHeight && historyScrollOffset < (totalHistoryContentHeight - historyAreaHeight);

        // 渲染滚动提示箭头 (向下)
        if (canScrollHistoryDown) {
            int arrowX = this.width / 2;
            int arrowY = historyAreaBottomY + 5; // 在历史区域下方
            guiGraphics.drawString(font, "▼", arrowX - font.width("▼") / 2, arrowY, 0xFFFFFF);
        }
        // 渲染滚动提示箭头 (向上)
        if (canScrollHistoryUp) {
            int arrowX = this.width / 2;
            int arrowY = historyAreaTopY - font.lineHeight - 5; // 在历史区域上方
            guiGraphics.drawString(font, "▲", arrowX - font.width("▲") / 2, arrowY, 0xFFFFFF);
        }

        // 渲染滚动条
        if (totalHistoryContentHeight > historyAreaHeight) {
            int scrollbarWidth = 5;
            int scrollbarX = this.width - textPaddingLeft + 20; // 调整到文本区域右侧
            int scrollbarTrackHeight = historyAreaHeight;
            
            // 滚动条背景
            guiGraphics.fill(scrollbarX, historyAreaTopY, scrollbarX + scrollbarWidth, historyAreaTopY + scrollbarTrackHeight, 0xFF555555); 

            float scrollPercentage = (float) historyScrollOffset / (totalHistoryContentHeight - historyAreaHeight);
            int scrollThumbHeight = Math.max(20, (int) ((float) historyAreaHeight / totalHistoryContentHeight * historyAreaHeight));
            int scrollThumbY = historyAreaTopY + (int) (scrollPercentage * (scrollbarTrackHeight - scrollThumbHeight));
            
            guiGraphics.fill(scrollbarX, scrollThumbY, scrollbarX + scrollbarWidth, scrollThumbY + scrollThumbHeight, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.showingHistory) {
            int scrollAmount = (int) (-scrollY * (font.lineHeight + 2) * 2); // 每次滚动2行的高度
            int newScrollOffset = this.historyScrollOffset + scrollAmount;
            int maxScroll = Math.max(0, totalHistoryContentHeight - (this.height - 40 - (int) (this.height * 0.1)));

            this.historyScrollOffset = Mth.clamp(newScrollOffset, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void renderBackgroundImage(GuiGraphics guiGraphics, BackgroundImageDisplayData bgData) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, bgData.imageLocation);
        
        // 获取当前透明度
        float alpha = bgData.getCurrentAlpha();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int screenWidth = this.width;
        int screenHeight = this.height;
        int imgWidth = bgData.imageWidth;
        int imgHeight = bgData.imageHeight;

        BackgroundRenderOption renderOption = bgData.renderOption!= null? bgData.renderOption : BackgroundRenderOption.FILL;

        switch (renderOption) {
            case FILL:
                float screenAspect = (float) screenWidth / screenHeight;
                float imageAspect = (float) imgWidth / imgHeight;
                int drawWidth, drawHeight, drawX, drawY;
                if (imageAspect > screenAspect) {
                    drawHeight = screenHeight;
                    drawWidth = (int) (screenHeight * imageAspect);
                    drawX = (screenWidth - drawWidth) / 2;
                    drawY = 0;
                } else {
                    drawWidth = screenWidth;
                    drawHeight = (int) (screenWidth / imageAspect);
                    drawX = 0;
                    drawY = (screenHeight - drawHeight) / 2;
                }
                guiGraphics.blit(bgData.imageLocation, drawX, drawY, drawWidth, drawHeight, 0, 0, imgWidth, imgHeight, imgWidth, imgHeight);
                break;
            case FIT:
                screenAspect = (float) screenWidth / screenHeight;
                imageAspect = (float) imgWidth / imgHeight;
                if (imageAspect > screenAspect) {
                    drawWidth = screenWidth;
                    drawHeight = (int) (screenWidth / imageAspect);
                } else {
                    drawHeight = screenHeight;
                    drawWidth = (int) (screenHeight * imageAspect);
                }
                drawX = (screenWidth - drawWidth) / 2;
                drawY = (screenHeight - drawHeight) / 2;
                guiGraphics.blit(bgData.imageLocation, drawX, drawY, drawWidth, drawHeight, 0, 0, imgWidth, imgHeight, imgWidth, imgHeight);
                break;
            case STRETCH:
                guiGraphics.blit(bgData.imageLocation, 0, 0, screenWidth, screenHeight, 0, 0, imgWidth, imgHeight, imgWidth, imgHeight);
                break;
            case TILE:
                for (int y = 0; y < screenHeight; y += imgHeight) {
                    for (int x = 0; x < screenWidth; x += imgWidth) {
                        int w = Math.min(imgWidth, screenWidth - x);
                        int h = Math.min(imgHeight, screenHeight - y);
                        guiGraphics.blit(bgData.imageLocation, x, y, 0, 0, w, h, imgWidth, imgHeight);
                    }
                }
                break;
            case CENTER:
                drawX = (screenWidth - imgWidth) / 2;
                drawY = (screenHeight - imgHeight) / 2;
                guiGraphics.blit(bgData.imageLocation, drawX, drawY, imgWidth, imgHeight, 0, 0, imgWidth, imgHeight, imgWidth, imgHeight);
                break;
        }
        RenderSystem.disableBlend();
    }

    /**
     * 渲染纯色背景。
     */
    private void renderBackgroundColor(GuiGraphics guiGraphics, BackgroundImageDisplayData bgData) {
        int color = parseHexColor(bgData.getInfo() != null && bgData.getInfo().getColorTop() != null
                ? bgData.getInfo().getColorTop() : "#000000");
        int alpha = (int) (bgData.getCurrentAlpha() * 255);
        int argb = (alpha << 24) | (color & 0xFFFFFF);
        guiGraphics.fill(0, 0, this.width, this.height, argb);
    }

    /**
     * 渲染垂直渐入渐变背景（顶部 color，底部 colorBottom）。
     */
    private void renderBackgroundGradient(GuiGraphics guiGraphics, BackgroundImageDisplayData bgData) {
        int colorTop = parseHexColor(bgData.getInfo().getColorTop() != null ? bgData.getInfo().getColorTop() : "#1a1a2e");
        int colorBottom = parseHexColor(bgData.getInfo().getColorBottom() != null ? bgData.getInfo().getColorBottom() : "#16213e");
        float a = bgData.getCurrentAlpha();
        int top = ((int) (a * 255) << 24) | (colorTop & 0xFFFFFF);
        int bottom = ((int) (a * 255) << 24) | (colorBottom & 0xFFFFFF);
        // fillGradient(x1,y1,x2,y2,colorFrom,colorTo) 垂直渐变
        guiGraphics.fillGradient(0, 0, this.width, this.height, top, bottom);
    }

    private int parseHexColor(String hex) {
        try {
            if (hex != null && hex.startsWith("#")) {
                return (int) Long.parseLong(hex.substring(1), 16) & 0xFFFFFF;
            }
        } catch (NumberFormatException ignored) { }
        return 0x000000;
    }

    /**
     * 带角度旋转渲染立绘，绕"底部中心锚点" (anchorX, anchorBottomY) 旋转，使立绘贴地旋转不漂移。
     * @param anchorX 底部中心的 X
     * @param anchorBottomY 底部中心（立绘底边）的 Y
     */
    private void renderRotated(GuiGraphics guiGraphics, ResourceLocation tex, int anchorX, int anchorBottomY, int w, int h, float angleDeg) {
        try {
            // 平移使坐标系原点落到锚点（底边中心），旋转后再按宽高平移到正确的绘制原点
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(anchorX, anchorBottomY, 0);
            guiGraphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(angleDeg));
            guiGraphics.pose().translate(-w / 2f, -h, 0);
            guiGraphics.blit(tex, 0, 0, 0, 0, w, h, w, h);
            guiGraphics.pose().popPose();
        } catch (Throwable t) {
            // 兜底：如果矩阵 API 失败则按未旋转绘制（锚点=底中 → 左上角）
            guiGraphics.blit(tex, anchorX - w / 2, anchorBottomY - h, 0, 0, w, h, w, h);
        }
    }

    /**
     * 根据锚点计算"底部中心"点坐标。
     * 语义：anchor 指出轴的底部中心应落在给定 (ax,ay) 的什么相对位置。
     * @return int[]{中心X, 底部Y}
     */
    private int[] anchorCenterPoint(String anchor, int ax, int ay, int baseW) {
        int cx = ax, bottomY = ay - 0; // 默认 bottom_center：底部在 ay
        switch (anchor == null ? "bottom_center" : anchor) {
            case "bottom_left":
                cx = ax + baseW / 2;
                bottomY = ay;
                break;
            case "bottom_right":
                cx = ax - baseW / 2;
                bottomY = ay;
                break;
            case "center":
                cx = ax;
                bottomY = ay + 0;
                break;
            case "top_center":
                cx = ax;
                bottomY = ay;
                break;
            case "bottom_center":
            default:
                cx = ax;
                bottomY = ay;
                break;
        }
        return new int[]{cx, bottomY};
    }
}