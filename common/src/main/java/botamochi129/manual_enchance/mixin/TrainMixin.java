package botamochi129.manual_enchance.mixin;

import botamochi129.manual_enchance.Main;
import botamochi129.manual_enchance.util.CouplingInfo;
import botamochi129.manual_enchance.util.CouplingManager;
import botamochi129.manual_enchance.util.RouteCouplingStore;
import botamochi129.manual_enchance.util.RouteCouplingStore.RouteCouplingAction;
import botamochi129.manual_enchance.util.SidingAccessor;
import botamochi129.manual_enchance.util.TrainAccessor;
import botamochi129.manual_enchance.mixin.TrainServerAccessor;
import mtr.data.*;
import mtr.path.PathData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

import mtr.data.TrainServer;

@Mixin(value = Train.class, remap = false)
public abstract class TrainMixin implements TrainAccessor {

    @Shadow protected int manualNotch;
    @Shadow protected float doorValue;
    @Shadow protected boolean doorTarget;
    @Shadow protected boolean isCurrentlyManual;
    @Shadow protected float speed;
    @Shadow public float accelerationConstant;
    @Shadow protected double railProgress;
    @Shadow protected List<Double> distances;
    @Shadow protected int nextStoppingIndex;
    @Shadow @Final public int trainCars;
    @Shadow @Final public int spacing;
    @Shadow protected boolean reversed;
    @Shadow public abstract float getModelZOffset();
    @Shadow @Final public List<PathData> path;
    @Shadow @Final public int maxManualSpeed;

    @Shadow protected abstract double getRailProgress(int car, int trainSpacing);

    @Shadow public abstract int getIndex(double tempRailProgress, boolean roundDown);

    @Shadow @Final public TransportMode transportMode;
    @Shadow public boolean isOnRoute;
    @Shadow public float elapsedDwellTicks;

    @Unique private int manualEnchance$lastRouteCouplingStation = -1;
    @Unique private boolean manualEnchance$routeCouplingDone = false;
    @Unique private boolean manualEnchance$waitingForCouple = false;
    @Unique private int manualEnchance$coupleWaitTicks = 0;
    @Unique private int manualEnchance$lastCouplingPathIndex = -1;

    // 状態
    private float nextManualSpeed = 0.0f;
    private double nextManualProgress = 0.0;
    private boolean isInsideDepot = false;
    private boolean hasLeftDepot = false;
    private double lastFixedProgress = -1;

    // 1:前進(F), 0:中立(N), -1:後進(B)
    private int reverser = 1;

    @Unique private int pantographState = 0;
    @Unique private final Map<String, Integer> rollsignIndices = new HashMap<>();
    @Unique private final Map<String, Float> rollsignOffsets = new HashMap<>();
    @Unique private final Map<String, Integer> rollsignStepsMap = new HashMap<>();
    @Unique private final Map<String, List<String>> rollsignNamesMap = new HashMap<>();

    @Unique private long manualEnchance$masterId = 0L;
    @Unique private double manualEnchance$couplingOffset = 0.0;
    @Unique private boolean manualEnchance$couplingMode = false;
    @Unique private boolean manualEnchance$positionFixed = false;
    @Unique private static final double COUPLING_MAX_DIVERGE_DISTANCE = 200.0;
    @Unique private float manualEnchance$bcPressure = 0.0f;
    @Unique private boolean manualEnchance$abruptStop = false;
    @Unique private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger("manual_enchance");

    // Auto-coupling stop-position override: when this (auto) train is the SECOND arrival at a
    // coupling station, it decelerates to and stops at the coupled position (just behind the
    // already-stopped master) instead of its natural platform position. This is the "true
    // deceleration stop" — manual driving is excluded (see updateCoupleStopOverride).
    @Unique private boolean manualEnchance$coupleStopActive = false;
    @Unique private double manualEnchance$coupleStopTarget = 0.0;

    // Turnback guard: prevents the coupling-chain role swap from re-firing every tick while the
    // chain is dwelling at a turnback node. Reset once the train has moved clear of the node.
    @Unique private boolean manualEnchance$turnBackDone = false;

    // Reversal tracking for auto turnback detection. For master trains, tracks own reversed state;
    // for slave trains, tracks the master's reversed state. Initialized on first tick to avoid
    // false detection on world load.
    @Unique private boolean manualEnchance$lastReversed = false;
    @Unique private boolean manualEnchance$reversedInitialized = false;

    // Tracks whether the train was off-route while coupled. When isOnRoute transitions from
    // false→true, this indicates a terminal reset — the turnback moment for non-repeat routes
    // where MTR doesn't toggle `reversed`.
    @Unique private boolean manualEnchance$wasOffRoute = false;

    // Set when the train is stopped at a turnback node and the player opens doors.
    // The turnback only fires when this flag is true AND doors are subsequently closed,
    // ensuring the player can open doors at the terminal before the role swap occurs.
    @Unique private boolean manualEnchance$doorsOpenedAtTerminal = false;

    // Cached world from the HEAD inject, used by @Redirect methods that don't receive world param.
    @Unique private Level manualEnchance$cachedWorld = null;

    @Inject(method = "changeManualSpeed", at = @At("HEAD"), cancellable = true)
    public void onChangeManualSpeed(boolean isAccelerate, CallbackInfoReturnable<Boolean> cir) {
        if (isAccelerate) {
            if (this.doorValue <= 0.01F && this.manualNotch < 5) {
                this.manualNotch++;
                cir.setReturnValue(true);
            }
        } else {
            if (this.manualNotch > -9) {
                this.manualNotch--;
                cir.setReturnValue(true);
            }
        }
    }

    @Override public Set<String> getRollsignIds() {
        return this.rollsignIndices.keySet();
    }

    @Override public int getManualNotch() { return this.manualNotch; }
    @Override public boolean getIsCurrentlyManual() { return this.isCurrentlyManual; }
    @Override public int getReverser() { return this.reverser; }
    @Override public float manualEnchance$getDoorValue() { return this.doorValue; }
    @Override public boolean manualEnchance$getDoorTarget() { return this.doorTarget; }
    @Override public void manualEnchance$setDoorTarget(boolean target) { this.doorTarget = target; }
    @Override public void setReverser(int value) { this.reverser = value; }
    @Override public int getNextStoppingIndex() { return this.nextStoppingIndex; }
    @Override public List<Double> manualEnchance$getDistances() { return this.distances; }
    @Override public double manualEnchance$getRailProgress() { return this.railProgress; }
    @Override public int getPantographState() { return pantographState; }
    @Override public void setPantographState(int state) { this.pantographState = state % 4; }
    @Override public void setRollsignIndex(String key, int index) { rollsignIndices.put(key, index); }
    @Override public int getRollsignIndex(String key) { return rollsignIndices.getOrDefault(key, 0); }
    @Override public float getRollsignOffset(String key) { return rollsignOffsets.getOrDefault(key, 0.0f); }
    @Override public void setRollsignOffset(String key, float offset) { rollsignOffsets.put(key, offset); }
    @Override public Map<String, Integer> getRollsignIndices() { return rollsignIndices; }
    @Override public void setRollsignSteps(String key, int steps) { rollsignStepsMap.put(key, steps); }
    @Override public int getRollsignSteps(String key) { return rollsignStepsMap.getOrDefault(key, 1); }
    @Override public void setRollsignNames(String key, List<String> names) { rollsignNamesMap.put(key, names); }
    @Override public List<String> getRollsignNames(String key) { return rollsignNamesMap.getOrDefault(key, java.util.Collections.emptyList()); }
    @Override public float manualEnchance$getBCPressure() { return this.manualEnchance$bcPressure; }
    @Override public void manualEnchance$setBCPressure(float pressure) { this.manualEnchance$bcPressure = pressure; }
    @Override public boolean manualEnchance$getDoorsOpenedAtTerminal() { return this.manualEnchance$doorsOpenedAtTerminal; }
    @Override public void manualEnchance$setDoorsOpenedAtTerminal(boolean opened) { this.manualEnchance$doorsOpenedAtTerminal = opened; }
    @Override public boolean manualEnchance$isOnRoute() { return this.isOnRoute; }
    @Override public void manualEnchance$setOnRoute(boolean onRoute) { this.isOnRoute = onRoute; }
    @Override public boolean manualEnchance$isWaitingForCouple() { return this.manualEnchance$waitingForCouple; }
    @Override public void manualEnchance$setWaitingForCouple(boolean waiting) { this.manualEnchance$waitingForCouple = waiting; }
    @Override public void manualEnchance$setNextManualProgress(double progress) { this.nextManualProgress = progress; }
    @Override public void manualEnchance$setLastFixedProgress(double progress) { this.lastFixedProgress = progress; }
    @Override public long manualEnchance$getMasterId() { return this.manualEnchance$masterId; }
    @Override public void manualEnchance$setMasterId(long id) { this.manualEnchance$masterId = id; }
    @Override public double manualEnchance$getCouplingOffset() { return this.manualEnchance$couplingOffset; }
    @Override public void manualEnchance$setCouplingOffset(double offset) { this.manualEnchance$couplingOffset = offset; }
    @Override public void manualEnchance$setPositionFixed(boolean fixed) { this.manualEnchance$positionFixed = fixed; }
    @Override public boolean manualEnchance$getPositionFixed() { return this.manualEnchance$positionFixed; }
    @Override public boolean manualEnchance$isCouplingMode() { return manualEnchance$couplingMode; }
    @Override public void manualEnchance$setCouplingMode(boolean mode) { this.manualEnchance$couplingMode = mode; }
    @Override public boolean manualEnchance$getTurnBackDone() { return this.manualEnchance$turnBackDone; }
    @Override public void manualEnchance$setTurnBackDone(boolean done) { this.manualEnchance$turnBackDone = done; }
    @Override public Vec3 callGetRoutePosition(int car, int trainSpacing) {
        final double tempRailProgress = Math.max(getRailProgress(car, trainSpacing) - getModelZOffset(), 0);
        final int index = this.getIndex(tempRailProgress, false);
        return path.get(index).rail.getPosition(tempRailProgress - (index == 0 ? 0 : distances.get(index - 1))).add(0, transportMode.railOffset, 0);
    }

