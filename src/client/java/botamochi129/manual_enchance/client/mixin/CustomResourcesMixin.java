package botamochi129.manual_enchance.client.mixin;

import com.google.gson.JsonObject;
import botamochi129.manual_enchance.Manual_enchance;
import mtr.client.CustomResources;
import mtr.client.DoorAnimationType;
import mtr.client.TrainProperties;
import net.minecraft.resource.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = CustomResources.class, remap = false)
public class CustomResourcesMixin {

    /**
     * MTRが各車両のデータを読み込み、変数を確定させたタイミングで実行されます。
     * 引数の jsonObject は現在処理中の車両のJSONデータそのものです。
     */
    @Inject(
            method = "lambda$reload$2",
            at = @At("HEAD"),
            remap = false
    )
    private static void onReloadEntry(
            ResourceManager manager, JsonObject jsonObject, String gangwayConnectionId, String baseTrainType, DoorAnimationType doorAnimationType, boolean useBveSound, String bveSoundBaseId, String speedSoundBaseId, String doorSoundBaseId, int speedSoundCount, float doorCloseSoundTime, boolean accelSoundAtCoast, boolean constPlaybackSpeed, String trainId, String name, String description, String wikipediaArticle, String textureId, int color, String trainBarrierId, float riderOffset, TrainProperties baseTrainProperties, List customTrains, JsonObject jsonModel, CallbackInfo ci
    ) {
        // jsonObject から直接 horn_sound_base_id を取得
        if (jsonObject != null && jsonObject.has("horn_sound_base_id")) {
            String hornId = jsonObject.get("horn_sound_base_id").getAsString();

            // trainId は既に "mtr_custom_train_..." の形式になっているはずです
            if (!hornId.isEmpty()) {
                Manual_enchance.HORN_MAP.put(trainId, hornId);
                System.out.println("[ManualEnchance] Successfully mapped: " + trainId + " -> " + hornId);
            }
        }
    }

    @Inject(method = "reload", at = @At("TAIL"), remap = false)
    private static void onReloadTail(ResourceManager manager, CallbackInfo ci) {
        System.out.println("[ManualEnchance] Reload Complete. Total Horns in Map: " + Manual_enchance.HORN_MAP.size());
    }
}