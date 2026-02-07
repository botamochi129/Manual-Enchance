package botamochi129.manual_enchance.mixin;

import org.msgpack.value.MapValue;
import org.msgpack.value.Value;
import org.msgpack.value.impl.AbstractImmutableRawValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;

// ログの最上段にある AbstractImmutableValue を直接ターゲットにします
@Mixin(value = AbstractImmutableRawValue.class, remap = false)
public class MessagePackRepairMixin {

    /**
     * あらゆるデータが MapValue としてキャストされる際の「入り口」を保護します。
     * ここでエラーをキャッチすれば、MTR本体の処理に null が流れるのを防げます。
     */
    @Inject(method = "asMapValue", at = @At("HEAD"), cancellable = true)
    private void onAsMapValue(CallbackInfoReturnable<MapValue> cir) {
        AbstractImmutableRawValue instance = (AbstractImmutableRawValue) (Object) this;

        // もしこのデータがMapではない（あなたが過去に書き込んだ reverser の数値 1 など）場合
        if (!instance.isMapValue()) {
            // エラーで止める代わりに、空のMapを返して「この車両データは無効」として処理させます
            System.err.println("[ManualEnchance-Repair] 不正なデータ型を検知。空のMapを返してスキップします。");
            cir.setReturnValue(org.msgpack.value.ValueFactory.newMap(new HashMap<>()));
        }
    }
}