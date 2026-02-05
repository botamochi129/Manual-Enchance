package com.manual_enchance.client;

import com.manual_enchance.Manual_enchance;
import com.manual_enchance.util.TrainAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
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

        // --- 毎ティック入力を監視 ---
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // 1. キーボード監視
            while (keyReverserUp.wasPressed()) {
                sendReverserPacket(true);
            }
            while (keyReverserDown.wasPressed()) {
                sendReverserPacket(false);
            }

            // 2. ジョイスティック（マスコン）監視
            // GLFW_JOYSTICK_1 (0番) を優先的にチェック
            if (GLFW.glfwJoystickPresent(GLFW.GLFW_JOYSTICK_1)) {
                pollJoystick(client);
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
    }

    private void pollJoystick(MinecraftClient client) {
        // 軸データの取得
        FloatBuffer axes = GLFW.glfwGetJoystickAxes(GLFW.GLFW_JOYSTICK_1);
        if (axes == null) return;

        // デバッグ用：1秒に1回、軸の状態をチャットに流す（位置特定のため）
        // 動作確認ができたら、このif文の中をコメントアウトしてください
        if (System.currentTimeMillis() - lastLogTime > 1000) {
            String name = GLFW.glfwGetJoystickName(GLFW.GLFW_JOYSTICK_1);
            client.player.sendMessage(Text.of("Device: " + name + " Axis 1 Value: " + axes.get(1)), true);
            lastLogTime = System.currentTimeMillis();
        }

        // 多くのマスコンでは Index 1 (Y軸) が前後移動です
        if (axes.capacity() > 1) {
            float rawValue = axes.get(1); // -1.0 ～ 1.0
            int currentNotch = convertAxisToNotch(rawValue);

            // 値が変化したときだけ送信
            if (currentNotch != lastSentNotch) {
                sendDirectNotchPacket(currentNotch);
                lastSentNotch = currentNotch;
            }
        }

        // ボタンデータの取得
        java.nio.ByteBuffer buttons = GLFW.glfwGetJoystickButtons(GLFW.GLFW_JOYSTICK_1);
        if (buttons != null && buttons.capacity() > 3) {
            boolean currentButton2 = buttons.get(2) == GLFW.GLFW_PRESS; // Xボタン
            boolean currentButton3 = buttons.get(3) == GLFW.GLFW_PRESS; // Yボタン

            // Xボタン：離した状態から押された瞬間だけ実行
            if (currentButton2 && !lastButton2Pressed) {
                sendReverserPacket(true);
            }
            // Yボタン：離した状態から押された瞬間だけ実行
            if (currentButton3 && !lastButton3Pressed) {
                sendReverserPacket(false);
            }

            // 状態を保存
            lastButton2Pressed = currentButton2;
            lastButton3Pressed = currentButton3;
        }
    }

    /**
     * ジョイスティックの数値をMTRのノッチに変換する
     */
    private int convertAxisToNotch(float value) {
        // ズイキマスコンの物理的なカチカチ（ノッチ）に合わせたしきい値
        // ブレーキ側 (負の数)
        if (value < -0.95f) return -9; // 非常(EB)
        if (value < -0.85f) return -8; // B8
        if (value < -0.75f) return -7;
        if (value < -0.65f) return -6;
        if (value < -0.55f) return -5;
        if (value < -0.45f) return -4;
        if (value < -0.35f) return -3;
        if (value < -0.25f) return -2;
        if (value < -0.15f) return -1; // B1

        // 中立 (N)
        if (value < 0.15f) return 0;

        // 力行側 (正の数)
        if (value < 0.35f) return 1;  // P1
        if (value < 0.55f) return 2;
        if (value < 0.75f) return 3;
        if (value < 0.95f) return 4;
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
}