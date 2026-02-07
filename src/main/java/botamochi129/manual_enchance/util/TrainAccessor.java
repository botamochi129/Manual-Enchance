package botamochi129.manual_enchance.util;

import java.util.List;
import java.util.Map;

// @Mixin は削除します
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
}