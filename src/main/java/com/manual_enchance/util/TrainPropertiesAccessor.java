package com.manual_enchance.util;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

public interface TrainPropertiesAccessor {
    void setHornSoundId(String id);
    String getHornSoundId();
}