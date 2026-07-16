package botamochi129.manual_enchance.mixin;

import botamochi129.manual_enchance.servlet.ManualEnchanceServletHandler;
import mtr.libraries.org.eclipse.jetty.servlet.ServletContextHandler;
import mtr.libraries.org.eclipse.jetty.servlet.ServletHolder;
import mtr.servlet.Webserver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.net.URL;

@Mixin(value = Webserver.class, remap = false)
public class WebserverMixin {

    @Inject(method = "init", at = @At("RETURN"), remap = false, locals = LocalCapture.CAPTURE_FAILHARD)
    private static void manualEnchance$onWebserverInit(CallbackInfo ci, ServletContextHandler context, URL url, ServletHolder servletHolder) {
        context.addServlet(ManualEnchanceServletHandler.class, "/me/*");
    }
}
