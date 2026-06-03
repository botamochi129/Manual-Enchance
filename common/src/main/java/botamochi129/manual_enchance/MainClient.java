package botamochi129.manual_enchance;

import botamochi129.manual_enchance.client.RollsignScreen;
import botamochi129.manual_enchance.util.TrainAccessor;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import io.netty.buffer.Unpooled;
import mtr.client.ClientData;
import mtr.data.TrainClient;
import mtr.mappings.RegistryUtilities;
import mtr.mappings.Text;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public class MainClient {

	private static KeyMapping keyReverserUp;
	private static KeyMapping keyReverserDown;
	private static KeyMapping pantoKey;
	private static KeyMapping keyHorn;
	private static KeyMapping keyRollsign;
	private static KeyMapping keyCoupling;

	private static int lastSentNotch = 0;
	private static boolean lastButton2Pressed = false;
	private static boolean lastButton3Pressed = false;
	private static String lastJoystickName = "";
	private static boolean lastPantoButtonPressed = false;
	private static boolean lastStartButtonPressed = false;
	private static int lastKatoReverser = 0;

	// 列車ごとの送信シーケンスおよびクライアント側で最後に適用したシーケンス
	private static final java.util.Map<Long, Long> localNotchSeqMap = new java.util.HashMap<>();
	private static final java.util.Map<Long, Long> lastAppliedNotchSeq = new java.util.HashMap<>();

	public static void init() {
		// --- キーバインディングの登録 ---
		keyReverserUp = new KeyMapping("key.manual_enchance.reverser_up", GLFW.GLFW_KEY_RIGHT_BRACKET, "category.manual_enchance");
		keyReverserDown = new KeyMapping("key.manual_enchance.reverser_down", GLFW.GLFW_KEY_BACKSLASH, "category.manual_enchance");
		pantoKey = new KeyMapping("key.manual_enchance.panto", GLFW.GLFW_KEY_P, "category.manual_enchance");
		keyHorn = new KeyMapping("key.manual_enchance.horn", GLFW.GLFW_KEY_RIGHT_SHIFT, "category.manual_enchance");
		keyRollsign = new KeyMapping("key.manual_enchance.rollsign", GLFW.GLFW_KEY_APOSTROPHE, "category.manual_enchance");
		keyCoupling = new KeyMapping("key.manual_enchance.coupling", GLFW.GLFW_KEY_C, "category.manual_enchance");

		KeyMappingRegistry.register(keyReverserUp);
		KeyMappingRegistry.register(keyReverserDown);
		KeyMappingRegistry.register(pantoKey);
		KeyMappingRegistry.register(keyHorn);
		KeyMappingRegistry.register(keyRollsign);
		KeyMappingRegistry.register(keyCoupling);

		// --- クライアントティックイベント ---
		ClientTickEvent.CLIENT_POST.register(client -> {
			if (client.player == null) return;

			// 1. キー入力判定
			while (keyReverserUp.consumeClick()) sendReverserPacket(true);
			while (keyReverserDown.consumeClick()) sendReverserPacket(false);

			while (pantoKey.consumeClick()) {
				for (TrainClient tc : ClientData.TRAINS) {
					if (tc.isPlayerRiding(client.player) && tc.isHoldingKey(client.player)) {
						TrainAccessor acc = (TrainAccessor) tc;
						int next = (acc.getPantographState() + 1) % 4;
						acc.setPantographState(next);

						FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
						buf.writeLong(tc.id);
						buf.writeInt(next);
						NetworkManager.sendToServer(Main.PANTO_UPDATE_PACKET, buf);

						String[] names = {"DOWN", "5.0m", "W51", "6.0m"};
						client.player.displayClientMessage(Text.literal("§b[Pantograph] §f" + names[next]), true);
						break;
					}
				}
			}

			while (keyHorn.consumeClick()) {
				for (TrainClient tc : ClientData.TRAINS) {
					if (tc.isPlayerRiding(client.player) && tc.isHoldingKey(client.player)) {
						sendHornPacket();
						break;
					}
				}
			}

			while (keyRollsign.consumeClick()) {
				for (TrainClient tc : ClientData.TRAINS) {
					if (tc.isPlayerRiding(client.player)) {
						client.setScreen(new RollsignScreen(tc));
						break;
					}
				}
			}

			// 2. ジョイスティック監視
			if (GLFW.glfwJoystickPresent(GLFW.GLFW_JOYSTICK_1)) {
				pollJoystick(client);
			}

			if (client.player == null) return;

		while (keyCoupling.consumeClick()) {
			System.out.println("[ManualEnchance-Debug] Coupling key pressed!");
			for (TrainClient tc : ClientData.TRAINS) {
				if (tc.isPlayerRiding(client.player) && tc.isHoldingKey(client.player)) {
					TrainAccessor acc = (TrainAccessor) tc;
					System.out.println("[ManualEnchance-Debug] Player riding train: " + tc.id + ", masterId=" + acc.manualEnchance$getMasterId());

					if (acc.manualEnchance$getMasterId() != 0L) {
						System.out.println("[ManualEnchance-Debug] Sending uncouple packet for: " + tc.id);
						sendUncouplePacket(tc.id);
						break;
					}

					// Send coupling attempt to server; server will validate all safety conditions
					System.out.println("[ManualEnchance-Debug] Sending attempt_coupling packet for: " + tc.id);
					FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
					buf.writeLong(tc.id);
					NetworkManager.sendToServer(new net.minecraft.resources.ResourceLocation(Main.MOD_ID, "attempt_coupling"), buf);
					client.player.displayClientMessage(Text.literal("§b[Coupling] §f連結を試みています..."), true);
					break;

				}
			}
		}
		});

		// --- サーバーからのパケット受信 (S2C) ---
		NetworkManager.registerReceiver(NetworkManager.Side.S2C, Main.REVERSER_SYNC_S2C_PACKET_ID, (buf, context) -> {
			long trainId = buf.readLong();
			int reverserValue = buf.readInt();
			context.queue(() -> {
				for (TrainClient tc : ClientData.TRAINS) {
					if (tc.id == trainId) {
						((TrainAccessor) tc).setReverser(reverserValue);
						break;
					}
				}
			});
		});

		NetworkManager.registerReceiver(NetworkManager.Side.S2C, Main.PANTO_UPDATE_PACKET, (buf, context) -> {
			long trainId = buf.readLong();
			int state = buf.readInt();
			context.queue(() -> {
				for (TrainClient tc : ClientData.TRAINS) {
					if (tc.id == trainId) {
						((TrainAccessor) tc).setPantographState(state);
						break;
					}
				}
			});
		});

		NetworkManager.registerReceiver(NetworkManager.Side.S2C, Main.HORN_PACKET_ID, (buf, context) -> {
			long trainId = buf.readLong();
			context.queue(() -> {
				Minecraft client = Minecraft.getInstance();
				if (client.level == null) return;

				for (TrainClient tc : ClientData.TRAINS) {
					if (tc.id == trainId) {
						String soundId = ((TrainAccessor) tc).getHornSoundId();
						if (soundId != null && !soundId.isEmpty()) {
							boolean isReversed = tc.isReversed();
							int headCarIndex = isReversed ? Math.max(0, tc.trainCars - 1) : 0;

							Vec3 pos = ((TrainAccessor) tc).manualEnchance$getHeadPosition();

							client.level.playSound(client.player, pos.x, pos.y, pos.z,
									RegistryUtilities.createSoundEvent(new ResourceLocation(soundId)),
									SoundSource.BLOCKS, 2.0F, 1.0F);
						}
						break;
					}
				}
			});
		});

		NetworkManager.registerReceiver(NetworkManager.Side.S2C, Main.ROLLSIGN_UPDATE_PACKET, (buf, context) -> {
			long trainId = buf.readLong();
			String rollsignId = buf.readUtf();
			int nextIndex = buf.readInt();
			context.queue(() -> {
				for (TrainClient tc : ClientData.TRAINS) {
					if (tc.id == trainId) {
						((TrainAccessor) tc).setRollsignIndex(rollsignId, nextIndex);
						break;
					}
				}
			});
		});

		NetworkManager.registerReceiver(NetworkManager.Side.S2C, Main.DIRECT_NOTCH_PACKET_ID, (buf, context) -> {
			long trainId = buf.readLong();
			int syncedNotch = buf.readInt();
			long seq = buf.readLong();
			context.queue(() -> {
				long last = lastAppliedNotchSeq.getOrDefault(trainId, Long.MIN_VALUE);
				if (seq < last) return; // 古い同期は無視
				lastAppliedNotchSeq.put(trainId, seq);
				for (TrainClient tc : ClientData.TRAINS) {
					if (tc.id == trainId) {
						((TrainAccessor) tc).setManualNotchDirect(syncedNotch);
						break;
					}
				}
			});
		});

		NetworkManager.registerReceiver(NetworkManager.Side.S2C, Main.COUPLING_SYNC_S2C_PACKET_ID, (buf, context) -> {
			long slaveId = buf.readLong();
			long masterId = buf.readLong();
			double offset = buf.readDouble();
			context.queue(() -> {
				for (TrainClient tc : ClientData.TRAINS) {
					if (tc.id == slaveId) {
						TrainAccessor acc = (TrainAccessor) tc;
						acc.manualEnchance$setMasterId(masterId);
						acc.manualEnchance$setCouplingOffset(offset);
						break;
					}
				}
			});
		});
	}

	private static void pollJoystick(Minecraft client) {
		String currentName = GLFW.glfwGetJoystickName(GLFW.GLFW_JOYSTICK_1);
		if (currentName != null && !currentName.equals(lastJoystickName)) {
			client.player.displayClientMessage(Text.literal("§b[Manual Enhance] §fコントローラー接続: §e" + currentName), false);
			lastJoystickName = currentName;
		}

		FloatBuffer axes = GLFW.glfwGetJoystickAxes(GLFW.GLFW_JOYSTICK_1);
		ByteBuffer buttons = GLFW.glfwGetJoystickButtons(GLFW.GLFW_JOYSTICK_1);
		if (axes == null || buttons == null) return;

		if (currentName != null && currentName.toLowerCase().contains("kato")) {
			handleKatoJoystick(client, axes, buttons);
		} else {
			handleZuikiJoystick(client, axes, buttons);
		}
	}

	private static void handleKatoJoystick(Minecraft client, FloatBuffer axes, ByteBuffer buttons) {
		boolean b7 = buttons.get(6) == GLFW.GLFW_PRESS;
		boolean b8 = buttons.get(7) == GLFW.GLFW_PRESS;
		boolean b9 = buttons.get(8) == GLFW.GLFW_PRESS;
		boolean b10 = buttons.get(9) == GLFW.GLFW_PRESS;

		int currentNotch = 0;
		if (b7 && b8 && b9 && b10) currentNotch = 5;
		else if (b7 && b8 && b9) currentNotch = 4;
		else if (b7 && b8 && b10) currentNotch = 3;
		else if (b7 && b8) currentNotch = 2;
		else if (b7 && b9 && b10) currentNotch = 1;
		else if (b7 && b9) currentNotch = 0;
		else if (b7 && b10) currentNotch = -1;
		else if (b7) currentNotch = -2;
		else if (b8 && b9 && b10) currentNotch = -3;
		else if (b8 && b9) currentNotch = -4;
		else if (b8 && b10) currentNotch = -5;
		else if (b8) currentNotch = -6;
		else if (b9 && b10) currentNotch = -7;
		else if (b9) currentNotch = -8;
		else if (b10) currentNotch = -9;

		if (currentNotch != lastSentNotch) {
			sendDirectNotchPacket(currentNotch);
			lastSentNotch = currentNotch;
		}

		if (axes.capacity() > 1) {
			float revAxis = axes.get(1);
			int targetReverser = (revAxis < -0.2f) ? 1 : (revAxis > 0.8f ? -1 : 0);
			if (lastKatoReverser != targetReverser) {
				sendDirectReverserPacket(targetReverser);
				lastKatoReverser = targetReverser;
			}
		}
	}

	private static void handleZuikiJoystick(Minecraft client, FloatBuffer axes, ByteBuffer buttons) {
		int currentNotch = convertAxisToNotch(axes.get(1));
		if (currentNotch != lastSentNotch) {
			sendDirectNotchPacket(currentNotch);
			lastSentNotch = currentNotch;
		}

		if (buttons.get(2) == GLFW.GLFW_PRESS) sendHornPacket();

		boolean btnX = buttons.get(3) == GLFW.GLFW_PRESS;
		if (btnX && !lastButton2Pressed) sendReverserPacket(true);
		lastButton2Pressed = btnX;

		boolean btnY = buttons.get(0) == GLFW.GLFW_PRESS;
		if (btnY && !lastButton3Pressed) sendReverserPacket(false);
		lastButton3Pressed = btnY;

		boolean pantoBtn = buttons.get(8) == GLFW.GLFW_PRESS;
		if (pantoBtn && !lastPantoButtonPressed) togglePantograph(client);
		lastPantoButtonPressed = pantoBtn;

		boolean startBtn = buttons.get(9) == GLFW.GLFW_PRESS;
		if (startBtn && !lastStartButtonPressed) {
			for (TrainClient tc : ClientData.TRAINS) {
				if (tc.isPlayerRiding(client.player)) {
					client.setScreen(new RollsignScreen(tc));
					break;
				}
			}
		}
		lastStartButtonPressed = startBtn;
	}

	private static void sendHornPacket() {
		Minecraft client = Minecraft.getInstance();
		for (TrainClient tc : ClientData.TRAINS) {
			if (tc.isPlayerRiding(client.player)) {
				FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
				buf.writeLong(tc.id);
				NetworkManager.sendToServer(Main.HORN_PACKET_ID, buf);
				break;
			}
		}
	}

	private static void togglePantograph(Minecraft client) {
		for (TrainClient tc : ClientData.TRAINS) {
			if (tc.isPlayerRiding(client.player)) {
				TrainAccessor acc = (TrainAccessor) tc;
				int next = (acc.getPantographState() + 1) % 4;
				acc.setPantographState(next);
				FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
				buf.writeLong(tc.id);
				buf.writeInt(next);
				NetworkManager.sendToServer(Main.PANTO_UPDATE_PACKET, buf);
				break;
			}
		}
	}

	private static void sendDirectReverserPacket(int value) {
		Minecraft client = Minecraft.getInstance();
		for (TrainClient train : ClientData.TRAINS) {
			if (train.isPlayerRiding(client.player)) {
				FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
				buf.writeInt(value);
				buf.writeLong(train.id);
				NetworkManager.sendToServer(Main.REVERSER_DIRECT_PACKET_ID, buf);
				((TrainAccessor) train).setReverser(value);
				break;
			}
		}
	}

	private static int convertAxisToNotch(float value) {
		if (value < -0.98f) return -9;
		if (value < -0.88f) return -8;
		if (value < -0.78f) return -7;
		if (value < -0.68f) return -6;
		if (value < -0.58f) return -5;
		if (value < -0.48f) return -4;
		if (value < -0.38f) return -3;
		if (value < -0.28f) return -2;
		if (value < -0.15f) return -1;
		if (value < 0.15f) return 0;
		if (value < 0.35f) return 1;
		if (value < 0.55f) return 2;
		if (value < 0.75f) return 3;
		if (value < 0.92f) return 4;
		return 5;
	}

	private static void sendDirectNotchPacket(int notch) {
		Minecraft client = Minecraft.getInstance();
		for (TrainClient train : ClientData.TRAINS) {
			if (train.isPlayerRiding(client.player)) {
				// 列車別シーケンスを更新して送信（古いパケットの適用を防止）
				long seq = localNotchSeqMap.getOrDefault(train.id, 0L) + 1L;
				localNotchSeqMap.put(train.id, seq);
				FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
				buf.writeInt(notch);
				buf.writeLong(train.id);
				buf.writeLong(seq);
				NetworkManager.sendToServer(Main.DIRECT_NOTCH_PACKET_ID, buf);
				// 送信側でもこのシーケンスを適用済みにする（サーバーの応答が来るまでのローカル整合性）
				lastAppliedNotchSeq.put(train.id, seq);
				((TrainAccessor) train).setManualNotchDirect(notch);
				break;
			}
		}
	}

	private static void sendReverserPacket(boolean isUp) {
		Minecraft client = Minecraft.getInstance();
		for (TrainClient train : ClientData.TRAINS) {
			if (train.isPlayerRiding(client.player)) {
				FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
				buf.writeBoolean(isUp);
				buf.writeLong(train.id);
				NetworkManager.sendToServer(Main.REVERSER_PACKET_ID, buf);
				((TrainAccessor) train).changeReverser(isUp);
				break;
			}
		}
	}

	public static void playHornLocal(long trainId) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) return;

		for (TrainClient tc : ClientData.TRAINS) {
			if (tc.id == trainId) {
				String soundId = ((TrainAccessor) tc).getHornSoundId();
				if (soundId != null && !soundId.isEmpty()) {
					// 先頭車両の座標を取得
					Vec3 pos = ((TrainAccessor) tc).manualEnchance$getHeadPosition();

					// ローカルで再生（全クライアントにパケットは飛ばない）
					client.level.playSound(client.player, pos.x, pos.y, pos.z,
							RegistryUtilities.createSoundEvent(new ResourceLocation(soundId)),
							SoundSource.BLOCKS, 2.0F, 1.0F);
				}
				break;
			}
		}
	}

	private static void sendUncouplePacket(long trainId) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeLong(trainId);
		NetworkManager.sendToServer(Main.UNCOUPLE_PACKET_ID, buf);
	}
}