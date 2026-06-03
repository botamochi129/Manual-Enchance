package botamochi129.manual_enchance;

import botamochi129.manual_enchance.util.CouplingManager;
import botamochi129.manual_enchance.util.SidingAccessor;
import botamochi129.manual_enchance.util.CouplingInfo;
import botamochi129.manual_enchance.util.SidingDataManager;
import botamochi129.manual_enchance.util.TrainAccessor;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import mtr.data.RailwayData;
import mtr.data.Siding;
import mtr.data.TrainServer;
import mtr.mappings.Text;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
	public static final ResourceLocation COUPLING_MODE_PACKET_ID = new ResourceLocation(MOD_ID, "coupling_mode");
	public static final ResourceLocation UNCOUPLE_PACKET_ID = new ResourceLocation(MOD_ID, "uncouple");
	public static final ResourceLocation COUPLING_SYNC_S2C_PACKET_ID = new ResourceLocation(MOD_ID, "coupling_sync_s2c");

	public static final Map<String, String> HORN_MAP = new HashMap<>();

	// trainId -> last applied sequence for direct-notch
	private static final Map<Long, Long> lastNotchSeqMap = new ConcurrentHashMap<>();

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
			long seq = buf.readLong(); // シーケンス番号
			context.queue(() -> {
				// シーケンス管理: 古いパケットは無視
				long last = lastNotchSeqMap.getOrDefault(trainId, Long.MIN_VALUE);
				if (seq <= last) return;
				lastNotchSeqMap.put(trainId, seq);
				processTrain(context.getPlayer(), trainId, accessor -> {
					accessor.setManualNotchDirect(targetNotch);
					// 他の全員に通知（シーケンスを含む）
					FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
					out.writeLong(trainId);
					out.writeInt(targetNotch);
					out.writeLong(seq);
					broadcast(context.getPlayer(), DIRECT_NOTCH_PACKET_ID, out);
				});
			});
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

		// --- 7. 連結待機モード ---
		NetworkManager.registerReceiver(NetworkManager.Side.C2S, COUPLING_MODE_PACKET_ID, (buf, context) -> {
			long trainId = buf.readLong();
			context.queue(() -> processTrain(context.getPlayer(), trainId, accessor -> {
				// Legacy handler - actual coupling logic now handled by attempt_coupling
				// This is kept for potential future use (e.g., UI state)
			}));
		});

	// --- 8.5. Attempt coupling (server validates safety, called by client when in coupling mode) ---
	NetworkManager.registerReceiver(NetworkManager.Side.C2S, new ResourceLocation(MOD_ID, "attempt_coupling"), (buf, context) -> {
		long trainId = buf.readLong();
		System.out.println("[ManualEnchance-Debug] attempt_coupling request received for trainId: " + trainId);
		context.queue(() -> {
				#if MC_VERSION >= "12000"
				RailwayData data = RailwayData.getInstance(context.getPlayer().level());
				#else
				RailwayData data = RailwayData.getInstance(context.getPlayer().level);
				#endif
				if (data == null) {
				System.out.println("[ManualEnchance-Error] RailwayData is null on server!");
				return;
			}
			System.out.println("[ManualEnchance-Debug] RailwayData loaded, sidings: " + data.sidings.size());

				TrainServer slaveCandidate = null;
				TrainServer masterCandidate = null;

			// Find the train that initiated coupling request
			for (Siding siding : data.sidings) {
				for (TrainServer train : ((SidingAccessor) siding).getTrains()) {
					if (train.id == trainId) {
						slaveCandidate = train;
						System.out.println("[ManualEnchance-Debug] Found slave train: " + trainId);
						break;
					}
				}
				if (slaveCandidate != null) break;
			}
			if (slaveCandidate == null) {
				System.out.println("[ManualEnchance-Error] Slave train not found: " + trainId);
				return;
			}

				// Check if already coupled
				TrainAccessor slaveAcc = (TrainAccessor) slaveCandidate;
				if (slaveAcc.manualEnchance$getMasterId() != 0L) return;

			// Find nearby train to couple with (anywhere in data.sidings)
			double bestDistance = Double.MAX_VALUE;
			int candidateCount = 0;
			for (Siding siding : data.sidings) {
				for (TrainServer other : ((SidingAccessor) siding).getTrains()) {
					if (other.id == trainId) continue; // skip self
					if (((TrainAccessor) other).manualEnchance$getMasterId() != 0L) continue; // skip already coupled trains

					double slaveProgress = slaveCandidate.getRailProgress();
					double masterProgress = other.getRailProgress();

					// Calculate front of slave train
					double slaveFrontProgress = slaveProgress + (slaveCandidate.trainCars * slaveCandidate.spacing);

					// Check if other train is ahead and within coupling distance
					double distance = masterProgress - slaveFrontProgress;
					candidateCount++;
					System.out.println("[ManualEnchance-Debug] Candidate " + other.id + ": distance=" + distance + ", slave=" + slaveFrontProgress + ", master=" + masterProgress);
					
					if (distance > 0.0 && distance < 2.5 && distance < bestDistance) {
						// Check safety conditions
						boolean isSlaveManual = slaveAcc.getIsCurrentlyManual();
						boolean isMasterManual = ((TrainAccessor) other).getIsCurrentlyManual();

						// Manual trains: allow if doors closed
						// Autopilot trains: server decides it's safe when proximity check passes
						boolean canCouple = slaveAcc.manualEnchance$getDoorValue() <= 0.01f;
						System.out.println("[ManualEnchance-Debug] Safety check for " + other.id + ": doorValue=" + slaveAcc.manualEnchance$getDoorValue() + ", canCouple=" + canCouple);

						if (canCouple) {
							masterCandidate = other;
							bestDistance = distance;
							System.out.println("[ManualEnchance-Debug] Selected master: " + other.id);
						}
					}
				}
			}
			System.out.println("[ManualEnchance-Debug] Total candidates checked: " + candidateCount);

			if (masterCandidate != null) {
				// Perform coupling
				double offset = masterCandidate.getRailProgress() - slaveCandidate.getRailProgress();
				slaveAcc.manualEnchance$setMasterId(masterCandidate.id);
				slaveAcc.manualEnchance$setCouplingOffset(offset);
				slaveAcc.manualEnchance$setCouplingMode(false);

				CouplingManager.getCouplingMap().put(trainId, new CouplingInfo(masterCandidate.id, offset));
				CouplingManager.saveAll(); // Persist immediately

				// Notify all clients of the coupling
				FriendlyByteBuf notifyBuf = new FriendlyByteBuf(Unpooled.buffer());
				notifyBuf.writeLong(trainId);
				notifyBuf.writeLong(masterCandidate.id);
				notifyBuf.writeDouble(offset);
				broadcast(context.getPlayer(), COUPLING_SYNC_S2C_PACKET_ID, notifyBuf);

				System.out.println("[ManualEnchance] Coupling successful: " + trainId + " -> " + masterCandidate.id + " (offset=" + offset + ")");
			} else {
				System.out.println("[ManualEnchance-Error] Master candidate not found after checking " + candidateCount + " candidates");
			}
			});
		});

		// --- 8. 解結 (切り離し) ---
		NetworkManager.registerReceiver(NetworkManager.Side.C2S, UNCOUPLE_PACKET_ID, (buf, context) -> {
			long trainId = buf.readLong();
			context.queue(() -> {
				CouplingManager.getCouplingMap().remove(trainId);

				// サーバー側の該当列車のMasterIDを0にリセット
				processTrain(context.getPlayer(), trainId, accessor -> {
					accessor.manualEnchance$setMasterId(0L);
				});

				// クライアント全員に「連結解除」を通知
				FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
				out.writeLong(trainId);
				out.writeLong(0L); // MasterID = 0 (解除)
				out.writeDouble(0.0);
				broadcast(context.getPlayer(), COUPLING_SYNC_S2C_PACKET_ID, out);

				context.getPlayer().displayClientMessage(Text.literal("§6[ManualEnchance] §f列車を切り離しました"), false);
			});
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
			// Coupling persistent data
			CouplingManager.loadAll();
		});

		LifecycleEvent.SERVER_STOPPING.register(server -> {
			SidingDataManager.save(server.overworld());
			// save coupling data
			CouplingManager.saveAll();
		});

	}

	// Broadcast to all players in the given world (server-side helper)
	public static void broadcastWorld(net.minecraft.world.level.Level world, ResourceLocation id, FriendlyByteBuf buf) {
		// Only available on server side
		if (world instanceof net.minecraft.server.level.ServerLevel serverLevel) {
			NetworkManager.sendToPlayers(serverLevel.players(), id, buf);
		}
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