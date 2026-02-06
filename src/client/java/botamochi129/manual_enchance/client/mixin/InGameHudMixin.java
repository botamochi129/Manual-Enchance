package botamochi129.manual_enchance.client.mixin;

import botamochi129.manual_enchance.util.TrainAccessor;
import mtr.data.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        // プレイヤーがいない、またはHUD非表示設定なら何もしない
        if (client.player == null || client.options.hudHidden) return;

        TrainClient train = null;
        for (TrainClient tc : mtr.client.ClientData.TRAINS) {
            // 条件1: プレイヤーがその列車に乗っている
            // 条件2: かつ、プレイヤーがマスコンキー（運転キー）を手に持っている
            if (tc.isPlayerRiding(client.player) && tc.isHoldingKey(client.player)) {
                train = tc;
                break;
            }
        }

        // 列車が見つかり、かつ手動運転モードの場合のみ描画
        if (train != null) {
            TrainAccessor accessor = (TrainAccessor) train;
            if (accessor.getIsCurrentlyManual()) {
                float speedKmh = train.getSpeed() * 20 * 3.6f;

                // RailTypeに基づく最高速度の計算
                float maxSpeedKmh = 120f;
                int maxManualSpeedInt = train.maxManualSpeed;
                if (maxManualSpeedInt >= 0 && maxManualSpeedInt < RailType.values().length) {
                    maxSpeedKmh = RailType.values()[maxManualSpeedInt].speedLimit;
                }

                drawCustomHUD(
                        client,
                        matrices,
                        accessor.getManualNotch(),
                        accessor.getReverser(),
                        accessor.manualEnchance$getDoorValue(),
                        speedKmh,
                        maxSpeedKmh,
                        train,
                        accessor
                );
            }
        }
    }

    @Unique
    private void drawCustomHUD(
            MinecraftClient client,
            MatrixStack matrices,
            int notch,
            int reverser,
            float doorValue,
            float speedKmh,
            float maxSpeedKmh,
            TrainClient train,
            TrainAccessor accessor
    ) {
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();

        // 配置座標
        int masconX = sw - 60;
        int masconY = sh / 2 - 40;
        int speedoX = sw / 2 - 100;
        int speedoY = sh - 75;

        // ==========================================
        // 1. マスコン・リバーサー・戸閉灯 (レイアウト修正版)
        // ==========================================
        // 背景
        DrawableHelper.fill(matrices, masconX - 5, masconY - 75, masconX + 20, masconY + 55, 0x88000000);

        // リバーサー
        String revLabel = (reverser == 1) ? "F" : (reverser == -1 ? "B" : "N");
        int revColor = (reverser == 1) ? 0xFFFFAA00 : (reverser == -1 ? 0xFFFF5555 : 0xFFFFFFFF);
        DrawableHelper.fill(matrices, masconX - 30, masconY - 10, masconX - 10, masconY + 10, 0x88000000);
        client.textRenderer.drawWithShadow(matrices, revLabel, masconX - 24, masconY - 4, revColor);
        client.textRenderer.drawWithShadow(matrices, "REV", masconX - 32, masconY - 22, 0xFFAAAAAA);

        // ノッチバー (サイズ固定の修正)
        // Nの位置
        DrawableHelper.fill(matrices, masconX - 2, masconY, masconX + 17, masconY + 1, 0xFFFFFFFF);

        int offset = notch * 8;
        int barTop = masconY + offset - 2;
        int barBottom = masconY + offset + 2;
        int barColor = (notch > 0) ? 0xFFADFF2F : (notch == -9 ? 0xFFFF0000 : (notch < 0 ? 0xFF5555FF : 0xFFFFFFFF));

        // 四角形の高さを 4px (barBottom - barTop) に固定して描画
        DrawableHelper.fill(matrices, masconX - 4, barTop, masconX + 19, barBottom, barColor);

        String label = (notch > 0) ? "P" + notch : (notch < 0 ? (notch == -9 ? "EB" : "B" + Math.abs(notch)) : "N");
        client.textRenderer.drawWithShadow(matrices, label, masconX + 25, masconY + offset - 4, 0xFFFFFFFF);

        // 戸閉灯
        boolean doorClosed = (doorValue == 0);
        DrawableHelper.fill(matrices, masconX - 36, masconY + 18, masconX - 6, masconY + 36, 0xFF111111);
        drawCircle(matrices, masconX - 21, masconY + 27, doorClosed ? 0xFFFFB300 : 0xFF4A3A1A);
        client.textRenderer.drawWithShadow(matrices, "DOOR", masconX - 33, masconY + 10, doorClosed ? 0xFFFFCC66 : 0xFF777777);

        // ==========================================
        // 2. アナログ速度計 (RailType連動版)
        // ==========================================
        drawAnalogSpeedometer(client, matrices, speedoX, speedoY, speedKmh, maxSpeedKmh);

        // ==========================================
        // 3. ディスプレイユニット (TIMS風)
        // ==========================================
        // InGameHudMixin などの中で
        int railIndex = train.getIndex(accessor.manualEnchance$getRailProgress(), true);
        drawTIMS(client, matrices, (client.getWindow().getScaledWidth() / 2) - 30, client.getWindow().getScaledHeight() - 60, train, railIndex, accessor);
    }

    @Unique
    private void drawAnalogSpeedometer(MinecraftClient client, MatrixStack matrices, int cx, int cy, float speed,
                                       float maxSpeed) {
        int r = 40;
        drawLargeDisk(matrices, cx, cy, r, 0xAA222222); // 基盤
        drawLargeCircleOutline(matrices, cx, cy, r, 0xFFAAAAAA); // 外枠

        // 速度目盛りの描画
        int step = (maxSpeed > 160) ? 40 : 20;
        for (int s = 0; s <= (int) maxSpeed; s += step) {
            float angle = -225f + (s / maxSpeed) * 270f;
            float rad = (float) Math.toRadians(angle);
            int tx = cx + (int) (Math.cos(rad) * (r - 12));
            int ty = cy + (int) (Math.sin(rad) * (r - 12));

            String text = String.valueOf(s);
            client.textRenderer.draw(matrices, text, tx - client.textRenderer.getWidth(text) / 2f, ty - 4, 0xBBEEEEEE);
        }

        // 針の計算
        float sAngle = -225f + (Math.min(speed, maxSpeed) / maxSpeed) * 270f;
        float sRad = (float) Math.toRadians(sAngle);
        drawSimpleLine(matrices, cx, cy, cx + (int) (Math.cos(sRad) * (r - 5)), cy + (int) (Math.sin(sRad) * (r - 5)), 0xFFFF0000);

        // デジタル速度
        String speedStr = String.format("%.0f", speed);
        client.textRenderer.drawWithShadow(matrices, speedStr, cx - client.textRenderer.getWidth(speedStr) / 2f, cy + 12, 0xFF00FF00);
        client.textRenderer.draw(matrices, "km/h", cx - client.textRenderer.getWidth("km/h") / 2f, cy + 22, 0xFF00FF00);
    }

    // --- ユーティリティ描画 ---
    @Unique
    private void drawCircle(MatrixStack matrices, int cx, int cy, int color) {
        DrawableHelper.fill(matrices, cx - 2, cy - 3, cx + 3, cy - 2, color);
        DrawableHelper.fill(matrices, cx - 3, cy - 2, cx + 4, cy + 2, color);
        DrawableHelper.fill(matrices, cx - 2, cy + 2, cx + 3, cy + 3, color);
    }

    @Unique
    private void drawLargeDisk(MatrixStack matrices, int cx, int cy, int r, int color) {
        for (int i = -r; i <= r; i++) {
            int w = (int) Math.sqrt(r * r - i * i);
            DrawableHelper.fill(matrices, cx - w, cy + i, cx + w, cy + i + 1, color);
        }
    }

    @Unique
    private void drawLargeCircleOutline(MatrixStack matrices, int cx, int cy, int r, int color) {
        for (int a = 0; a < 360; a += 5) {
            double rad = Math.toRadians(a);
            int px = cx + (int) (Math.cos(rad) * r);
            int py = cy + (int) (Math.sin(rad) * r);
            DrawableHelper.fill(matrices, px, py, px + 1, py + 1, color);
        }
    }

    @Unique
    private void drawSimpleLine(MatrixStack matrices, int x1, int y1, int x2, int y2, int color) {
        int dist = (int) Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
        for (int i = 0; i <= dist; i++) {
            float t = (float) i / dist;
            DrawableHelper.fill(matrices, (int) (x1 + (x2 - x1) * t), (int) (y1 + (y2 - y1) * t), (int) (x1 + (x2 - x1) * t) + 1, (int) (y1 + (y2 - y1) * t) + 1, color);
        }
    }

    @Unique
    private void drawTIMS(MinecraftClient client, MatrixStack matrices, int x, int y, TrainClient train, int railIndex, TrainAccessor accessor) {
        // --- レイアウト設定 ---
        int timsY = y - 40;
        int width = 165; // 秒表示が増えるので少し幅を広げる
        int height = 75;

        DrawableHelper.fill(matrices, x, timsY, x + width, timsY + height, 0xAA000000);
        DrawableHelper.fill(matrices, x + 1, timsY + 1, x + width - 1, timsY + height - 1, 0xFF111111);

        // --- 1. 時刻データの取得 ---
        // リアル時刻 (HH:mm:ss) 秒単位に変更
        String realTime = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());

        // マイクラ時刻 (1000 = 6:00 AM)
        long mcTicks = client.world.getTimeOfDay() % 24000L;
        long mcHour = (mcTicks / 1000 + 6) % 24;
        long mcMin = (mcTicks % 1000) * 60 / 1000;
        String mcTime = String.format("%02d:%02d", mcHour, mcMin);

        // --- 2. 現在の線路の制限速度 (Limit) ---
        float limitKmh = train.getRailSpeed(railIndex) * 20 * 3.6f;
        String limitStr = (limitKmh > 0) ? String.format("%.0f", limitKmh) : "---";

        // --- 3. 運行データの取得 ---
        mtr.data.Route thisRoute = train.getThisRoute();
        mtr.data.Station nextStation = train.getNextStation();

        int titleColor = 0xFF00FFFF;
        int textColor = 0xFFFFFFFF;

        // 1段目: 時刻表示 (Real / MC)
        // 秒が入ると長くなるため、少しフォントサイズ感を意識して配置
        String timeDisplay = "R:" + realTime + " M:" + mcTime;
        client.textRenderer.draw(matrices, timeDisplay, x + 5, timsY + 5, 0xFFBBBBBB);

        // 2段目: 路線名
        String routeName = (thisRoute == null) ? "Not In Service" : mtr.data.IGui.formatStationName(thisRoute.name);
        client.textRenderer.draw(matrices, routeName, x + 5, timsY + 18, titleColor);

        // 3段目: 次駅
        if (nextStation != null) {
            String nextName = I18n.translate("gui.manual_enchance.tims.next", mtr.data.IGui.formatStationName(nextStation.name));
            client.textRenderer.draw(matrices, nextName, x + 5, timsY + 31, textColor);
        }

        // 4段目: 制限速度表示
        String limitDisplay = "LIMIT: " + limitStr + " km/h";
        int limitColor = (train.getSpeed() * 20 * 3.6f > limitKmh + 1) ? 0xFFFF5555 : 0xFFFFAA00;
        client.textRenderer.draw(matrices, limitDisplay, x + 5, timsY + 46, limitColor);

        // 5段目: 戸閉状態
        boolean isClosed = train.getDoorValue() == 0;
        String doorKey = isClosed ? "gui.manual_enchance.tims.door_closed" : "gui.manual_enchance.tims.door_open";
        client.textRenderer.draw(matrices, I18n.translate(doorKey), x + 5, timsY + 61, isClosed ? 0xFF00FF00 : 0xFFFF5555);

        String[] pantoNames = {"DOWN", "5.0m", "W51", "6.0m"};
        int pantoState = accessor.getPantographState();
        client.textRenderer.draw(matrices, "PANTO: " + pantoNames[pantoState], x + 90, timsY + 46, 0xFFFFFFFF);
    }
}