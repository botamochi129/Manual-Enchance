package com.manual_enchance.mixin;

import com.manual_enchance.util.TrainAccessor;
import mtr.data.Depot;
import mtr.data.Train;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = Train.class, remap = false)
public abstract class TrainMixin implements TrainAccessor {

    @Shadow protected int manualNotch;
    @Shadow protected float doorValue;
    @Shadow protected boolean isCurrentlyManual;
    @Shadow protected float speed;
    @Shadow public float accelerationConstant;
    @Shadow protected double railProgress;
    @Shadow protected List<Double> distances;
    @Shadow protected int nextStoppingIndex;

    // 状態
    private float nextManualSpeed = 0.0f;
    private double nextManualProgress = 0.0;
    private boolean isInsideDepot = false;
    private boolean hasLeftDepot = false;
    private double lastFixedProgress = -1;

    // 1:前進(F), 0:中立(N), -1:後進(B)
    private int reverser = 1;

    @Overwrite
    public boolean changeManualSpeed(boolean isAccelerate) {
        if (isAccelerate) {
            if (this.doorValue <= 0.01F && this.manualNotch < 5) {
                this.manualNotch++;
                return true;
            }
        } else {
            if (this.manualNotch > -9) {
                this.manualNotch--;
                return true;
            }
        }
        return false;
    }

    @Override
    public int getManualNotch() { return this.manualNotch; }

    @Override
    public boolean getIsCurrentlyManual() { return this.isCurrentlyManual; }

    @Override
    public int getReverser() { return this.reverser; }

    @Override
    public float manualEnchance$getDoorValue() { return this.doorValue; }

    @Override
    public void setReverser(int value) {
        this.reverser = value;
    }

    @Override
    public int getNextStoppingIndex() {
        return this.nextStoppingIndex;
    }

    @Override public List<Double> manualEnchance$getDistances() {
        return this.distances;
    }

    @Override
    public double manualEnchance$getRailProgress() {
        return this.railProgress;
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

    /**
     * リバーサーを操作するメソッド
     * 外部（パケットハンドラ等）から呼び出す想定
     */
    @Override
    public void changeReverser(boolean isUp) {
        if (isUp) {
            if (this.reverser < 1) this.reverser++;
        } else {
            if (this.reverser > -1) this.reverser--;
        }
        // 走行中にリバーサーを動かした場合の挙動（実車では故障の原因ですが）
        // 必要に応じてここに「停止中のみ操作可能」などの制約を入れられます
    }

    @Inject(method = "simulateTrain", at = @At("HEAD"))
    private void calculateManualPhysics(World world, float ticksElapsed, Depot depot, CallbackInfo ci) {

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

        double multiplier = switch (this.manualNotch) {
            case 5 -> 1.0; case 4 -> 0.8; case 3 -> 0.6; case 2 -> 0.4; case 1 -> 0.2;
            case 0 -> 0.0;
            // ブレーキ側はリバーサーに関係なく減速として働く
            case -1 -> -0.1428; case -2 -> -0.2857; case -3 -> -0.4285;
            case -4 -> -0.5714; case -5 -> -0.7142; case -6 -> -0.8571;
            case -7 -> -1.0;    case -8 -> -1.25;   case -9 -> -2.0;
            default -> 0.0;
        };

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
        if (this.manualNotch <= -1 && ticksElapsed > 0 && !world.isClient && world.getTime() % 20 == 0) {
            System.out.println(String.format(
                    "[TrainDebug-SERVER] Notch:%d Speed:%.4f→%.4f Delta:%.4f",
                    this.manualNotch,
                    this.speed,
                    this.nextManualSpeed,
                    this.nextManualSpeed - this.speed
            ));
        }

        // 停止判定
        if (this.distances != null && !this.distances.isEmpty()
                && this.nextStoppingIndex >= 0
                && this.nextStoppingIndex < this.distances.size()) {

            double targetPos = this.distances.get(this.nextStoppingIndex);
            if (this.nextManualProgress > targetPos + 0.1 && this.doorValue <= 0.01F) {
                if (this.nextStoppingIndex < this.distances.size() - 1) {
                    this.nextStoppingIndex++;
                }
            }
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
    private void forceFinalSpeed(World world, float ticksElapsed, Depot depot, CallbackInfo ci) {
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
