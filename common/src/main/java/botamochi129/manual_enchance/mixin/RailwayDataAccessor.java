package botamochi129.manual_enchance.mixin;

import mtr.data.Rail;
import mtr.data.RailwayData;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = RailwayData.class, remap = false)
public interface RailwayDataAccessor {

    @Accessor("rails")
    Map<BlockPos, Map<BlockPos, Rail>> getRails();
}
