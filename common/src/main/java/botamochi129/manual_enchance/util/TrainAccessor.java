package botamochi129.manual_enchance.util;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

import mtr.data.TrainServer;

public interface TrainAccessor {
    int getManualNotch();
    boolean getIsCurrentlyManual();
    int getReverser();
    void changeReverser(boolean isUp);
    void setReverser(int value);
    boolean getReversed();
    void setReversed(boolean reversed);
    void setManualNotchDirect(int notch);
    float manualEnchance$getDoorValue();

    int getNextStoppingIndex();
    List<Double> manualEnchance$getDistances();
    int getRepeatIndex1();
    double manualEnchance$getRailProgress();

    void setRailProgress(double railProgress);
    void setSpeed(float speed);
    float getSpeed();
    void setDoorValue(float doorValue);

    int getPantographState();
    void setPantographState(int state);

    String getHornSoundId();

    void setRollsignIndex(String key, int index);
    int getRollsignIndex(String key);
    void setRollsignOffset(String key, float offset); // 追加
    float getRollsignOffset(String key);
    java.util.Set<String> getRollsignIds();
    Map<String, Integer> getRollsignIndices();
    void setRollsignSteps(String key, int steps);
    int getRollsignSteps(String key);
    void setRollsignNames(String key, List<String> names);
    List<String> getRollsignNames(String key);

    boolean manualEnchance$getPositionFixed();
    void manualEnchance$setPositionFixed(boolean fixed);

    boolean manualEnchance$getTurnBackDone();
    void manualEnchance$setTurnBackDone(boolean done);
    boolean manualEnchance$isCouplingMode();
    void manualEnchance$setCouplingMode(boolean mode);

    long manualEnchance$getMasterId();
    void manualEnchance$setMasterId(long id);
    double manualEnchance$getCouplingOffset();
    void manualEnchance$setCouplingOffset(double offset);

    Vec3 manualEnchance$getFrontPosition();
    Vec3 manualEnchance$getRearPosition();
    Vec3 manualEnchance$getCouplerFrontPos();
    Vec3 manualEnchance$getCouplerRearPos();

    Vec3 manualEnchance$getDirectionVector(double finalSlaveTargetProg);

    double manualEnchance$getRailProgressAtCar(int car, double longitudinalOffsetBlocks);

    Vec3 callGetRoutePosition(int car, int trainSpacing);

    Vec3 manualEnchance$getPositionByProgress(double frontBogieProgress);

    float[] manualEnchance$getRotationByProgress(double currentCarProgress);

    long manualEnchance$getRouteId();
    void manualEnchance$syncPathFrom(TrainServer master);
    float manualEnchance$getBCPressure();

    boolean manualEnchance$isOnRoute();
    void manualEnchance$setOnRoute(boolean onRoute);

    boolean manualEnchance$getDoorTarget();
    void manualEnchance$setDoorTarget(boolean target);

    boolean manualEnchance$isWaitingForCouple();
    void manualEnchance$setWaitingForCouple(boolean waiting);

    void manualEnchance$setBCPressure(float pressure);
    boolean manualEnchance$getDoorsOpenedAtTerminal();
    void manualEnchance$setDoorsOpenedAtTerminal(boolean opened);

    void manualEnchance$setNextManualProgress(double progress);
    void manualEnchance$setLastFixedProgress(double progress);
}