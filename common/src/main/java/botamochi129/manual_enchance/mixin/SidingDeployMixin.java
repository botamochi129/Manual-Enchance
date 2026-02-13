package botamochi129.manual_enchance.mixin;

import botamochi129.manual_enchance.util.SidingAccessor;
import botamochi129.manual_enchance.util.SidingDataManager;
import botamochi129.manual_enchance.util.TrainAccessor;
import mtr.data.Siding;
import mtr.data.TrainServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(value = Siding.class, remap = false)
public abstract class SidingDeployMixin {
    @Shadow @Final private Set<TrainServer> trains;

    @Inject(method = "simulateTrain", at = @At(value = "INVOKE", target = "Ljava/util/Set;add(Ljava/lang/Object;)Z", shift = At.Shift.AFTER))
    private void injectDefaultPanto(CallbackInfo ci) {
        long sidingId = ((Siding)(Object)this).id;
        int defaultState = SidingDataManager.getPantoState(sidingId);

        if (defaultState > 0) {
            this.trains.stream()
                    .filter(train -> ((TrainAccessor)train).getPantographState() == 0)
                    .forEach(train -> {
                        ((TrainAccessor) train).setPantographState(defaultState);
                    });
        }
    }
}