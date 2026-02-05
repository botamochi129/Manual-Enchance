package com.manual_enchance.client.mixin;

import com.manual_enchance.client.util.PantoHelper;
import mtr.data.TrainClient;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TrainClient.class)
public abstract class TrainClientMixin {

    // mtr.data.TrainClient.simulateTrain(World, float, ...) に割り込む
    // このメソッドは RenderTrains.render から全列車に対して毎フレーム呼ばれます
    @Inject(method = "simulateTrain", at = @At("HEAD"), remap = false)
    private void onSimulateTrainHead(World world, float ticksElapsed, TrainClient.SpeedCallback speedCallback, TrainClient.AnnouncementCallback announcementCallback, TrainClient.AnnouncementCallback lightRailAnnouncementCallback, CallbackInfo ci) {
        // 現在シミュレート（＝この後描画）される列車をセット
        PantoHelper.setCurrentTrain((TrainClient) (Object) this);
    }

    // メモリリーク防止のため、シミュレーション終了時にクリア
    @Inject(method = "simulateTrain", at = @At("RETURN"), remap = false)
    private void onSimulateTrainReturn(World world, float ticksElapsed, TrainClient.SpeedCallback speedCallback, TrainClient.AnnouncementCallback announcementCallback, TrainClient.AnnouncementCallback lightRailAnnouncementCallback, CallbackInfo ci) {
        PantoHelper.clear();
    }
}