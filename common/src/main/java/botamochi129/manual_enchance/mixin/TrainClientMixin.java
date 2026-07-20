package botamochi129.manual_enchance.mixin;

import botamochi129.manual_enchance.client.PantoHelper;
import botamochi129.manual_enchance.util.TrainAccessor;
import mtr.data.Train;
import mtr.data.TrainClient;
import mtr.path.PathData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = TrainClient.class, remap = false)
public abstract class TrainClientMixin extends TrainMixin implements TrainAccessor {

    @Inject(method = "handlePositions", at = @At("RETURN"))
    private void onHandlePositionsReturn(Level world, Vec3[] positions, float ticksElapsed, CallbackInfoReturnable<Boolean> cir) {
        // No position override needed. railProgress is the server-authoritative value synced
        // via MTR packets, and positions are computed from it by Train.simulateTrain().
        // The previous override using callGetRoutePosition was redundant and contributed to
        // server/client desync because it ran after a (now-removed) railProgress override.
    }

    @Inject(method = "simulateTrain", at = @At("HEAD"))
    private void onSimulateTrainHead(Level world, float ticksElapsed, TrainClient.SpeedCallback speedCallback, TrainClient.AnnouncementCallback announcementCallback, TrainClient.AnnouncementCallback lightRailAnnouncementCallback, CallbackInfo ci) {
        PantoHelper.setCurrentTrain((TrainClient) (Object) this);

        if (this.manualEnchance$getMasterId() != 0L) {
            TrainClient master = manualEnchance$findMasterClient();
            if (master != null) manualEnchance$syncSlaveFromMaster(master);
        }
    }

    @Inject(method = "simulateTrain", at = @At("TAIL"))
    private void simulateRollsign(Level world, float ticksElapsed, TrainClient.SpeedCallback speedCallback, TrainClient.AnnouncementCallback announcementCallback, TrainClient.AnnouncementCallback lightRailAnnouncementCallback, CallbackInfo ci) {
        if (this.manualEnchance$getMasterId() != 0L) {
            TrainClient master = manualEnchance$findMasterClient();
            if (master != null) manualEnchance$syncSlaveFromMaster(master);
        }

        this.getRollsignIds().forEach(id -> {
            float target = (float) this.getRollsignIndex(id);
            float current = this.getRollsignOffset(id);
            float signSpeed = 0.05f * ticksElapsed;

            if (Math.abs(current - target) > 0.001f) {
                if (current < target) {
                    this.setRollsignOffset(id, Math.min(target, current + signSpeed));
                } else {
                    this.setRollsignOffset(id, Math.max(target, current - signSpeed));
                }
            } else {
                this.setRollsignOffset(id, target);
            }
        });
    }

    @Unique
    private TrainClient manualEnchance$findMasterClient() {
        long masterId = this.manualEnchance$getMasterId();
        for (TrainClient potentialMaster : mtr.client.ClientData.TRAINS) {
            if (potentialMaster.id == masterId) return potentialMaster;
        }
        return null;
    }

    @Unique
    private void manualEnchance$syncSlaveFromMaster(TrainClient master) {
        TrainAccessor masterAcc = (TrainAccessor) master;

        // Compute slave railProgress from master's current position on the client.
        // The master's railProgress is updated every tick by MTR's client simulation,
        // giving smooth visual interpolation. Server sync packets (every ~5 ticks)
        // will correct any drift from the golden rule (master - offset) on the server.
        double slaveRP = master.getRailProgress() - this.manualEnchance$getCouplingOffset();
        // Clamp to valid path range
        double maxP = (this.distances != null && !this.distances.isEmpty())
                ? this.distances.get(this.distances.size() - 1) : Double.MAX_VALUE;
        double minP = this.trainCars * (double) this.spacing;
        if (maxP > minP && maxP != Double.MAX_VALUE) {
            slaveRP = Math.max(minP, Math.min(maxP, slaveRP));
        }
        this.railProgress = slaveRP;

        this.reversed = master.isReversed();
        this.setSpeed(master.getSpeed());
        if (this.manualEnchance$getDoorValue() != masterAcc.manualEnchance$getDoorValue()) {
            this.setDoorValue(masterAcc.manualEnchance$getDoorValue());
        }
        this.manualEnchance$setDoorTarget(masterAcc.manualEnchance$getDoorTarget());
        this.setPantographState(masterAcc.getPantographState());
        this.setReverser(masterAcc.getReverser());
    }

    @Inject(method = "simulateTrain", at = @At("RETURN"))
    private void onSimulateTrainReturn(Level world, float ticksElapsed, TrainClient.SpeedCallback speedCallback, TrainClient.AnnouncementCallback announcementCallback, TrainClient.AnnouncementCallback lightRailAnnouncementCallback, CallbackInfo ci) {
        PantoHelper.clear();
    }

    @Unique
    public Vec3 manualEnchance$getPositionByProgress(double targetProgress) {
        List<PathData> pathList = this.path;
        List<Double> distanceList = this.distances;

        if (pathList == null || distanceList == null || pathList.isEmpty() || distanceList.isEmpty()) {
            return net.minecraft.world.phys.Vec3.ZERO;
        }

        double maxDistance = distanceList.get(distanceList.size() - 1);
        double clampedProgress = net.minecraft.util.Mth.clamp(targetProgress, 0, maxDistance);

        int index = java.util.Collections.binarySearch(distanceList, clampedProgress);
        if (index < 0) index = -index - 1;
        index = net.minecraft.util.Mth.clamp(index, 1, distanceList.size() - 1);

        PathData pathData = pathList.get(index);
        double startDistance = distanceList.get(index - 1);
        double endDistance = distanceList.get(index);
        double segmentLength = endDistance - startDistance;
        double ratio = segmentLength <= 0 ? 0 : (clampedProgress - startDistance) / segmentLength;

        return pathData.rail.getPosition(ratio);
    }

    @Unique
    public float[] manualEnchance$getRotationByProgress(double targetProgress) {
        double delta = 0.05;
        Vec3 posA = manualEnchance$getPositionByProgress(targetProgress - delta);
        Vec3 posB = manualEnchance$getPositionByProgress(targetProgress + delta);

        double diffX = posB.x - posA.x;
        double diffY = posB.y - posA.y;
        double diffZ = posB.z - posA.z;

        float yaw = (float) Math.toDegrees(Math.atan2(diffX, diffZ));
        double horizontalDistance = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float pitch = (float) Math.toDegrees(Math.atan2(-diffY, horizontalDistance));

        return new float[]{yaw, pitch};
    }
}
