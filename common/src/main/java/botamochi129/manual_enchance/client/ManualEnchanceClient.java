package botamochi129.manual_enchance.client;

import botamochi129.manual_enchance.util.TrainAccessor;
import dev.architectury.event.events.client.ClientGuiEvent;
import mtr.data.IGui;
import mtr.data.RailType;
import mtr.data.Route;
import mtr.data.Station;
import mtr.data.TrainClient;
import mtr.mappings.UtilitiesClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

public class ManualEnchanceClient {

    public static void init() {
        ClientGuiEvent.RENDER_HUD.register((matrixStack, tickDelta) -> {
            renderManualHUD(matrixStack, tickDelta);
        });
    }

    private static void renderManualHUD(Object matrixStack, float tickDelta) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;

        TrainClient train = null;
        for (TrainClient tc : mtr.client.ClientData.TRAINS) {
            if (tc.isPlayerRiding(client.player) && tc.isHoldingKey(client.player)) {
                train = tc;
                break;
            }
        }

        if (train != null) {
            TrainAccessor accessor = (TrainAccessor) train;
            if (accessor.getIsCurrentlyManual()) {
                float speedKmh = train.getSpeed() * 20 * 3.6f;
                float maxSpeedKmh = 120f;
                int maxManualSpeedInt = train.maxManualSpeed;
                if (maxManualSpeedInt >= 0 && maxManualSpeedInt < RailType.values().length) {
                    maxSpeedKmh = RailType.values()[maxManualSpeedInt].speedLimit;
                }

                // GraphicsHolderを作成して、描画処理を統一
                GraphicsHolder graphicsHolder = new GraphicsHolder();
                graphicsHolder.setupContext(matrixStack);

                drawCustomHUD(client, graphicsHolder, accessor.getManualNotch(), accessor.getReverser(),
                        accessor.manualEnchance$getDoorValue(), speedKmh, maxSpeedKmh, train, accessor);
            }
        }
    }

    private static void drawCustomHUD(Minecraft client, GraphicsHolder graphicsHolder, int notch, int reverser,
                                      float doorValue, float speedKmh, float maxSpeedKmh, TrainClient train, TrainAccessor accessor) {
        int sw = client.getWindow().getGuiScaledWidth();
        int sh = client.getWindow().getGuiScaledHeight();

        int masconX = sw - 60;
        int masconY = sh / 2 - 40;
        int speedoX = sw / 2 - 100;
        int speedoY = sh - 75;

        // 背景 (GuiComponent.fillの代わりにdrawFillを使用)
        graphicsHolder.drawFill(masconX - 5, masconY - 75, masconX + 20, masconY + 55, 0x88000000);

        // リバーサー
        String revLabel = (reverser == 1) ? "F" : (reverser == -1 ? "B" : "N");
        int revColor = (reverser == 1) ? 0xFFFFAA00 : (reverser == -1 ? 0xFFFF5555 : 0xFFFFFFFF);
        graphicsHolder.drawFill(masconX - 30, masconY - 10, masconX - 10, masconY + 10, 0x88000000);

        // 文字描画
        graphicsHolder.drawText(revLabel, masconX - 24, masconY - 4, revColor, true, 255);
        graphicsHolder.drawText("REV", masconX - 32, masconY - 22, 0xFFAAAAAA, true, 255);

        // ノッチ
        graphicsHolder.drawFill(masconX - 2, masconY, masconX + 17, masconY + 1, 0xFFFFFFFF);
        int offset = notch * 8;
        int barColor = (notch > 0) ? 0xFFADFF2F : (notch == -9 ? 0xFFFF0000 : (notch < 0 ? 0xFF5555FF : 0xFFFFFFFF));
        graphicsHolder.drawFill(masconX - 4, masconY + offset - 2, masconX + 19, masconY + offset + 2, barColor);

        String label = (notch > 0) ? "P" + notch : (notch < 0 ? (notch == -9 ? "EB" : "B" + Math.abs(notch)) : "N");
        graphicsHolder.drawText(label, masconX + 25, masconY + offset - 4, 0xFFFFFFFF, true, 255);

        // 戸閉灯
        boolean doorClosed = (doorValue == 0);
        graphicsHolder.drawFill(masconX - 36, masconY + 18, masconX - 6, masconY + 36, 0xFF111111);
        drawCircle(graphicsHolder, masconX - 21, masconY + 27, doorClosed ? 0xFFFFB300 : 0xFF4A3A1A);
        graphicsHolder.drawText("DOOR", masconX - 33, masconY + 10, doorClosed ? 0xFFFFCC66 : 0xFF777777, true, 255);

        drawAnalogSpeedometer(client, graphicsHolder, speedoX, speedoY, speedKmh, maxSpeedKmh);
        int railIndex = train.getIndex(accessor.manualEnchance$getRailProgress(), true);
        drawTIMS(client, graphicsHolder, (sw / 2) - 30, sh - 60, train, railIndex, accessor);
    }

    private static void drawAnalogSpeedometer(Minecraft client, GraphicsHolder graphicsHolder, int cx, int cy, float speed, float maxSpeed) {
        int r = 40;
        drawLargeDisk(graphicsHolder, cx, cy, r, 0xAA222222);
        drawLargeCircleOutline(graphicsHolder, cx, cy, r, 0xFFAAAAAA);

        int step = (maxSpeed > 160) ? 40 : 20;
        for (int s = 0; s <= (int) maxSpeed; s += step) {
            float angle = -225f + (s / maxSpeed) * 270f;
            float rad = (float) Math.toRadians(angle);
            int tx = cx + (int) (Math.cos(rad) * (r - 12));
            int ty = cy + (int) (Math.sin(rad) * (r - 12));
            graphicsHolder.drawText(String.valueOf(s), (int)(tx - client.font.width(String.valueOf(s)) / 2f), ty - 4, 0xBBEEEEEE, false, 255);
        }

        float sAngle = -225f + (Math.min(speed, maxSpeed) / maxSpeed) * 270f;
        float sRad = (float) Math.toRadians(sAngle);
        drawSimpleLine(graphicsHolder, cx, cy, cx + (int) (Math.cos(sRad) * (r - 5)), cy + (int) (Math.sin(sRad) * (r - 5)), 0xFFFF0000);

        String speedStr = String.format("%.0f", speed);
        graphicsHolder.drawCenteredText(speedStr, cx, cy + 12, 0xFF00FF00, true, 255);
        graphicsHolder.drawCenteredText("km/h", cx, cy + 22, 0xFF00FF00, false, 255);
    }

    private static void drawCircle(GraphicsHolder graphicsHolder, int cx, int cy, int color) {
        graphicsHolder.drawFill(cx - 2, cy - 3, cx + 3, cy - 2, color);
        graphicsHolder.drawFill(cx - 3, cy - 2, cx + 4, cy + 2, color);
        graphicsHolder.drawFill(cx - 2, cy + 2, cx + 3, cy + 3, color);
    }

    private static void drawLargeDisk(GraphicsHolder graphicsHolder, int cx, int cy, int r, int color) {
        for (int i = -r; i <= r; i++) {
            int w = (int) Math.sqrt(r * r - i * i);
            graphicsHolder.drawFill(cx - w, cy + i, cx + w, cy + i + 1, color);
        }
    }

    private static void drawLargeCircleOutline(GraphicsHolder graphicsHolder, int cx, int cy, int r, int color) {
        for (int a = 0; a < 360; a += 5) {
            double rad = Math.toRadians(a);
            int px = cx + (int) (Math.cos(rad) * r);
            int py = cy + (int) (Math.sin(rad) * r);
            graphicsHolder.drawFill(px, py, px + 1, py + 1, color);
        }
    }

    private static void drawSimpleLine(GraphicsHolder graphicsHolder, int x1, int y1, int x2, int y2, int color) {
        int dist = (int) Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
        for (int i = 0; i <= dist; i++) {
            float t = (float) i / dist;
            graphicsHolder.drawFill((int) (x1 + (x2 - x1) * t), (int) (y1 + (y2 - y1) * t), (int) (x1 + (x2 - x1) * t) + 1, (int) (y1 + (y2 - y1) * t) + 1, color);
        }
    }

    private static void drawTIMS(Minecraft client, GraphicsHolder graphicsHolder, int x, int y, TrainClient train, int railIndex, TrainAccessor accessor) {
        int timsY = y - 40;
        int width = 165;
        int height = 75;

        graphicsHolder.drawFill(x, timsY, x + width, timsY + height, 0xAA000000);
        graphicsHolder.drawFill(x + 1, timsY + 1, x + width - 1, timsY + height - 1, 0xFF111111);

        String realTime = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        long mcTicks = client.level.getDayTime() % 24000L;
        long mcHour = (mcTicks / 1000 + 6) % 24;
        long mcMin = (mcTicks % 1000) * 60 / 1000;
        String mcTime = String.format("%02d:%02d", mcHour, mcMin);

        float limitKmh = train.getRailSpeed(railIndex) * 20 * 3.6f;
        String limitStr = (limitKmh > 0) ? String.format("%.0f", limitKmh) : "---";

        Route thisRoute = train.getThisRoute();
        Station nextStation = train.getNextStation();

        graphicsHolder.drawText("R:" + realTime + " M:" + mcTime, x + 5, timsY + 5, 0xFFBBBBBB, false, 255);
        String routeName = (thisRoute == null) ? "Not In Service" : IGui.formatStationName(thisRoute.name);
        graphicsHolder.drawText(routeName, x + 5, timsY + 18, 0xFF00FFFF, false, 255);

        if (nextStation != null) {
            String nextName = I18n.get("gui.manual_enchance.tims.next", IGui.formatStationName(nextStation.name));
            graphicsHolder.drawText(nextName, x + 5, timsY + 31, 0xFFFFFFFF, false, 255);
        }

        int limitColor = (train.getSpeed() * 20 * 3.6f > limitKmh + 1) ? 0xFFFF5555 : 0xFFFFAA00;
        graphicsHolder.drawText("LIMIT: " + limitStr + " km/h", x + 5, timsY + 46, limitColor, false, 255);

        boolean isClosed = train.getDoorValue() == 0;
        String doorKey = isClosed ? "gui.manual_enchance.tims.door_closed" : "gui.manual_enchance.tims.door_open";
        graphicsHolder.drawText(I18n.get(doorKey), x + 5, timsY + 61, isClosed ? 0xFF00FF00 : 0xFFFF5555, false, 255);

        String[] pantoNames = {"DOWN", "5.0m", "W51", "6.0m"};
        graphicsHolder.drawText("PANTO: " + pantoNames[accessor.getPantographState()], x + 90, timsY + 46, 0xFFFFFFFF, false, 255);
    }
}