    @Override
    public void setManualNotchDirect(int notch) {
        this.manualNotch = Math.max(-9, Math.min(5, notch));
        if (this.manualNotch > 0 && this.isInsideDepot) {
            this.hasLeftDepot = true;
        }
    }

    @Override
    public String getHornSoundId() {
        Train self = (Train) (Object) this;
        if (self.trainId == null) return "";
        for (Map.Entry<String, String> entry : Main.HORN_MAP.entrySet()) {
            if (self.trainId.contains(entry.getKey())) return entry.getValue();
        }
        return "";
    }

    @Override public void setRailProgress(double rp) { this.railProgress = rp; }
    @Override public void setSpeed(float sp) { this.speed = sp; }
    @Override public void setDoorValue(float dv) { this.doorValue = dv; }

    @Inject(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("RETURN"), remap = false, require = 0)
    private void injectReadPacket(FriendlyByteBuf packet, CallbackInfo ci) {
        try {
            this.pantographState = packet.readInt();
            this.manualEnchance$masterId = packet.readLong();
            this.manualEnchance$couplingOffset = packet.readDouble();
            this.reverser = packet.readInt();
            this.manualEnchance$bcPressure = packet.readFloat();
        } catch (Exception ignored) {}
    }

    @Inject(method = "writePacket(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("TAIL"), remap = false, require = 0)
    private void injectWritePacket(FriendlyByteBuf packet, CallbackInfo ci) {
        try {
            packet.writeInt(this.pantographState);
            packet.writeLong(this.manualEnchance$masterId);
            packet.writeDouble(this.manualEnchance$couplingOffset);
            packet.writeInt(this.reverser);
            packet.writeFloat(this.manualEnchance$bcPressure);
        } catch (Exception ignored) {}
    }

    @Unique
    public Vec3 manualEnchance$getFrontPosition() {
        Train self = (Train)(Object)this;
        if (self.path == null || self.path.isEmpty()) return Vec3.ZERO;
        int car = this.reversed ? self.trainCars - 1 : 0;
        Vec3 pos = ((TrainAccessor)this).callGetRoutePosition(car, self.spacing);
        return pos == null ? Vec3.ZERO : pos;
    }

    @Unique
    public Vec3 manualEnchance$getRearPosition() {
        Train self = (Train)(Object)this;
        if (self.path == null || self.path.isEmpty()) return Vec3.ZERO;
        int car = this.reversed ? 0 : self.trainCars;
        Vec3 pos = ((TrainAccessor)this).callGetRoutePosition(car, self.spacing);
        return pos == null ? Vec3.ZERO : pos;
    }

    // 連結器位置（reversed非依存、常に car 0 が front、car trainCars が rear）
    @Override
    public Vec3 manualEnchance$getCouplerFrontPos() {
        return manualEnchance$getCarPosition(0);
    }

    @Override
    public Vec3 manualEnchance$getCouplerRearPos() {
        Train self = (Train)(Object)this;
        return manualEnchance$getCarPosition(self.trainCars);
    }

    @Unique
    private Vec3 manualEnchance$getCarPosition(int car) {
        Train self = (Train)(Object)this;
        if (self.path == null || self.path.isEmpty()) return Vec3.ZERO;
        Vec3 pos = ((TrainAccessor)this).callGetRoutePosition(car, self.spacing);
        return pos == null ? Vec3.ZERO : pos;
    }

    // ★ 進行方向ベクトルの取得
    @Override
    public double manualEnchance$getRailProgressAtCar(int car, double longitudinalOffsetBlocks) {
        Train self = (Train) (Object) this;
        int mappedCar = self.isReversed() ? self.trainCars - car : car;
        double progress = getRailProgress(mappedCar, self.spacing) - getModelZOffset();
        progress += self.isReversed() ? -longitudinalOffsetBlocks : longitudinalOffsetBlocks;
        return Math.max(progress, 0);
    }

    @Unique
    public Vec3 manualEnchance$getDirectionVector(double progress) {
        Train self = (Train)(Object)this;
        double p1 = Math.max(progress - this.getModelZOffset(), 0);
        double p2 = p1 + 0.1;

        int index1 = self.getIndex(p1, false);
        if (this.path == null || this.path.isEmpty() || index1 >= this.path.size()) return Vec3.directionFromRotation(0,-90);
        double offset1 = (index1 == 0) ? 0 : this.distances.get(index1 - 1);
        Vec3 pos1 = this.path.get(index1).rail.getPosition(p1 - offset1);

        int index2 = self.getIndex(p2, false);
        if (index2 >= this.path.size()) index2 = this.path.size() - 1;
        double offset2 = (index2 == 0) ? 0 : this.distances.get(index2 - 1);
        Vec3 pos2 = this.path.get(index2).rail.getPosition(p2 - offset2);

        Vec3 dir = pos2.subtract(pos1);
        return dir.lengthSqr() > 0 ? dir.normalize() : Vec3.directionFromRotation(0,-90);
    }

    // ★ グループの最先頭（リーダー）列車を探すヘルパー
    @Unique
    private Train manualEnchance$findLeaderTrain(Level world) {
        Train current = (Train)(Object)this;
        int safetyCount = 0;
        while (((TrainAccessor)current).manualEnchance$getMasterId() != 0L && safetyCount < 20) {
            long parentId = ((TrainAccessor)current).manualEnchance$getMasterId();
            Train parent = manualEnchance$findTrainById(world, parentId);
            if (parent != null) current = parent;
            else break;
            safetyCount++;
        }
        return current;
    }

    @Unique
    private Train manualEnchance$findTrainById(Level world, long id) {
        RailwayData data = RailwayData.getInstance(world);
        if (data != null) {
            for (Siding s : data.sidings) {
                for (TrainServer t : ((SidingAccessor) s).getTrains()) {
                    if (t.id == id) return (Train) (Object) t;
                }
            }
        }
        return null;
    }

    @Override
    public void changeReverser(boolean isUp) {
        if (this.speed < 0.0001F) {
            if (isUp) {
                if (this.reverser < 1) this.reverser++;
            } else {
                if (this.reverser > -1) this.reverser--;
            }
        }
    }

    @Override
    public void manualEnchance$syncPathFrom(TrainServer master) {
        Train self = (Train)(Object)this;
        self.path.clear();
        self.path.addAll(master.path);
        this.distances.clear();
        this.distances.addAll(((TrainAccessor)master).manualEnchance$getDistances());
    }

    @Inject(method = "simulateTrain", at = @At("HEAD"))
    private void calculateManualPhysics(Level world, float ticksElapsed, Depot depot, CallbackInfo ci) {
        this.manualEnchance$cachedWorld = world;
        Train self = (Train)(Object)this;

        // When waiting for a coupling partner OR already coupled as a slave, freeze
        // elapsedDwellTicks BEFORE MTR's departure check (line 484) so the train never
        // departs independently.  For a waiting train this prevents premature departure;
        // for a coupled slave this prevents startUp() from being called (which would reset
        // elapsedDwellTicks=0, nextStoppingIndex, reversed, doorTarget).
        if (!this.isCurrentlyManual && this.path != null && this.nextStoppingIndex >= 0
                && this.nextStoppingIndex < this.path.size()
                && (this.manualEnchance$waitingForCouple || this.manualEnchance$masterId != 0L)) {
            int dwellTicks = this.path.get(this.nextStoppingIndex).dwellTime * 10;
            int maxDoorMoveTime = Math.min(64, dwellTicks / 2 - 20);
            float doorCloseThreshold = (float)(dwellTicks - 20) - maxDoorMoveTime;
            if (this.elapsedDwellTicks >= doorCloseThreshold) {
                this.elapsedDwellTicks = doorCloseThreshold - 1;
            }
        }

        // Initialize reversed tracking on first tick to avoid false detection on world load.
        if (!this.manualEnchance$reversedInitialized) {
            if (this.manualEnchance$masterId != 0L) {
                Train initMaster = manualEnchance$findTrainById(world, this.manualEnchance$masterId);
                if (initMaster != null) this.manualEnchance$lastReversed = initMaster.isReversed();
            } else {
                this.manualEnchance$lastReversed = this.reversed;
            }
            this.manualEnchance$reversedInitialized = true;
        }

        if (this.manualEnchance$masterId != 0L) {
            // Slave: sync position/speed/direction from master EARLY
            // so MTR's intermediate simulation sees correct values
            Train master = manualEnchance$findTrainById(world, this.manualEnchance$masterId);
            if (master != null) {
                TrainAccessor masterAcc = (TrainAccessor) master;
                // Sync all state from master: position, speed, direction, and controls.
                // The slave NEVER pushes its own controls to master — doing so would allow
                // driving from the slave cab and would overwrite master's correct manualNotch
                // with MTR-modified slave values (e.g. -2 from door open).
                this.manualNotch = masterAcc.getManualNotch();
                this.doorValue = masterAcc.manualEnchance$getDoorValue();
                this.doorTarget = masterAcc.manualEnchance$getDoorTarget();
                this.pantographState = masterAcc.getPantographState();
                this.reverser = masterAcc.getReverser();
                boolean sameRoute = (self instanceof TrainServer selfServer)
                        && ((TrainServerAccessor) selfServer).getRouteId() == ((TrainServerAccessor) master).getRouteId();
                this.nextManualProgress = master.getRailProgress() - this.manualEnchance$couplingOffset;
                this.nextManualSpeed = master.getSpeed();
                this.reversed = master.isReversed();
                this.reverser = masterAcc.getReverser();
                // Safety: clamp to valid path range to prevent depot teleport.
                double maxP = (this.distances != null && !this.distances.isEmpty())
                        ? this.distances.get(this.distances.size() - 1) : Double.MAX_VALUE;
                double minP = this.trainCars * (double) this.spacing;
                if (maxP > minP && maxP != Double.MAX_VALUE) {
                    if (this.nextManualProgress < minP) this.nextManualProgress = minP;
                    if (this.nextManualProgress > maxP) this.nextManualProgress = maxP;
                }
                this.railProgress = this.nextManualProgress;
                this.speed = this.nextManualSpeed;
                this.isOnRoute = masterAcc.manualEnchance$isOnRoute();
                // Only mirror nextStoppingIndex on the SAME route; cross-route slaves keep their
                // own route's station indices (their position is master-locked anyway).
                if (sameRoute) {
                    this.nextStoppingIndex = masterAcc.getNextStoppingIndex();
                }
                this.isCurrentlyManual = masterAcc.getIsCurrentlyManual();
                this.doorTarget = masterAcc.manualEnchance$getDoorTarget();
            } else {
                // Client-side: RailwayData.getInstance() returns null (server-only), so
                // findTrainById cannot locate the master. We MUST preserve the current
                // railProgress here because redirectRailProgressPut will set
                // railProgress = nextManualProgress. Without this, nextManualProgress stays
                // at its default 0.0 and the slave teleports to the start of its path every tick.
                // The server-synced railProgress (from the packet) is correct; just keep it.
                this.nextManualProgress = this.railProgress;
                this.nextManualSpeed = this.speed;
            }
            // Prevent MTR from auto-switching the slave away from the master's mode
            if (self instanceof TrainServer selfServer) {
                ((TrainServerAccessor) selfServer).setManualCoolDown(0);
            }
            return;
        }

        if (this.isOnRoute && world instanceof ServerLevel serverLevel) {
            manualEnchance$checkRouteCoupling(serverLevel, depot);
        }

        // Auto-coupling: make the SECOND arrival decelerate to the coupled position so it stops
        // bumper-to-bumper behind the waiting master (true deceleration stop, not a post-stop teleport).
        manualEnchance$updateCoupleStopOverride(world, depot);

        // ★ 総括制御ロジック (手動運転時の逆伝播) — マスター列車のみ実行
        if (this.isCurrentlyManual) {
            Train leader = manualEnchance$findLeaderTrain(world);
            if (leader != null && leader != self) {
                TrainAccessor leaderAcc = (TrainAccessor) leader;
                if (this.manualNotch != leaderAcc.getManualNotch()) leaderAcc.setManualNotchDirect(this.manualNotch);
                if (this.reverser != leaderAcc.getReverser()) leaderAcc.setReverser(this.reverser);
                if (Math.abs(this.doorValue - leaderAcc.manualEnchance$getDoorValue()) > 0.01f) leaderAcc.setDoorValue(this.doorValue);
                if (this.pantographState != leaderAcc.getPantographState()) leaderAcc.setPantographState(this.pantographState);
            }
        }

        // --- 自動運転: MTRの物理に完全委任 ---
        if (!this.isCurrentlyManual) {
            this.nextManualSpeed = this.speed;
            this.nextManualProgress = this.railProgress;
            this.lastFixedProgress = this.railProgress;
            // ★ Do NOT force reverser=1 here. MTR controls reverser for auto turnback;
            // forcing it to 1 prevents MTR from setting reverser=0 (neutral) at terminals,
            // which is how MTR triggers the turnback mechanism.
            this.manualEnchance$bcPressure = 0.0f;
            return;
        }

        // ★ Prevent MTR from auto-switching manual→auto after manualToAutomaticTime
        if (self instanceof TrainServer selfServer) {
            ((TrainServerAccessor) selfServer).setManualCoolDown(0);
        }

        this.isInsideDepot = (depot != null);
        if (this.isInsideDepot && !this.hasLeftDepot) {
            if (this.manualNotch > 0) {
                this.hasLeftDepot = true;
                this.lastFixedProgress = this.railProgress;
            } else {
                this.nextManualSpeed = this.speed;
                this.nextManualProgress = this.railProgress;
                return;
            }
        }

        if (this.lastFixedProgress < 0) this.lastFixedProgress = this.railProgress;

        // --- BC pressure dynamics (手動運転時のみ) ---
        float bcTarget;
        if (this.manualNotch >= 0) {
            bcTarget = 0.0f;
        } else if (this.manualNotch == -9) {
            bcTarget = 1.0f;
        } else {
            bcTarget = Math.min(1.0f, Math.abs(this.manualNotch) / 8.0f);
        }

        if (this.manualEnchance$bcPressure < bcTarget) {
            this.manualEnchance$bcPressure += 0.4f * ticksElapsed;
            if (this.manualEnchance$bcPressure > bcTarget) this.manualEnchance$bcPressure = bcTarget;
        } else if (this.manualEnchance$bcPressure > bcTarget) {
            this.manualEnchance$bcPressure -= 0.2f * ticksElapsed;
            if (this.manualEnchance$bcPressure < bcTarget) this.manualEnchance$bcPressure = bcTarget;
        }
        if (this.manualEnchance$bcPressure < 0.001f) this.manualEnchance$bcPressure = 0.0f;

        float maxAllowedBPT = this.maxManualSpeed / 2.4f;

        if (ticksElapsed > 0) {
            // Drive force (notch 1-5)
            float driveMultiplier = (this.manualNotch > 0) ? (this.manualNotch / 5.0f) : 0.0f;
            float driveForce = driveMultiplier * this.accelerationConstant * ticksElapsed;
            float weatherModifier = world.isThundering() ? 0.6f : (world.isRaining() ? 0.85f : 1.0f);
            driveForce *= weatherModifier;

            // Brake force (proportional to BC pressure, stronger than power)
            float brakeDecel = this.manualEnchance$bcPressure * 2.0f * this.accelerationConstant * ticksElapsed;

            // Slope gravity — always physical, direction-independent of reverser
            double y1 = getYAt(this.railProgress);
            double y2 = getYAt(this.railProgress + 0.1);
            double slope = (y2 - y1) / 0.1;
            float gravityConstant = 0.01f;
            float gravityEffect = (float) (slope * gravityConstant * ticksElapsed);

            // Rolling resistance (extremely low)
            float friction = 0.00002f * ticksElapsed;
            // Air drag (speed-squared)
            float airDrag = this.speed * this.speed * 0.000003f * ticksElapsed;

            // Net force on speed magnitude
            // gravity flips sign based on reverser:
            //   reverser=1 (forward): uphill (slope>0) slows down
            //   reverser=-1 (backward): uphill-forward = downhill-backward, speeds up
            float netForce = driveForce - brakeDecel - gravityEffect * this.reverser - friction - airDrag;

            this.nextManualSpeed = Math.max(0.0F, this.speed + netForce);
            if (this.nextManualSpeed > maxAllowedBPT) this.nextManualSpeed = maxAllowedBPT;
        }

        double moveDelta = this.nextManualSpeed * ticksElapsed;
        this.nextManualProgress = this.lastFixedProgress + (this.reverser == -1 ? -moveDelta : moveDelta);

        // ★ Residual pressure stop penalty — abrupt stop if BC > 95% at very low speed
        if (this.nextManualSpeed < 0.01f) {
            if (this.manualEnchance$bcPressure > 0.95f) {
                this.nextManualSpeed = 0.0f;
                this.speed = 0.0f;
                this.manualEnchance$abruptStop = true;
            }
        } else {
            this.manualEnchance$abruptStop = false;
        }

        // 折り返しノードの処理など
        int currentIndex = self.getIndex(this.railProgress, false);
        if (currentIndex >= 0 && currentIndex < path.size() - 1) {
            double nodeProgress = distances.get(currentIndex);
            if (path.get(currentIndex + 1).isOppositeRail(path.get(currentIndex))) {
                // 折り返し検知: (a) ノードをこのティックで通過した、または
                // (b) ノードの直前で停止した（プラットフォームがノードと一致する終端駅など）。
                // 従来は (a) のみだったが、列車はノードちょうどで止まるため (a) が発火せず、
                // 折り返さずにそのまま止まり続けていた。
                boolean crossing = this.lastFixedProgress <= nodeProgress && this.nextManualProgress > nodeProgress;
                boolean stoppedAtNode = this.nextManualSpeed < 0.1F
                        && this.railProgress >= nodeProgress - 3.0
                        && this.nextManualProgress >= nodeProgress - 3.0;
                // Track if doors were opened at the terminal (turnback only fires after
                // the player opens doors and then closes them, not immediately on arrival).
                if (stoppedAtNode && this.doorTarget) {
                    this.manualEnchance$doorsOpenedAtTerminal = true;
                }
                boolean canTurnBack = stoppedAtNode && this.manualEnchance$doorsOpenedAtTerminal;
                if (crossing || canTurnBack) {
                    this.nextManualSpeed = 0;
                    this.speed = 0;
                    this.manualNotch = 0;
                    this.nextManualProgress = nodeProgress;
                    // 折り返し: master/slave を入れ替え、編成を再配置する。
                    // リバーサーは 1（前進）のまま変えず、reversed も切り替えない（折り返しは
                    // ルートに組み込まれているため、ルートに沿って前進するだけ）。
                    // 手動運転: 自前で位置を再配置 (reposition=true)。自動運転は別ブロックで処理。
                    if (world instanceof ServerLevel serverLevel && self instanceof TrainServer selfServer) {
                        manualEnchance$tryTurnBackCouplingChain(world, serverLevel, self, selfServer, true, true);
                        // ★ reposition 後に nextManualProgress/lastFixedProgress を更新する。
                        // しないと redirectRailProgressPut が railProgress を nodeProgress に
                        // 上書きして再配置が無効化され、列車がチカチカする。
                        this.nextManualProgress = this.railProgress;
                        this.lastFixedProgress = this.railProgress;
                        this.manualEnchance$doorsOpenedAtTerminal = false;
                    }
                }
            }
        }

        handleBoundaryLogic();
    }

    @Unique
    private double getYAt(double progress) {
        if (this.path == null || this.path.isEmpty()) return 0;
        Train self = (Train)(Object)this;
        int index = self.getIndex(Math.max(0, progress), false);
        if (index >= this.path.size()) index = this.path.size() - 1;
        double offset = (index == 0) ? 0 : this.distances.get(index - 1);
        return this.path.get(index).rail.getPosition(progress - offset).y;
    }

    @Unique
    private void handleBoundaryLogic() {
        double maxProgress = (this.distances != null && !this.distances.isEmpty()) ? this.distances.get(this.distances.size() - 1) : 0;
        if (this.nextManualProgress < 0) {
            this.nextManualProgress = 0;
            this.nextManualSpeed = 0;
        } else if (this.nextManualProgress > maxProgress) {
            this.nextManualProgress = maxProgress;
            this.nextManualSpeed = 0;
        }
    }

    @Unique
    private int manualEnchance$resolveStationIndex(RailwayData data, long routeId, Depot depot) {
        Train self = (Train)(Object)this;
        if (self.path == null || nextStoppingIndex < 0 || nextStoppingIndex >= self.path.size()) return -1;
        PathData pd = self.path.get(nextStoppingIndex);
        int stopIdx = pd.stopIndex;
        if (stopIdx <= 0) return -1;
        int globalIdx = stopIdx - 1;

        if (depot == null) return -1;
        int precedingPlatforms = 0;
        for (long rid : depot.routeIds) {
            if (rid == routeId) break;
            Route r = manualEnchance$findRoute(data, rid);
            if (r != null) precedingPlatforms += r.platformIds.size();
        }
        int stationIdx = globalIdx - precedingPlatforms;

        Route route = manualEnchance$findRoute(data, routeId);
        if (route == null || stationIdx < 0 || stationIdx >= route.platformIds.size()) return -1;
        return stationIdx;
    }

    @Unique
    private void manualEnchance$checkRouteCoupling(ServerLevel serverLevel, Depot depot) {
        Train self = (Train)(Object)this;
        if (this.manualEnchance$masterId != 0L) return;
        if (!isOnRoute) return;
        if (!(self instanceof TrainServer selfServer)) return;

        // Route-based coupling/uncoupling is for AUTO driving only. When the player is driving
        // manually, getting close to the partner must NOT auto-couple — manual coupling is done
        // via the manual command (attempt_coupling). Without this guard, pulling up to a station
        // that has a COUPLE action would couple automatically even in manual control.
        if (this.isCurrentlyManual) return;

        long routeId = ((TrainServerAccessor) selfServer).getRouteId();
        RailwayData data = RailwayData.getInstance(serverLevel);
        if (data == null) return;

        // Guard: path index must be valid
        if (self.path == null || nextStoppingIndex < 0 || nextStoppingIndex >= self.path.size()) return;
        PathData pd = self.path.get(nextStoppingIndex);
        if (pd.stopIndex <= 0) return;

        int stationIdx = manualEnchance$resolveStationIndex(data, routeId, depot);
        if (stationIdx < 0) return;

        // Reset per-stop coupling state whenever the train moves to a different stopping point on
        // its path. This is what lets a round-trip route COUPLE at a station on the outbound leg
        // and UNCOUPLE at the SAME station on the inbound leg (the two visits use different path
        // indices, so they are treated as separate stops). Coupling/uncoupling is only ever
        // performed when the train is stopped at the station (speed == 0) — never while running.
        if (this.nextStoppingIndex != manualEnchance$lastCouplingPathIndex) {
            manualEnchance$lastCouplingPathIndex = this.nextStoppingIndex;
            manualEnchance$routeCouplingDone = false;
            manualEnchance$lastRouteCouplingStation = -1;
            this.manualEnchance$coupleWaitTicks = 0;
        }

        RouteCouplingStore.RouteCouplingAction action = RouteCouplingStore.getAction(routeId, stationIdx);
        boolean doCouple = action != null && action.doCouple;
        boolean doUncouple = action != null && action.doUncouple;
        if (!doCouple && !doUncouple) {
            manualEnchance$lastRouteCouplingStation = stationIdx;
            manualEnchance$routeCouplingDone = false;
            this.manualEnchance$waitingForCouple = false;
            this.manualEnchance$coupleWaitTicks = 0;
            return;
        }

        // Skip if we already processed this exact stop (avoids re-triggering every tick while dwelling).
        if (manualEnchance$lastRouteCouplingStation == stationIdx && manualEnchance$routeCouplingDone) return;

        long selfMasterId = ((TrainAccessor) self).manualEnchance$getMasterId();

        // --- UNCOUPLE (only meaningful when already coupled) ---
        // Priority over COUPLE: if the train is part of a coupled chain at this stop, detach it.
        // The stop is then "consumed" so it will not immediately re-couple here.
        // MUST be stopped at the station (speed == 0, doors open) — never uncouple while running.
        if (doUncouple) {
            if (this.elapsedDwellTicks <= 0 || Math.abs(this.speed) > 0.05F) {
                // Still approaching / dwelling with doors not yet open / not at rest: wait.
                return;
            }
            if (selfMasterId != 0L) {
                // self is a slave: detach itself from its master (its own subtree stays attached,
                // self simply becomes the new independent master of that subtree).
                CouplingManager.uncouple(data, self.id, serverLevel, Main.COUPLING_SYNC_S2C_PACKET_ID);
                manualEnchance$lastRouteCouplingStation = stationIdx;
                manualEnchance$routeCouplingDone = true;
                return;
            }
            // self is the master: detach the subtree only if it actually has slaves.
            if (manualEnchance$chainHasSlaves(self.id)) {
                int splitIdx = Math.max(1, action.splitTrainIndex);
                long rootId = self.id;
                if (splitIdx > 1) {
                    long cur = self.id;
                    long target = -1L;
                    java.util.Map<Long, CouplingInfo> cmap = CouplingManager.getCouplingMap();
                    for (int i = 0; i < splitIdx; i++) {
                        target = -1L;
                        for (java.util.Map.Entry<Long, CouplingInfo> entry : cmap.entrySet()) {
                            if (entry.getValue().masterId == cur) {
                                target = entry.getKey();
                                break;
                            }
                        }
                        if (target == -1L) break;
                        cur = target;
                    }
                    rootId = target;
                }
                if (rootId != -1L) {
                    manualEnchance$uncoupleSubtree(data, serverLevel, rootId);
                }
                manualEnchance$lastRouteCouplingStation = stationIdx;
                manualEnchance$routeCouplingDone = true;
                return;
            }
            // Not coupled and nothing to detach. If this stop is a COUPLE+UNCOUPLE (BOTH) stop on a
            // round-trip, then "not coupled yet" means we are on the OUTBOUND visit — fall through
            // to the COUPLE logic below instead of consuming the stop. For a pure-UNCOUPLE stop with
            // nothing to detach, consume the stop so we don't try to couple here.
            if (doCouple) {
                // fall through to COUPLE branch
            } else {
                manualEnchance$lastRouteCouplingStation = stationIdx;
                manualEnchance$routeCouplingDone = true;
                return;
            }
        }

        // --- COUPLE ---
        // Only when this train is not already part of a chain, and BOTH trains are stopped at the
        // station (speed == 0) at a location that matches (same nextStoppingIndex + COUPLE action).
        if (doCouple && selfMasterId == 0L) {
            if (elapsedDwellTicks <= 0) {
                manualEnchance$routeCouplingDone = false;
                // Set waitingForCouple so the HEAD cap fires on this tick, preventing MTR from
                // advancing elapsedDwellTicks past the door-close threshold before we can check
                // for a coupling partner.
                this.manualEnchance$waitingForCouple = true;
                return;
            }
            if (manualEnchance$lastRouteCouplingStation == stationIdx && manualEnchance$routeCouplingDone) return;

            TrainServer target = null;
            if (action.targetSidingId != 0L) {
                target = manualEnchance$findCoupleTarget(data, action.targetSidingId, routeId);
            }
            if (target == null) {
                target = manualEnchance$findStationCoupleTarget(data, routeId, stationIdx, nextStoppingIndex, self.id);
            }

            if (target != null && target.id != self.id) {
                TrainAccessor selfAcc = (TrainAccessor) self;
                TrainAccessor targetAcc = (TrainAccessor) target;

                // Don't auto-couple to a train that is being driven manually — manual coupling
                // is done via the V-key command, not by the auto route-coupling system.
                if (targetAcc.getIsCurrentlyManual()) return;

                long selfMaster = selfAcc.manualEnchance$getMasterId();
                long targetMaster = targetAcc.manualEnchance$getMasterId();

                // Already coupled to this target (the other train initiated it this tick)
                if (selfMaster == target.id || targetMaster == self.id) {
                    manualEnchance$lastRouteCouplingStation = stationIdx;
                    manualEnchance$routeCouplingDone = true;
                    return;
                }

                // Only couple once BOTH trains are actually dwelling (stopped at the station with
                // doors open) AND at rest. This prevents "coupled while running / not at a station".
                // Coupling is only allowed on the section where the routes physically share track,
                // which here means both trains are stopped together at the same station.
                if (this.elapsedDwellTicks <= 0) {
                    this.manualEnchance$waitingForCouple = true;
                    return;
                }
                if (Math.abs(this.speed) > 0.05F || Math.abs(target.getSpeed()) > 0.05F) {
                    this.manualEnchance$waitingForCouple = true;
                    return;
                }

                if (selfMaster == 0L && targetMaster == 0L) {
                    // Master = the train that arrived FIRST (already waiting), otherwise the train
                    // physically in FRONT (higher railProgress in travel direction). This keeps the
                    // coupling geometrically correct (slave behind master) for station AND siding pickups.
                    TrainServer slaveT = target;
                    TrainServer masterT = (TrainServer) self;
                    boolean targetWaiting = targetAcc.manualEnchance$isWaitingForCouple();
                    boolean selfWaiting = selfAcc.manualEnchance$isWaitingForCouple();
                    if (targetWaiting && !selfWaiting) {
                        slaveT = (TrainServer) self;
                        masterT = target;
                    } else if (selfWaiting && !targetWaiting) {
                        slaveT = target;
                        masterT = (TrainServer) self;
                    } else if (manualEnchance$isBehind(selfAcc, targetAcc)) {
                        slaveT = (TrainServer) self;
                        masterT = target;
                    }

                    // At a shared station both trains stop at their normal platform positions, which
                    // leaves a gap too large for applyNaturalCoupling's proximity gate.
                    // Do NOT teleport the slave — that skips the natural coupling process.
                    // Instead, the second arrival should decelerate to the coupled position via
                    // updateCoupleStopOverride before it stops. If the gap is still too large
                    // (e.g. both already stopped), just wait — the trains will couple when close
                    // enough, or give up after coupleWaitTicks timeout.

                    boolean coupled = CouplingManager.applyNaturalCoupling(
                            slaveT, masterT,
                            CouplingManager.ConnectionType.SLAVE_FRONT_TO_MASTER_REAR,
                            serverLevel, Main.COUPLING_SYNC_S2C_PACKET_ID
                    );
                    if (coupled) {
                        this.manualEnchance$waitingForCouple = false;
                        targetAcc.manualEnchance$setWaitingForCouple(false);
                        manualEnchance$coupleWaitTicks = 0;
                        manualEnchance$lastRouteCouplingStation = stationIdx;
                        manualEnchance$routeCouplingDone = true;
                        return;
                    }
                }
            }

            // No target / coupling failed → cap elapsedDwellTicks just below the door-close
            // threshold so doors stay open for passengers, but the train never departs.
            // MTR opens doors when elapsedDwellTicks >= 20.0f, closes when >= dwellTicks-20-maxDoorMoveTime.
            this.manualEnchance$coupleWaitTicks++;
            if (this.manualEnchance$coupleWaitTicks > 1200) {
                LOGGER.info("[checkRouteCoupling] giving up waiting to couple at station {} (train {})", stationIdx, self.id);
                manualEnchance$coupleWaitTicks = 0;
                manualEnchance$routeCouplingDone = true;
                this.manualEnchance$waitingForCouple = false;
                return;
            }
            if (this.nextStoppingIndex >= 0 && this.nextStoppingIndex < this.path.size()) {
                int dwellTicks = this.path.get(this.nextStoppingIndex).dwellTime * 10;
                int maxDoorMoveTime = Math.min(64, dwellTicks / 2 - 20);
                float doorCloseThreshold = (float)(dwellTicks - 20) - maxDoorMoveTime;
                if (this.elapsedDwellTicks < doorCloseThreshold) {
                    // Doors are open and will stay open — let the timer advance naturally.
                } else {
                    // Doors would close or train would depart — freeze just below threshold.
                    this.elapsedDwellTicks = doorCloseThreshold - 1;
                }
            }
            this.manualEnchance$waitingForCouple = true;
            return;
        }
    }

    // When an auto train is the second arrival at a coupling station, MTR's deceleration target
    // (distances.get(nextStoppingIndex) - railProgress) is replaced with the coupled stop position
    // so it slows down and stops bumper-to-bumper behind the waiting master. Manual driving is
    // never affected (coupleStopActive is only set for auto trains in updateCoupleStopOverride).
    @ModifyVariable(method = "simulateTrain", at = @At(value = "STORE", ordinal = 0), name = "stoppingDistance", require = 0)
    private double manualEnchance$modifyStoppingDistance(double original) {
        if (manualEnchance$coupleStopActive) {
            return manualEnchance$coupleStopTarget - this.railProgress;
        }
        return original;
    }

    @Unique
    private void manualEnchance$updateCoupleStopOverride(Level world, Depot depot) {
        manualEnchance$coupleStopActive = false;
        Train self = (Train)(Object)this;
        // Manual driving is excluded by design — only AUTO trains pre-decelerate to the coupled stop.
        if (this.isCurrentlyManual) return;
        // Already part of a chain → follows master, no override needed.
        if (this.manualEnchance$masterId != 0L) return;
        if (!this.isOnRoute) return;
        if (this.path == null || this.nextStoppingIndex < 0 || this.nextStoppingIndex >= this.path.size()) return;

        double naturalStop = this.distances.get(this.nextStoppingIndex);
        double distToStop = naturalStop - this.railProgress;
        // Only engage while approaching the station (not when already past / far away).
        // Note: the coupled stop (adj) is earlier than naturalStop by the master's length,
        // so distToStop > 0 means we haven't reached the coupled stop yet.
        if (distToStop <= 0.0 || distToStop > 300.0) return;

        if (!(world instanceof ServerLevel serverLevel)) return;
        RailwayData data = RailwayData.getInstance(serverLevel);
        if (data == null) return;
        if (!(self instanceof TrainServer selfServer)) return;
        long routeId = ((TrainServerAccessor) selfServer).getRouteId();
        if (routeId == 0L) return;

        int stationIdx = manualEnchance$resolveStationIndex(data, routeId, depot);
        if (stationIdx < 0) return;
        RouteCouplingStore.RouteCouplingAction act = RouteCouplingStore.getAction(routeId, stationIdx);
        if (act == null || !act.doCouple) return;
        // If coupling was already attempted and gave up at this stop, don't keep holding the train
        // at the coupled position — let it proceed to its natural platform.
        if (manualEnchance$routeCouplingDone) return;

        // Partner already stopped at this same station stop = the future master.
        // Mirror the same lookup order as checkRouteCoupling: siding-based first, then path-index.
        TrainServer partner = null;
        if (act.targetSidingId != 0L) {
            partner = manualEnchance$findCoupleTarget(data, act.targetSidingId, routeId);
            // Filter out self
            if (partner != null && partner.id == self.id) partner = null;
        }
        if (partner == null) {
            partner = manualEnchance$findStationCoupleTarget(data, routeId, stationIdx, this.nextStoppingIndex, self.id);
        }
        if (partner == null) return;
        // Removed partner.getSpeed() > 0.1 check: the second train must start decelerating to
        // the coupled stop position even while the master is still approaching its natural stop.
        // The adj calculation uses the station position (not the partner's current position), so
        // it's valid regardless of the partner's current speed.
        if (((TrainAccessor) partner).manualEnchance$getMasterId() != 0L) return; // partner is itself a slave
        if (((TrainAccessor) partner).getIsCurrentlyManual()) return; // partner is driven manually

        // Only the physically-rear train (second arrival) should decelerate to the coupled stop.
        // The front train (first arrival / master) must keep its default stop position.
        // Using geometric isBehind() instead of timing: works regardless of arrival order,
        // because it uses actual coupler positions rather than dwell-tick state.
        if (!manualEnchance$isBehind((TrainAccessor) self, (TrainAccessor) partner)) return;

        // Coupled stop = natural platform position minus the master's length, in THIS train's own
        // railProgress frame. Because the shared track has a single consistent scale, shifting by the
        // master's length places this train's front coupler at the master's rear coupler. This is a
        // LOCAL adjustment (never a global geometric scan), so it cannot teleport the train to a
        // distant point on a round-trip path.
        double masterLength = partner.trainCars * (double) partner.spacing;
        double adj = naturalStop - masterLength;
        // Safety: only stop earlier than the natural platform, within the path, and not behind us.
        if (adj >= naturalStop || adj < 0.0) return;
        // If we've already passed the coupled stop position, don't engage override —
        // it's too late to decelerate to it (the distToStop check above used naturalStop,
        // but the actual target is adj which is earlier).
        if (this.railProgress >= adj) return;
        manualEnchance$coupleStopActive = true;
        manualEnchance$coupleStopTarget = adj;
    }

    @Unique
    private TrainServer manualEnchance$findStationCoupleTarget(RailwayData data, long routeId, int stationIdx, int targetNextStop, long excludeId) {
        RouteCouplingStore.RouteCouplingAction act = RouteCouplingStore.getAction(routeId, stationIdx);
        if (act == null || !act.doCouple) return null;
        for (Siding siding : data.sidings) {
            for (TrainServer ts : ((SidingAccessor) siding).getTrains()) {
                if (ts.id == excludeId) continue;
                // Skip already-coupled trains — they are following their master and
                // must not be selected as a coupling target.
                if (((TrainAccessor) ts).manualEnchance$getMasterId() != 0L) continue;
                int otherNextStop = ((TrainAccessor) ts).getNextStoppingIndex();
                if (otherNextStop != targetNextStop) continue;
                return ts;
            }
        }
        return null;
    }

    @Unique
    private void manualEnchance$uncoupleSubtree(RailwayData data, ServerLevel level, long rootId) {
        java.util.List<Long> toUncouple = new java.util.ArrayList<>();
        manualEnchance$collectSlaves(rootId, toUncouple);
        for (Long sid : toUncouple) {
            CouplingManager.uncouple(data, sid, level, Main.COUPLING_SYNC_S2C_PACKET_ID);
        }
    }

    @Unique
    private void manualEnchance$collectSlaves(long masterId, java.util.List<Long> out) {
        manualEnchance$collectSlaves(masterId, out, new java.util.HashSet<>());
    }

    @Unique
    private void manualEnchance$collectSlaves(long masterId, java.util.List<Long> out, java.util.Set<Long> visited) {
        if (!visited.add(masterId)) return;
        for (java.util.Map.Entry<Long, CouplingInfo> e : CouplingManager.getCouplingMap().entrySet()) {
            if (e.getValue().masterId == masterId) {
                out.add(e.getKey());
                manualEnchance$collectSlaves(e.getKey(), out, visited);
            }
        }
    }

    @Unique
    private boolean manualEnchance$chainHasSlaves(long masterId) {
        for (java.util.Map.Entry<Long, CouplingInfo> e : CouplingManager.getCouplingMap().entrySet()) {
            if (e.getValue().masterId == masterId) return true;
        }
        return false;
    }

    // Swap master/slave roles of a coupled chain when it reaches a turnback node, so the same
    // vehicles keep running in the new direction with the leading vehicle now becoming the rear
    // (and vice versa). Runs for BOTH manual and auto driving. The `force` flag (manual only) lets
    // the caller decide the trigger; for auto the trigger is "stopped at the node". A per-chain
    // guard (turnBackDone) stops it re-firing every dwell tick.
    @Unique
    private void manualEnchance$tryTurnBackCouplingChain(Level world, ServerLevel serverLevel, Train self, TrainServer selfServer, boolean reposition, boolean force) {
        if (this.manualEnchance$masterId == 0L && !manualEnchance$chainHasSlaves(self.id)) return;
        if (self.path == null || self.path.isEmpty()) return;
        int curIdx = self.getIndex(this.railProgress, false);
        if (curIdx < 0 || curIdx >= self.path.size() - 1) return;
        double nodeP = this.distances.get(curIdx);
        if (!self.path.get(curIdx + 1).isOppositeRail(self.path.get(curIdx))) return;

        double distToNode = Math.abs(this.railProgress - nodeP);
        // Left the node well behind → allow the next turnback to fire.
        if (distToNode > 20.0) {
            this.manualEnchance$turnBackDone = false;
            return;
        }
        if (this.manualEnchance$turnBackDone) return;

        boolean trigger;
        if (force) {
            trigger = true;
        } else {
            // Auto: fire once the chain is stopped at the turnback node (the terminal platform).
            trigger = this.speed < 0.1F && distToNode < 12.0;
        }
        if (!trigger) return;

        RailwayData data = RailwayData.getInstance(world);
        if (data == null) return;
        LOGGER.info("[TurnBack] swapping coupling chain for master={} (reposition={}, force={})", self.id, reposition, force);
        // Mark the whole chain so the swapped-in master does not immediately re-fire.
        java.util.List<Long> chain = new java.util.ArrayList<>();
        chain.add(self.id);
        manualEnchance$collectSlaves(self.id, chain);
        CouplingManager.turnBackCouplingChain(data, selfServer, serverLevel, Main.COUPLING_SYNC_S2C_PACKET_ID, reposition);
        for (Long id : chain) {
            Train t = manualEnchance$findTrainById(world, id);
            if (t != null) ((TrainAccessor) t).manualEnchance$setTurnBackDone(true);
        }
    }

    @Unique
    private Route manualEnchance$findRoute(RailwayData data, long routeId) {
        for (Route route : data.routes) {
            if (route.id == routeId) return route;
        }
        return null;
    }

    @Unique
    private TrainServer manualEnchance$findCoupleTarget(RailwayData data, long sidingId, long routeId) {
        for (Siding siding : data.sidings) {
            if (siding.id == sidingId) {
                for (TrainServer trainServer : ((SidingAccessor) siding).getTrains()) {
                    return trainServer;
                }
            }
        }
        return null;
    }

    @Unique
    private boolean manualEnchance$isBehind(TrainAccessor selfAcc, TrainAccessor targetAcc) {
        // Determine front/back by world coordinates projected onto the travel direction
        // (front coupler - rear coupler), which is route-independent and correct for
        // cross-route coupling where railProgress origins differ between routes.
        Vec3 selfFront = selfAcc.manualEnchance$getCouplerFrontPos();
        Vec3 selfRear = selfAcc.manualEnchance$getCouplerRearPos();
        Vec3 targetFront = targetAcc.manualEnchance$getCouplerFrontPos();
        Vec3 targetRear = targetAcc.manualEnchance$getCouplerRearPos();

        Vec3 selfCenter = selfFront.add(selfRear).scale(0.5);
        Vec3 targetCenter = targetFront.add(targetRear).scale(0.5);

        Vec3 forward = selfFront.subtract(selfRear);
        double len = forward.length();
        if (len < 1e-3) {
            // Degenerate geometry: fall back to railProgress sign
            double selfProgress = selfAcc.manualEnchance$getRailProgress();
            double targetProgress = targetAcc.manualEnchance$getRailProgress();
            return this.reversed ? (selfProgress > targetProgress) : (selfProgress < targetProgress);
        }
        forward = forward.scale(1.0 / len);

        double dot = targetCenter.subtract(selfCenter).dot(forward);
        // dot > 0 → target is ahead of self → self is behind
        return dot > 0;
    }

    @Unique
    double manualEnchance$findAdjacentProgress(Train slave, Train master, Level world) {
        TrainAccessor slaveAcc = (TrainAccessor) slave;
        TrainAccessor masterAcc = (TrainAccessor) master;
        // Master's rear coupler world position; we want the slave's FRONT coupler there
        // (SLAVE_FRONT_TO_MASTER_REAR), placing the slave directly behind the master.
        Vec3 masterRear = masterAcc.manualEnchance$getCouplerRearPos();
        if (masterRear.equals(Vec3.ZERO)) return slaveAcc.manualEnchance$getRailProgress();
        List<Double> dists = slaveAcc.manualEnchance$getDistances();
        if (dists == null || dists.isEmpty()) return slaveAcc.manualEnchance$getRailProgress();
        double maxDistance = dists.get(dists.size() - 1);
        if (maxDistance <= 0) return slaveAcc.manualEnchance$getRailProgress();
        int steps = 240;

        // Only search a WINDOW around the slave's CURRENT position. A full-path scan would pick the
        // global geometric minimum, which on a round-trip (shared track in both directions) can be a
        // distant point on the return leg and teleport the train there. The correct coupled position
        // is always near the train's current (station) position, so a ±250 window is more than enough.
        double current = slaveAcc.manualEnchance$getRailProgress();
        double lo = Math.max(0.0, current - 250.0);
        double hi = Math.min(maxDistance, current + 250.0);
        double step = (hi - lo) / steps;
        double bestP = slaveAcc.manualEnchance$getRailProgress();
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i <= steps; i++) {
            double p = lo + step * i;
            double saved = slaveAcc.manualEnchance$getRailProgress();
            slaveAcc.setRailProgress(p);
            Vec3 front = slaveAcc.manualEnchance$getCouplerFrontPos();
            slaveAcc.setRailProgress(saved);
            double d = front.distanceTo(masterRear);
            if (d < bestDist) { bestDist = d; bestP = p; }
        }

        // Pass 1: refine around the coarse best for accuracy
        double lo2 = Math.max(0.0, bestP - step);
        double hi2 = Math.min(maxDistance, bestP + step);
        double step2 = (hi2 - lo2) / steps;
        double bestP2 = bestP;
        double bestDist2 = bestDist;
        for (int i = 0; i <= steps; i++) {
            double p = lo2 + step2 * i;
            double saved = slaveAcc.manualEnchance$getRailProgress();
            slaveAcc.setRailProgress(p);
            Vec3 front = slaveAcc.manualEnchance$getCouplerFrontPos();
            slaveAcc.setRailProgress(saved);
            double d = front.distanceTo(masterRear);
            if (d < bestDist2) { bestDist2 = d; bestP2 = p; }
        }

        LOGGER.debug("[findAdjacentProgress] slave={} master={} bestDist={} targetProgress={} (from {})",
                slave.id, master.id, String.format("%.2f", bestDist2),
                String.format("%.2f", bestP2), String.format("%.2f", slaveAcc.manualEnchance$getRailProgress()));
        return bestP2;
    }

