package botamochi129.manual_enchance.mixin;

import botamochi129.manual_enchance.util.RouteCouplingStore;
import botamochi129.manual_enchance.util.TrainAccessor;
import mtr.data.*;
import mtr.data.Train;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

@Mixin(value = mtr.data.TrainServer.class, remap = false)
public class TrainServerMixin {

    @Inject(method = "simulateTrain(Lnet/minecraft/world/level/Level;FLmtr/data/Depot;Lmtr/data/DataCache;Ljava/util/List;Ljava/util/Map;Ljava/util/Map;Ljava/util/Map;)Z", at = @At("HEAD"), require = 0)
    private void manualEnchance$resetCoolDownOnHead(Level world, float ticksElapsed, Depot depot, DataCache dataCache, List<?> trainPositions, Map<?, ?> trainsInPlayerRange, Map<?, ?> schedulesForPlatform, Map<?, ?> trainDelays, CallbackInfoReturnable<Boolean> cir) {
        // Safety net: reset manualCoolDown BEFORE MTR's cooldown logic (which runs AFTER
        // this.simulateTrain() returns, at lines 311-329). Without this, if manualCoolDown
        // somehow drifts, isCurrentlyManual could flip to false and allow auto-coupling of
        // a manual train.
        Train train = (Train) (Object) this;
        TrainAccessor acc = (TrainAccessor) train;
        if (acc.getIsCurrentlyManual()) {
            ((TrainServerAccessor) this).setManualCoolDown(0);
        }
    }

    @Inject(method = "isRailBlocked", at = @At("HEAD"), cancellable = true)
    private void manualEnchance$bypassIsRailBlocked(int pathIndex, CallbackInfoReturnable<Boolean> cir) {
        Train train = (Train) (Object) this;
        TrainAccessor acc = (TrainAccessor) train;

        if (acc.manualEnchance$getMasterId() != 0L) {
            cir.setReturnValue(false);
            return;
        }

        if (!train.getIsOnRoute() || acc.getNextStoppingIndex() < 0) {
            return;
        }

        long routeId = ((TrainServerAccessor) this).getRouteId();
        int stopIdx = -1;
        if (train.path != null && acc.getNextStoppingIndex() >= 0 && acc.getNextStoppingIndex() < train.path.size()) {
            int rawStop = train.path.get(acc.getNextStoppingIndex()).stopIndex;
            if (rawStop > 0) stopIdx = rawStop - 1;
        }
        RouteCouplingStore.RouteCouplingAction action = stopIdx >= 0 ? RouteCouplingStore.getAction(routeId, stopIdx) : null;
        if (action != null && action.doCouple) {
            cir.setReturnValue(false);
        }
    }
}
