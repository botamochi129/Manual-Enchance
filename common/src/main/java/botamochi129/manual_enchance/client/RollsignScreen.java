package botamochi129.manual_enchance.client;

import botamochi129.manual_enchance.Main;
import botamochi129.manual_enchance.util.TrainAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import mtr.client.IDrawing;
import mtr.data.IGui;
import mtr.data.TrainClient;
import mtr.mappings.ButtonMapper;
import mtr.mappings.ScreenMapper;
import mtr.mappings.Text;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
#if MC_VERSION >= "12000"
import net.minecraft.client.gui.GuiGraphics;
#else
import net.minecraft.client.gui.GuiComponent;
#endif

import java.util.ArrayList;
import java.util.List;

public class RollsignScreen extends ScreenMapper {
    private final TrainClient train;
    private final List<String> targetIds;
    private int selectedIdIndex = 0;

    public RollsignScreen(TrainClient train) {
        super(Text.literal("Train Control Panel"));
        this.train = train;
        this.targetIds = new ArrayList<>(((TrainAccessor) train).getRollsignIds());
    }

    @Override
    protected void init() {
        if (targetIds.isEmpty()) return;

        this.addRenderableWidget(new ButtonMapper(this.width / 2 - 100, 80, 20, 20, Text.literal("<"), (button) -> {
            sendUpdate(-1);
        }) {
        });

        this.addRenderableWidget(new ButtonMapper(this.width / 2 + 80, 80, 20, 20, Text.literal(">"), (button) -> {
            sendUpdate(1);
        }) {
        });

        this.addRenderableWidget(new ButtonMapper(this.width / 2 - 50, 120, 100, 20, Text.literal("Switch Part"), (button) -> {
            if (!targetIds.isEmpty()) {
                selectedIdIndex = (selectedIdIndex + 1) % targetIds.size();
            }
        }) {
        });
    }

    private void sendUpdate(int delta) {
        if (targetIds.isEmpty()) return;

        String rollsignId = targetIds.get(selectedIdIndex);
        int currentIndex = ((TrainAccessor) train).getRollsignIndex(rollsignId);
        int totalSteps = ((TrainAccessor) train).getRollsignSteps(rollsignId);

        int nextIndex = (currentIndex + delta + totalSteps) % totalSteps;

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeLong(train.id);
        buf.writeUtf(rollsignId); // writeString -> writeUtf
        buf.writeInt(nextIndex);
        NetworkManager.sendToServer(Main.ROLLSIGN_UPDATE_PACKET, buf);
    }

    #if MC_VERSION >= "12000"
        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            this.renderBackground(graphics);
            super.render(graphics, mouseX, mouseY, delta);
        String currentId = targetIds.isEmpty() ? "No Rollsign Found" : targetIds.get(selectedIdIndex);
            graphics.drawCenteredString(this.font, "Part: " + currentId, this.width / 2, 85, 0xFFFFFF);
        if (!targetIds.isEmpty()) {
            int currentIndex = ((TrainAccessor) train).getRollsignIndex(currentId);
            int totalSteps = ((TrainAccessor) train).getRollsignSteps(currentId);
            graphics.drawCenteredString(this.font,
                    "Index: " + currentIndex + " / " + (totalSteps - 1),
                    this.width / 2, 100, 0xAAAAAA);
        }
        }
    #else
    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float delta) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, delta);
        String currentId = targetIds.isEmpty() ? "No Rollsign Found" : targetIds.get(selectedIdIndex);
        drawCenteredString(poseStack, this.font, "Part: " + currentId, this.width / 2, 85, 0xFFFFFF);
        if (!targetIds.isEmpty()) {
            int currentIndex = ((TrainAccessor) train).getRollsignIndex(currentId);
            int totalSteps = ((TrainAccessor) train).getRollsignSteps(currentId);
            drawCenteredString(poseStack, this.font,
                    "Index: " + currentIndex + " / " + (totalSteps - 1),
                    this.width / 2, 100, 0xAAAAAA);
        }
    }
    #endif

    @Override
    public boolean isPauseScreen() { // shouldPause -> isPauseScreen
        return false;
    }
}