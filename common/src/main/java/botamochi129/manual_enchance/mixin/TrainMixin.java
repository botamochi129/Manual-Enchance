package botamochi129.manual_enchance.mixin;

import botamochi129.manual_enchance.Main;
import botamochi129.manual_enchance.util.CouplingInfo;
import botamochi129.manual_enchance.util.CouplingManager;
import botamochi129.manual_enchance.util.SidingAccessor;
import botamochi129.manual_enchance.util.TrainAccessor;
import mtr.client.ClientData;
import mtr.data.*;
import mtr.path.PathData;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(value = Train.class, remap = false)
public abstract class TrainMixin implements TrainAccessor {

    @Shadow
    protected int manualNotch;
    @Shadow
    protected float doorValue;
    @Shadow
    protected boolean isCurrentlyManual;
    @Shadow
    protected float speed;
    @Shadow
    public float accelerationConstant;
    @Shadow
    protected double railProgress;
    @Shadow
    protected List<Double> distances;
    @Shadow
    protected int nextStoppingIndex;

    @Shadow @Final public int trainCars;
    @Shadow @Final public int spacing;

    @Shadow protected boolean reversed;

    // 状態
    private float nextManualSpeed = 0.0f;
    private double nextManualProgress = 0.0;
    private boolean isInsideDepot = false;
    private boolean hasLeftDepot = false;
    private double lastFixedProgress = -1;

    // 1:前進(F), 0:中立(N), -1:後進(B)
    private int reverser = 1;

    @Unique
    private int pantographState = 0;

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

    @Override
    public int getManualNotch() {
        return this.manualNotch;
    }

    @Override
    public boolean getIsCurrentlyManual() {
        return this.isCurrentlyManual;
    }

    @Override
    public int getReverser() {
        return this.reverser;
    }

    @Override
    public float manualEnchance$getDoorValue() {
        return this.doorValue;
    }

    @Override
    public void setReverser(int value) {
        this.reverser = value;
    }

    @Override
    public int getNextStoppingIndex() {
        return this.nextStoppingIndex;
    }

    @Override
    public List<Double> manualEnchance$getDistances() {
        return this.distances;
    }

    @Override
    public double manualEnchance$getRailProgress() {
        return this.railProgress;
    }

    @Override
    public int getPantographState() {
        return pantographState;
    }

    @Override
    public void setPantographState(int state) {
        // 0~3の範囲に収める
        this.pantographState = state % 4;
    }

    @Override
    public void setManualNotchDirect(int notch) {
        // 範囲制限をかけて代入
        this.manualNotch = Math.max(-9, Math.min(5, notch));

        // もし加速(P)に入れたなら、出庫フラグを立てる（既存ロジックとの互換性）
        if (this.manualNotch > 0 && this.isInsideDepot) {
            this.hasLeftDepot = true;
        }
    }