    @Unique
    private double getNotchMultiplier(int notch) {
        return switch (notch) {
            case 5 -> 1.0; case 4 -> 0.8; case 3 -> 0.6; case 2 -> 0.4; case 1 -> 0.2; case 0 -> 0.0;
            case -1 -> -0.1428; case -2 -> -0.2857; case -3 -> -0.4285; case -4 -> -0.5714;
            case -5 -> -0.7142; case -6 -> -0.8571; case -7 -> -1.0; case -8 -> -1.25; case -9 -> -2.0;
            default -> 0.0;
        };
    }

    @Redirect(method = "simulateTrain", at = @At(value = "FIELD", target = "Lmtr/data/Train;speed:F", opcode = 181))
    private void redirectSpeedPut(Train instance, float newValue) {
        if (this.manualEnchance$masterId != 0L) {
            if (this.manualEnchance$cachedWorld != null && this.manualEnchance$cachedWorld.isClientSide()) {
                // Client: keep the server-synced speed (from packet, same reason as railProgress).
                return;
            }
            this.speed = this.nextManualSpeed;
            return;
        }
        if (this.isCurrentlyManual) {
            this.speed = this.nextManualSpeed;
        } else {
            // When coupleStopActive and railProgress has reached the target, force full stop
            // so the train doesn't coast past the coupled position at 0.01 blocks/tick.
            if (manualEnchance$coupleStopActive && this.railProgress >= manualEnchance$coupleStopTarget - 0.1) {
                this.speed = 0.0f;
            } else {
                this.speed = newValue;
            }
        }
    }

