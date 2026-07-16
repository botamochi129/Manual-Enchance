package botamochi129.manual_enchance;

import botamochi129.manual_enchance.client.GraphicsHolder;
import botamochi129.manual_enchance.client.RollsignScreen;
import botamochi129.manual_enchance.util.SidingDataManager;
import botamochi129.manual_enchance.util.TrainAccessor;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import io.netty.buffer.Unpooled;
import mtr.client.ClientData;
import mtr.data.IGui;
import mtr.data.RailType;
import mtr.data.Route;
import mtr.data.Station;
import mtr.data.TrainClient;
import mtr.mappings.RegistryUtilities;
import mtr.mappings.Text;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
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
			for (TrainClient tc : ClientData.TRAINS) {
				if (tc.isPlayerRiding(client.player)) {
					TrainAccessor acc = (TrainAccessor) tc;

					if (acc.manualEnchance$getMasterId() != 0L) {
						sendUncouplePacket(tc.id);
						break;
					}

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

							Vec3 viewOffset = tc.getViewOffset();
							if (viewOffset == null) return;
							double posX = viewOffset.x;
							double posY = viewOffset.y;
							double posZ = viewOffset.z;

							client.level.playSound(client.player, posX, posY, posZ,
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

		NetworkManager.registerReceiver(NetworkManager.Side.S2C, Main.STATE_SYNC_S2C_PACKET_ID, (buf, context) -> {
			long trainId = buf.readLong();
			float serverSpeed = buf.readFloat();
			double serverProgress = buf.readDouble();
			int serverNotch = buf.readInt();
			int serverReverser = buf.readInt();
			context.queue(() -> {
				Minecraft mc = Minecraft.getInstance();
				for (TrainClient tc : ClientData.TRAINS) {
					if (tc.id != trainId) continue;
					TrainAccessor acc = (TrainAccessor) tc;
					// 自分が操作中の列車はノッチ・リバーサーを上書きしない
					boolean isLocallyDriven = mc.player != null && tc.isPlayerRiding(mc.player) && tc.isHoldingKey(mc.player);
					if (!isLocallyDriven) {
						if (acc.getManualNotch() != serverNotch) acc.setManualNotchDirect(serverNotch);
						if (acc.getReverser() != serverReverser) acc.setReverser(serverReverser);
					}
					// 手動運転中は常にサーバー位置で上書き（閾値なし）
					// 自動運転中はMTRのローカルシミュレーションに任せる
					if (tc.isCurrentlyManual()) {
						acc.setRailProgress(serverProgress);
						acc.setSpeed(serverSpeed);
					} else {
						if (Math.abs(acc.manualEnchance$getRailProgress() - serverProgress) > 0.5) acc.setRailProgress(serverProgress);
						if (Math.abs(tc.getSpeed() - serverSpeed) > 0.1f) acc.setSpeed(serverSpeed);
					}
					break;
				}
			});
		});

		NetworkManager.registerReceiver(NetworkManager.Side.S2C, Main.COUPLING_SYNC_S2C_PACKET_ID, (buf, context) -> {
                    long slaveId = buf.readLong();
                    long masterId = buf.readLong();
                    double offset = buf.readDouble();
                    int typeOrdinal = buf.readInt();
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

		NetworkManager.registerReceiver(NetworkManager.Side.S2C, Main.SIDING_COUPLING_SYNC_S2C_PACKET_ID, (buf, context) -> {
			long slaveSidingId = buf.readLong();
			long masterSidingId = buf.readLong();
			context.queue(() -> SidingDataManager.setMasterSidingId(slaveSidingId, masterSidingId));
		});

		// --- HUD ---
		ClientGuiEvent.RENDER_HUD.register((matrixStack, tickDelta) -> {
			renderManualHUD(matrixStack, tickDelta);
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
					Vec3 pos = ((TrainAccessor) tc).manualEnchance$getFrontPosition();

					// ローカルで再生（全クライアントにパケットは飛ばない）
					client.level.playSound(client.player, pos.x, pos.y, pos.z,
							RegistryUtilities.createSoundEvent(new ResourceLocation(soundId)),
							SoundSource.BLOCKS, 2.0F, 1.0F);
				}
				break;
			}
		}
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

		graphicsHolder.drawFill(masconX - 5, masconY - 75, masconX + 20, masconY + 55, 0x88000000);

		String revLabel = (reverser == 1) ? "F" : (reverser == -1 ? "B" : "N");
		int revColor = (reverser == 1) ? 0xFFFFAA00 : (reverser == -1 ? 0xFFFF5555 : 0xFFFFFFFF);
		graphicsHolder.drawFill(masconX - 30, masconY - 10, masconX - 10, masconY + 10, 0x88000000);

		graphicsHolder.drawText(revLabel, masconX - 24, masconY - 4, revColor, true, 255);
		graphicsHolder.drawText("REV", masconX - 32, masconY - 22, 0xFFAAAAAA, true, 255);

		graphicsHolder.drawFill(masconX - 2, masconY, masconX + 17, masconY + 1, 0xFFFFFFFF);
		int offset = notch * 8;
		int barColor = (notch > 0) ? 0xFFADFF2F : (notch == -9 ? 0xFFFF0000 : (notch < 0 ? 0xFF5555FF : 0xFFFFFFFF));
		graphicsHolder.drawFill(masconX - 4, masconY + offset - 2, masconX + 19, masconY + offset + 2, barColor);

		String label = (notch > 0) ? "P" + notch : (notch < 0 ? (notch == -9 ? "EB" : "B" + Math.abs(notch)) : "N");
		graphicsHolder.drawText(label, masconX + 25, masconY + offset - 4, 0xFFFFFFFF, true, 255);

		boolean doorClosed = (doorValue == 0);
		graphicsHolder.drawFill(masconX - 36, masconY + 18, masconX - 6, masconY + 36, 0xFF111111);
		drawCircle(graphicsHolder, masconX - 21, masconY + 27, doorClosed ? 0xFFFFB300 : 0xFF4A3A1A);
		graphicsHolder.drawText("DOOR", masconX - 33, masconY + 10, doorClosed ? 0xFFFFCC66 : 0xFF777777, true, 255);

		// ★ BC pressure gauge
		float bcPressure = accessor.manualEnchance$getBCPressure();
		int bcGaugeX = masconX + 35;
		int bcGaugeY = masconY - 65;
		int bcGaugeW = 14;
		int bcGaugeH = 105;
		graphicsHolder.drawFill(bcGaugeX, bcGaugeY, bcGaugeX + bcGaugeW, bcGaugeY + bcGaugeH, 0x88000000);
		graphicsHolder.drawText("BC", bcGaugeX + 2, bcGaugeY - 10, 0xFFAAAAAA, true, 255);
		// Fill level from bottom
		int fillH = (int) (bcPressure * (bcGaugeH - 4));
		if (fillH > 0) {
			int bcColor = bcPressure > 0.7f ? 0xFFFF3333 : (bcPressure > 0.3f ? 0xFFFF8800 : 0xFF33CC33);
			graphicsHolder.drawFill(bcGaugeX + 2, bcGaugeY + bcGaugeH - 2 - fillH,
				bcGaugeX + bcGaugeW - 2, bcGaugeY + bcGaugeH - 2, bcColor);
		}
		String bcPct = String.format("%.0f%%", bcPressure * 100);
		graphicsHolder.drawText(bcPct, bcGaugeX - 2, bcGaugeY + bcGaugeH + 2, 0xFFFFFFFF, true, 255);

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

	private static void sendUncouplePacket(long trainId) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeLong(trainId);
		NetworkManager.sendToServer(Main.UNCOUPLE_PACKET_ID, buf);
	}
}