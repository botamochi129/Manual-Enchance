package botamochi129.manual_enchance.client;

import mtr.client.ClientData;
import mtr.data.Depot;
import mtr.data.IGui;
import mtr.data.Siding;
import mtr.mappings.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

#if MC_VERSION >= "12000"
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
#else
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
#endif

public class CouplingRouteSidingPicker extends Screen {

    private static final int ROW_HEIGHT = 20;
    private static final int PANEL_WIDTH = 280;

    private final CouplingRouteScreen parent;
    private final int entryIndex;
    private final List<Siding> allSidings = new ArrayList<>();
    private final List<Siding> filteredSidings = new ArrayList<>();
    private EditBox searchBox;
    private int scrollOffset = 0;

    public CouplingRouteSidingPicker(int entryIndex, CouplingRouteScreen parent) {
        super(Text.literal("Select Target Siding"));
        this.entryIndex = entryIndex;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        allSidings.clear();
        allSidings.addAll(ClientData.SIDINGS);

        int listX = (width - PANEL_WIDTH) / 2;
        int startY = IGui.SQUARE_SIZE * 2;

        // Search box
        #if MC_VERSION >= "12000"
        searchBox = new EditBox(font, listX, startY - IGui.TEXT_FIELD_PADDING - 12, PANEL_WIDTH, 16, Text.literal(""));
        #else
        searchBox = new EditBox(font, listX, startY - IGui.TEXT_FIELD_PADDING - 12, PANEL_WIDTH, 16, Text.literal(""));
        #endif
        searchBox.setMaxLength(50);
        searchBox.setBordered(true);
        searchBox.setVisible(true);
        searchBox.setTextColor(0xFFFFFF);
        addRenderableWidget(searchBox);

        applyFilter();

        int headerY = startY;
        addRenderableWidget(createButton(listX, startY, PANEL_WIDTH, ROW_HEIGHT,
                Text.literal("§7Type to filter..."), btn -> {}));

        int visibleRows = (height - headerY - IGui.SQUARE_SIZE * 2) / ROW_HEIGHT;
        for (int i = 0; i < Math.min(visibleRows - 1, filteredSidings.size()); i++) {
            int idx = i + scrollOffset;
            if (idx >= filteredSidings.size()) break;
            Siding siding = filteredSidings.get(idx);
            int y = headerY + ROW_HEIGHT * (i + 1);
            String depotName = getDepotName(siding.id);
            String sidingName = siding.name.isEmpty() ? "Siding #" + siding.id : IGui.formatStationName(siding.name);
            String label = depotName.isEmpty() ? "§e" + sidingName : "§e" + depotName + " - " + sidingName;
            final long targetId = siding.id;
            addRenderableWidget(createButton(listX, y, PANEL_WIDTH, ROW_HEIGHT,
                    Text.literal(label), btn -> selectSiding(targetId)));
        }

        int cancelY = height - IGui.SQUARE_SIZE - IGui.TEXT_FIELD_PADDING;
        addRenderableWidget(createButton(listX, cancelY, PANEL_WIDTH, ROW_HEIGHT,
                Text.literal("§cCancel"), btn -> onClose()));
    }

    private void applyFilter() {
        String query = searchBox != null ? searchBox.getValue().toLowerCase() : "";
        filteredSidings.clear();
        if (query.isEmpty()) {
            filteredSidings.addAll(allSidings);
        } else {
            for (Siding s : allSidings) {
                String sidingName = s.name.isEmpty() ? "" : IGui.formatStationName(s.name).toLowerCase();
                String depotName = getDepotName(s.id).toLowerCase();
                if (sidingName.contains(query) || depotName.contains(query)) {
                    filteredSidings.add(s);
                }
            }
        }
        scrollOffset = Math.min(scrollOffset, Math.max(0, filteredSidings.size() - 1));
    }

    private void selectSiding(long sidingId) {
        parent.onSidingSelected(entryIndex, sidingId);
        onClose();
    }

    private String getDepotName(long sidingId) {
        Depot depot = ClientData.DATA_CACHE.sidingIdToDepot.get(sidingId);
        if (depot != null && depot.name != null) {
            return IGui.formatStationName(depot.name);
        }
        return "";
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            init();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox != null && searchBox.charTyped(codePoint, modifiers)) {
            init();
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = Math.max(0, filteredSidings.size() - 1);
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - (int) delta));
        init();
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
