package botamochi129.manual_enchance.mixin;

import botamochi129.manual_enchance.Main;
import botamochi129.manual_enchance.util.SidingAccessor;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import mtr.data.Siding;
import mtr.data.TransportMode;
import mtr.screen.DashboardScreen;
import mtr.screen.SidingScreen;
import mtr.screen.SavedRailScreenBase;
import mtr.mappings.Text;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
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

@Mixin(value = SidingScreen.class)
public abstract class SidingScreenMixin extends SavedRailScreenBase<Siding> {

    @Shadow(remap = false) private boolean isSelectingTrain;
    @Unique
    private Button buttonDefaultPanto;

    public SidingScreenMixin(Siding savedRailBase, TransportMode transportMode, DashboardScreen dashboardScreen, Component... additionalTexts) {
        super(savedRailBase, transportMode, dashboardScreen, additionalTexts);
    }

    @Inject(
            method = {"init", "method_25426"},
            at = @At("TAIL"),
            remap = true,
            require = 0
    )
    private void initPantoButton(CallbackInfo ci) {
        #if MC_VERSION >= "11903"
        buttonDefaultPanto = Button.builder(Text.literal(""), button -> {
            SidingAccessor accessor = (SidingAccessor) savedRailBase;
            int nextState = (accessor.manualEnchance$getDefaultPantographState() + 1) % 4;
            accessor.manualEnchance$setDefaultPantographState(nextState);
            updatePantoButtonText();

            // サーバーへ通知
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeLong(savedRailBase.id);
            buf.writeInt(nextState);
            NetworkManager.sendToServer(Main.SIDING_PANTO_UPDATE_PACKET, buf);
        })
        .bounds(0, 0, 0, 20)
        .build();
        #else
        buttonDefaultPanto = new Button(0, 0, 0, 20, Text.literal(""), button -> {
            SidingAccessor accessor = (SidingAccessor) savedRailBase;
            int nextState = (accessor.manualEnchance$getDefaultPantographState() + 1) % 4;
            accessor.manualEnchance$setDefaultPantographState(nextState);
            updatePantoButtonText();

            // サーバーへ通知
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeLong(savedRailBase.id);
            buf.writeInt(nextState);
            NetworkManager.sendToServer(Main.SIDING_PANTO_UPDATE_PACKET, buf);
        });
        #endif

        int x = SQUARE_SIZE + textWidth;
        int y = SQUARE_SIZE * 5 + TEXT_FIELD_PADDING * 2;

        #if MC_VERSION >= "11903"
        buttonDefaultPanto.setX(x);
        buttonDefaultPanto.setY(y);
        #else
        buttonDefaultPanto.x = x;
        buttonDefaultPanto.y = y;
        #endif
        buttonDefaultPanto.setWidth(width - textWidth - SQUARE_SIZE * 2);

        updatePantoButtonText();
        this.addRenderableWidget(buttonDefaultPanto);
    }

    @Unique
    private void updatePantoButtonText() {
        int state = ((SidingAccessor)savedRailBase).manualEnchance$getDefaultPantographState();
        String[] names = {"DOWN", "5.0m", "W51", "6.0m"};
        buttonDefaultPanto.setMessage(Text.literal("Default: " + names[state]));
    }

    #if MC_VERSION >= "12000"
    @Inject(
        method = {"render", "method_25394"}, // 併記
        at = @At("TAIL"),
        require = 0
    )
    private void renderPantoLabel(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!isSelectingTrain) {
            graphics.drawString(this.font, Text.translatable("gui.manual_enchance.default_panto"),
                    SQUARE_SIZE, SQUARE_SIZE * 5 + TEXT_FIELD_PADDING * 2 + TEXT_PADDING, 0xFFFFFFFF);
        }
    }
    #else
    @Inject(
            method = {"render", "method_25394"}, // 併記
            at = @At("TAIL"),
            require = 0
    )
    private void renderPantoLabel(PoseStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!isSelectingTrain) {
            this.font.draw(matrices, Text.translatable("gui.manual_enchance.default_panto"),
                    SQUARE_SIZE, SQUARE_SIZE * 5 + TEXT_FIELD_PADDING * 2 + TEXT_PADDING, 0xFFFFFFFF);
        }
    }
    #endif
}