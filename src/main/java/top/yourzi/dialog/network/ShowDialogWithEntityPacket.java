package top.yourzi.dialog.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import top.yourzi.dialog.DialogManager;

import java.util.function.Supplier;

/**
 * 服务端向客户端发送带实体信息的对话显示包。
 */
public class ShowDialogWithEntityPacket {
    private final String dialogId;
    private final String dialogJson;
    private final int speakerEntityId;

    public ShowDialogWithEntityPacket(String dialogId, String dialogJson, int speakerEntityId) {
        this.dialogId = dialogId;
        this.dialogJson = dialogJson;
        this.speakerEntityId = speakerEntityId;
    }

    /**
     * 将包数据编码到字节缓冲区。
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.dialogId);
        buf.writeUtf(this.dialogJson);
        buf.writeInt(this.speakerEntityId);
    }

    /**
     * 从字节缓冲区解码包数据。
     */
    public static ShowDialogWithEntityPacket decode(FriendlyByteBuf buf) {
        return new ShowDialogWithEntityPacket(buf.readUtf(), buf.readUtf(), buf.readInt());
    }

    /**
     * 处理接收到的包
     */
    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // 确保在客户端线程中执行
            handleOnClient();
        });
        ctx.get().setPacketHandled(true);
        return true;
    }

    /**
     * 在客户端处理包
     */
    @OnlyIn(Dist.CLIENT)
    private void handleOnClient() {
        Minecraft.getInstance().execute(() -> {
            // 获取说话实体
            Entity speakerEntity = null;
            if (Minecraft.getInstance().level != null) {
                speakerEntity = Minecraft.getInstance().level.getEntity(this.speakerEntityId);
            }
            
            // 接收并显示带实体信息的对话
            DialogManager.getInstance().receiveAndShowPlayerSpecificDialogWithEntity(this.dialogId, this.dialogJson, this.speakerEntityId);
        });
    }
}