    @Redirect(method = "simulateTrain", at = @At(value = "FIELD", target = "Lmtr/data/Train;railProgress:D", opcode = 181))
    private void redirectRailProgressPut(Train instance, double newValue) {
        if (this.manualEnchance$masterId != 0L) {
            if (this.manualEnchance$cachedWorld != null && this.manualEnchance$cachedWorld.isClientSide()) {
                // Client: the server-synced railProgress (from the packet received before
                // simulateTrain) is already correct. MTR's simulation doesn't know about
                // coupling and would overwrite it with wrong values. By returning without
                // writing, we keep the packet value. Note: we CANNOT use nextManualProgress
                // here because it is set in forceFinalSpeed (TAIL) which runs AFTER this
                // redirect — it would always be one tick behind.
                this.lastFixedProgress = this.railProgress;
                return;
            }
            // Server: forceFinalSpeed (TAIL) sets nextManualProgress from findAdjacentProgress.
            // Use that value (it runs after this redirect in the same tick).
            this.railProgress = this.nextManualProgress;
            this.lastFixedProgress = this.nextManualProgress;
            return;
        }
        if (!this.isCurrentlyManual || (this.isInsideDepot && !this.hasLeftDepot)) {
            // When coupleStopActive, clamp railProgress to the coupled stop target so the train
            // stops bumper-to-bumper behind the master instead of coasting to its natural platform.
            if (manualEnchance$coupleStopActive && newValue > manualEnchance$coupleStopTarget) {
                newValue = manualEnchance$coupleStopTarget;
            }
            this.railProgress = newValue;
            this.lastFixedProgress = newValue;
            return;
        }
        this.railProgress = this.nextManualProgress;
        this.lastFixedProgress = this.nextManualProgress;
    }

