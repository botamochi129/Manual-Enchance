package com.manual_enchance.client.mixin;

import com.google.gson.JsonObject;
import com.manual_enchance.Manual_enchance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = mtr.client.ICustomResources.class, remap = false)
public interface ICustomResourcesMixin {

    @Inject(method = "createCustomTrainSchema", at = @At("TAIL"))
    private static void onAddHornSchema(JsonObject jsonObject, String id, String name, String description, String wikipediaArticle, String color, String gangwayConnectionId, String trainBarrierId, String doorAnimationType, boolean b, float v, CallbackInfo ci) {

        // 1. MTRが無視する前に、生データ(jsonObject)から警笛IDを抜き取ってMapに保存
        if (jsonObject.has("horn_sound_base_id")) {
            String hornId = jsonObject.get("horn_sound_base_id").getAsString();

            // id は "20m_4d_straight" のような形式で渡されます
            Manual_enchance.HORN_MAP.put(id, hornId);

            // ログを出して確認
            System.out.println("[ManualEnchance] Schema Hook Success: " + id + " -> " + hornId);
        }

        // 2. MTRのスキーマ自体にも追加（エラー防止のため）
        if (!jsonObject.has("horn_sound_base_id")) {
            jsonObject.addProperty("horn_sound_base_id", "");
        }
    }
}