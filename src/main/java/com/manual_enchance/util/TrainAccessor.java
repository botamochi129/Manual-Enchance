package com.manual_enchance.util;

import java.util.List;

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
}