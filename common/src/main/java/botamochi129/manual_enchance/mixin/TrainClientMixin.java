package botamochi129.manual_enchance.mixin;

import botamochi129.manual_enchance.client.PantoHelper;
import botamochi129.manual_enchance.util.TrainAccessor;
import mtr.data.Train;
import mtr.data.TrainClient;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(value = TrainClient.class, remap = false)
public abstract class TrainClientMixin implements TrainAccessor {


    @Inject(method = "simulateTrain", at = @At("HEAD"))
    private void onSimulateTrainHead(Level world, float ticksElapsed, TrainClient.SpeedCallback speedCallback, TrainClient.AnnouncementCallback announcementCallback, TrainClient.AnnouncementCallback lightRailAnnouncementCallback, CallbackInfo ci) {
        PantoHelper.setCurrentTrain((TrainClient) (Object) this);

        if (this.manualEnchance$getMasterId() != 0L) {
            long masterId = this.manualEnchance$getMasterId();
            TrainClient self = (TrainClient) (Object) this;
            System.out.println("[ManualEnchance-Debug] Client simulateTrain: train=" + self.id + ", masterId=" + masterId);

            for (mtr.data.TrainClient potentialMaster : mtr.client.ClientData.TRAINS) {
                if (potentialMaster.id == masterId) {
                    double masterProgress = potentialMaster.getRailProgress();
                    double offset = this.manualEnchance$getCouplingOffset();
                    this.setRailProgress(masterProgress - offset);
                    this.setSpeed(potentialMaster.getSpeed());
                    System.out.println("[ManualEnchance-Debug] Client coupling applied: master_progress=" + masterProgress + ", offset=" + offset + ", new_progress=" + (masterProgress - offset));
                    return;
                }
            }
            System.out.println("[ManualEnchance-Warn] Master train " + masterId + " not found in ClientData.TRAINS");
        }
    }

    @Inject(method = "simulateTrain", at = @At("TAIL"))
    private void simulateRollsign(Level world, float ticksElapsed, TrainClient.SpeedCallback speedCallback, TrainClient.AnnouncementCallback announcementCallback, TrainClient.AnnouncementCallback lightRailAnnouncementCallback, CallbackInfo ci) {
        // 登録されている全ての rollsignId に対して処理
        // 注: accessor経由でMapにアクセスできるようにインターフェースを調整してください
        this.getRollsignIds().forEach(id -> {
            float target = (float) this.getRollsignIndex(id);
            float current = this.getRollsignOffset(id);
            float speed = 0.05f * ticksElapsed;

            if (Math.abs(current - target) > 0.001f) {
                if (current < target) {
                    this.setRollsignOffset(id, Math.min(target, current + speed));
                } else {
                    this.setRollsignOffset(id, Math.max(target, current - speed));
                }
            } else {
                this.setRollsignOffset(id, target);
            }
        });
    }

    @Inject(method = "simulateTrain", at = @At("RETURN"))
    private void onSimulateTrainReturn(Level world, float ticksElapsed, TrainClient.SpeedCallback speedCallback, TrainClient.AnnouncementCallback announcementCallback, TrainClient.AnnouncementCallback lightRailAnnouncementCallback, CallbackInfo ci) {
        PantoHelper.clear();
    }

    @Override
    public java.util.Set<String> getRollsignIds() {
        // 全ての登録済みID（パーツ名）のリストを返す
        return this.getRollsignIndices().keySet();
    }

    @Unique
    private final Map<String, Float> rollsignOffsets = new HashMap<>();

    @Override
    public float getRollsignOffset(String key) {
        return rollsignOffsets.getOrDefault(key, 0.0f);
    }

    @Override
    public void setRollsignOffset(String key, float offset) {
        rollsignOffsets.put(key, offset);
    }

    @Unique private final Map<String, Integer> rollsignStepsMap = new HashMap<>();

    @Override
    public void setRollsignSteps(String key, int steps) {
        rollsignStepsMap.put(key, steps);
    }

    @Override
    public int getRollsignSteps(String key) {
        // 登録されていない場合は、とりあえず大きな値（または1）を返す
        return rollsignStepsMap.getOrDefault(key, 1);
    }
}