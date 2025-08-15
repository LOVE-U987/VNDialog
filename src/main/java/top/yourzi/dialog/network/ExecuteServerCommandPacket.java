package top.yourzi.dialog.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.network.NetworkEvent;
import top.yourzi.dialog.Dialog;

import java.util.function.Supplier;

/**
 * 用于从客户端向服务器发送命令执行请求的网络包。
 */
public class ExecuteServerCommandPacket {
    private final String command;
    private final int executorEntityId; // 执行指令的实体ID，-1表示使用玩家自己

    public ExecuteServerCommandPacket(String command) {
        this.command = command;
        this.executorEntityId = -1; // 默认使用玩家自己
    }
    
    public ExecuteServerCommandPacket(String command, int executorEntityId) {
        this.command = command;
        this.executorEntityId = executorEntityId;
    }

    /**
     * 将包数据编码到字节缓冲区。
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.command);
        buf.writeInt(this.executorEntityId);
    }

    /**
     * 从字节缓冲区解码包数据。
     */
    public static ExecuteServerCommandPacket decode(FriendlyByteBuf buf) {
        String command = buf.readUtf();
        int executorEntityId = buf.readInt();
        return new ExecuteServerCommandPacket(command, executorEntityId);
    }

    /**
     * 处理接收到的包（在服务端）。
     */
    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender(); // 获取发送此数据包的玩家
            if (sender == null) {
                Dialog.LOGGER.warn("ExecuteServerCommandPacket received from null sender.");
                return;
            }

            MinecraftServer server = sender.getServer();
            if (server == null) {
                Dialog.LOGGER.warn("ExecuteServerCommandPacket handler: MinecraftServer instance is null.");
                return;
            }


            CommandSourceStack commandSource;
            
            if (executorEntityId == -1) {
                // 使用玩家自己作为指令执行者（向后兼容）
                commandSource = sender.createCommandSourceStack()
                                     .withPermission(Commands.LEVEL_GAMEMASTERS)
                                     .withSuppressedOutput();
            } else {
                // 使用指定实体作为指令执行者
                net.minecraft.world.entity.Entity executorEntity = sender.level().getEntity(executorEntityId);
                if (executorEntity != null) {
                    commandSource = new CommandSourceStack(
                        executorEntity,
                        executorEntity.position(),
                        executorEntity.getRotationVector(),
                        sender.serverLevel(),
                        Commands.LEVEL_GAMEMASTERS,
                        executorEntity.getName().getString(),
                        executorEntity.getDisplayName(),
                        server,
                        executorEntity
                    ).withSuppressedOutput();
                } else {
                    Dialog.LOGGER.warn("ExecuteServerCommandPacket: Executor entity with ID {} not found, falling back to player.", executorEntityId);
                    commandSource = sender.createCommandSourceStack()
                                         .withPermission(Commands.LEVEL_GAMEMASTERS)
                                         .withSuppressedOutput();
                }
            }

            try {
                server.getCommands().performPrefixedCommand(commandSource, command);
            } catch (Exception e) {
                Dialog.LOGGER.error("Error executing command on server: {}", command, e);
            }
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}