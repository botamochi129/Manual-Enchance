package botamochi129.manual_enchance.client.gui;

import botamochi129.manual_enchance.Manual_enchance;
import botamochi129.manual_enchance.util.TrainAccessor;
import mtr.data.TrainClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

public class RollsignScreen extends Screen {
    private final TrainClient train;
    private final java.util.List<String> targetIds; // SetをList化して保持
    private int selectedIdIndex = 0;

    public RollsignScreen(TrainClient train) {
        super(Text.literal("Train Control Panel"));
        this.train = train;
        // SetをListに変換して、添え字でアクセスできるようにする
        this.targetIds = new java.util.ArrayList<>(((TrainAccessor) train).getRollsignIds());
    }

    @Override
    protected void init() {
        // IDが一つもない場合の安全策
        if (targetIds.isEmpty()) return;

        // 「前へ」ボタン
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 100, 80, 20, 20, Text.literal("<"), (button) -> {
            sendUpdate(-1);
        }));

        // 「次へ」ボタン
        this.addDrawableChild(new ButtonWidget(this.width / 2 + 80, 80, 20, 20, Text.literal(">"), (button) -> {
            sendUpdate(1);
        }));

        // パーツ切り替えボタン
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 50, 120, 100, 20, Text.literal("Switch Part"), (button) -> {
            if (!targetIds.isEmpty()) {
                selectedIdIndex = (selectedIdIndex + 1) % targetIds.size();
            }
        }));
    }

    private void sendUpdate(int delta) {
        if (targetIds.isEmpty()) return;

        String rollsignId = targetIds.get(selectedIdIndex);
        int currentIndex = ((TrainAccessor) train).getRollsignIndex(rollsignId);
        int totalSteps = ((TrainAccessor) train).getRollsignSteps(rollsignId);

        // ループ処理の計算
        // (現在の値 + 増分 + 最大数) % 最大数  とすることで、マイナス方向のループも安全に行えます
        int nextIndex = (currentIndex + delta + totalSteps) % totalSteps;

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeLong(train.id);
        buf.writeString(rollsignId);
        buf.writeInt(nextIndex);
        ClientPlayNetworking.send(Manual_enchance.ROLLSIGN_UPDATE_PACKET, buf);
    }

    @Override
    public void render(net.minecraft.client.util.math.MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);

        String currentId = targetIds.isEmpty() ? "No Rollsign Found" : targetIds.get(selectedIdIndex);
        drawCenteredText(matrices, this.textRenderer, "Part: " + currentId, this.width / 2, 85, 0xFFFFFF);

        if (!targetIds.isEmpty()) {
            int currentIndex = ((TrainAccessor) train).getRollsignIndex(currentId);
            int totalSteps = ((TrainAccessor) train).getRollsignSteps(currentId);
            drawCenteredText(matrices, this.textRenderer,
                    "Index: " + currentIndex + " / " + (totalSteps - 1), // 0-indexed表示
                    this.width / 2, 100, 0xAAAAAA);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