    @Inject(method = "simulateTrain", at = @At("TAIL"))
    private void forceFinalSpeed(Level world, float ticksElapsed, Depot depot, CallbackInfo ci) {
        // Track isOnRoute false→true transitions for non-slave trains (before the else-if chain
        // so it runs even when the slave branch or manual branch is entered).
        if (this.manualEnchance$masterId == 0L) {
            if (!this.isOnRoute) {
                this.manualEnchance$wasOffRoute = true;
            }
        }

        if (this.manualEnchance$masterId != 0L) {
            if (!(world instanceof ServerLevel)) return;

            Train self = (Train)(Object)this;
            Train frontTrain = manualEnchance$findTrainById(world, this.manualEnchance$masterId);

            if (self instanceof TrainServer selfServer) {
                ((TrainServerAccessor) selfServer).setManualCoolDown(0);
            }

            if (frontTrain == null) {
                // ★ マスター消失 → 自動連結解除
                LOGGER.warn("[forceFinalSpeed] master {} not found for slave {}, auto-uncoupling",
                        this.manualEnchance$masterId, self.id);
                if (world instanceof ServerLevel serverLevel) {
                    RailwayData data = RailwayData.getInstance(world);
                    if (data != null) {
                        CouplingManager.uncouple(data, self.id, serverLevel, Main.COUPLING_SYNC_S2C_PACKET_ID);
                    }
                }
                return;
            }

            TrainAccessor frontAcc = (TrainAccessor) frontTrain;

            // マスターの「実際の位置（ワールド座標）」に追従する。
            // かつては slave = master.railProgress - offset によって計算していたが、
            // ・ルート起点付近で offset が master.railProgress を超えて負になり、クランプで
            //   車庫(railProgress=0)に飛ばされていた（乗車時に車庫にテレポートする原因）
            // ・異ルート間（railProgress の原点が異なる）や折り返し（master が逆走して
            //   railProgress が減少）ではこの式が成り立たない
            // そのため、slave のパス上で「master の後端連結器に slave の前端連結器が一致する」
            // 位置を毎ティック探す。これはフレーム非依存で、負のクランプも発生しない。
            double targetProgress = manualEnchance$findAdjacentProgress(self, frontTrain, world);
            // Safety: clamp to valid path range to prevent depot teleport
            double slaveMaxP = (this.distances != null && !this.distances.isEmpty())
                    ? this.distances.get(this.distances.size() - 1) : Double.MAX_VALUE;
            double slaveMinP = this.trainCars * (double) this.spacing;
            if (slaveMaxP > slaveMinP && slaveMaxP != Double.MAX_VALUE) {
                if (targetProgress < slaveMinP) targetProgress = slaveMinP;
                if (targetProgress > slaveMaxP) targetProgress = slaveMaxP;
            }

            // ★ Set railProgress FIRST so getCouplerDistance uses corrected position
            this.nextManualProgress = targetProgress;
            this.nextManualSpeed = frontTrain.getSpeed();
            this.reversed = frontTrain.isReversed();

            this.speed = this.nextManualSpeed;
            this.railProgress = this.nextManualProgress;
            this.lastFixedProgress = this.nextManualProgress;

            // Recalculate offset when master reverses at turnback. After MTR reverses the
            // master, the physical arrangement changes and the old offset produces wrong
            // slave positions. Using the actual railProgress difference captures the new
            // arrangement correctly (works for both same-route and cross-route).
            if (frontTrain.isReversed() != this.manualEnchance$lastReversed) {
                double newOffset = frontTrain.getRailProgress() - this.railProgress;
                this.manualEnchance$couplingOffset = newOffset;
                java.util.Map<Long, CouplingInfo> cmap = CouplingManager.getCouplingMap();
                CouplingInfo oldInfo = cmap.get(self.id);
                if (oldInfo != null) {
                    cmap.put(self.id, new CouplingInfo(oldInfo.masterId, newOffset, oldInfo.type));
                }
                if (world instanceof ServerLevel serverLevel) {
                    FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                    buf.writeLong(self.id);
                    buf.writeLong(this.manualEnchance$masterId);
                    buf.writeDouble(newOffset);
                    buf.writeInt(oldInfo != null ? oldInfo.type.ordinal() : 0);
                    CouplingManager.broadcast(serverLevel, Main.COUPLING_SYNC_S2C_PACKET_ID, buf);
                }
                LOGGER.info("[forceFinalSpeed] slave {} offset recalculated: {} → {} (master reversed)",
                        self.id, String.format("%.2f", oldInfo != null ? oldInfo.offset : 0),
                        String.format("%.2f", newOffset));
            }
            this.manualEnchance$lastReversed = frontTrain.isReversed();

            // ★ 3D距離チェック（今は railProgress が既に補正済み）
            CouplingInfo info = CouplingManager.getCouplingMap().get(self.id);
            if (info == null) {
                LOGGER.warn("[forceFinalSpeed] slave {} has no CouplingInfo but has masterId={}, auto-uncoupling",
                        self.id, frontTrain.id);
                if (world instanceof ServerLevel serverLevel) {
                    RailwayData data = RailwayData.getInstance(world);
                    if (data != null) {
                        CouplingManager.uncouple(data, self.id, serverLevel, Main.COUPLING_SYNC_S2C_PACKET_ID);
                    }
                }
                return;
            }
            double dist3d = CouplingManager.getCouplerDistanceMin((TrainAccessor) this, (TrainAccessor) frontTrain);
            if (dist3d > COUPLING_MAX_DIVERGE_DISTANCE) {
                LOGGER.warn("[forceFinalSpeed] slave {} diverged from master {}: dist={} (type={}), auto-uncoupling",
                        self.id, frontTrain.id, String.format("%.2f", dist3d), info.type);
                if (world instanceof ServerLevel serverLevel) {
                    RailwayData data = RailwayData.getInstance(world);
                    if (data != null) {
                        CouplingManager.uncouple(data, self.id, serverLevel, Main.COUPLING_SYNC_S2C_PACKET_ID);
                    }
                }
                return;
            }

            this.doorValue = frontAcc.manualEnchance$getDoorValue();
            this.doorTarget = frontAcc.manualEnchance$getDoorTarget();
            this.pantographState = frontAcc.getPantographState();
            this.reverser = frontAcc.getReverser();
            this.manualNotch = frontAcc.getManualNotch();
            this.manualEnchance$bcPressure = frontAcc.manualEnchance$getBCPressure();
            this.isOnRoute = frontAcc.manualEnchance$isOnRoute();
            // Only mirror nextStoppingIndex on the SAME route; cross-route slaves keep their own
            // route's station indices (their position is master-locked anyway).
            if (!(self instanceof TrainServer selfServer)
                    || ((TrainServerAccessor) selfServer).getRouteId() == ((TrainServerAccessor) frontTrain).getRouteId()) {
                this.nextStoppingIndex = frontAcc.getNextStoppingIndex();
            }
            this.isCurrentlyManual = frontAcc.getIsCurrentlyManual();
        } else if (this.isCurrentlyManual) {
            this.speed = this.nextManualSpeed;
            if (!(this.isInsideDepot && !this.hasLeftDepot)) {
                this.railProgress = this.nextManualProgress;
            }
        } else if (world instanceof ServerLevel) {
            // Auto non-slave: MTR controls speed/progress directly. Nothing to override.
        }

        // AUTO TURNBACK DETECTION — runs for ALL non-slave trains regardless of isCurrentlyManual.
        // After a terminal reset, MTR temporarily sets isCurrentlyManual=true (cooldown), which
        // means the auto branch above is not entered. Moving turnback detection here ensures it
        // fires even during that cooldown window.
        if (this.manualEnchance$masterId == 0L && world instanceof ServerLevel serverLevel && this.isOnRoute) {
            Train self = (Train)(Object)this;

            // wasOffRoute is set at the top of forceFinalSpeed (before the else-if chain) when
            // isOnRoute is false. When isOnRoute transitions back to true, this IS the turnback
            // moment for non-repeat routes where MTR never toggles `reversed`.
            boolean isOnRouteResumed = this.manualEnchance$wasOffRoute;
            if (isOnRouteResumed) {
                this.manualEnchance$wasOffRoute = false;
            }

            boolean reversedChanged = this.reversed != this.manualEnchance$lastReversed;
            // Fallback: detect terminal arrival by path position (nextStoppingIndex near end).
            boolean atTerminal = self.path != null && !self.path.isEmpty()
                    && this.nextStoppingIndex >= self.path.size() - 2
                    && this.speed < 0.1F && this.elapsedDwellTicks > 20;

            // Reset turnBackDone when train leaves terminal so turnback can fire again on return.
            if (this.manualEnchance$turnBackDone && !atTerminal && !reversedChanged && !isOnRouteResumed) {
                this.manualEnchance$turnBackDone = false;
            }

            if ((reversedChanged || atTerminal || isOnRouteResumed)
                    && manualEnchance$chainHasSlaves(self.id)
                    && self.path != null && !self.path.isEmpty()
                    && self instanceof TrainServer selfServer
                    && !this.manualEnchance$turnBackDone) {
                RailwayData data = RailwayData.getInstance(serverLevel);
                if (data != null) {
                    LOGGER.info("[forceFinalSpeed] auto turnback detected for master={}, reversedChanged={}, atTerminal={}, isOnRouteResumed={}, swapping roles",
                            self.id, reversedChanged, atTerminal, isOnRouteResumed);
                    java.util.List<Long> chain = new java.util.ArrayList<>();
                    chain.add(self.id);
                    manualEnchance$collectSlaves(self.id, chain);
                    CouplingManager.turnBackCouplingChain(data, selfServer, serverLevel,
                            Main.COUPLING_SYNC_S2C_PACKET_ID, false);
                    for (Long id : chain) {
                        Train t = manualEnchance$findTrainById(world, id);
                        if (t != null) ((TrainAccessor) t).manualEnchance$setTurnBackDone(true);
                    }
                }
            }
            this.manualEnchance$lastReversed = this.reversed;
        }
    }

    @Redirect(method = "simulateTrain", at = @At(value = "FIELD", target = "Lmtr/data/Train;manualNotch:I", opcode = 181))
    private void blockMtrManualNotchPut(Train instance, int newValue) {
        if (this.manualEnchance$masterId != 0L) return;
        if (!this.isCurrentlyManual || (this.isInsideDepot && !this.hasLeftDepot)) {
            this.manualNotch = newValue;
        }
    }

    @Redirect(method = "simulateTrain", at = @At(value = "FIELD", target = "Lmtr/data/Train;accelerationConstant:F"))
    private float keepAccel(Train instance) { return instance.accelerationConstant; }
}