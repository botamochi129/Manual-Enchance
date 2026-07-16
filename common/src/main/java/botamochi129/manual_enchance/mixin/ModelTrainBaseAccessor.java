package botamochi129.manual_enchance.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mtr.mappings.ModelMapper;
import mtr.model.ModelTrainBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ModelTrainBase.class)
public interface ModelTrainBaseAccessor {

    @Invoker(remap = false)
    static void invokeRenderOnce(ModelMapper model, PoseStack matrices, VertexConsumer vertexConsumer, int light, float f1, float f2) {
        throw new AssertionError("Mixin not applied");
    }

    @Invoker(remap = false)
    static void invokeRenderOnceFlipped(ModelMapper model, PoseStack matrices, VertexConsumer vertexConsumer, int light, float f1, float f2) {
        throw new AssertionError("Mixin not applied");
    }
}
