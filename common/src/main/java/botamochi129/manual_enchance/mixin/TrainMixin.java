package botamochi129.manual_enchance.mixin;

import botamochi129.manual_enchance.Main;
import botamochi129.manual_enchance.util.TrainAccessor;
import com.llamalad7.mixinextras.sugar.Local;
import mtr.data.Depot;
import mtr.data.MessagePackHelper;
import mtr.data.Train;
import mtr.path.PathData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.msgpack.core.MessagePacker;
import org.msgpack.value.Value;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Shadow public abstract boolean isReversed();

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
    public void onChangeManualSpeed(boolean isAccelerate, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
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

    @Inject(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V",
            at = @At("RETURN"),
            remap = false,
            require = 0)
    private void injectReadPacket(FriendlyByteBuf packet, CallbackInfo ci) {
        try {
            this.pantographState = packet.readInt();
        } catch (Exception ignored) {}
    }

    @Inject(method = "writePacket(Lnet/minecraft/network/FriendlyByteBuf;)V",
            at = @At("TAIL"),
            remap = false,
            require = 0)
    private void injectWritePacket(FriendlyByteBuf packet, CallbackInfo ci) {
        try {
            packet.writeInt(this.pantographState);
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

        if (!this.isCurrentlyManual) {
            this.nextManualSpeed = this.speed;
            this.nextManualProgress = this.railProgress;
            this.lastFixedProgress = this.railProgress;
            this.reverser = 1;
            return;
        }

        this.isInsideDepot = (depot != null);

        // 出庫前は動かさない
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

        if (this.lastFixedProgress < 0) {
            this.lastFixedProgress = this.railProgress;
        }

        double multiplier;
        switch (this.manualNotch) {
            case 5:
                multiplier = 1.0;
                break;
            case 4:
                multiplier = 0.8;
                break;
            case 3:
                multiplier = 0.6;
                break;
            case 2:
                multiplier = 0.4;
                break;
            case 1:
                multiplier = 0.2;
                break;
            case 0:
                multiplier = 0.0;
                break;
            case -1:
                multiplier = -0.1428;
                break;
            case -2:
                multiplier = -0.2857;
                break;
            case -3:
                multiplier = -0.4285;
                break;
            case -4:
                multiplier = -0.5714;
                break;
            case -5:
                multiplier = -0.7142;
                break;
            case -6:
                multiplier = -0.8571;
                break;
            case -7:
                multiplier = -1.0;
                break;
            case -8:
                multiplier = -1.25;
                break;
            case -9:
                multiplier = -2.0;
                break;
            default:
                multiplier = 0.0;
                break;
        }

        if (ticksElapsed > 0) {
            float delta = (float) (this.accelerationConstant * multiplier * ticksElapsed);

            // 力行時（Notch > 0）のみリバーサーの影響を受ける
            if (this.manualNotch > 0) {
                // 中立(0)なら加速しない、後進(-1)なら負の方向に加速
                delta *= this.reverser;
            }

            // 速度更新（バックを許容するため Math.max(0, ...) を外すか、
            // MTRの仕様に合わせて「絶対値」で扱うか検討が必要ですが、
            // MTRの内部的には speed は正の値として扱い、railProgress の増減で向きを決めるのが安全です。

            if (this.reverser == -1 && this.manualNotch > 0) {
                // 後進時の加速：speed自体は「速さ」として正の数で増やす
                this.nextManualSpeed = Math.max(0.0F, this.speed + Math.abs(delta));
            } else if (this.manualNotch > 0 && this.reverser == 0) {
                // 中立時は加速deltaを無視（慣性走行またはブレーキのみ）
                this.nextManualSpeed = Math.max(0.0F, this.speed + (multiplier < 0 ? delta : 0));
            } else {
                // 通常の前進またはブレーキ
                this.nextManualSpeed = Math.max(0.0F, this.speed + delta);
            }
        }

        // すでに速度が乗っている状態でリバーサーを切り替えた場合の判定
        // 本来は「現在の進行方向」に合わせるべきですが、簡易的にリバーサーに合わせます
        if (this.reverser == -1) {
            this.nextManualProgress = this.lastFixedProgress - (this.nextManualSpeed * ticksElapsed);
        } else {
            this.nextManualProgress = this.lastFixedProgress + (this.nextManualSpeed * ticksElapsed);
        }

        // デバッグ
        /*if (this.manualNotch <= -1 && ticksElapsed > 0 && !world.isClient && world.getTime() % 20 == 0) {
            System.out.println(String.format(
                    "[TrainDebug-SERVER] Notch:%d Speed:%.4f→%.4f Delta:%.4f",
                    this.manualNotch,
                    this.speed,
                    this.nextManualSpeed,
                    this.nextManualSpeed - this.speed
            ));
        }*/

        // 停止判定
        if (this.distances != null && !this.distances.isEmpty()
                && this.nextStoppingIndex >= 0
                && this.nextStoppingIndex < this.distances.size()) {

            double targetPos = this.distances.get(this.nextStoppingIndex);

            // 【前進時】 ターゲット（駅）を通り過ぎたら、次の駅へ
            if (this.reverser >= 0) {
                if (this.nextManualProgress > targetPos + 0.1 && this.doorValue <= 0.01F) {
                    if (this.nextStoppingIndex < this.distances.size() - 1) {
                        this.nextStoppingIndex++;
                    }
                }
            }
            // 【後進時】 ターゲット（駅）より後ろに戻ったら、インデックスを戻す
            else {
                // 1つ前の駅の座標を取得
                double prevTargetPos = (this.nextStoppingIndex > 0) ? this.distances.get(this.nextStoppingIndex - 1) : -1;

                // 現在地が「今目指している駅」よりも手前（Depot寄り）に戻り、
                // かつ1つ前の駅よりも後ろにいる場合、インデックスを戻す
                if (this.nextManualProgress < prevTargetPos - 0.1 && this.doorValue <= 0.01F) {
                    if (this.nextStoppingIndex > 0) {
                        this.nextStoppingIndex--;
                    }
                }
            }
        }

        // 1. 路線全体の長さを取得（distancesの最後の要素が終点）
        double maxProgress = (this.distances != null && !this.distances.isEmpty())
                ? this.distances.get(this.distances.size() - 1)
                : 0;

        // 2. 0（起点）を下回る、または maxProgress（終点）を上回る場合の強制停止
        if (this.nextManualProgress < 0) {
            // 起点を超えて後退しようとした場合
            this.nextManualProgress = 0;
            this.nextManualSpeed = 0;
            //System.out.println("[ManualEnchance-Safety] 起点に到達したため強制停止しました。");
        } else if (this.nextManualProgress > maxProgress) {
            // 終点を超えて前進しようとした場合
            this.nextManualProgress = maxProgress;
            this.nextManualSpeed = 0;
            //System.out.println("[ManualEnchance-Safety] 終点に到達したため強制停止しました。");
        }
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
}
