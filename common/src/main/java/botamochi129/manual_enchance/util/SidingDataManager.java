package botamochi129.manual_enchance.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class SidingDataManager {
    private static final Map<Long, Integer> DEFAULT_PANTO_STATES = new HashMap<>();
    // このSidingの列車が追従すべき「前の編成」のSiding ID (0L = 連結なし)
    private static final Map<Long, Long> MASTER_SIDING_MAP = new HashMap<>();

    public static void setPantoState(long sidingId, int state) {
        DEFAULT_PANTO_STATES.put(sidingId, state);
    }

    public static int getPantoState(long sidingId) {
        return DEFAULT_PANTO_STATES.getOrDefault(sidingId, 0);
    }

    public static long getMasterSidingId(long sidingId) {
        return MASTER_SIDING_MAP.getOrDefault(sidingId, 0L);
    }

    public static void setMasterSidingId(long sidingId, long masterSidingId) {
        if (masterSidingId == 0L) MASTER_SIDING_MAP.remove(sidingId);
        else MASTER_SIDING_MAP.put(sidingId, masterSidingId);
    }

    public static void syncAllToPlayer(net.minecraft.server.level.ServerPlayer player, net.minecraft.resources.ResourceLocation packetId) {
        MASTER_SIDING_MAP.forEach((sidingId, masterSidingId) -> {
            net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            buf.writeLong(sidingId);
            buf.writeLong(masterSidingId);
            dev.architectury.networking.NetworkManager.sendToPlayer(player, packetId, buf);
        });
    }

    public static void save(Level world) {
        if (world.isClientSide) return;
        try {
            File dir = new File(world.getServer().getWorldPath(LevelResource.ROOT).toFile(), "manual_enchance");
            if (!dir.exists()) dir.mkdirs();

            CompoundTag nbt = new CompoundTag();
            DEFAULT_PANTO_STATES.forEach((id, state) -> nbt.putInt("p_" + id, state));
            MASTER_SIDING_MAP.forEach((id, masterId) -> nbt.putLong("m_" + id, masterId));

            NbtIo.writeCompressed(nbt, new File(dir, "sidings.nbt"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void load(Level world) {
        if (world.isClientSide) return;
        try {
            File file = new File(world.getServer().getWorldPath(LevelResource.ROOT).toFile(), "manual_enchance/sidings.nbt");
            if (!file.exists()) return;

            CompoundTag nbt = NbtIo.readCompressed(file);
            DEFAULT_PANTO_STATES.clear();
            MASTER_SIDING_MAP.clear();
            for (String key : nbt.getAllKeys()) {
                if (key.startsWith("p_")) DEFAULT_PANTO_STATES.put(Long.parseLong(key.substring(2)), nbt.getInt(key));
                else if (key.startsWith("m_")) MASTER_SIDING_MAP.put(Long.parseLong(key.substring(2)), nbt.getLong(key));
                else DEFAULT_PANTO_STATES.put(Long.parseLong(key), nbt.getInt(key)); // 旧形式互換
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}