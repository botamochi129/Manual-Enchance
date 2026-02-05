package com.manual_enchance.client.mixin;

import mtr.render.RenderDrivingOverlay;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.client.gui.DrawableHelper;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderDrivingOverlay.class, remap = false)
public class RenderDrivingOverlayMixin {

    @Inject(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void cancelMTRHUD(MatrixStack matrices, CallbackInfo ci) {
        ci.cancel();
    }
}
