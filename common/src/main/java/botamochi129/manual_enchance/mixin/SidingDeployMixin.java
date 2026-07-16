package botamochi129.manual_enchance.mixin;

import botamochi129.manual_enchance.Main;
import botamochi129.manual_enchance.util.CouplingManager;
import botamochi129.manual_enchance.util.SidingAccessor;
import botamochi129.manual_enchance.util.SidingDataManager;
import botamochi129.manual_enchance.util.TrainAccessor;
import mtr.data.RailwayData;
import mtr.data.Siding;
import mtr.data.TrainServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
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
    @Shadow private Level world;

    @Inject(method = "simulateTrain", at = @At(value = "INVOKE", target = "Ljava/util/Set;add(Ljava/lang/Object;)Z", shift = At.Shift.AFTER))
    private void onTrainDeployed(CallbackInfo ci) {
        long sidingId = ((Siding)(Object)this).id;

        int defaultPanto = SidingDataManager.getPantoState(sidingId);
        if (defaultPanto > 0) {
            this.trains.stream()
                    .filter(t -> ((TrainAccessor) t).getPantographState() == 0)
                    .forEach(t -> ((TrainAccessor) t).setPantographState(defaultPanto));
        }

        long masterSidingId = SidingDataManager.getMasterSidingId(sidingId);
        if (masterSidingId == 0L) return;

        RailwayData data = RailwayData.getInstance(this.world);
        if (data == null || !(this.world instanceof ServerLevel serverLevel)) return;

        TrainServer masterTrain = null;
        for (Siding siding : data.sidings) {
            if (siding.id != masterSidingId) continue;
            Set<TrainServer> masterTrains = ((SidingAccessor) siding).getTrains();
            if (!masterTrains.isEmpty()) {
                masterTrain = masterTrains.iterator().next();
            }
            break;
        }
        if (masterTrain == null) return;

        final TrainServer finalMasterTrain = masterTrain;
        for (TrainServer slave : this.trains) {
            TrainAccessor slaveAcc = (TrainAccessor) slave;
            if (slaveAcc.manualEnchance$getMasterId() != 0L || slave.id == finalMasterTrain.id) continue;

            TrainAccessor masterAcc = (TrainAccessor) finalMasterTrain;
            double distance = slaveAcc.manualEnchance$getFrontPosition()
                    .distanceTo(masterAcc.manualEnchance$getRearPosition());
            if (distance > CouplingManager.MAX_COUPLING_DISTANCE) continue;

            CouplingManager.applyCoupling(
                    slave,
                    finalMasterTrain,
                    CouplingManager.ConnectionType.SLAVE_FRONT_TO_MASTER_REAR,
                    serverLevel,
                    Main.COUPLING_SYNC_S2C_PACKET_ID
            );
        }
    }
}
