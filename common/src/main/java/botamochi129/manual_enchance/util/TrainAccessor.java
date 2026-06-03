package botamochi129.manual_enchance.util;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

public interface TrainAccessor {
    int getManualNotch();
    boolean getIsCurrentlyManual();
    int getReverser();
    void changeReverser(boolean isUp);
    void setReverser(int value);
    void setManualNotchDirect(int notch);
    float manualEnchance$getDoorValue();

    int getNextStoppingIndex();
    List<Double> manualEnchance$getDistances();
    double manualEnchance$getRailProgress();

    void setRailProgress(double railProgress);
    void setSpeed(float speed);

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

    Vec3 manualEnchance$getHeadPosition();

    boolean manualEnchance$getPositionFixed();
    void manualEnchance$setPositionFixed(boolean fixed);
    boolean manualEnchance$isCouplingMode();
    void manualEnchance$setCouplingMode(boolean mode);

    long manualEnchance$getMasterId();
    void manualEnchance$setMasterId(long id);
    double manualEnchance$getCouplingOffset();
    void manualEnchance$setCouplingOffset(double offset);
}