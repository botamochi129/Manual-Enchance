package botamochi129.manual_enchance.client;

import mtr.data.IGui;
import mtr.mappings.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

#if MC_VERSION >= "12000"
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
#else
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
#endif

public class CouplingRouteUncoupleConfig extends Screen {

    private static final int PANEL_WIDTH = 200;

    private final CouplingRouteScreen parent;
    private final int entryIndex;
    private int splitTrainIndex;

    public CouplingRouteUncoupleConfig(int entryIndex, CouplingRouteScreen parent, int currentSplitIndex) {
        super(Text.literal("Uncouple Position"));
        this.entryIndex = entryIndex;
        this.parent = parent;
        this.splitTrainIndex = Math.max(1, currentSplitIndex);
    }

    @Override
    protected void init() {
        super.init();
        int listX = (width - PANEL_WIDTH) / 2;
        int centerY = height / 2;

        addRenderableWidget(createButton(listX, centerY - 40, PANEL_WIDTH, 20,
                Text.literal("§6Split after which train?"), btn -> {}));

        addRenderableWidget(createButton(listX, centerY - 10, PANEL_WIDTH, 20,
                Text.literal("§eTrain #" + splitTrainIndex), btn -> {}));

        addRenderableWidget(createButton(listX, centerY + 20, PANEL_WIDTH / 2 - 2, 20,
                Text.literal("§c[-]"), btn -> {
            if (splitTrainIndex > 1) splitTrainIndex--;
            init();
        }));

        addRenderableWidget(createButton(listX + PANEL_WIDTH / 2 + 2, centerY + 20, PANEL_WIDTH / 2 - 2, 20,
                Text.literal("§a[+]"), btn -> {
            splitTrainIndex++;
            init();
        }));

        addRenderableWidget(createButton(listX, centerY + 50, PANEL_WIDTH, 20,
                Text.literal("§aOK"), btn -> {
            parent.onSplitIndexSet(entryIndex, splitTrainIndex);
            onClose();
        }));

        addRenderableWidget(createButton(listX, centerY + 75, PANEL_WIDTH, 20,
                Text.literal("Cancel"), btn -> onClose()));
    }

    #if MC_VERSION >= "11903"
    private Button createButton(int x, int y, int w, int h, Component text, Button.OnPress onPress) {
        return Button.builder(text, onPress).bounds(x, y, w, h).build();
    }
    #else
    private Button createButton(int x, int y, int w, int h, Component text, Button.OnPress onPress) {
        return new Button(x, y, w, h, text, onPress);
    }
    #endif

    #if MC_VERSION >= "12000"
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, IGui.TEXT_PADDING, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, delta);
    }
    #else
    @Override
    public void render(PoseStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        drawCenteredString(matrices, font, title, width / 2, IGui.TEXT_PADDING, 0xFFFFFF);
        super.render(matrices, mouseX, mouseY, delta);
    }
    #endif

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
