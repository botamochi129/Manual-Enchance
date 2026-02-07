package botamochi129.manual_enchance.mixin;

import mtr.data.Train;
import org.msgpack.value.Value;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(value = Train.class, remap = false)
public class TrainRepairMixin {

    /**
     * MTRがMessagePack（Map型）からTrainを生成する際のコンストラクタに割り込みます。
     * 読み込み前に、混入してしまった「reverser」などの不正データをMapから消去します。
     */
    @Inject(method = "<init>(JFLjava/util/List;Ljava/util/List;IIFZIILjava/util/Map;)V", at = @At("HEAD"))
    private static void onInit(long sidingId, float railLength, List path, List distances, int repeatIndex1, int repeatIndex2, float accelerationConstant, boolean isManualAllowed, int maxManualSpeed, int manualToAutomaticTime, Map map, CallbackInfo ci) {
        // 不正なキーが存在する場合、それらを削除してMTRを安心させる
        if (map.containsKey("reverser")) {
            map.remove("reverser");
            System.out.println("[ManualEnchance-Repair] 異物 'reverser' を除去しました。");
        }
        if (map.containsKey("pantograph_state")) {
            map.remove("pantograph_state");
            System.out.println("[ManualEnchance-Repair] 異物 'pantograph_state' を除去しました。");
        }
        if (map.containsKey("rollsign_indices_map")) {
            map.remove("rollsign_indices_map");
            System.out.println("[ManualEnchance-Repair] 異物 'rollsign_indices_map' を除去しました。");
        }
    }
}