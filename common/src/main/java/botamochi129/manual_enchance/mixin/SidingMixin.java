package botamochi129.manual_enchance.mixin;

import botamochi129.manual_enchance.util.SidingAccessor;
import botamochi129.manual_enchance.util.SidingDataManager;
import mtr.data.Siding;
import mtr.data.TrainServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Set;

@Mixin(value = Siding.class, remap = false)
public abstract class SidingMixin implements SidingAccessor {
    @Shadow @Final private Set<TrainServer> trains;

    @Override
    public Set<TrainServer> getTrains() {
        return trains;
    }

    @Override
    public int manualEnchance$getDefaultPantographState() {
        long sidingId = ((Siding)(Object)this).id;
        return SidingDataManager.getPantoState(sidingId);
    }

    @Override
    public void manualEnchance$setDefaultPantographState(int state) {
        long sidingId = ((Siding)(Object)this).id;
        SidingDataManager.setPantoState(sidingId, state);
    }
}