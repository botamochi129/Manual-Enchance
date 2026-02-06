package botamochi129.manual_enchance.client.mixin;

import com.google.gson.JsonObject;
import botamochi129.manual_enchance.Manual_enchance;
import mtr.client.ICustomResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ICustomResources.class, remap = false)
public interface ICustomResourcesMixin {

    /**
     * mtr_custom_resources.json の custom_trains セクションを解析するラムダ式に割り込みます。
     * ここでは引数 jsonObject が各車両の設定（"20m_4d_straight" の中身）、
     * 引数 id が JSON のキー（"20m_4d_straight"）そのものです。
     */
    @Inject(method = "createCustomTrainSchema", at = @At("HEAD"))
    private static void onReadCustomTrain(JsonObject jsonObject, String id, String name, String description, String wikipediaArticle, String color, String gangwayConnectionId, String trainBarrierId, String doorAnimationType, boolean renderDoorOverlay, float riderOffset, CallbackInfo ci) {
        if (jsonObject != null && jsonObject.has("horn_sound_base_id")) {
            String hornId = jsonObject.get("horn_sound_base_id").getAsString();

            // ID をキーに保存 (mtr_custom_train_... ではなく JSON のキー名で保存)
            Manual_enchance.HORN_MAP.put(id, hornId);

            //System.out.println("[ManualEnchance] Found Horn: " + id + " -> " + hornId);
        }
    }
}