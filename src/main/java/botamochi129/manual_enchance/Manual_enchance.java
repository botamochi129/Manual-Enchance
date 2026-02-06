package botamochi129.manual_enchance;

import botamochi129.manual_enchance.mixin.SidingAccessor; // Accessorのインポート
import botamochi129.manual_enchance.util.TrainAccessor;
import mtr.data.RailwayData;
import mtr.data.TrainServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.HashMap;
import java.util.Map;

public class Manual_enchance implements ModInitializer {
    public static final Identifier REVERSER_PACKET_ID = new Identifier("manual_enchance", "reverser_packet");
    public static final Identifier REVERSER_SYNC_S2C_PACKET_ID = new Identifier("manual_enchance", "reverser_sync_s2c");
    public static final Identifier DIRECT_NOTCH_PACKET_ID = new Identifier("manual_enchance", "direct_notch");
    public static final Identifier PANTO_UPDATE_PACKET = new Identifier("manual_enchance", "panto_update");
    public static final Identifier HORN_PACKET_ID = new Identifier("manual_enchance", "train_horn");

    public static final Map<String, String> HORN_MAP = new HashMap<>();

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

        ServerPlayNetworking.registerGlobalReceiver(PANTO_UPDATE_PACKET, (server, player, handler, buf, responseSender) -> {
            long trainId = buf.readLong();
            int newState = buf.readInt();

            server.execute(() -> {
                // 1. サーバー側の列車データを更新（保存用）
                // ※ RailwayDataからIDで列車を探す処理が必要ですが、
                // 簡易的には全プレイヤーへそのまま転送します。

                // 2. 全プレイヤーに拡散
                PacketByteBuf outBuf = PacketByteBufs.create();
                outBuf.writeLong(trainId);
                outBuf.writeInt(newState);

                server.getPlayerManager().getPlayerList().forEach(p -> {
                    ServerPlayNetworking.send(p, PANTO_UPDATE_PACKET, outBuf);
                });
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(HORN_PACKET_ID, (server, player, handler, buf, responseSender) -> {
            long trainId = buf.readLong();

            server.execute(() -> {
                PacketByteBuf outBuf = PacketByteBufs.create();
                outBuf.writeLong(trainId);

                // 全プレイヤーに拡散（これで全員の耳に音が届くようになる）
                server.getPlayerManager().getPlayerList().forEach(p -> {
                    ServerPlayNetworking.send(p, HORN_PACKET_ID, outBuf);
                });
            });
        });
    }
}