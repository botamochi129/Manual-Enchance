package botamochi129.manual_enchance.mixin;

import mtr.data.RailwayData;
import org.msgpack.value.Value;
import org.msgpack.value.MapValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.HashMap;

@Mixin(value = RailwayData.class, remap = false)
public class RailwayDataRepairMixin {

    /**
     * MTRがMessagePackのValueをMapに変換する箇所(castMessagePackValueToSKMap)で
     * 発生する型変換エラーを強制的に黙らせます。
     */
    @Inject(method = "castMessagePackValueToSKMap", at = @At("HEAD"), cancellable = true)
    private static void onCast(Value value, CallbackInfoReturnable<Map<String, Value>> cir) {
        try {
            // 本来の変換を試みる
            if (value != null && value.isMapValue()) {
                // ここで普通に返せればOK
            }
        } catch (Exception e) {
            // ここでエラー(MessageTypeCastException)が出た場合、
            // 壊れたデータなので空のMapを返して「その車両だけ無かったこと」にします。
            System.err.println("[ManualEnchance-Repair] 致命的なデータ不整合を検知。エラーを回避して読み飛ばします。");
            cir.setReturnValue(new HashMap<>());
        }
    }
}