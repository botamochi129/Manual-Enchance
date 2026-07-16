package botamochi129.manual_enchance.client;

import botamochi129.manual_enchance.Main;
import botamochi129.manual_enchance.util.SidingDataManager;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import mtr.client.ClientData;
import mtr.data.Depot;
import mtr.data.IGui;
import mtr.data.Siding;
import mtr.mappings.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

#if MC_VERSION >= "12000"
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
#else
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
#endif

/**
 * Siding の連結先（マスターSiding）を選択するリスト画面。
 * slaveSidingId: この画面を開いた Siding（スレーブ側）
 */
public class CouplingTargetScreen extends Screen {

    private static final int ROW_HEIGHT = 20;
    private static final int PANEL_WIDTH = 200;

    private final long slaveSidingId;
    private final Screen parent;
    private final List<Siding> sidingList = new ArrayList<>();

    private int scrollOffset = 0;

    public CouplingTargetScreen(long slaveSidingId, Screen parent) {
        super(Text.literal("Select Master Siding"));
        this.slaveSidingId = slaveSidingId;
        this.parent = parent;
    }

    @Override
    protected void init() {
        sidingList.clear();
        // 自分自身と循環になる Siding を除いてリストアップ
        for (Siding s : ClientData.SIDINGS) {
            if (s.id != slaveSidingId) sidingList.add(s);
        }

        int listX = (width - PANEL_WIDTH) / 2;
        int startY = IGui.SQUARE_SIZE * 2;
        int visibleRows = (height - startY - IGui.SQUARE_SIZE * 2) / ROW_HEIGHT;

        // 「なし（解除）」ボタン
        #if MC_VERSION >= "11903"
        addRenderableWidget(Button.builder(Text.literal("§c[None] Disconnect"), btn -> sendAndClose(0L))
                .bounds(listX, startY, PANEL_WIDTH, ROW_HEIGHT)
                .build());
        #else
        addRenderableWidget(new Button(listX, startY, PANEL_WIDTH, ROW_HEIGHT,
                Text.literal("§c[None] Disconnect"), btn -> sendAndClose(0L)));
        #endif

        for (int i = 0; i < Math.min(visibleRows - 1, sidingList.size()); i++) {
            int idx = i + scrollOffset;
            if (idx >= sidingList.size()) break;
            Siding siding = sidingList.get(idx);
            int y = startY + ROW_HEIGHT * (i + 1);
            String sidingName = siding.name.isEmpty() ? "Siding #" + siding.id : IGui.formatStationName(siding.name);
            Depot depot = ClientData.DATA_CACHE.sidingIdToDepot.get(siding.id);
            String depotName = (depot != null && depot.name != null) ? IGui.formatStationName(depot.name) : "";
            String label = depotName.isEmpty() ? "§e" + sidingName : "§e" + depotName + " - " + sidingName;
            final long targetId = siding.id;
            #if MC_VERSION >= "11903"
            addRenderableWidget(Button.builder(Text.literal(label), btn -> sendAndClose(targetId))
                    .bounds(listX, y, PANEL_WIDTH, ROW_HEIGHT)
                    .build());
            #else
            addRenderableWidget(new Button(listX, y, PANEL_WIDTH, ROW_HEIGHT,
                    Text.literal(label), btn -> sendAndClose(targetId)));
            #endif
        }

        // 閉じるボタン
        int closeY = height - IGui.SQUARE_SIZE - IGui.TEXT_FIELD_PADDING;
        #if MC_VERSION >= "11903"
        addRenderableWidget(Button.builder(Text.literal("Cancel"), btn -> onClose())
                .bounds(listX, closeY, PANEL_WIDTH, ROW_HEIGHT)
                .build());
        #else
        addRenderableWidget(new Button(listX, closeY, PANEL_WIDTH, ROW_HEIGHT,
                Text.literal("Cancel"), btn -> onClose()));
        #endif
    }

    private void sendAndClose(long masterSidingId) {
        SidingDataManager.setMasterSidingId(slaveSidingId, masterSidingId);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeLong(slaveSidingId);
        buf.writeLong(masterSidingId);
        NetworkManager.sendToServer(Main.SIDING_COUPLING_UPDATE_PACKET, buf);
        onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = Math.max(0, sidingList.size() - 1);
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - delta));
        init(); // スクロール時にボタン再構築
        return true;
    }

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