    @Override
    public String getHornSoundId() {
        Train self = (Train) (Object) this;
        if (self.trainId == null) return "";

        // Mapをループして、自分の trainId に含まれるキーワードを探す
        for (Map.Entry<String, String> entry : Main.HORN_MAP.entrySet()) {
            if (self.trainId.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "";
    }

    @Override
    public void setRailProgress(double rp) {
        this.railProgress = rp;
    }

    @Override
    public void setSpeed(float sp) {
        this.speed = sp;
    }

    @Inject(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V",
            at = @At("RETURN"),
            remap = false,
            require = 0)
    private void injectReadPacket(FriendlyByteBuf packet, CallbackInfo ci) {
        try {
            this.pantographState = packet.readInt();

            this.manualEnchance$masterId = packet.readLong();
            this.manualEnchance$couplingOffset = packet.readDouble();
        } catch (Exception ignored) {}
    }

    @Inject(method = "writePacket(Lnet/minecraft/network/FriendlyByteBuf;)V",
            at = @At("TAIL"),
            remap = false,
            require = 0)
    private void injectWritePacket(FriendlyByteBuf packet, CallbackInfo ci) {
        try {
            packet.writeInt(this.pantographState);

            packet.writeLong(this.manualEnchance$masterId);
            packet.writeDouble(this.manualEnchance$couplingOffset);
        } catch (Exception ignored) {}
    }
//
//    @Inject(method = "<init>(JFLjava/util/List;Ljava/util/List;IIFZIILjava/util/Map;)V", at = @At("TAIL"))
//    private void injectReadMessagePack(long sidingId, float railLength, List<PathData> path, List<Double> distances, int repeatIndex1, int repeatIndex2, float accelerationConstant, boolean isManualAllowed, int maxManualSpeed, int manualToAutomaticTime, Map<String, Value> map, CallbackInfo ci) {///        Value pantoValue = map.get("manualEnchance$pantoState");
//        if (pantoValue != null && !pantoValue.isNilValue()) {
//            this.pantographState = pantoValue.asIntegerValue().asInt();
//        }
//
//        try {
//            if (map != null && map.containsKey("manualEnchance$pantoState")) {
//                Value pantoValue = map.get("manualEnchance$pantoState");
//
//                if (pantoValue != null && pantoValue.isIntegerValue()) {
//                    this.pantographState = pantoValue.asIntegerValue().asInt() % 4;
//                } else {
//                    this.pantographState = 0;
//                    System.err.println("[ManualEnchance-Repair] Invalid pantoState type detected. Resetting to 0.");
//                }
//            }
//        } catch (Exception e) {
//            this.pantographState = 0;
//            System.err.println("[ManualEnchance-Critical] Data corruption detected in MessagePack map. Repairing...");
//            e.printStackTrace();
//        }
//    }
//
//    @Inject(method = "toMessagePack", at = @At("TAIL"))
//    private void injectToMessagePack(MessagePacker messagePacker, CallbackInfo ci) throws IOException {
//        messagePacker.packString("manualEnchance$pantoState").packInt(this.pantographState);
//    }

    @Unique
    private final Map<String, Integer> rollsignIndices = new HashMap<>();
    @Unique
    private final Map<String, Float> rollsignOffsets = new HashMap<>();

    @Override
    public void setRollsignIndex(String key, int index) {
        rollsignIndices.put(key, index);
    }

    @Override
    public int getRollsignIndex(String key) {
        return rollsignIndices.getOrDefault(key, 0);
    }

    @Override
    public float getRollsignOffset(String key) {
        return rollsignOffsets.getOrDefault(key, 0.0f);
    }

    @Override
    public Map<String, Integer> getRollsignIndices() {
        return rollsignIndices;
    }

    @Unique
    private final Map<String, Integer> rollsignStepsMap = new HashMap<>();

    @Override
    public void setRollsignSteps(String key, int steps) {
        rollsignStepsMap.put(key, steps);
    }

    @Override
    public int getRollsignSteps(String key) {
        // 登録されていない場合は、とりあえず大きな値（または1）を返す
        return rollsignStepsMap.getOrDefault(key, 1);
    }

    @Shadow public abstract float getModelZOffset();

    @Shadow @Final public List<PathData> path;

    @Shadow @Final public int maxManualSpeed;

    @Unique private long manualEnchance$masterId = 0L;
    @Unique private double manualEnchance$couplingOffset = 0.0;

    @Override public long manualEnchance$getMasterId() { return this.manualEnchance$masterId; }
    @Override public void manualEnchance$setMasterId(long id) { this.manualEnchance$masterId = id; }
    @Override public double manualEnchance$getCouplingOffset() { return this.manualEnchance$couplingOffset; }
    @Override public void manualEnchance$setCouplingOffset(double offset) { this.manualEnchance$couplingOffset = offset; }

    @Unique
    public Vec3 manualEnchance$getHeadPosition() {
        Train self = (Train)(Object)this;

        double headProgress = self.isReversed()
                ? this.railProgress - (this.trainCars - 1) * this.spacing
                : this.railProgress;

        double tempRailProgress = Math.max(headProgress - this.getModelZOffset(), 0);

        int index = self.getIndex(tempRailProgress, false);

        if (this.path == null || this.path.isEmpty() || index >= this.path.size()) {
            return Vec3.ZERO;
        }

        double offset = (index == 0) ? 0 : this.distances.get(index - 1);

        return this.path.get(index).rail.getPosition(tempRailProgress - offset)
                .add(0, self.transportMode.railOffset, 0);
    }

    /**
     * リバーサーを操作するメソッド
     * 外部（パケットハンドラ等）から呼び出す想定
     */
    @Override
    public void changeReverser(boolean isUp) {
        // 速度がほぼ0（停止中）のときのみ入力を受け付ける
        // 0.0001Fは浮動小数点の誤差を考慮した「停止」判定です
        if (this.speed < 0.0001F) {
            if (isUp) {
                if (this.reverser < 1) this.reverser++;
            } else {
                if (this.reverser > -1) this.reverser--;
            }
        } else {
            // 走行中に操作しようとした場合、警告を出すことも可能です（任意）
            // System.out.println("[ManualEnchance-System] 走行中はリバーサーを操作できません。");
        }
    }

    @Inject(method = "simulateTrain", at = @At("HEAD"))
    private void calculateManualPhysics(Level world, float ticksElapsed, Depot depot, CallbackInfo ci) {
        if (this.manualEnchance$masterId != 0L) {
            Train self = (Train)(Object)this;
            System.out.println("[ManualEnchance-Debug] simulateTrain called: train=" + self.id + ", masterId=" + this.manualEnchance$masterId);
            
            boolean found = false;
            Train master = null;

            RailwayData data = RailwayData.getInstance(world);
            if (data != null) {
                for (Siding s : data.sidings) {
                    for (TrainServer t : ((SidingAccessor) s).getTrains()) {
                        if (t.id == this.manualEnchance$masterId) {
                            master = (Train) (Object) t;
                            found = true;
                            System.out.println("[ManualEnchance-Debug] Found master train in siding");
                            break;
                        }
                    }
                    if (found) break;
                }
            }

            if (found && master != null) {
                this.nextManualProgress = master.getRailProgress() - this.manualEnchance$couplingOffset;
                this.nextManualSpeed = master.getSpeed();

                this.railProgress = this.nextManualProgress;
                this.speed = this.nextManualSpeed;
                System.out.println("[ManualEnchance-Debug] Coupling sync applied: progress=" + this.railProgress + ", speed=" + this.speed);
                return;
            } else if (this.manualEnchance$masterId != 0L) {
                System.out.println("[ManualEnchance-Warn] Master train not found for masterId: " + this.manualEnchance$masterId);
            }
        }

        if (!this.isCurrentlyManual) {
            this.nextManualSpeed = this.speed;
            this.nextManualProgress = this.railProgress;
            this.lastFixedProgress = this.railProgress;
            this.reverser = 1;
            return;
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

        double multiplier = getNotchMultiplier(this.manualNotch);
        float maxAllowedBPT = this.maxManualSpeed / 2.4f; // km/h → block(m)/tick

        if (ticksElapsed > 0) {
            float delta = (float) (this.accelerationConstant * multiplier * ticksElapsed);

            float weatherModifier = 1.0f;
            if (world.isThundering()) weatherModifier = 0.6f;
            else if (world.isRaining()) weatherModifier = 0.85f;

            if (multiplier > 0) {
                delta *= weatherModifier; // 加速時の空転
            } else if (multiplier < 0) {
                delta *= (weatherModifier + 0.1f); // ブレーキ時の滑走
            }

            double y1 = getYAt(this.railProgress);
            double y2 = getYAt(this.railProgress + 0.1);
            double slope = (y2 - y1) / 0.1; // 1mあたりの高さの変化量

            float gravityConstant = 0.01f;
            float gravityEffect = (float) (slope * gravityConstant * ticksElapsed);

            delta -= gravityEffect;

            if (this.manualNotch == 0) {
                float friction = 0.00015f * ticksElapsed;
                float speedChange = -friction - (float)(slope * gravityConstant * ticksElapsed);
                this.nextManualSpeed = Math.max(0.0F, this.speed + speedChange);
            }
            else {
                if (this.manualNotch > 0) delta *= this.reverser;

                if (this.reverser == -1 && this.manualNotch > 0) {
                    this.nextManualSpeed = Math.max(0.0F, this.speed + Math.abs(delta));
                } else {
                    this.nextManualSpeed = Math.max(0.0F, this.speed + delta);
                }
            }

            if (this.nextManualSpeed > maxAllowedBPT) {
                this.nextManualSpeed = maxAllowedBPT;
            }
        }

        double moveDelta = this.nextManualSpeed * ticksElapsed;
        if (this.reverser == -1) {
            this.nextManualProgress = this.lastFixedProgress - moveDelta;
        } else {
            this.nextManualProgress = this.lastFixedProgress + moveDelta;
        }

        Train self = (Train)(Object)this;
        int currentIndex = self.getIndex(this.railProgress, false);

        if (currentIndex >= 0 && currentIndex < path.size() - 1) {
            double nodeProgress = distances.get(currentIndex);

            if (path.get(currentIndex + 1).isOppositeRail(path.get(currentIndex))) {

                boolean crossingNode = (this.lastFixedProgress <= nodeProgress && this.nextManualProgress > nodeProgress);

                if (crossingNode) {
                    this.nextManualSpeed = 0;
                    this.speed = 0;

                    double trainLength = this.trainCars * this.spacing;
                    double newProgress = nodeProgress + trainLength;

                    this.railProgress = newProgress;
                    this.nextManualProgress = newProgress;
                    this.lastFixedProgress = newProgress;

                    this.reversed = !this.reversed;

                    this.manualNotch = 0;
                }
            }
        }

        handleStationLogic();
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
    private void handleStationLogic() {
        if (this.distances != null && !this.distances.isEmpty() && this.nextStoppingIndex >= 0 && this.nextStoppingIndex < this.distances.size()) {
            double targetPos = this.distances.get(this.nextStoppingIndex);
            if (this.reverser >= 0) {
                if (this.nextManualProgress > targetPos + 0.1 && this.doorValue <= 0.01F) {
                    if (this.nextStoppingIndex < this.distances.size() - 1) this.nextStoppingIndex++;
                }
            } else {
                double prevTargetPos = (this.nextStoppingIndex > 0) ? this.distances.get(this.nextStoppingIndex - 1) : -1;
                if (this.nextManualProgress < prevTargetPos - 0.1 && this.doorValue <= 0.01F) {
                    if (this.nextStoppingIndex > 0) this.nextStoppingIndex--;
                }
            }
        }
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
    private double getNotchMultiplier(int notch) {
        return switch (notch) {
            case 5 -> 1.0;
            case 4 -> 0.8;
            case 3 -> 0.6;
            case 2 -> 0.4;
            case 1 -> 0.2;
            case 0 -> 0.0;
            case -1 -> -0.1428;
            case -2 -> -0.2857;
            case -3 -> -0.4285;
            case -4 -> -0.5714;
            case -5 -> -0.7142;
            case -6 -> -0.8571;
            case -7 -> -1.0;
            case -8 -> -1.25;
            case -9 -> -2.0; // 非常ブレーキ
            default -> 0.0;
        };
    }

    @Redirect(
            method = "simulateTrain",
            at = @At(value = "FIELD", target = "Lmtr/data/Train;speed:F", opcode = 181)
    )
    private void redirectSpeedPut(Train instance, float newValue) {
        if (this.isCurrentlyManual) {
            this.speed = this.nextManualSpeed;
        } else {
            this.speed = newValue;
        }
    }

    @Redirect(
            method = "simulateTrain",
            at = @At(value = "FIELD", target = "Lmtr/data/Train;railProgress:D", opcode = 181)
    )
    private void redirectRailProgressPut(Train instance, double newValue) {

        if (!this.isCurrentlyManual) {
            this.railProgress = newValue;
            this.lastFixedProgress = newValue;
            return;
        }

        if (this.isInsideDepot && !this.hasLeftDepot) {
            this.railProgress = newValue;
            this.lastFixedProgress = newValue;
            return;
        }

        this.railProgress = this.nextManualProgress;
        this.lastFixedProgress = this.nextManualProgress;
    }

    @Inject(method = "simulateTrain", at = @At("TAIL"))
    private void forceFinalSpeed(Level world, float ticksElapsed, Depot depot, CallbackInfo ci) {
        if (this.isCurrentlyManual) {
            // ★ Client / Server 両方で最終値を強制
            this.speed = this.nextManualSpeed;
        }
    }

    @Redirect(
            method = "simulateTrain",
            at = @At(value = "FIELD", target = "Lmtr/data/Train;manualNotch:I", opcode = 181)
    )
    private void blockMtrManualNotchPut(Train instance, int newValue) {
        if (!this.isCurrentlyManual || (this.isInsideDepot && !this.hasLeftDepot)) {
            this.manualNotch = newValue;
        }
    }

    @Redirect(
            method = "simulateTrain",
            at = @At(value = "FIELD", target = "Lmtr/data/Train;accelerationConstant:F")
    )
    private float keepAccel(Train instance) {
        return instance.accelerationConstant;
    }

    //beta-連結機能
    @Unique private boolean manualEnchance$couplingMode = false;
    @Unique
    private boolean manualEnchance$positionFixed = false;

    @Override
    public void manualEnchance$setPositionFixed(boolean fixed) {
        this.manualEnchance$positionFixed = fixed;
    }

    @Override
    public boolean manualEnchance$getPositionFixed() {
        return this.manualEnchance$positionFixed;
    }

    @Override
    public boolean manualEnchance$isCouplingMode() {
        return manualEnchance$couplingMode;
    }

    @Override
    public void manualEnchance$setCouplingMode(boolean mode) {
        this.manualEnchance$couplingMode = mode;
    }

    // Coupling is now handled purely by server-side packet handler in Main.java
    // This prevents crashes and ensures server authority over coupling decisions
}
