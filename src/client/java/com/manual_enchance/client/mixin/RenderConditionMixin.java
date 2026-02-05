package com.manual_enchance.client.mixin;

import mtr.client.ResourcePackCreatorProperties;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ResourcePackCreatorProperties.RenderCondition.class)
public class RenderConditionMixin {
    // Enumに項目を注入するのは難易度が高いため、
    // 既存の ALL などの判定ロジックを書き換えるか、
    // GUI側で文字列として処理するのが一般的です。
}