package botamochi129.manual_enchance;

import botamochi129.manual_enchance.util.*;
		import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import mtr.data.RailwayData;
import mtr.data.Siding;
import mtr.data.TrainServer;
import mtr.path.PathData;
import mtr.mappings.Text;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashMap;
import java.util.List;
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
	public static final ResourceLocation STATE_SYNC_S2C_PACKET_ID = new ResourceLocation(MOD_ID, "state_sync_s2c");
	// Siding連結設定パケット: (slaveSidingId: long, masterSidingId: long) - 0L = 解除
	public static final ResourceLocation SIDING_COUPLING_UPDATE_PACKET = new ResourceLocation(MOD_ID, "siding_coupling_update");
	public static final ResourceLocation SIDING_COUPLING_SYNC_S2C_PACKET_ID = new ResourceLocation(MOD_ID, "siding_coupling_sync_s2c");
	public static final ResourceLocation ROUTE_COUPLING_UPDATE_PACKET = new ResourceLocation(MOD_ID, "route_coupling_update");

	public static final Map<String, String> HORN_MAP = new HashMap<>();

	private static final Logger LOGGER = LogManager.getLogger("manual_enchance");
	private static final Map<Long, Long> lastNotchSeqMap = new ConcurrentHashMap<>();
	// 手動運転列車の状態同期: 5tick(0.25秒)ごとにサーバー→クライアントへ補正パケットを送信
	private static int stateSyncTickCounter = 0;
	private static final int STATE_SYNC_INTERVAL_TICKS = 5;
	private static int couplingCleanupTickCounter = 0;
	private static final int COUPLING_CLEANUP_INTERVAL_TICKS = 100;
	private static boolean couplingRestorePending = false;
	private static int couplingRestoreAttempts = 0;
	private static final int MAX_COUPLING_RESTORE_ATTEMPTS = 600;

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

		// --- 2. リバーサー操作 (直接) ---
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
			long seq = buf.readLong();
			context.queue(() -> {
				long last = lastNotchSeqMap.getOrDefault(trainId, Long.MIN_VALUE);
				if (seq <= last) return;
				lastNotchSeqMap.put(trainId, seq);
				processTrain(context.getPlayer(), trainId, accessor -> {
					accessor.setManualNotchDirect(targetNotch);
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
				processTrain(context.getPlayer(), trainId, accessor -> {
					accessor.setPantographState(newState);
				});
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
			String rollsignId = buf.readUtf();
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
				accessor.manualEnchance$setCouplingMode(true);
			}));
		});

		// --- 8. 連結実行 ---
		NetworkManager.registerReceiver(NetworkManager.Side.C2S, new ResourceLocation(MOD_ID, "attempt_coupling"), (buf, context) -> {
			long trainId = buf.readLong();
			context.queue(() -> {
				LOGGER.info("[Coupling] attempt_coupling received for trainId={}", trainId);
             #if MC_VERSION >= "12000"
                RailwayData data = RailwayData.getInstance(context.getPlayer().level());
                                ServerLevel serverLevel = (ServerLevel) context.getPlayer().level();
             #else
				RailwayData data = RailwayData.getInstance(context.getPlayer().level);
				ServerLevel serverLevel = (ServerLevel) context.getPlayer().level;
             #endif
				if (data == null) {
					LOGGER.warn("[Coupling] data == null");
					context.getPlayer().displayClientMessage(Text.literal("§c[Coupling] §f線路データが見つかりません"), true);
					return;
				}

				TrainServer operatorTrain = CouplingManager.findTrain(data, trainId);
				if (operatorTrain == null) {
					LOGGER.warn("[Coupling] operatorTrain not found for id={}", trainId);
					context.getPlayer().displayClientMessage(Text.literal("§c[Coupling] §f列車が見つかりません"), true);
					return;
				}

				TrainAccessor operatorAcc = (TrainAccessor) operatorTrain;

				Vec3 operatorFront = operatorAcc.manualEnchance$getCouplerFrontPos();
				Vec3 operatorRear = operatorAcc.manualEnchance$getCouplerRearPos();
				LOGGER.info("[Coupling] operator coupler positions: front={}, rear={}, pathEmpty={}",
						operatorFront, operatorRear, operatorTrain.path == null || operatorTrain.path.isEmpty());

				TrainServer bestOther = null;
				CouplingManager.ConnectionType bestType = null;
				double bestDistance = Double.MAX_VALUE;
				int totalOtherTrains = 0;

				for (Siding siding : data.sidings) {
					for (TrainServer other : ((SidingAccessor) siding).getTrains()) {
						if (other.id == trainId) continue;
						totalOtherTrains++;
						if (CouplingManager.wouldCreateLoop(data, trainId, other.id)) {
							LOGGER.info("[Coupling] loop would be created with other={}", other.id);
							continue;
						}

						TrainAccessor otherAcc = (TrainAccessor) other;
						Vec3 otherFront = otherAcc.manualEnchance$getCouplerFrontPos();
						Vec3 otherRear = otherAcc.manualEnchance$getCouplerRearPos();
						CouplingManager.ConnectionType type = CouplingManager.resolveConnectionType(
								operatorFront, operatorRear,
								otherFront, otherRear
						);

						double distance = switch (type) {
							case SLAVE_FRONT_TO_MASTER_REAR -> operatorFront.distanceTo(otherRear);
							case SLAVE_REAR_TO_MASTER_FRONT -> operatorRear.distanceTo(otherFront);
							case SLAVE_FRONT_TO_MASTER_FRONT -> operatorFront.distanceTo(otherFront);
							case SLAVE_REAR_TO_MASTER_REAR -> operatorRear.distanceTo(otherRear);
						};

						LOGGER.info("[Coupling] other={} type={} distance={} posFront={} posRear={}",
								other.id, type, String.format("%.2f", distance), otherFront, otherRear);

						if (distance < CouplingManager.MAX_COUPLING_DISTANCE && distance < bestDistance) {
							bestOther = other;
							bestType = type;
							bestDistance = distance;
						}
					}
				}

				LOGGER.info("[Coupling] totalOtherTrains={}, bestOther={}, bestType={}, bestDistance={}",
						totalOtherTrains, bestOther != null ? bestOther.id : null, bestType, String.format("%.2f", bestDistance));

				if (bestOther == null || bestType == null) {
					LOGGER.info("[Coupling] no suitable train found within {}m", CouplingManager.MAX_COUPLING_DISTANCE);
					context.getPlayer().displayClientMessage(Text.literal("§c[Coupling] §f近くに連結可能な列車がありません"), true);
					return;
				}

				// 走行中は連結できない（停止してから実行）
				if (Math.abs(operatorTrain.getSpeed()) > 0.05F || Math.abs(bestOther.getSpeed()) > 0.05F) {
					context.getPlayer().displayClientMessage(Text.literal("§c[Coupling] §f停止してから連結してください"), true);
					return;
				}

				if (!CouplingManager.isSupportedConnection(bestType)) {
					LOGGER.info("[Coupling] unsupported connection type: {}", bestType);
					context.getPlayer().displayClientMessage(Text.literal("§c[Coupling] §f向きが合いません（正対して停止してください）"), true);
					return;
				}

				TrainServer slave = operatorTrain;
				TrainServer master = bestOther;
				CouplingManager.ConnectionType applyType = bestType;
				if (bestType == CouplingManager.ConnectionType.SLAVE_REAR_TO_MASTER_FRONT) {
					slave = bestOther;
					master = operatorTrain;
					applyType = CouplingManager.ConnectionType.SLAVE_FRONT_TO_MASTER_REAR;
				}

				// Multi-segment check: the new slave must be free (not already coupled).
				// A train can only be a slave to one master, so we reject if it's already a slave.
				if (((TrainAccessor) slave).manualEnchance$getMasterId() != 0L) {
					LOGGER.info("[Coupling] slave {} already has masterId={}, cannot branch", slave.id, ((TrainAccessor) slave).manualEnchance$getMasterId());
					context.getPlayer().displayClientMessage(Text.literal("§c[Coupling] §fこの列車は既に連結中です"), true);
					return;
				}
				// ALLOW multi-segment chains: the master CAN already have a slave.
				// This enables chains like A→B→C where B is slave of A and C is slave of B.
				// We only need to ensure we're not creating a loop (checked by wouldCreateLoop above).

				LOGGER.info("[Coupling] attempting applyNaturalCoupling: slave={}, master={}, type={}", slave.id, master.id, applyType);
				if (CouplingManager.applyNaturalCoupling(slave, master, applyType, serverLevel, COUPLING_SYNC_S2C_PACKET_ID)) {
					double offset = CouplingManager.getCouplingMap().get(slave.id).offset;
					LOGGER.info("[Coupling] SUCCESS slave={} master={} offset={}", slave.id, master.id, String.format("%.2f", offset));
					context.getPlayer().displayClientMessage(Text.literal("§6[Coupling] §e連結完了 (Offset: " + String.format("%.2f", offset) + "m)"), false);
				} else {
					LOGGER.warn("[Coupling] applyCoupling returned false (maybe slave already coupled?)");
					context.getPlayer().displayClientMessage(Text.literal("§c[Coupling] §f連結に失敗しました"), true);
				}
			});
		});

		// --- 9. 解結 (切り離し) ---
		NetworkManager.registerReceiver(NetworkManager.Side.C2S, UNCOUPLE_PACKET_ID, (buf, context) -> {
			long trainId = buf.readLong();
			context.queue(() -> {
                                #if MC_VERSION >= "12000"
                                        ServerLevel serverLevel = (ServerLevel) context.getPlayer().level();
                                        RailwayData data = RailwayData.getInstance(context.getPlayer().level());
                                 #else
				ServerLevel serverLevel = (ServerLevel) context.getPlayer().level;
				RailwayData data = RailwayData.getInstance(context.getPlayer().level);
                                 #endif
				TrainServer train = CouplingManager.findTrain(data, trainId);
				if (train != null && Math.abs(train.getSpeed()) > 0.05F) {
					context.getPlayer().displayClientMessage(Text.literal("§c[Coupling] §f停止してから解結してください"), true);
					return;
				}
				CouplingManager.uncouple(data, trainId, serverLevel, COUPLING_SYNC_S2C_PACKET_ID);
				context.getPlayer().displayClientMessage(Text.literal("§6[ManualEnchance] §f列車を切り離しました"), false);
			});
		});

		// --- 10. 留置線パンタグラフ連動 ---
		NetworkManager.registerReceiver(NetworkManager.Side.C2S, SIDING_PANTO_UPDATE_PACKET, (buf, context) -> {
			long sidingId = buf.readLong();
			int state = buf.readInt();
			context.queue(() -> {
				SidingDataManager.setPantoState(sidingId, state);

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

		// --- 11. Siding自動連結設定 (slaveSidingId, masterSidingId) masterSidingId=0で解除 ---
		NetworkManager.registerReceiver(NetworkManager.Side.C2S, SIDING_COUPLING_UPDATE_PACKET, (buf, context) -> {
			long slaveSidingId = buf.readLong();
			long masterSidingId = buf.readLong();
			context.queue(() -> {
				SidingDataManager.setMasterSidingId(slaveSidingId, masterSidingId);
                                #if MC_VERSION >= "12000"
                                ServerLevel level = ((ServerPlayer) context.getPlayer()).serverLevel();
                                #else
				ServerLevel level = ((ServerPlayer) context.getPlayer()).getLevel();
                                #endif
				SidingDataManager.save(level);

				FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
				out.writeLong(slaveSidingId);
				out.writeLong(masterSidingId);
				CouplingManager.broadcast(level, SIDING_COUPLING_SYNC_S2C_PACKET_ID, out);
			});
		});

		// --- Route coupling settings update ---
		NetworkManager.registerReceiver(NetworkManager.Side.C2S, ROUTE_COUPLING_UPDATE_PACKET, (buf, context) -> {
			long routeId = buf.readLong();
			int count = buf.readInt();
			System.out.println("[ManualEnchance] ROUTE_COUPLING_UPDATE_PACKET received: routeId=" + routeId + " count=" + count);
			List<RouteCouplingStore.RouteCouplingAction> actions = new java.util.ArrayList<>();
			for (int i = 0; i < count; i++) {
				int stationIndex = buf.readInt();
				boolean doCouple = buf.readBoolean();
				boolean doUncouple = buf.readBoolean();
				long targetSidingId = buf.readLong();
				boolean waitForever = buf.readBoolean();
				int splitTrainIndex = buf.readInt();
				System.out.println("[ManualEnchance]   - entry " + i + ": station=" + stationIndex + " couple=" + doCouple + " uncouple=" + doUncouple + " sidingId=" + targetSidingId);
				actions.add(new RouteCouplingStore.RouteCouplingAction(routeId, stationIndex, doCouple, doUncouple, targetSidingId, waitForever, splitTrainIndex));
			}
			context.queue(() -> {
                                #if MC_VERSION >= "12000"
                                ServerLevel level = ((ServerPlayer) context.getPlayer()).serverLevel();
                                #else
				ServerLevel level = ((ServerPlayer) context.getPlayer()).getLevel();
                                #endif
				System.out.println("[ManualEnchance] processing ROUTE_COUPLING_UPDATE_PACKET on server thread: " + actions.size() + " actions");
				for (RouteCouplingStore.RouteCouplingAction a : actions) {
					RouteCouplingStore.setAction(a);
				}
				System.out.println("[ManualEnchance] saving route coupling to disk");
				RouteCouplingStore.save(level);
			});
		});

		TickEvent.SERVER_POST.register(server -> {
			if (couplingRestorePending && couplingRestoreAttempts < MAX_COUPLING_RESTORE_ATTEMPTS) {
				couplingRestoreAttempts++;
				ServerLevel serverLevel = server.overworld();
				RailwayData data = RailwayData.getInstance(serverLevel);
				if (data != null && !data.sidings.isEmpty()) {
					int restored = CouplingManager.restoreAll(data, serverLevel, COUPLING_SYNC_S2C_PACKET_ID);
					if (restored > 0) {
						System.out.println("[ManualEnchance] Restored " + restored + " coupling(s) from save data");
					}
					if (CouplingManager.isFullyRestored(data)) {
						couplingRestorePending = false;
					}
				}
			}

			// --- Periodically clean stale coupling entries (deleted trains) ---
			couplingCleanupTickCounter++;
			if (couplingCleanupTickCounter >= COUPLING_CLEANUP_INTERVAL_TICKS) {
				couplingCleanupTickCounter = 0;
				ServerLevel overworld = server.overworld();
				RailwayData rData = RailwayData.getInstance(overworld);
				if (rData != null) {
					CouplingManager.cleanStaleEntries(rData, overworld, COUPLING_SYNC_S2C_PACKET_ID);
				}
			}

			stateSyncTickCounter++;
			if (stateSyncTickCounter < STATE_SYNC_INTERVAL_TICKS) return;
			stateSyncTickCounter = 0;

			ServerLevel serverLevel = server.overworld();
			RailwayData data = RailwayData.getInstance(serverLevel);
			if (data == null) return;

			for (Siding siding : data.sidings) {
				for (TrainServer train : ((SidingAccessor) siding).getTrains()) {
					if (!(train instanceof TrainAccessor acc)) continue;
					if (!acc.getIsCurrentlyManual()) continue;

					FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
					out.writeLong(train.id);
					out.writeFloat(train.getSpeed());
					out.writeDouble(acc.manualEnchance$getRailProgress());
					out.writeInt(acc.getManualNotch());
					out.writeInt(acc.getReverser());
					NetworkManager.sendToPlayers(serverLevel.players(), STATE_SYNC_S2C_PACKET_ID, out);
				}
			}
		});

		LifecycleEvent.SERVER_STARTED.register(server -> {
			SidingDataManager.load(server.overworld());
			CouplingManager.loadAll(server.overworld());
			RouteCouplingStore.load(server.overworld());
			couplingRestorePending = true;
			couplingRestoreAttempts = 0;
		});

		PlayerEvent.PLAYER_JOIN.register(player -> {
			SidingDataManager.syncAllToPlayer(player, SIDING_COUPLING_SYNC_S2C_PACKET_ID);
		});

		LifecycleEvent.SERVER_STOPPING.register(server -> {
			SidingDataManager.save(server.overworld());
			CouplingManager.saveAll(server.overworld());
			RouteCouplingStore.save(server.overworld());
		});
	}

	// ★ 任意のProgressから絶対座標(Vec3)を割り出す共通ヘルパー
	@Unique
	private static Vec3 getPositionAtProgress(TrainServer train, double progress) {
		TrainAccessor accessor = (TrainAccessor) train;
		List<PathData> path = train.path;
		List<Double> distances = accessor.manualEnchance$getDistances();

		double tempRailProgress = Math.max(progress, 0);
		int index = train.getIndex(tempRailProgress, false);
		if (path == null || path.isEmpty() || index >= path.size()) return Vec3.ZERO;

		double offset = (index == 0) ? 0 : distances.get(index - 1);
		return path.get(index).rail.getPosition(tempRailProgress - offset).add(0, train.transportMode.railOffset, 0);
	}

	public static void broadcastWorld(net.minecraft.world.level.Level world, ResourceLocation id, FriendlyByteBuf buf) {
		if (world instanceof net.minecraft.server.level.ServerLevel serverLevel) {
			NetworkManager.sendToPlayers(serverLevel.players(), id, buf);
		}
	}

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

	private static void syncReverser(net.minecraft.world.entity.player.Player player, long trainId, int value) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeLong(trainId);
		buf.writeInt(value);
		broadcast(player, REVERSER_SYNC_S2C_PACKET_ID, buf);
	}

	private static void broadcast(net.minecraft.world.entity.player.Player player, ResourceLocation id, FriendlyByteBuf buf) {
       #if MC_VERSION >= "12000"
       NetworkManager.sendToPlayers(((ServerPlayer)player).serverLevel().players(), id, buf);
       #else
		NetworkManager.sendToPlayers(((ServerPlayer)player).getLevel().players(), id, buf);
       #endif
	}

}