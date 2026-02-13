package botamochi129.manual_enchance.forge.mixin;

import forge.cn.zbx1425.mtrsteamloco.render.scripting.train.TrainScriptContext;
import mtr.data.TrainClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = TrainScriptContext.class, remap = false)
public abstract class TrainScriptContextMixin {

    @Shadow
    public TrainClient train;

    @Unique
    public void playHorn() {
        // 先ほど MainClient に作成したローカル再生メソッドを呼び出す
        botamochi129.manual_enchance.MainClient.playHornLocal(train.id);
    }
}