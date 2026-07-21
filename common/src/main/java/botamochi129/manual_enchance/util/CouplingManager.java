package botamochi129.manual_enchance.util;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import mtr.data.RailwayData;
import mtr.data.Siding;
import mtr.data.Train;
import mtr.data.TrainServer;
import botamochi129.manual_enchance.mixin.TrainServerAccessor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CouplingManager {
    private static final Logger LOGGER = LogManager.getLogger("manual_enchance");
    public static final double MAX_COUPLING_DISTANCE = 3.5;
    // 自動連結も手動と同様に「実際に隣接している」ときだけ結合する。
    // 世界座標の連結器間距離で判定するため、異なるルート間（railProgress 原点が異なる）でも
    // 正しく隣接判定される。200 だと離れた列車同士が結合して隙間が残るため小さくする。
    public static final double COUPLING_PROXIMITY = 5.0;
    private static final Map<Long, CouplingInfo> COUPLING_MAP = new HashMap<>();

    public enum ConnectionType {
        SLAVE_FRONT_TO_MASTER_REAR,
        SLAVE_REAR_TO_MASTER_FRONT,
        SLAVE_FRONT_TO_MASTER_FRONT,
        SLAVE_REAR_TO_MASTER_REAR
    }

    public static Map<Long, CouplingInfo> getCouplingMap() {
        return COUPLING_MAP;
    }

    public static void loadAll(ServerLevel server) {
        COUPLING_MAP.clear();
        COUPLING_MAP.putAll(MECouplingStore.load(server));
    }

    public static void saveAll(ServerLevel server) {
        MECouplingStore.save(server, COUPLING_MAP);
    }

    public static TrainServer findTrain(RailwayData data, long trainId) {
        if (data == null) { LOGGER.warn("[findTrain] data is null"); return null; }
        int sidingCount = 0;
        int trainCount = 0;
        for (Siding siding : data.sidings) {
            sidingCount++;
            for (TrainServer train : ((SidingAccessor) siding).getTrains()) {
                trainCount++;
                if (train.id == trainId) {
                    LOGGER.info("[findTrain] found trainId={} in sidingId={} (checked {} sidings, {} trains)", trainId, siding.id, sidingCount, trainCount);
                    return train;
                }
            }
        }
        LOGGER.warn("[findTrain] trainId={} NOT found in {} sidings, {} trains", trainId, sidingCount, trainCount);
        return null;
    }

    public static boolean wouldCreateLoop(RailwayData data, long slaveId, long masterId) {
        long checkId = masterId;
        int safety = 0;
        while (checkId != 0L && safety < 50) {
            LOGGER.debug("[wouldCreateLoop] iter{}: checkId={}, slaveId={}, equal={}", safety, checkId, slaveId, checkId == slaveId);
            if (checkId == slaveId) {
                LOGGER.info("[wouldCreateLoop] LOOP DETECTED: checkId == slaveId");
                return true;
            }
            TrainServer train = findTrain(data, checkId);
            LOGGER.debug("[wouldCreateLoop] findTrain({}) -> {}", checkId, train != null ? train.id : "null");
            long parentId = 0L;
            if (train instanceof TrainAccessor accessor) {
                parentId = accessor.manualEnchance$getMasterId();
                LOGGER.debug("[wouldCreateLoop] train {} instanceof TrainAccessor, masterId={}", checkId, parentId);
            } else {
                LOGGER.debug("[wouldCreateLoop] train {} NOT instanceof TrainAccessor", checkId);
            }
            if (parentId == 0L) {
                CouplingInfo info = COUPLING_MAP.get(checkId);
                LOGGER.debug("[wouldCreateLoop] parentId == 0, couplingMap entry for {}: {}", checkId, info != null ? "masterId=" + info.masterId + ",offset=" + info.offset : "null");
                if (info != null && info.masterId != checkId) {
                    parentId = info.masterId;
                    LOGGER.debug("[wouldCreateLoop] using couplingMap parentId={}", parentId);
                }
            }
            checkId = parentId;
            safety++;
        }
        boolean result = safety >= 50;
        LOGGER.debug("[wouldCreateLoop] result={}{}", result, safety >= 50 ? " (safety limit)" : "");
        return result;
    }

    public static ConnectionType resolveConnectionType(Vec3 slaveFront, Vec3 slaveRear, Vec3 masterFront, Vec3 masterRear) {
        double d1 = slaveFront.distanceTo(masterRear);
        double d2 = slaveRear.distanceTo(masterFront);
        double d3 = slaveFront.distanceTo(masterFront);
        double d4 = slaveRear.distanceTo(masterRear);

        double min = Math.min(Math.min(d1, d2), Math.min(d3, d4));
        if (min == d1) return ConnectionType.SLAVE_FRONT_TO_MASTER_REAR;
        if (min == d2) return ConnectionType.SLAVE_REAR_TO_MASTER_FRONT;
        if (min == d3) return ConnectionType.SLAVE_FRONT_TO_MASTER_FRONT;
        return ConnectionType.SLAVE_REAR_TO_MASTER_REAR;
    }

    public static boolean isSupportedConnection(ConnectionType type) {
        return type == ConnectionType.SLAVE_FRONT_TO_MASTER_REAR
                || type == ConnectionType.SLAVE_REAR_TO_MASTER_FRONT;
    }

    public static double computeOffset(TrainServer master, TrainServer slave, ConnectionType type) {
        // offset = master.railProgress - slave.railProgress
        // forceFinalSpeed: slave.railProgress = master.railProgress - offset
        //
        // SLAVE_FRONT_TO_MASTER_REAR:
        //   slave car 0 と master car trainCars を同じ位置に → offset = master.trainLength
        // SLAVE_REAR_TO_MASTER_FRONT:
        //   slave car trainCars と master car 0 を同じ位置に → offset = -slave.trainLength
        if (type == ConnectionType.SLAVE_FRONT_TO_MASTER_REAR) {
            return master.trainCars * master.spacing;
        } else if (type == ConnectionType.SLAVE_REAR_TO_MASTER_FRONT) {
            return -(slave.trainCars * slave.spacing);
        }
        return master.getRailProgress() - slave.getRailProgress();
    }

    public static boolean applyCoupling(
            TrainServer slave,
            TrainServer master,
            ConnectionType type,
            ServerLevel level,
            ResourceLocation syncPacketId
    ) {
        if (slave.id == master.id) {
            LOGGER.warn("[applyCoupling] slave.id == master.id ({})", slave.id);
            return false;
        }

        TrainAccessor slaveAcc = (TrainAccessor) slave;
        TrainAccessor masterAcc = (TrainAccessor) master;
        long slaveMasterId = slaveAcc.manualEnchance$getMasterId();
        if (slaveMasterId != 0L) {
            LOGGER.warn("[applyCoupling] slave {} already coupled to masterId={}", slave.id, slaveMasterId);
            return false;
        }

        long slaveRouteId = ((TrainServerAccessor) slave).getRouteId();
        long masterRouteId = ((TrainServerAccessor) master).getRouteId();
        if (slaveRouteId != 0L && masterRouteId != 0L && slaveRouteId != masterRouteId) {
            LOGGER.warn("[applyCoupling] route mismatch: slave route={}, master route={}", slaveRouteId, masterRouteId);
            return false;
        }

        // NO forced movement: capture the ACTUAL railProgress difference at the meeting point.
        // This folds in the per-route origin difference (the reference the user wants tracked)
        // and forceFinalSpeed maintains it every tick without teleporting the slave.
        double offset = master.getRailProgress() - slave.getRailProgress();
        LOGGER.info("[applyCoupling] slave={} master={} offset={} slaveProgress={} masterProgress={}",
                slave.id, master.id, String.format("%.2f", offset),
                String.format("%.2f", slave.getRailProgress()),
                String.format("%.2f", master.getRailProgress()));
        slaveAcc.manualEnchance$setMasterId(master.id);
        slaveAcc.manualEnchance$setCouplingOffset(offset);
        slaveAcc.manualEnchance$setCouplingMode(false);
        slaveAcc.manualEnchance$setPositionFixed(false);

        slaveAcc.setDoorValue(masterAcc.manualEnchance$getDoorValue());
        slaveAcc.setPantographState(masterAcc.getPantographState());
        slaveAcc.setReverser(masterAcc.getReverser());
        slaveAcc.setManualNotchDirect(masterAcc.getManualNotch());
        slaveAcc.setSpeed(master.getSpeed());
        slaveAcc.manualEnchance$setOnRoute(masterAcc.manualEnchance$isOnRoute());

        COUPLING_MAP.put(slave.id, new CouplingInfo(master.id, offset, type));
        saveAll(level);

        FriendlyByteBuf notifyBuf = new FriendlyByteBuf(Unpooled.buffer());
        notifyBuf.writeLong(slave.id);
        notifyBuf.writeLong(master.id);
        notifyBuf.writeDouble(offset);
        notifyBuf.writeInt(type.ordinal());
        broadcast(level, syncPacketId, notifyBuf);
        return true;
    }

    public static boolean applyStoredCoupling(TrainServer slave, TrainServer master, CouplingInfo info, ServerLevel level, ResourceLocation syncPacketId) {
        if (slave.id == master.id || info == null) return false;

        TrainAccessor slaveAcc = (TrainAccessor) slave;
        if (slaveAcc.manualEnchance$getMasterId() != 0L) return false;

        long slaveRouteId = ((TrainServerAccessor) slave).getRouteId();
        long masterRouteId = ((TrainServerAccessor) master).getRouteId();
        if (slaveRouteId != 0L && masterRouteId != 0L && slaveRouteId != masterRouteId) {
            LOGGER.warn("[applyStoredCoupling] route mismatch: slave route={}, master route={}, removing stale entry", slaveRouteId, masterRouteId);
            COUPLING_MAP.remove(slave.id);
            saveAll(level);
            return false;
        }

        slaveAcc.manualEnchance$setMasterId(master.id);
        slaveAcc.manualEnchance$setCouplingOffset(info.offset);
        slaveAcc.manualEnchance$setCouplingMode(false);
        slaveAcc.manualEnchance$setPositionFixed(false);

        TrainAccessor masterAcc = (TrainAccessor) master;
        slaveAcc.setDoorValue(masterAcc.manualEnchance$getDoorValue());
        slaveAcc.setPantographState(masterAcc.getPantographState());
        slaveAcc.setReverser(masterAcc.getReverser());
        slaveAcc.setManualNotchDirect(masterAcc.getManualNotch());
        slaveAcc.setSpeed(master.getSpeed());
        slaveAcc.manualEnchance$setOnRoute(masterAcc.manualEnchance$isOnRoute());

        FriendlyByteBuf notifyBuf = new FriendlyByteBuf(Unpooled.buffer());
        notifyBuf.writeLong(slave.id);
        notifyBuf.writeLong(master.id);
        notifyBuf.writeDouble(info.offset);
        notifyBuf.writeInt(info.type.ordinal());
        broadcast(level, syncPacketId, notifyBuf);
        return true;
    }

    /**
     * Natural coupling: use the ACTUAL offset between trains (no teleport).
     * Trains should already be physically close (driven together).
     * The actual railProgress difference is preserved as the coupling offset.
     */
    public static boolean applyNaturalCoupling(
            TrainServer slave,
            TrainServer master,
            ConnectionType type,
            ServerLevel level,
            ResourceLocation syncPacketId
    ) {
        if (slave.id == master.id) {
            LOGGER.warn("[applyNaturalCoupling] slave.id == master.id ({})", slave.id);
            return false;
        }

        TrainAccessor slaveAcc = (TrainAccessor) slave;
        TrainAccessor masterAcc = (TrainAccessor) master;
        long slaveMasterId = slaveAcc.manualEnchance$getMasterId();
        if (slaveMasterId != 0L) {
            LOGGER.warn("[applyNaturalCoupling] slave {} already coupled to masterId={}", slave.id, slaveMasterId);
            return false;
        }

        long slaveRouteId = ((TrainServerAccessor) slave).getRouteId();
        long masterRouteId = ((TrainServerAccessor) master).getRouteId();

        // Cross-route coupling is allowed (e.g. two routes sharing a common track section);
        // the slave keeps its own routeId and only follows the master's position. To avoid
        // coupling trains that merely happen to share a station index on unrelated tracks,
        // require them to be physically adjacent.
        double couplerDist = getCouplerDistance(slaveAcc, masterAcc, type);
        if (couplerDist > COUPLING_PROXIMITY) {
            LOGGER.warn("[applyNaturalCoupling] trains too far to couple (dist={}, limit={}): slave route={}, master route={}",
                    String.format("%.2f", couplerDist), COUPLING_PROXIMITY, slaveRouteId, masterRouteId);
            return false;
        }

        // ★ Shared-section check (cross-route): verify both trains are on physically connected rails
        // using PathData.savedRailBaseId. If they're on different base rails, WARN but still allow
        // coupling because physical proximity (couplerDist < COUPLING_PROXIMITY) is definitive proof
        // of shared track. The savedRailBaseId can differ across routes even on physically shared track.
        if (!areOnSharedSection(slave, master)) {
            LOGGER.warn("[applyNaturalCoupling] trains on different savedRailBaseId (slaveBaseId may differ from masterBaseId) but physically adjacent (dist={}): slave route={}, master route={} -- coupling ALLOWED via fallback",
                    String.format("%.2f", couplerDist), slaveRouteId, masterRouteId);
        }

        // ★ Butt-to-butt reposition: move slave so its coupler touches master's coupler exactly.
        // This ensures zero physical gap at coupling time. The offset is then measured from the
        // corrected positions, giving a clean offset that forceFinalSpeed maintains as
        // slave.railProgress = master.railProgress - offset every tick.
        double slaveProgressBefore = slave.getRailProgress();
        double masterProgressBefore = master.getRailProgress();
        
        // Get master and slave coupler world positions for this connection type
        Vec3 masterCouplerPos;
        Vec3 slaveCouplerPos;
        if (type == ConnectionType.SLAVE_REAR_TO_MASTER_FRONT) {
            masterCouplerPos = masterAcc.manualEnchance$getCouplerFrontPos();
            slaveCouplerPos = slaveAcc.manualEnchance$getCouplerRearPos();
        } else if (type == ConnectionType.SLAVE_FRONT_TO_MASTER_REAR) {
            masterCouplerPos = masterAcc.manualEnchance$getCouplerRearPos();
            slaveCouplerPos = slaveAcc.manualEnchance$getCouplerFrontPos();
        } else if (type == ConnectionType.SLAVE_REAR_TO_MASTER_REAR) {
            masterCouplerPos = masterAcc.manualEnchance$getCouplerRearPos();
            slaveCouplerPos = slaveAcc.manualEnchance$getCouplerRearPos();
        } else { // SLAVE_FRONT_TO_MASTER_FRONT
            masterCouplerPos = masterAcc.manualEnchance$getCouplerFrontPos();
            slaveCouplerPos = slaveAcc.manualEnchance$getCouplerFrontPos();
        }
        
        // Vector from slave coupler to master coupler
        Vec3 delta = masterCouplerPos.subtract(slaveCouplerPos);
        
        // Get slave's forward direction (unit vector along track)
        Vec3 slaveDir = slaveAcc.manualEnchance$getDirectionVector(slaveProgressBefore);
        double dirLen = slaveDir.length();
        if (dirLen < 1e-6) {
            LOGGER.warn("[applyNaturalCoupling] slave {} direction vector too small, skipping butt-to-butt reposition", slave.id);
        } else {
            slaveDir = slaveDir.scale(1.0 / dirLen);
            
            // Project delta onto track direction to get distance along track
            double distanceAlongTrack = delta.dot(slaveDir);
            
            // Reposition slave: new railProgress = old + distanceAlongTrack
            // (positive means slave moves forward along its track to close the gap)
            double newSlaveProgress = slaveProgressBefore + distanceAlongTrack;
            
            // Safety clamp to valid path range
            List<Double> slaveDistances = slaveAcc.manualEnchance$getDistances();
            double slaveMinP = slave.trainCars * (double) slave.spacing;
            double slaveMaxP = (slaveDistances != null && !slaveDistances.isEmpty())
                    ? slaveDistances.get(slaveDistances.size() - 1) : Double.MAX_VALUE;
            if (newSlaveProgress < slaveMinP) newSlaveProgress = slaveMinP;
            if (newSlaveProgress > slaveMaxP) newSlaveProgress = slaveMaxP;
            
            // Apply the reposition
            slaveAcc.setRailProgress(newSlaveProgress);
            
            LOGGER.info("[applyNaturalCoupling] butt-to-butt reposition: slave={} progress {:.2f} -> {:.2f} (delta={:.2f}m along track)",
                    slave.id, slaveProgressBefore, newSlaveProgress, distanceAlongTrack);
        }
        
        // Now measure the actual offset from the (possibly corrected) positions
        double offset = master.getRailProgress() - slave.getRailProgress();
        LOGGER.info("[applyNaturalCoupling] slave={} master={} offset={} (actual) slaveProgress={} masterProgress={}",
                slave.id, master.id, String.format("%.2f", offset),
                String.format("%.2f", slave.getRailProgress()),
                String.format("%.2f", master.getRailProgress()));

        slaveAcc.manualEnchance$setMasterId(master.id);
        slaveAcc.manualEnchance$setCouplingOffset(offset);
        slaveAcc.manualEnchance$setCouplingMode(false);
        slaveAcc.manualEnchance$setPositionFixed(false);

        // IMPORTANT: Do NOT set railProgress — trains are already at correct positions
        // forceFinalSpeed will maintain this offset from the next frame

        slaveAcc.setDoorValue(masterAcc.manualEnchance$getDoorValue());
        slaveAcc.setPantographState(masterAcc.getPantographState());
        slaveAcc.setReverser(masterAcc.getReverser());
        slaveAcc.setManualNotchDirect(masterAcc.getManualNotch());
        slaveAcc.setSpeed(master.getSpeed());
        slaveAcc.manualEnchance$setOnRoute(masterAcc.manualEnchance$isOnRoute());

        COUPLING_MAP.put(slave.id, new CouplingInfo(master.id, offset, type));
        saveAll(level);

        FriendlyByteBuf notifyBuf = new FriendlyByteBuf(Unpooled.buffer());
        notifyBuf.writeLong(slave.id);
        notifyBuf.writeLong(master.id);
        notifyBuf.writeDouble(offset);
        notifyBuf.writeInt(type.ordinal());
        broadcast(level, syncPacketId, notifyBuf);
        return true;
    }

    public static int restoreAll(RailwayData data, ServerLevel level, ResourceLocation syncPacketId) {
        if (data == null || COUPLING_MAP.isEmpty()) return 0;

        int restored = 0;
        int cleaned = 0;
        for (Map.Entry<Long, CouplingInfo> entry : new HashMap<>(COUPLING_MAP).entrySet()) {
            TrainServer slave = findTrain(data, entry.getKey());
            TrainServer master = findTrain(data, entry.getValue().masterId);
            if (slave == null || master == null) {
                LOGGER.warn("[restoreAll] removing stale coupling: slave={} ({}), master={} ({})",
                        entry.getKey(), slave == null ? "not found" : "ok",
                        entry.getValue().masterId, master == null ? "not found" : "ok");
                COUPLING_MAP.remove(entry.getKey());
                cleaned++;
                continue;
            }
            if (((TrainAccessor) slave).manualEnchance$getMasterId() == master.id) continue;
            if (applyStoredCoupling(slave, master, entry.getValue(), level, syncPacketId)) restored++;
        }
        if (cleaned > 0) {
            saveAll(level);
            LOGGER.info("[restoreAll] cleaned {} stale coupling entries, restored {}", cleaned, restored);
        }
        return restored;
    }

    public static void uncouple(RailwayData data, long slaveId, ServerLevel level, ResourceLocation syncPacketId) {
        CouplingInfo removed = COUPLING_MAP.remove(slaveId);
        LOGGER.info("[uncouple] slaveId={}, removed={}", slaveId, removed != null);
        saveAll(level);
        if (removed != null) {
            TrainServer slave = findTrain(data, slaveId);
            if (slave != null) {
                TrainAccessor acc = (TrainAccessor) slave;
                acc.manualEnchance$setMasterId(0L);
                acc.manualEnchance$setCouplingOffset(0.0);
                acc.manualEnchance$setPositionFixed(false);
                acc.manualEnchance$setCouplingMode(false);
                LOGGER.info("[uncouple] reset train {} coupling state", slaveId);
            }
            FriendlyByteBuf out = new FriendlyByteBuf(Unpooled.buffer());
            out.writeLong(slaveId);
            out.writeLong(0L);
            out.writeDouble(0.0);
            out.writeInt(0);
            broadcast(level, syncPacketId, out);
        }
    }

    /**
     * Remove stale coupling entries where the slave or master train no longer exists.
     * Should be called periodically during gameplay (e.g. every 100 ticks).
     */
    public static void cleanStaleEntries(RailwayData data, ServerLevel level, ResourceLocation syncPacketId) {
        boolean changed = false;
        for (Map.Entry<Long, CouplingInfo> entry : new HashMap<>(COUPLING_MAP).entrySet()) {
            TrainServer slave = findTrain(data, entry.getKey());
            TrainServer master = findTrain(data, entry.getValue().masterId);
            if (slave == null || master == null) {
                LOGGER.info("[cleanStaleEntries] removing stale coupling: slave={} ({}), master={} ({})",
                        entry.getKey(), slave == null ? "removed" : "ok",
                        entry.getValue().masterId, master == null ? "removed" : "ok");
                COUPLING_MAP.remove(entry.getKey());
                changed = true;
            }
        }
        if (changed) {
            saveAll(level);
            LOGGER.info("[cleanStaleEntries] cleaned stale entries, {} remaining", COUPLING_MAP.size());
        }
    }

    public static boolean isFullyRestored(RailwayData data) {
        boolean changed = false;
        for (Map.Entry<Long, CouplingInfo> entry : new HashMap<>(COUPLING_MAP).entrySet()) {
            TrainServer slave = findTrain(data, entry.getKey());
            TrainServer master = findTrain(data, entry.getValue().masterId);
            if (slave == null || master == null) {
                LOGGER.warn("[isFullyRestored] removing stale coupling: slave={}, master={}", entry.getKey(), entry.getValue().masterId);
                COUPLING_MAP.remove(entry.getKey());
                changed = true;
                continue;
            }
            if (((TrainAccessor) slave).manualEnchance$getMasterId() != master.id) return false;
        }
        if (changed) {
            LOGGER.info("[isFullyRestored] cleaned stale entries, {} remaining", COUPLING_MAP.size());
        }
        return true;
    }

    /**
     * 連結器間の3D距離を計算する（reversed非依存の固定車両位置を使用）
     */
    public static double getCouplerDistance(TrainAccessor slave, TrainAccessor master, ConnectionType type) {
        // AIOOBE guard: if either train has empty path, return MAX (infinite distance = don't couple)
        Train slaveTrain = (Train) (Object) slave;
        Train masterTrain = (Train) (Object) master;
        if (slaveTrain.path == null || slaveTrain.path.isEmpty()) return Double.MAX_VALUE;
        if (masterTrain.path == null || masterTrain.path.isEmpty()) return Double.MAX_VALUE;
        
        Vec3 slavePos = (type == ConnectionType.SLAVE_FRONT_TO_MASTER_REAR || type == ConnectionType.SLAVE_FRONT_TO_MASTER_FRONT)
                ? slave.manualEnchance$getCouplerFrontPos()
                : slave.manualEnchance$getCouplerRearPos();
        Vec3 masterPos = (type == ConnectionType.SLAVE_FRONT_TO_MASTER_REAR || type == ConnectionType.SLAVE_REAR_TO_MASTER_REAR)
                ? master.manualEnchance$getCouplerRearPos()
                : master.manualEnchance$getCouplerFrontPos();
        return slavePos.distanceTo(masterPos);
    }

    /**
     * 全結合形のうち最小の連結器間距離を返す。reversed の反転などで結合形が変わっても、
     * 実際に物理的にくっついていれば最小距離は小さくなるため、誤った乖離判定（自動解結ループ
     * によるチカチカ）を防げる。
     */
    public static double getCouplerDistanceMin(TrainAccessor slave, TrainAccessor master) {
        double min = Double.MAX_VALUE;
        for (ConnectionType t : ConnectionType.values()) {
            double d = getCouplerDistance(slave, master, t);
            if (d < min) min = d;
        }
        return min;
    }

    /**
     * 折り返し時に連結列車の master/slave を入れ替え、再配置する（手動）または MTR に委任（自動）。
     *
     * Spec §3:
     * - 旧 chain: oldMaster -> slave1 -> ... -> slaveN
     * - 新 chain: slaveN(new master) -> slaveN-1 -> ... -> oldMaster
     * - Pattern A (manual, reposition=true): MTR の処理後に実際の位置からオフセット再計算
     * - Pattern B (auto, reposition=false): MTR が各車両をワープさせるので、その後にオフセット再計算
     * - syncPathFrom(oldMaster -> newMaster) so new master has return path.
     * - reverser stays 1 always. MTR handles reversed flag.
     */
    public static void turnBackCouplingChain(RailwayData data, TrainServer oldMaster, ServerLevel level, ResourceLocation syncPacketId, boolean reposition) {
        // 1. Build chain: oldMaster -> slave1 -> ... -> slaveN
        List<TrainServer> chain = new ArrayList<>();
        chain.add(oldMaster);
        long cur = oldMaster.id;
        for (int i = 0; i < 50; i++) {
            Long next = null;
            for (Map.Entry<Long, CouplingInfo> e : COUPLING_MAP.entrySet()) {
                if (e.getValue().masterId == cur) { next = e.getKey(); break; }
            }
            if (next == null) break;
            TrainServer t = findTrain(data, next);
            if (t == null) break;
            chain.add(t);
            cur = next;
        }

        int n = chain.size() - 1;
        if (n < 0) return;

        // Compute lengths and pre-turnback railProgress (for logging only)
        double[] L = new double[chain.size()];
        double[] pi = new double[chain.size()];
        double totalLength = 0;
        for (int i = 0; i < chain.size(); i++) {
            TrainServer t = chain.get(i);
            L[i] = t.trainCars * t.spacing;
            totalLength += L[i];
            pi[i] = ((TrainAccessor) t).manualEnchance$getRailProgress();
        }

        LOGGER.info("[turnBackCouplingChain] chain={} totalLength={} L={} pre-turnback RP={} reposition={}",
                chain.stream().map(t -> t.id).toList(),
                String.format("%.2f", totalLength),
                java.util.Arrays.toString(L),
                java.util.Arrays.toString(pi),
                reposition);

        // 2. Clear all coupling entries BEFORE MTR processes turnback
        for (TrainServer t : chain) {
            COUPLING_MAP.remove(t.id);
            TrainAccessor a = (TrainAccessor) t;
            LOGGER.debug("[TBCC] clearing id={} masterId={}->0 offset={}->0",
                    t.id, a.manualEnchance$getMasterId(), String.format("%.1f", a.manualEnchance$getCouplingOffset()));
            a.manualEnchance$setMasterId(0L);
            a.manualEnchance$setCouplingOffset(0.0);
            a.manualEnchance$setPositionFixed(false);
        }

        // 3. NO syncPathFrom - each train keeps its own path (path changes prohibited per spec)

        // 4. Common: reverser = 1 (MTR handles reversed flag)
        for (int i = 0; i < chain.size(); i++) {
            TrainAccessor a = (TrainAccessor) chain.get(i);
            a.setReverser(1);
            a.manualEnchance$setPositionFixed(false);
        }

        // 5. Compute correct RP for ALL trains in the chain (Unified for both Manual and Auto modes).
        // Chain is currently: [oldMaster, slave1, ..., newMaster] (indices 0 to n)
        // After turnback, the physical order is: [newMaster, ..., slave1, oldMaster]
        // The rear of the chain (oldMaster, index 0) should be at base + ownLength.
        // Each preceding vehicle (index i) should be at base + (sum of lengths behind it) + ownLength.
        for (int i = n; i >= 0; i--) {
            TrainServer t = chain.get(i);
            TrainAccessor acc = (TrainAccessor) t;
            int repeatIdx1 = acc.getRepeatIndex1();
            List<Double> distances = acc.manualEnchance$getDistances();

            double base = 0;
            if (distances != null && !distances.isEmpty() && repeatIdx1 > 0 && repeatIdx1 <= distances.size()) {
                base = distances.get(Math.max(0, repeatIdx1 - 1));
            }

            // Sum of lengths of vehicles behind this one in the new chain (indices 0 to i-1)
            double lengthBehind = 0;
            for (int j = 0; j < i; j++) {
                lengthBehind += L[j];
            }
            double ownLength = L[i];

            double newRP = base + lengthBehind + ownLength;
            acc.setRailProgress(newRP);
            acc.manualEnchance$setNextManualProgress(newRP);
            acc.manualEnchance$setLastFixedProgress(newRP);

            LOGGER.info("[turnBackCouplingChain] set train {} RP={} (base={}, lengthBehind={}, ownLength={}, L[i]={})",
                    t.id, String.format("%.2f", newRP), String.format("%.2f", base),
                    String.format("%.2f", lengthBehind), String.format("%.2f", ownLength),
                    String.format("%.2f", L[i]));
        }

        // 6. Re-link reversed chain with offset calculated from the NOW CORRECT post-turnback positions.
        for (int k = n - 1; k >= 0; k--) {
            TrainServer slave = chain.get(k);
            TrainServer master = chain.get(k + 1); // k+1 is closer to newMaster (index n)
            TrainAccessor slaveAcc = (TrainAccessor) slave;
            TrainAccessor masterAcc = (TrainAccessor) master;

            // Now that railProgress is correct for all trains, we can safely measure the actual offset.
            double offset = masterAcc.manualEnchance$getRailProgress() - slaveAcc.manualEnchance$getRailProgress();
            ConnectionType storedType = ConnectionType.SLAVE_FRONT_TO_MASTER_REAR;

            LOGGER.info("[turnBackCouplingChain] linking slave={} to master={}, slaveRP={}, masterRP={}, offset={}",
                    slave.id, master.id, String.format("%.2f", slaveAcc.manualEnchance$getRailProgress()),
                    String.format("%.2f", masterAcc.manualEnchance$getRailProgress()), String.format("%.2f", offset));

            slaveAcc.manualEnchance$setMasterId(master.id);
            slaveAcc.manualEnchance$setCouplingOffset(offset);
            slaveAcc.manualEnchance$setCouplingMode(false);
            slaveAcc.manualEnchance$setPositionFixed(false);

            COUPLING_MAP.put(slave.id, new CouplingInfo(master.id, offset, storedType));

            FriendlyByteBuf notifyBuf = new FriendlyByteBuf(Unpooled.buffer());
            notifyBuf.writeLong(slave.id);
            notifyBuf.writeLong(master.id);
            notifyBuf.writeDouble(offset);
            notifyBuf.writeInt(storedType.ordinal());
            broadcast(level, syncPacketId, notifyBuf);

            LOGGER.debug("[TBCC] linked slave={} -> master={} offset={}, slaveRP={}, masterRP={}",
                    slave.id, master.id, String.format("%.2f", offset),
                    String.format("%.2f", slaveAcc.manualEnchance$getRailProgress()),
                    String.format("%.2f", masterAcc.manualEnchance$getRailProgress()));
        }
        saveAll(level);

        TrainServer newMaster = chain.get(n);
        LOGGER.info("[turnBackCouplingChain] done: newMaster={}, chain size={}, newMaster masterId={}",
                newMaster.id, chain.size(), ((TrainAccessor) newMaster).manualEnchance$getMasterId());
    }

    /**
     * doorTarget/doorValue をチェーン全列車に同期する。
     * 呼び出し元の doorTarget を元に、チェーン内の全列車に反映する。
     * chain の先頭（masterId == 0）を起点として、master→slave 方向で伝播する。
     */
    public static void syncDoorTargetAcrossChain(RailwayData data, long anyTrainId, boolean doorTarget, float doorValue) {
        // チェーン構築: まずルート（masterId == 0 の列車）を見つける
        List<TrainServer> chain = new ArrayList<>();
        // anyTrainId から順に遡ってルートを探す
        long cur = anyTrainId;
        for (int i = 0; i < 50; i++) {
            CouplingInfo info = COUPLING_MAP.get(cur);
            if (info == null) break;
            cur = info.masterId;
            if (cur == 0L) break;
        }
        // cur がルート（masterId == 0）
        TrainServer root = findTrain(data, cur);
        if (root == null) return;
        chain.add(root);

        // root → slave1 → ... → slaveN の順にチェーン構築
        long cursor = root.id;
        for (int i = 0; i < 50; i++) {
            Long next = null;
            for (Map.Entry<Long, CouplingInfo> e : COUPLING_MAP.entrySet()) {
                if (e.getValue().masterId == cursor) { next = e.getKey(); break; }
            }
            if (next == null) break;
            TrainServer t = findTrain(data, next);
            if (t == null) break;
            chain.add(t);
            cursor = next;
        }

        // 全列車に doorTarget/doorValue を反映
        for (TrainServer t : chain) {
            TrainAccessor a = (TrainAccessor) t;
            a.manualEnchance$setDoorTarget(doorTarget);
            a.setDoorValue(doorValue);
        }
    }

    public static void broadcast(ServerLevel level, ResourceLocation packetId, FriendlyByteBuf buf) {
        NetworkManager.sendToPlayers(level.players(), packetId, buf);
    }

    /**
     * Check if two trains are on the same shared track section.
     * Uses PathData.savedRailBaseId to verify both trains are on physically connected rails.
     * Cross-route coupling is only allowed when both trains share the same savedRailBaseId.
     * Returns false if either train has empty path or invalid index.
     */
    public static boolean areOnSharedSection(TrainServer slave, TrainServer master) {
        Train slaveTrain = (Train) (Object) slave;
        Train masterTrain = (Train) (Object) master;
        
        if (slaveTrain.path == null || slaveTrain.path.isEmpty()) return false;
        if (masterTrain.path == null || masterTrain.path.isEmpty()) return false;
        
        int slavePathIdx = slaveTrain.getIndex(slave.getRailProgress(), false);
        int masterPathIdx = masterTrain.getIndex(master.getRailProgress(), false);
        
        if (slavePathIdx < 0 || slavePathIdx >= slaveTrain.path.size()) return false;
        if (masterPathIdx < 0 || masterPathIdx >= masterTrain.path.size()) return false;
        
        // Check if both path segments have the same savedRailBaseId
        long slaveBaseId = slaveTrain.path.get(slavePathIdx).savedRailBaseId;
        long masterBaseId = masterTrain.path.get(masterPathIdx).savedRailBaseId;
        
        return slaveBaseId == masterBaseId && slaveBaseId != 0L;
    }
}