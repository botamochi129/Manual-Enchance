package botamochi129.manual_enchance.util;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import mtr.data.RailwayData;
import mtr.data.Siding;
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

        // NO forced movement: the slave keeps its actual position. The offset below captures the
        // real railProgress difference at the (physically close) meeting point — which folds in the
        // per-route origin difference the user wants tracked as a single reference — and
        // forceFinalSpeed maintains slave.railProgress = master.railProgress - offset every tick,
        // keeping the gap constant on the shared rails without teleporting.
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
     * - newRP[i] = pi[i] + beforeSum + totalLength - afterSum
     * - Pattern A (manual, reposition=true): setRailProgress(newRP), flip reversed, reset speed/notch/BC,
     *   offset = master.railProgress - slave.railProgress (actual post-reposition)
     * - Pattern B (auto, reposition=false): MTR warps positions; offset = -(slave.trainCars * slave.spacing)
     * - syncPathFrom(oldMaster -> newMaster) so new master has return path.
     * - reverser stays 1 always.
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

        // Compute lengths and pre-turnback railProgress
        double[] L = new double[chain.size()];
        double[] pi = new double[chain.size()];
        double totalLength = 0;
        for (int i = 0; i < chain.size(); i++) {
            TrainServer t = chain.get(i);
            L[i] = t.trainCars * t.spacing;
            totalLength += L[i];
            pi[i] = ((TrainAccessor) t).manualEnchance$getRailProgress();
        }

        // 2. Compute newRP for each train (used when reposition=true)
        double[] newRP = new double[chain.size()];
        for (int i = 0; i < chain.size(); i++) {
            double beforeSum = 0;
            for (int j = 0; j < i; j++) beforeSum += L[j];
            double afterSum = 0;
            for (int j = i + 1; j < chain.size(); j++) afterSum += L[j];
            newRP[i] = pi[i] + beforeSum + totalLength - afterSum;
        }

        LOGGER.info("[turnBackCouplingChain] chain={} totalLength={:.2f} newRP={} reposition={}",
                chain.stream().map(t -> t.id).toList(),
                totalLength,
                java.util.Arrays.toString(newRP),
                reposition);

        // 3. Clear all entries
        for (TrainServer t : chain) {
            COUPLING_MAP.remove(t.id);
            TrainAccessor a = (TrainAccessor) t;
            a.manualEnchance$setMasterId(0L);
            a.manualEnchance$setCouplingOffset(0.0);
            a.manualEnchance$setPositionFixed(false);
        }

        // 4. syncPathFrom: copy oldMaster's (return) path to new master (chain[n])
        TrainServer newMaster = chain.get(n);
        ((TrainAccessor) newMaster).manualEnchance$syncPathFrom(oldMaster);

        // 5. Common: reverser = 1, positionFixed = false
        for (int i = 0; i < chain.size(); i++) {
            TrainAccessor a = (TrainAccessor) chain.get(i);
            a.setReverser(1);
            a.manualEnchance$setPositionFixed(false);
        }

        // 6. Reposition (manual only) and flip reversed on whole chain
        if (reposition) {
            for (int i = 0; i < chain.size(); i++) {
                TrainAccessor a = (TrainAccessor) chain.get(i);
                double rp = newRP[i];
                double len = chain.get(i).trainCars * (double) chain.get(i).spacing;
                if (rp < len) rp = len;
                // Clamp to max path length
                List<Double> dists = a.manualEnchance$getDistances();
                if (dists != null && !dists.isEmpty()) {
                    double maxP = dists.get(dists.size() - 1);
                    if (maxP > len && maxP != Double.MAX_VALUE && rp > maxP) rp = maxP;
                }
                a.setRailProgress(rp);
                a.manualEnchance$setNextManualProgress(rp);
                a.manualEnchance$setLastFixedProgress(rp);
                a.setSpeed(0.0f);
                a.setManualNotchDirect(0);
                a.manualEnchance$setBCPressure(0.0f);
                // Flip reversed (slaves inherit via per-tick sync)
                a.setReversed(!a.getReversed());
            }
        }

        // 7. Re-link reversed chain with per-mode offset
        //    Manual: offset = master.railProgress - slave.railProgress (actual post-reposition)
        //    Auto:   offset = -(slave.trainCars * slave.spacing) (fixed negative)
        for (int k = n - 1; k >= 0; k--) {
            TrainServer slave = chain.get(k);
            TrainServer master = chain.get(k + 1);
            TrainAccessor slaveAcc = (TrainAccessor) slave;
            TrainAccessor masterAcc = (TrainAccessor) master;

            double offset;
            if (reposition) {
                // Manual: use actual railProgress difference (positions are already set)
                offset = masterAcc.manualEnchance$getRailProgress()
                        - slaveAcc.manualEnchance$getRailProgress();
            } else {
                // Auto: fixed negative offset = -slaveLength
                offset = -(slave.trainCars * (double) slave.spacing);
            }

            slaveAcc.manualEnchance$setMasterId(master.id);
            slaveAcc.manualEnchance$setCouplingOffset(offset);
            slaveAcc.manualEnchance$setCouplingMode(false);
            slaveAcc.manualEnchance$setPositionFixed(false);

            COUPLING_MAP.put(slave.id, new CouplingInfo(master.id, offset, ConnectionType.SLAVE_FRONT_TO_MASTER_REAR));

            FriendlyByteBuf notifyBuf = new FriendlyByteBuf(Unpooled.buffer());
            notifyBuf.writeLong(slave.id);
            notifyBuf.writeLong(master.id);
            notifyBuf.writeDouble(offset);
            notifyBuf.writeInt(ConnectionType.SLAVE_FRONT_TO_MASTER_REAR.ordinal());
            broadcast(level, syncPacketId, notifyBuf);
        }
        saveAll(level);

        LOGGER.info("[turnBackCouplingChain] done: newMaster={}, reposition={}", newMaster.id, reposition);
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
}
