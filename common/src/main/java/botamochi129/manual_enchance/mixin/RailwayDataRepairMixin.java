package botamochi129.manual_enchance.mixin;

import mtr.data.RailwayData;
import org.msgpack.value.Value;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

@Mixin(value = RailwayData.class, remap = false)
public class RailwayDataRepairMixin {
    @Inject(method = "castMessagePackValueToSKMap", at = @At("HEAD"), cancellable = true)
    private static void onCastMessagePackValueToSKMap(Value value, CallbackInfoReturnable<Map<String, Value>> cir) {
        try {
            if (value == null || !value.isMapValue()) {
                System.err.println("[ManualEnchance-Repair] Corrupted MessagePack value detected! Skipping entry.");
                cir.setReturnValue(new HashMap<>());
            }
        } catch (Exception e) {
            cir.setReturnValue(new HashMap<>());
        }
    }
}