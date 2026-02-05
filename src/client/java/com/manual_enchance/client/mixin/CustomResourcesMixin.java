package com.manual_enchance.client.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.manual_enchance.Manual_enchance;
import mtr.client.CustomResources;
import net.minecraft.resource.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;

@Mixin(value = CustomResources.class, remap = false)
public class CustomResourcesMixin {

    // 【修正ポイント1】 ラムダを狙わず、各車両の登録処理を直接フックします
    // createCustomTrainSchema を呼び出している箇所に割り込みます
    @Inject(method = "reload", at = @At(value = "INVOKE", target = "Lmtr/client/CustomResources;createCustomTrainSchema(Lcom/google/gson/JsonObject;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZF)V"))
    private static void onInvokeCreateSchema(ResourceManager manager, CallbackInfo ci) {
        // ここは特定のためのマーカーです
    }

    // 【修正ポイント2】 引数の数と型をMTRのソースと完全に一致させます
    // ログに出ていた「読み込みループ」の中で、JsonObjectから直接値を奪います
    @Inject(method = "createCustomTrainSchema", at = @At("HEAD"))
    private static void onAddHorn(JsonObject jsonObject, String id, String name, String description, String wikipediaArticle, String color, String gangwayConnectionId, String trainBarrierId, String doorAnimationType, boolean renderDoorOverlay, boolean isJacobsBogie, float riderOffset, CallbackInfo ci) {
        if (jsonObject.has("horn_sound_base_id")) {
            String hornId = jsonObject.get("horn_sound_base_id").getAsString();

            // Mapに保存 (例: 20m_4d_straight -> mtr:horn_1)
            Manual_enchance.HORN_MAP.put(id, hornId);

            System.out.println("[ManualEnchance] Map Success: " + id + " -> " + hornId);
        }
    }

    @Inject(method = "reload", at = @At("TAIL"))
    private static void onReloadTail(ResourceManager manager, CallbackInfo ci) {
        System.out.println("[ManualEnchance] Reload Complete. Map Size: " + Manual_enchance.HORN_MAP.size());
    }
}