package botamochi129.manual_enchance.client.mixin;

import com.google.gson.JsonObject;
import botamochi129.manual_enchance.client.util.PantoHelper;
import botamochi129.manual_enchance.util.TrainAccessor;
import mtr.client.DynamicTrainModel;
import mtr.data.TrainClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DynamicTrainModel.class)
public abstract class DynamicTrainModelMixin {
    @Inject(method = "shouldSkipRender", at = @At("HEAD"), cancellable = true, remap = false)
    private void onShouldSkipRender(JsonObject partObject, CallbackInfoReturnable<Boolean> cir) {
        if (!partObject.has("render_condition")) return;
        String condition = partObject.get("render_condition").getAsString();

        TrainClient train = PantoHelper.getCurrentTrain();
        // もし列車が特定できない場合は、デフォルトの描画（条件無視）をさせる
        if (train == null) return;

        int pantoState = ((TrainAccessor) train).getPantographState();
        // 条件判定
        switch (condition) {
            case "PANTAGRAPH_DOWN":
                if (pantoState != 0) cir.setReturnValue(true);
                break;
            case "PANTAGRAPH_5M":
                if (pantoState != 1) cir.setReturnValue(true);
                break;
            case "PANTAGRAPH_W51":
                if (pantoState != 2) cir.setReturnValue(true);
                break;
            case "PANTAGRAPH_6M":
                if (pantoState != 3) cir.setReturnValue(true);
                break;
        }
    }
}