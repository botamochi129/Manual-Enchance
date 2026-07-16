package botamochi129.manual_enchance.mixin;

import mtr.data.TrainServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = TrainServer.class, remap = false)
public interface TrainServerAccessor {
    @Accessor("routeId")
    long getRouteId();

    @Accessor("manualCoolDown")
    void setManualCoolDown(int value);
}
