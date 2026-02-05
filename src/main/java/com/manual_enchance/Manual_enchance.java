package com.manual_enchance;

import com.manual_enchance.mixin.SidingAccessor; // Accessorのインポート
import com.manual_enchance.util.TrainAccessor;
import mtr.data.RailwayData;
import mtr.data.TrainServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class Manual_enchance implements ModInitializer {
    public static final Identifier REVERSER_PACKET_ID = new Identifier("manual_enchance", "reverser_packet");
    public static final Identifier REVERSER_SYNC_S2C_PACKET_ID = new Identifier("manual_enchance", "reverser_sync_s2c");
    public static final Identifier DIRECT_NOTCH_PACKET_ID = new Identifier("manual_enchance", "direct_notch");

    @Override
    public void onInitialize() {
        ServerPlayNetworking.registerGlobalReceiver(REVERSER_PACKET_ID, (server, player, handler, buf, responseSender) -> {
            boolean isUp = buf.readBoolean();
            long trainId = buf.readLong();

            server.execute(() -> {
                RailwayData railwayData = RailwayData.getInstance(player.getWorld());
                if (railwayData == null) return;

                railwayData.sidings.forEach(siding -> {
                    for (TrainServer train : ((SidingAccessor) siding).getTrains()) {
                        if (train.id == trainId) {
                            if (train instanceof TrainAccessor accessor) {
                                accessor.changeReverser(isUp);

                                // --- 同期パケットの送信 ---
                                PacketByteBuf syncBuf = PacketByteBufs.create();
                                syncBuf.writeLong(trainId);
                                syncBuf.writeInt(accessor.getReverser()); // 更新後の値

                                server.getPlayerManager().getPlayerList().forEach(p -> {
                                    if (p.getWorld().getRegistryKey().equals(player.getWorld().getRegistryKey())) { // 同じ次元にいる人のみ
                                        ServerPlayNetworking.send(p, REVERSER_SYNC_S2C_PACKET_ID, syncBuf);
                                    }
                                });
                            }
                        }
                    }
                });
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DIRECT_NOTCH_PACKET_ID, (server, player, handler, buf, responseSender) -> {
            int targetNotch = buf.readInt();
            long trainId = buf.readLong();

            server.execute(() -> {
                RailwayData railwayData = RailwayData.getInstance(player.getWorld());
                if (railwayData == null) return;

                railwayData.sidings.forEach(siding -> {
                    for (TrainServer train : ((SidingAccessor) siding).getTrains()) {
                        if (train.id == trainId && train instanceof TrainAccessor accessor) {
                            accessor.setManualNotchDirect(targetNotch);
                            // 同期パケットも送るとより親切（任意）
                        }
                    }
                });
            });
        });
    }
}