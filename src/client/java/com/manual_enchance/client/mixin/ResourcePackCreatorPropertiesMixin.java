package com.manual_enchance.client.mixin;

import com.google.gson.JsonObject;
import mtr.client.ResourcePackCreatorProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.List;

@Mixin(ResourcePackCreatorProperties.class)
public abstract class ResourcePackCreatorPropertiesMixin {

    @Unique
    private static final List<String> CUSTOM_LIST = Arrays.asList(
            "PANTAGRAPH_DOWN",
            "PANTAGRAPH_5M",
            "PANTAGRAPH_W51",
            "PANTAGRAPH_6M"
    );

    @Inject(method = "editPartRenderCondition", at = @At("HEAD"), cancellable = true, remap = false)
    private void onEditPartRenderConditionHead(int index, CallbackInfo ci) {
        ResourcePackCreatorProperties self = (ResourcePackCreatorProperties)(Object)this;
        JsonObject partObject = self.getPropertiesPartsArray().get(index).getAsJsonObject();
        String current = partObject.get("render_condition").getAsString();

        String next;
        // 1. 標準Enumの最後(MOVING_BACKWARDS)なら、独自リストの最初へ
        if (current.equals("MOVING_BACKWARDS")) {
            next = CUSTOM_LIST.get(0);
        }
        // 2. 独自リストの中にいる場合
        else if (CUSTOM_LIST.contains(current)) {
            int customIdx = CUSTOM_LIST.indexOf(current);
            if (customIdx < CUSTOM_LIST.size() - 1) {
                // リストの次へ
                next = CUSTOM_LIST.get(customIdx + 1);
            } else {
                // 独自リストの最後なら、標準の最初(ALL)へ
                next = "ALL";
            }
        } else {
            // 3. それ以外（標準Enumの途中）は、そのまま標準の処理(cycleEnumProperty)に任せる
            return;
        }

        // 自前で書き換えて、元のメソッドの実行をキャンセルする
        partObject.addProperty("render_condition", next);
        ((ResourcePackCreatorPropertiesAccessor)self).callUpdateModel();
        ci.cancel();
    }
}