package botamochi129.manual_enchance.client;

import botamochi129.manual_enchance.Manual_enchance;
import botamochi129.manual_enchance.client.gui.RollsignScreen;
import botamochi129.manual_enchance.client.mixin.AccessorKeyBinding;
import botamochi129.manual_enchance.util.TrainAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.FloatBuffer;

public class Manual_enchanceClient implements ClientModInitializer {
    private static KeyBinding keyReverserUp;
    private static KeyBinding keyReverserDown;

    // 前回の値を保持（パケット連打防止用）
    private int lastSentNotch = 0;
    private long lastLogTime = 0;

    private boolean lastButton2Pressed = false;
    private boolean lastButton3Pressed = false;

    private boolean isJoystickInitialNotified = false;

    private static KeyBinding pantoKey;
    private static KeyBinding keyHorn;

    private static KeyBinding keyRollsign;
    private int lastKatoReverser;

    private String lastJoystickName = "";
    private boolean lastDoorButtonPressed = false;
    private boolean lastPantoButtonPressed = false;
    private boolean lastStartButtonPressed = false;

    @Override
    public void onInitializeClient() {
        // --- キーの登録 ---
        keyReverserUp = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.manual_enchance.reverser_up",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_BRACKET,
                "category.manual_enchance"));

        keyReverserDown = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.manual_enchance.reverser_down",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_BACKSLASH,
                "category.manual_enchance"));

        pantoKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.manual_enchance.panto",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                "category.manual_enchance"
        ));

        keyHorn = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.manual_enchance.horn",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.manual_enchance"
        ));

        keyRollsign = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.manual_enchance.rollsign",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_APOSTROPHE,
                "category.manual_enchance"
        ));

        // まとめた形の例（onInitializeClient内）
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // 1. リバーサー操作
            while (keyReverserUp.wasPressed()) sendReverserPacket(true);
            while (keyReverserDown.wasPressed()) sendReverserPacket(false);

            // 2. パンタグラフ操作
            while (pantoKey.wasPressed()) {
                for (mtr.data.TrainClient tc : mtr.client.ClientData.TRAINS) {
                    if (tc.isPlayerRiding(client.player) && tc.isHoldingKey(client.player)) {
                        TrainAccessor acc = (TrainAccessor) tc;
                        int next = (acc.getPantographState() + 1) % 4;
                        acc.setPantographState(next);

                        // --- ここにパケット送信を追加 ---
                        PacketByteBuf buf = PacketByteBufs.create();
                        buf.writeLong(tc.id);   // 列車のID
                        buf.writeInt(next);     // 新しい状態(0-3)
                        ClientPlayNetworking.send(Manual_enchance.PANTO_UPDATE_PACKET, buf);
                        // ----------------------------

                        String[] names = {"DOWN", "5.0m", "W51", "6.0m"};
                        client.player.sendMessage(Text.literal("§b[Pantograph] §f" + names[next]), true);
                        break;
                    }
                }
            }

            // 3. 警笛
            while (keyHorn.wasPressed()) {
                for (mtr.data.TrainClient tc : mtr.client.ClientData.TRAINS) {
                    if (tc.isPlayerRiding(client.player) && tc.isHoldingKey(client.player)) {
                        PacketByteBuf buf = PacketByteBufs.create();
                        buf.writeLong(tc.id);
                        ClientPlayNetworking.send(Manual_enchance.HORN_PACKET_ID, buf);
                        break;
                    }
                }
            }

            // 4. ジョイスティック監視
            if (GLFW.glfwJoystickPresent(GLFW.GLFW_JOYSTICK_1)) {
                pollJoystick(client);
            }

            while (keyRollsign.wasPressed()) {
                for (mtr.data.TrainClient tc : mtr.client.ClientData.TRAINS) {
                    if (tc.isPlayerRiding(client.player)) {
                        client.setScreen(new RollsignScreen(tc));
                        break;
                    }
                }
            }
        });

        // --- サーバーからのリバーサー同期受信 ---
        ClientPlayNetworking.registerGlobalReceiver(Manual_enchance.REVERSER_SYNC_S2C_PACKET_ID, (client, handler, buf, responseSender) -> {
            long trainId = buf.readLong();
            int reverserValue = buf.readInt();
            client.execute(() -> {
                for (mtr.data.TrainClient tc : mtr.client.ClientData.TRAINS) {
                    if (tc.id == trainId) {
                        if (tc instanceof TrainAccessor accessor) {
                            accessor.setReverser(reverserValue);
                        }
                        break;
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Manual_enchance.PANTO_UPDATE_PACKET, (client, handler, buf, responseSender) -> {
            long trainId = buf.readLong();
            int newState = buf.readInt();

            client.execute(() -> {
                // ClientData内の全列車からIDが一致するものを探す
                mtr.client.ClientData.TRAINS.forEach(train -> {
                    if (train.id == trainId) {
                        ((TrainAccessor) train).setPantographState(newState);
                    }
                });
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Manual_enchance.HORN_PACKET_ID, (client, handler, buf, responseSender) -> {
            long trainId = buf.readLong();
            //System.out.println("Received Horn Packet for ID: " + trainId);
            client.execute(() -> {
                if (client.world == null) return;

                for (mtr.data.TrainClient tc : mtr.client.ClientData.TRAINS) {
                    if (tc.id == trainId) {
                        String soundId = ((TrainAccessor) tc).getHornSoundId();

                        if (soundId != null && !soundId.isEmpty()) {
                            // 暫定でプレイヤー位置
                            client.world.playSound(
                                    client.player.getX(),
                                    client.player.getY(),
                                    client.player.getZ(),
                                    new SoundEvent(new Identifier(soundId)),
                                    SoundCategory.BLOCKS,
                                    1.0F, 1.0F, false
                            );
                        }
                        break;
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Manual_enchance.ROLLSIGN_UPDATE_PACKET, (client, handler, buf, responseSender) -> {
            long trainId = buf.readLong();
            String rollsignId = buf.readString(); // IDを読み取る
            int nextIndex = buf.readInt();
            client.execute(() -> {
                mtr.client.ClientData.TRAINS.forEach(train -> {
                    if (train.id == trainId) {
                        // 引数に ID を追加
                        ((TrainAccessor) train).setRollsignIndex(rollsignId, nextIndex);
                    }
                });
            });
        });
    }

    private void pollJoystick(MinecraftClient client) {
        if (!GLFW.glfwJoystickPresent(GLFW.GLFW_JOYSTICK_1)) {
            lastJoystickName = ""; // 切断時はリセット
            return;
        }

        String currentName = GLFW.glfwGetJoystickName(GLFW.GLFW_JOYSTICK_1);

        // 名前が変わった（または新規接続）ときだけ通知
        if (currentName != null && !currentName.equals(lastJoystickName)) {
            client.player.sendMessage(Text.literal("§b[Manual Enhance] §fコントローラー使用中: §e" + currentName), false);
            lastJoystickName = currentName;
        }

        FloatBuffer axes = GLFW.glfwGetJoystickAxes(GLFW.GLFW_JOYSTICK_1);
        java.nio.ByteBuffer buttons = GLFW.glfwGetJoystickButtons(GLFW.GLFW_JOYSTICK_1);
        if (axes == null || buttons == null) return;

        // --- デバイスごとの処理分岐 ---
        if (currentName != null && (currentName.contains("KATO") || currentName.contains("Kato"))) {
            handleKatoJoystick(client, axes, buttons);
        } else {
            // デフォルト（ズイキ等）の処理
            handleZuikiJoystick(client, axes, buttons);
        }
    }

    /**
     * KATO EC-1 マスコン用処理
     */
    private void handleKatoJoystick(MinecraftClient client, FloatBuffer axes, java.nio.ByteBuffer buttons) {
        // --- 1. ノッチ処理 (変更なし) ---
        boolean b7  = buttons.get(6) == GLFW.GLFW_PRESS;
        boolean b8  = buttons.get(7) == GLFW.GLFW_PRESS;
        boolean b9  = buttons.get(8) == GLFW.GLFW_PRESS;
        boolean b10 = buttons.get(9) == GLFW.GLFW_PRESS;

        int currentNotch = 0;
        if (b7 && b8 && b9 && b10) currentNotch = 5;
        else if (b7 && b8 && b9 && !b10) currentNotch = 4;
        else if (b7 && b8 && !b9 && b10) currentNotch = 3;
        else if (b7 && b8 && !b9 && !b10) currentNotch = 2;
        else if (b7 && !b8 && b9 && b10) currentNotch = 1;
        else if (b7 && !b8 && b9 && !b10) currentNotch = 0;
        else if (b7 && !b8 && !b9 && b10) currentNotch = -1;
        else if (b7 && !b8 && !b9 && !b10) currentNotch = -2;
        else if (!b7 && b8 && b9 && b10) currentNotch = -3;
        else if (!b7 && b8 && b9 && !b10) currentNotch = -4;
        else if (!b7 && b8 && !b9 && b10) currentNotch = -5;
        else if (!b7 && b8 && !b9 && !b10) currentNotch = -6;
        else if (!b7 && !b8 && b9 && b10) currentNotch = -7;
        else if (!b7 && !b8 && b9 && !b10) currentNotch = -8;
        else if (!b7 && !b8 && !b9 && b10) currentNotch = -9;
        else currentNotch = 0;

        if (currentNotch != lastSentNotch) {
            sendDirectNotchPacket(currentNotch);
            //client.player.sendMessage(Text.literal("§7Notch: §f" + getNotchName(currentNotch)), true);
            lastSentNotch = currentNotch;
        }

        // --- 2. KATOリバーサー処理 (再・再修正) ---
        if (axes.capacity() > 1) {
            float revAxis = axes.get(1);
            int targetReverser = 0; // 0:中立, 1:前進, -1:後進

            // 判定ロジック
            if (revAxis < -0.2f) {
                targetReverser = 1;  // 前進 (F)
            } else if (revAxis > 0.8f) {
                targetReverser = -1; // 後進 (B)
            } else {
                targetReverser = 0;  // 中立 (N)
            }

            // 値が変化した時だけ送信
            if (this.lastKatoReverser != targetReverser) {
                sendDirectReverserPacket(targetReverser);
                this.lastKatoReverser = targetReverser;

                // 表示用配列: Index 0=後進(-1+1), 1=中立(0+1), 2=前進(1+1)
                String[] revNames = {"§c後進", "§7中立", "§b前進"};

                // 安全のために targetReverser + 1 をインデックスにする
                int displayIndex = targetReverser + 1;
                //client.player.sendMessage(Text.literal("§b[Reverser] §f" + revNames[displayIndex]), false);
            }
        }
    }

    /**
     * ズイキ等のデフォルト処理 (これまでの pollJoystick の中身を移動)
     */
    private void handleZuikiJoystick(MinecraftClient client, FloatBuffer axes, java.nio.ByteBuffer buttons) {
        // ノッチ（既存の軸判定）
        float rawValue = axes.get(1);
        int currentNotch = convertAxisToNotch(rawValue);
        if (currentNotch != lastSentNotch) {
            sendDirectNotchPacket(currentNotch);
            //client.player.sendMessage(Text.literal("§7Notch: §f" + getNotchName(currentNotch)), true);
            lastSentNotch = currentNotch;
        }

        // --- ズイキボタン割り当て ---

        // A (ボタン3 -> Index 2): 警笛
        if (buttons.get(2) == GLFW.GLFW_PRESS) sendHornPacket();

        // X (ボタン4 -> Index 3): リバーサーUP (F方向)
        boolean btnX = buttons.get(3) == GLFW.GLFW_PRESS;
        if (btnX && !lastButton2Pressed) sendReverserPacket(true);
        lastButton2Pressed = btnX;

        // Y (ボタン1 -> Index 0): リバーサーDOWN (B方向)
        boolean btnY = buttons.get(0) == GLFW.GLFW_PRESS;
        if (btnY && !lastButton3Pressed) sendReverserPacket(false);
        lastButton3Pressed = btnY;

        // SELECT/マイナス (ボタン9 -> Index 8): パンタグラフ切替
        boolean pantoBtn = buttons.get(8) == GLFW.GLFW_PRESS;
        if (pantoBtn && !lastPantoButtonPressed) togglePantograph(client);
        lastPantoButtonPressed = pantoBtn;

        // START/プラス (ボタン10 -> Index 9): 方向幕UIを開く
        boolean startBtn = buttons.get(9) == GLFW.GLFW_PRESS;
        if (startBtn && !lastStartButtonPressed) openRollsignScreen(client);
        lastStartButtonPressed = startBtn;
    }

    // 補助メソッド：警笛パケット送信
    private void sendHornPacket() {
        MinecraftClient client = MinecraftClient.getInstance();
        for (mtr.data.TrainClient tc : mtr.client.ClientData.TRAINS) {
            if (tc.isPlayerRiding(client.player)) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeLong(tc.id);
                ClientPlayNetworking.send(Manual_enchance.HORN_PACKET_ID, buf);
                break;
            }
        }
    }

    // 補助メソッド：パンタグラフ切替
    private void togglePantograph(MinecraftClient client) {
        for (mtr.data.TrainClient tc : mtr.client.ClientData.TRAINS) {
            if (tc.isPlayerRiding(client.player)) {
                TrainAccessor acc = (TrainAccessor) tc;
                int next = (acc.getPantographState() + 1) % 4;
                acc.setPantographState(next);
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeLong(tc.id);
                buf.writeInt(next);
                ClientPlayNetworking.send(Manual_enchance.PANTO_UPDATE_PACKET, buf);
                break;
            }
        }
    }

    private void setKeyBindState(KeyBinding key, boolean pressed) {
        ((AccessorKeyBinding)key).setPressed(pressed);
        if (pressed) {
            // wasPressed()を呼び出したのと同じカウントを増やす
            ((AccessorKeyBinding)key).setTimesPressed(((AccessorKeyBinding)key).getTimesPressed() + 1);
        }
    }

    // 補助メソッド：方向幕を開く
    private void openRollsignScreen(MinecraftClient client) {
        for (mtr.data.TrainClient tc : mtr.client.ClientData.TRAINS) {
            if (tc.isPlayerRiding(client.player)) {
                client.setScreen(new botamochi129.manual_enchance.client.gui.RollsignScreen(tc));
                break;
            }
        }
    }

    /**
     * リバーサーの値を直接指定して送信する（KATO用）
     * @param value 0: Backward, 1: Neutral, 2: Forward
     */
    private void sendDirectReverserPacket(int value) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        for (mtr.data.TrainClient train : mtr.client.ClientData.TRAINS) {
            if (train.isPlayerRiding(client.player)) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeInt(value);  // int型で 0, 1, 2 を送る
                buf.writeLong(train.id);

                // 重要：ここでズイキ用とは別のID(DIRECT)を使う！
                ClientPlayNetworking.send(Manual_enchance.REVERSER_DIRECT_PACKET_ID, buf);

                if (train instanceof TrainAccessor accessor) {
                    accessor.setReverser(value);
                }
                break;
            }
        }
    }

    // 段位名を文字列にするヘルパー
    private String getNotchName(int notch) {
        if (notch == -9) return "§cEB";
        if (notch < 0) return "§6B" + Math.abs(notch);
        if (notch > 0) return "§aP" + notch;
        return "§fN";
    }

    /**
     * ジョイスティックの数値をMTRのノッチに変換する
     */
    private int convertAxisToNotch(float value) {
        // ブレーキ側 (負の数)
        // EB付近の判定を厳密にし、B7とB8の範囲を調整
        if (value < -0.98f) return -9; // 非常(EB) ほぼ最大値のみ
        if (value < -0.88f) return -8; // B8
        if (value < -0.78f) return -7; // B7 (ここに入りやすく調整)
        if (value < -0.68f) return -6; // B6
        if (value < -0.58f) return -5;
        if (value < -0.48f) return -4;
        if (value < -0.38f) return -3;
        if (value < -0.28f) return -2;
        if (value < -0.15f) return -1; // B1

        // 中立 (N) - 遊びを少し持たせる
        if (value < 0.15f) return 0;

        // 力行側 (正の数)
        if (value < 0.35f) return 1;  // P1
        if (value < 0.55f) return 2;
        if (value < 0.75f) return 3;
        if (value < 0.92f) return 4;
        return 5;                     // P5
    }

    private void sendDirectNotchPacket(int notch) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        for (mtr.data.TrainClient train : mtr.client.ClientData.TRAINS) {
            if (train.isPlayerRiding(client.player)) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeInt(notch);
                buf.writeLong(train.id);
                ClientPlayNetworking.send(Manual_enchance.DIRECT_NOTCH_PACKET_ID, buf);

                // クライアント側（HUD）にも即座に反映
                if (train instanceof TrainAccessor accessor) {
                    accessor.setManualNotchDirect(notch);
                }
                break;
            }
        }
    }

    private void sendReverserPacket(boolean isUp) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        for (mtr.data.TrainClient train : mtr.client.ClientData.TRAINS) {
            if (train.isPlayerRiding(client.player)) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeBoolean(isUp);
                buf.writeLong(train.id);
                ClientPlayNetworking.send(Manual_enchance.REVERSER_PACKET_ID, buf);

                if (train instanceof TrainAccessor accessor) {
                    accessor.changeReverser(isUp);
                }
                break;
            }
        }
    }

    private void sendRollsignPacket(boolean isUp) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        for (mtr.data.TrainClient tc : mtr.client.ClientData.TRAINS) {
            if (tc.isPlayerRiding(client.player) && tc.isHoldingKey(client.player)) {
                String rollsignId = "default";
                int currentIndex = ((TrainAccessor) tc).getRollsignIndex(rollsignId);
                int nextIndex = isUp ? currentIndex + 1 : currentIndex - 1;
                if (nextIndex < 0) nextIndex = 0; // 必要に応じて最大値制限も追加

                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeString(rollsignId); // パケットにIDを書き込む
                buf.writeInt(nextIndex);
                ClientPlayNetworking.send(Manual_enchance.ROLLSIGN_UPDATE_PACKET, buf);
                break;
            }
        }
    }
}