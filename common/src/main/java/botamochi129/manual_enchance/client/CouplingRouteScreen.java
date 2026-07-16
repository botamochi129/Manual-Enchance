package botamochi129.manual_enchance.client;

import botamochi129.manual_enchance.Main;
import botamochi129.manual_enchance.util.RouteCouplingStore;
import botamochi129.manual_enchance.util.RouteCouplingStore.RouteCouplingAction;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import mtr.client.ClientData;
import mtr.data.*;
import mtr.mappings.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.*;

#if MC_VERSION >= "12000"
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
#else
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
#endif

public class CouplingRouteScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int PANEL_WIDTH = 340;
    private static final int ACTION_BTN_W = 50;
    private static final int CFG_BTN_W = 60;

    private final long routeId;
    private final Screen parent;
    private final List<StationEntry> entries = new ArrayList<>();
    private int scrollOffset = 0;

    public CouplingRouteScreen(long routeId, Screen parent) {
        super(Text.literal("Route Coupling Settings"));
        this.routeId = routeId;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        entries.clear();
        Route route = findRoute(routeId);
        if (route != null) {
            for (int i = 0; i < route.platformIds.size(); i++) {
                Route.RoutePlatform rp = route.platformIds.get(i);
                String stationName = getStationName(rp.platformId);
                RouteCouplingAction existing = RouteCouplingStore.getAction(routeId, i);
                entries.add(new StationEntry(i, stationName, existing));
            }
        }
        rebuildButtons();
    }

    #if MC_VERSION >= "11903"
    private Button makeButton(int x, int y, int w, int h, Component text, Button.OnPress onPress) {
        return Button.builder(text, onPress).bounds(x, y, w, h).build();
    }
    #else
    private Button makeButton(int x, int y, int w, int h, Component text, Button.OnPress onPress) {
        return new Button(x, y, w, h, text, onPress);
    }
    #endif

    private void rebuildButtons() {
        clearWidgets();
        int listX = (width - PANEL_WIDTH) / 2;
        int startY = IGui.SQUARE_SIZE * 2;
        int visibleRows = (height - startY - IGui.SQUARE_SIZE * 2) / ROW_HEIGHT;

        addRenderableWidget(makeButton(listX, IGui.TEXT_PADDING, PANEL_WIDTH, 20,
                Text.literal("§6" + getRouteName()), btn -> {}));

        int headerY = startY;
        int labelW = PANEL_WIDTH - ACTION_BTN_W - 4 - CFG_BTN_W;

        for (int i = 0; i < Math.min(visibleRows - 2, entries.size()); i++) {
            int idx = i + scrollOffset;
            if (idx >= entries.size()) break;
            StationEntry entry = entries.get(idx);
            int y = headerY + ROW_HEIGHT * (i + 1);

            // ---- Station label with inline status ----
            String coupleIcon = entry.doCouple
                    ? (entry.targetSidingId != 0L ? " §a\u25C9" + getShortSidingName(entry.targetSidingId) : " §e\u25CB")
                    : "";
            String uncoupleIcon = entry.doUncouple ? " §c#" + entry.splitTrainIndex : "";
            String stationLabel = "§7" + entry.stationName + coupleIcon + uncoupleIcon;
            addRenderableWidget(makeButton(listX, y, labelW, ROW_HEIGHT,
                    Text.literal(stationLabel), btn -> {}));

            // ---- Action cycle button ----
            String actionLabel;
            if (entry.doCouple && entry.doUncouple) actionLabel = "§d[C+U]";
            else if (entry.doCouple) actionLabel = "§a[C]";
            else if (entry.doUncouple) actionLabel = "§c[U]";
            else actionLabel = "§7[-]";
            final int idxFinal = idx;
            addRenderableWidget(makeButton(listX + labelW + 2, y, ACTION_BTN_W, ROW_HEIGHT,
                    Text.literal(actionLabel), btn -> cycleAction(idxFinal)));

            // ---- Config button ----
            if (entry.doCouple || entry.doUncouple) {
                String cfgLabel;
                if (entry.doCouple) {
                    cfgLabel = entry.targetSidingId != 0L ? "Change" : "Select";
                } else {
                    cfgLabel = "Split#" + entry.splitTrainIndex;
                }
                addRenderableWidget(makeButton(listX + labelW + ACTION_BTN_W + 4, y, CFG_BTN_W, ROW_HEIGHT,
                        Text.literal(cfgLabel), btn -> openConfig(idxFinal)));
            }
        }

        int btnY = height - IGui.SQUARE_SIZE - IGui.TEXT_FIELD_PADDING;
        addRenderableWidget(makeButton(listX, btnY, PANEL_WIDTH / 2 - 2, 20,
                Text.literal("§aSave"), btn -> saveAndClose()));
        addRenderableWidget(makeButton(listX + PANEL_WIDTH / 2 + 2, btnY, PANEL_WIDTH / 2 - 2, 20,
                Text.literal("Cancel"), btn -> onClose()));
    }

    private void cycleAction(int idx) {
        if (idx < 0 || idx >= entries.size()) return;
        StationEntry entry = entries.get(idx);
        if (!entry.doCouple && !entry.doUncouple) {
            entry.doCouple = true;
            entry.doUncouple = false;
            entry.targetSidingId = 0L;
            rebuildButtons();
            openSidingPicker(idx);
            return;
        } else if (entry.doCouple && !entry.doUncouple) {
            entry.doCouple = false;
            entry.doUncouple = true;
            entry.targetSidingId = 0L;
            entry.splitTrainIndex = 1;
        } else if (!entry.doCouple && entry.doUncouple) {
            entry.doCouple = true;
            entry.doUncouple = true;
        } else {
            entry.doCouple = false;
            entry.doUncouple = false;
            entry.targetSidingId = 0L;
            entry.splitTrainIndex = 1;
        }
        rebuildButtons();
    }

    private void openConfig(int idx) {
        if (idx < 0 || idx >= entries.size()) return;
        StationEntry entry = entries.get(idx);
        if (entry.doCouple) {
            Minecraft.getInstance().setScreen(new CouplingRouteSidingPicker(idx, this));
        } else {
            Minecraft.getInstance().setScreen(new CouplingRouteUncoupleConfig(idx, this, entry.splitTrainIndex));
        }
    }

    void onSidingSelected(int entryIdx, long sidingId) {
        if (entryIdx >= 0 && entryIdx < entries.size()) {
            StationEntry entry = entries.get(entryIdx);
            entry.doCouple = true;
            entry.targetSidingId = sidingId;
            entry.waitForever = true;
            RouteCouplingStore.setAction(new RouteCouplingAction(
                    routeId, entry.stationIndex, true, entry.doUncouple, sidingId, true, entry.splitTrainIndex));
            Minecraft.getInstance().setScreen(this);
        }
    }

    void onSplitIndexSet(int entryIdx, int splitIndex) {
        if (entryIdx >= 0 && entryIdx < entries.size()) {
            StationEntry entry = entries.get(entryIdx);
            entry.doUncouple = true;
            entry.splitTrainIndex = splitIndex;
            RouteCouplingStore.setAction(new RouteCouplingAction(
                    routeId, entry.stationIndex, entry.doCouple, true, entry.targetSidingId, entry.waitForever, splitIndex));
            Minecraft.getInstance().setScreen(this);
        }
    }

    private void openSidingPicker(int idx) {
        Minecraft.getInstance().setScreen(new CouplingRouteSidingPicker(idx, this));
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "\u2026";
    }

    private Route findRoute(long id) {
        for (Route r : ClientData.ROUTES) {
            if (r.id == id) return r;
        }
        return null;
    }

    private String getRouteName() {
        Route r = findRoute(routeId);
        return r == null ? "Route #" + routeId : IGui.formatStationName(r.name);
    }

    private String getStationName(long platformId) {
        Platform platform = ClientData.DATA_CACHE.platformIdMap.get(platformId);
        if (platform != null) {
            String platName = IGui.formatStationName(platform.name);
            Station station = ClientData.DATA_CACHE.platformIdToStation.get(platformId);
            if (station != null) {
                String staName = IGui.formatStationName(station.name);
                if (!staName.isEmpty()) {
                    return platName.isEmpty() ? staName : staName + "[" + platName + "]";
                }
            }
            return platName.isEmpty() ? "Platform #" + platformId : platName;
        }
        return "Platform #" + platformId;
    }

    private String getShortSidingName(long sidingId) {
        Siding siding = ClientData.DATA_CACHE.sidingIdMap.get(sidingId);
        if (siding != null) {
            String sidingName = siding.name.isEmpty() ? "#" + sidingId : IGui.formatStationName(siding.name);
            Depot depot = ClientData.DATA_CACHE.sidingIdToDepot.get(sidingId);
            if (depot != null && depot.name != null) {
                String depotName = IGui.formatStationName(depot.name);
                if (!depotName.isEmpty()) {
                    return truncate(depotName, 8) + "-" + truncate(sidingName, 10);
                }
            }
            return truncate(sidingName, 14);
        }
        return "#" + sidingId;
    }

    private void saveAndClose() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeLong(routeId);
        buf.writeInt(entries.size());
        for (StationEntry e : entries) {
            buf.writeInt(e.stationIndex);
            buf.writeBoolean(e.doCouple);
            buf.writeBoolean(e.doUncouple);
            buf.writeLong(e.targetSidingId);
            buf.writeBoolean(e.waitForever);
            buf.writeInt(e.splitTrainIndex);
        }
        NetworkManager.sendToServer(Main.ROUTE_COUPLING_UPDATE_PACKET, buf);
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Text.literal("§a[Coupling] Route coupling settings saved"), true);
        }
        onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = Math.max(0, entries.size() - 1);
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - (int)delta));
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

    private static class StationEntry {
        final int stationIndex;
        final String stationName;
        boolean doCouple;
        boolean doUncouple;
        long targetSidingId;
        boolean waitForever;
        int splitTrainIndex = 1;

        StationEntry(int stationIndex, String stationName, RouteCouplingAction existing) {
            this.stationIndex = stationIndex;
            this.stationName = stationName;
            if (existing != null) {
                this.doCouple = existing.doCouple;
                this.doUncouple = existing.doUncouple;
                this.targetSidingId = existing.targetSidingId;
                this.waitForever = existing.waitForever;
                this.splitTrainIndex = existing.splitTrainIndex;
            } else {
                this.doCouple = false;
                this.doUncouple = false;
                this.targetSidingId = 0L;
                this.waitForever = true;
            }
        }
    }
}
