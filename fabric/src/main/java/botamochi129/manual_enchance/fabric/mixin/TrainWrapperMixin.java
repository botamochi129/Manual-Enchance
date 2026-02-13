package botamochi129.manual_enchance.fabric.mixin;

import botamochi129.manual_enchance.util.TrainAccessor;
import fabric.cn.zbx1425.mtrsteamloco.render.scripting.train.TrainWrapper;
import mtr.data.TrainClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = TrainWrapper.class, remap = false)
public class TrainWrapperMixin {

    @Shadow public TrainClient train;

    @Unique
    public int pantographState() {
        if (train instanceof TrainAccessor accessor) {
            return accessor.getPantographState();
        }
        return 0;
    }

    @Unique
    public int reverser() {
        if (train instanceof TrainAccessor accessor) {
            return accessor.getReverser();
        }
        return 0;
    }

    @Unique
    public int getRollsignIndex(String key) {
        if (train instanceof TrainAccessor accessor) {
            return accessor.getRollsignIndex(key);
        }
        return 0;
    }
}