package botamochi129.manual_enchance;

import botamochi129.manual_enchance.util.SidingAccessor;
import botamochi129.manual_enchance.util.SidingDataManager;
import botamochi129.manual_enchance.util.TrainAccessor;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import mtr.data.RailwayData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;

public class Main {
	public static final String MOD_ID = "manual_enchance";

	public static final ResourceLocation REVERSER_PACKET_ID = new ResourceLocation(MOD_ID, "reverser_packet");
	public static final ResourceLocation REVERSER_DIRECT_PACKET_ID = new ResourceLocation(MOD_ID, "reverser_direct_packet");
	public static final ResourceLocation REVERSER_SYNC_S2C_PACKET_ID = new ResourceLocation(MOD_ID, "reverser_sync_s2c");
	public static final ResourceLocation DIRECT_NOTCH_PACKET_ID = new ResourceLocation(MOD_ID, "direct_notch");
	public static final ResourceLocation PANTO_UPDATE_PACKET = new ResourceLocation(MOD_ID, "panto_update");
	public static final ResourceLocation HORN_PACKET_ID = new ResourceLocation(MOD_ID, "train_horn");
	public static final ResourceLocation ROLLSIGN_UPDATE_PACKET = new ResourceLocation(MOD_ID, "rollsign_update");
	public static final ResourceLocation SIDING_PANTO_UPDATE_PACKET = new ResourceLocation(MOD_ID, "siding_panto_update");

	public static final Map<String, String> HORN_MAP = new HashMap<>();

	public static void init(RegistriesWrapper wrapper) {
		// --- 1. リバーサー操作 (相対) ---
		NetworkManager.registerReceiver(NetworkManager.Side.C2S, REVERSER_PACKET_ID, (buf, context) -> {
			boolean isUp = buf.readBoolean();
			long trainId = buf.readLong();
			context.queue(() -> processTrain(context.getPlayer(), trainId, accessor -> {
				accessor.changeReverser(isUp);
				syncReverser(context.getPlayer(), trainId, accessor.getReverser());
			}));
		});

		// --- 2. リバーサー操作 (直接/KATO) ---
		NetworkManager.registerReceiver(NetworkManager.Side.C2S, REVERSER_DIRECT_PACKET_ID, (buf, context) -> {
			int targetValue = buf.readInt();
			long trainId = buf.readLong();
			context.queue(() -> processTrain(context.getPlayer(), trainId, accessor -> {
				accessor.setReverser(targetValue);
				syncReverser(context.getPlayer(), trainId, accessor.getReverser());
			}));
		});

		// --- 3. 直接ノッチ操作 ---
		NetworkManager.registerReceiver(NetworkManager.Side.C2S, DIRECT_NOTCH_PACKET_ID, (buf, context) -> {
			int targetNotch = buf.readInt();
			long trainId = buf.readLong();
			context.queue(() -> processTrain(context.getPlayer(), trainId, accessor -> {
				accessor.setManualNotchDirect(targetNotch);
				// 他の全員に通知
				FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
				out.writeLong(trainId);
				out.writeInt(targetNotch);
				broadcast(context.getPlayer(), DIRECT_NOTCH_PACKET_ID, out);
			}));
		});

		// --- 4. パンタグラフ ---
		NetworkManager.registerReceiver(NetworkManager.Side.C2S, PANTO_UPDATE_PACKET, (buf, context) -> {
			long trainId = buf.readLong();
			int newState = buf.readInt();
			context.queue(() -> {
				FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
				out.writeLong(trainId);
				out.writeInt(newState);
				broadcast(context.getPlayer(), PANTO_UPDATE_PACKET, out);
			});
		});

		// --- 5. 警笛 ---
		NetworkManager.registerReceiver(NetworkManager.Side.C2S, HORN_PACKET_ID, (buf, context) -> {
			long trainId = buf.readLong();
			context.queue(() -> {
				FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
				out.writeLong(trainId);
				broadcast(context.getPlayer(), HORN_PACKET_ID, out);
			});
		});

		// --- 6. 方向幕 ---
		NetworkManager.registerReceiver(NetworkManager.Side.C2S, ROLLSIGN_UPDATE_PACKET, (buf, context) -> {
			long trainId = buf.readLong();
			String rollsignId = buf.readUtf(); // readString は 1.19.2 では readUtf
			int nextIndex = buf.readInt();
			context.queue(() -> processTrain(context.getPlayer(), trainId, accessor -> {
				accessor.setRollsignIndex(rollsignId, nextIndex);
				FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
				out.writeLong(trainId);
				out.writeUtf(rollsignId);
				out.writeInt(nextIndex);
				broadcast(context.getPlayer(), ROLLSIGN_UPDATE_PACKET, out);
			}));
		});

		NetworkManager.registerReceiver(NetworkManager.Side.C2S, SIDING_PANTO_UPDATE_PACKET, (buf, context) -> {
			long sidingId = buf.readLong();
			int state = buf.readInt();
			context.queue(() -> {
				// 1. サーバー側のマネージャーに保存（永続化用）
				SidingDataManager.setPantoState(sidingId, state);

				// 2. そのSidingに所属する全列車の状態を更新（即時反映）
        #if MC_VERSION >= "12000"
        RailwayData data = RailwayData.getInstance(context.getPlayer().level());
        #else
				RailwayData data = RailwayData.getInstance(context.getPlayer().level);
        #endif

				if (data != null) {
					data.sidings.forEach(siding -> {
						if (siding.id == sidingId) {
							((SidingAccessor) siding).getTrains().forEach(train -> {
								((TrainAccessor) train).setPantographState(state);

								// 3. 全クライアントへ「この列車のパンタを変えろ」と通知
								FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
								out.writeLong(train.id);
								out.writeInt(state);
								broadcast(context.getPlayer(), PANTO_UPDATE_PACKET, out);
							});
						}
					});
				}
			});
		});

		LifecycleEvent.SERVER_STARTED.register(server -> {
			SidingDataManager.load(server.overworld());
		});

		LifecycleEvent.SERVER_STOPPING.register(server -> {
			SidingDataManager.save(server.overworld());
		});
	}

	// 共通ヘルパー: 列車を探して処理を実行
	private static void processTrain(net.minecraft.world.entity.player.Player player, long trainId, java.util.function.Consumer<TrainAccessor> action) {
		#if MC_VERSION >= "12000"
		RailwayData data = RailwayData.getInstance(player.level());
		#else
		RailwayData data = RailwayData.getInstance(player.level);
		#endif
		if (data == null) return;
		data.sidings.forEach(siding -> {
			((SidingAccessor) siding).getTrains().forEach(train -> {
				if (train.id == trainId && train instanceof TrainAccessor accessor) {
					action.accept(accessor);
				}
			});
		});
	}

	// 共通ヘルパー: リバーサー同期
	private static void syncReverser(net.minecraft.world.entity.player.Player player, long trainId, int value) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeLong(trainId);
		buf.writeInt(value);
		broadcast(player, REVERSER_SYNC_S2C_PACKET_ID, buf);
	}

	// 共通ヘルパー: 同じワールドのプレイヤー全員に送信
	private static void broadcast(net.minecraft.world.entity.player.Player player, ResourceLocation id, FriendlyByteBuf buf) {
		#if MC_VERSION >= "12000"
		NetworkManager.sendToPlayers(((ServerPlayer)player).serverLevel().players(), id, buf);
		#else
		NetworkManager.sendToPlayers(((ServerPlayer)player).getLevel().players(), id, buf);
		#endif
	}
}