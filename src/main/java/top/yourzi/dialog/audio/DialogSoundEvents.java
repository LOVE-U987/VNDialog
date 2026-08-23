package top.yourzi.dialog.audio;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import top.yourzi.dialog.Dialog;

import java.util.function.Supplier;

/**
 * 对话相关的 SoundEvent 注册。
 * <p>注意：实际声音文件（.ogg）需放入 assets/dialog/sounds/ 并在 assets/dialog/sounds.json 声明。
 * 若缺失，播放会自动静默不报错。
 */
public class DialogSoundEvents {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, Dialog.MODID);

    public static final Supplier<SoundEvent> BGM_THEME = register("bgm_theme");
    public static final Supplier<SoundEvent> SE_CLICK = register("se_click");
    public static final Supplier<SoundEvent> TYPE = register("type_sound");
    public static final Supplier<SoundEvent> SE_COMFIRM = register("se_confirm");
    public static final Supplier<SoundEvent> VOICE_DEFAULT = register("voice_default");

    private static Supplier<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(Dialog.MODID, name)));
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}