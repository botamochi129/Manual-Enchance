package com.manual_enchance.client;

import com.manual_enchance.Manual_enchance;
import com.manual_enchance.util.TrainAccessor;
import mtr.data.TrainClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.datafixer.fix.EntityMinecartIdentifiersFix;
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

    private static KeyBinding pantoKey;
    private static KeyBinding keyHorn;

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

            // 3. ジョイスティック監視
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
            System.out.println("Received Horn Packet for ID: " + trainId);
            client.execute(() -> {
                if (client.world == null) return;

                for (mtr.data.TrainClient tc : mtr.client.ClientData.TRAINS) {
                    if (tc.id == trainId) {
                        String soundId = ((TrainAccessor) tc).getHornSoundId();

                        if (soundId != null && !soundId.isEmpty()) {
                            // --- 座標取得の修正 ---
                            // tc.vehicleRidingClient.getViewOffset() はプレイヤーの視点オフセットなので、
                            // 列車の絶対座標を取得するには、もっとも簡単な方法として
                            // プレイヤーが乗っているならその位置、そうでないなら 0両目の位置などを使用します。

                            // MTRの内部で使われている Vec3 座標を利用（例として先頭車両の座標）
                            // simulateTrain等で計算された直後の座標が取れない場合があるため、
                            // 安全に取得できる方法として、クライアントプレイヤーの位置を基準にするか、
                            // tcから直接座標を取得できるフィールドを探します。

                            // MTR 3.x では、tc自体が位置を保持するフィールドが制限されているため、
                            // 最も確実なのは「列車の中心点」または「先頭車」の座標です。
                            // 簡易的にプレイヤーの座標で鳴らすか、tcの内部的な位置計算を利用します。

                            client.world.playSound(
                                    client.player.getX(), // 暫定的にプレイヤー位置。
                                    client.player.getY(), // 本来は列車の座標を送るのがベストです。
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