package botamochi129.manual_enchance.client.mixin;

import botamochi129.manual_enchance.util.TrainPropertiesAccessor;
import mtr.client.TrainProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = TrainProperties.class, remap = false)
public class TrainPropertiesMixin implements TrainPropertiesAccessor {
    @Unique
    private String hornSoundId = "";

    @Override
    public void setHornSoundId(String id) { this.hornSoundId = id; }

    @Override
    public String getHornSoundId() { return this.hornSoundId; }
}