package top.yourzi.dialog;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import top.yourzi.dialog.audio.DialogSoundEvents;
import top.yourzi.dialog.client.DialogConfig;
import top.yourzi.dialog.client.DialogManager;
import top.yourzi.dialog.command.DialogCommand;
import top.yourzi.dialog.network.NetworkHandler;

import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * VNDialog - 非侵入式对话系统
 * NeoForge 1.21.1
 */
@Mod(Dialog.MODID)
public class Dialog {
    public static final String MODID = "dialog";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Dialog(IEventBus modEventBus) {
        // 注册通用事件
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::registerCommands);
        modEventBus.addListener(this::onResourceReload);

        // 注册网络包处理器
        modEventBus.addListener(NetworkHandler::register);

        // 注册声音事件
        DialogSoundEvents.register(modEventBus);

        // 注册全局事件监听器
        NeoForge.EVENT_BUS.addListener(this::onResourceReload);

        LOGGER.info("VNDialog loaded successfully");
    }

    /**
     * 通用初始化
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // 创建默认配置目录
            Path configPath = FMLPaths.CONFIGDIR.get().resolve("vndialog");
            if (!Files.exists(configPath)) {
                try {
                    Files.createDirectories(configPath);
                    LOGGER.info("Created config directory: {}", configPath);
                } catch (Exception e) {
                    LOGGER.error("Failed to create config directory", e);
                }
            }
        });
    }

    /**
     * 客户端初始化
     */
    private void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // 初始化客户端配置
            DialogConfig.getInstance();
            LOGGER.info("VNDialog client initialized");
        });
    }

    /**
     * 注册命令
     */
    private void registerCommands(final RegisterCommandsEvent event) {
        DialogCommand.register(event.getDispatcher());
    }

    /**
     * 资源重载事件（热重载配置）
     */
    public void onResourceReload(AddReloadListenerEvent event) {
        event.addListener(new top.yourzi.dialog.client.DialogConfig.ReloadListener());
    }
}
