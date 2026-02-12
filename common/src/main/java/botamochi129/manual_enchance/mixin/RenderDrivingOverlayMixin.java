package botamochi129.manual_enchance.mixin;

import mtr.render.RenderDrivingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

#if MC_VERSION >= "12000" 
import net.minecraft.client.gui.GuiGraphics;
#else
import com.mojang.blaze3d.vertex.PoseStack;
#endif

@Mixin(value = RenderDrivingOverlay.class, remap = false)
public class RenderDrivingOverlayMixin {

    @Inject(method = "render*", at = @At("HEAD"), cancellable = true)
    #if MC_VERSION >= "12000" 
    private static void cancelMTRHUD(GuiGraphics graphics, CallbackInfo ci) {
        // 1.20.1用の処理
        ci.cancel();
    }
    #else
    private static void cancelMTRHUD(PoseStack poseStack, CallbackInfo ci) {
        // 1.19.2以前用の処理
        ci.cancel();
    }
    #endif
}