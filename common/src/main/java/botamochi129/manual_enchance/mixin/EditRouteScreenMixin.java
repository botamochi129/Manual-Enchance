package botamochi129.manual_enchance.mixin;

import botamochi129.manual_enchance.client.CouplingRouteScreen;
import mtr.data.IGui;
import mtr.data.NameColorDataBase;
import mtr.data.Route;
import mtr.mappings.Text;
import mtr.screen.EditNameColorScreenBase;
import mtr.screen.EditRouteScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

#if MC_VERSION >= "12000"
import net.minecraft.client.gui.GuiGraphics;
#else
import com.mojang.blaze3d.vertex.PoseStack;
#endif

@Mixin(value = EditRouteScreen.class)
public abstract class EditRouteScreenMixin extends EditNameColorScreenBase<Route> {

    @Unique private Button buttonCouplingConfig;

    protected EditRouteScreenMixin(Route data, mtr.screen.DashboardScreen dashboardScreen, String nameText, String colorText) {
        super(data, dashboardScreen, nameText, colorText);
    }

    @Inject(method = "init", at = @At("TAIL"), remap = true, require = 0)
    private void onInit(CallbackInfo ci) {
        int btnX = 0;
        int btnY = height - IGui.SQUARE_SIZE - IGui.TEXT_FIELD_PADDING;
        int btnWidth = 120;

        #if MC_VERSION >= "11903"
        buttonCouplingConfig = Button.builder(Text.literal("Coupling Settings..."), btn -> {
            Minecraft.getInstance().setScreen(new CouplingRouteScreen(data.id, this));
        }).bounds(btnX, btnY, btnWidth, 20).build();
        #else
        buttonCouplingConfig = new Button(btnX, btnY, btnWidth, 20,
                Text.literal("Coupling Settings..."), btn -> {
            Minecraft.getInstance().setScreen(new CouplingRouteScreen(data.id, this));
        });
        #endif

        addRenderableWidget(buttonCouplingConfig);
    }

    #if MC_VERSION >= "12000"
    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void onRender(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (buttonCouplingConfig != null) {
            buttonCouplingConfig.visible = true;
        }
    }
    #else
    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void onRender(PoseStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (buttonCouplingConfig != null) {
            buttonCouplingConfig.visible = true;
        }
    }
    #endif
}
