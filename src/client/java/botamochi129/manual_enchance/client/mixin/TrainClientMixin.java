package botamochi129.manual_enchance.client.mixin;

import botamochi129.manual_enchance.client.util.PantoHelper;
import botamochi129.manual_enchance.util.TrainAccessor;
import mtr.data.TrainClient;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(value = TrainClient.class, remap = false)
public abstract class TrainClientMixin implements TrainAccessor { // インターフェースを追加

    @Inject(method = "simulateTrain", at = @At("HEAD"))
    private void onSimulateTrainHead(World world, float ticksElapsed, TrainClient.SpeedCallback speedCallback, TrainClient.AnnouncementCallback announcementCallback, TrainClient.AnnouncementCallback lightRailAnnouncementCallback, CallbackInfo ci) {
        PantoHelper.setCurrentTrain((TrainClient) (Object) this);
    }

    @Inject(method = "simulateTrain", at = @At("TAIL"))
    private void simulateRollsign(World world, float ticksElapsed, TrainClient.SpeedCallback speedCallback, TrainClient.AnnouncementCallback announcementCallback, TrainClient.AnnouncementCallback lightRailAnnouncementCallback, CallbackInfo ci) {
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
    private void onSimulateTrainReturn(World world, float ticksElapsed, TrainClient.SpeedCallback speedCallback, TrainClient.AnnouncementCallback announcementCallback, TrainClient.AnnouncementCallback lightRailAnnouncementCallback, CallbackInfo ci) {
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