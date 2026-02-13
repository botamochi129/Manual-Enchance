package botamochi129.manual_enchance.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class SidingDataManager {
    // Siding ID -> データ
    private static final Map<Long, Integer> DEFAULT_PANTO_STATES = new HashMap<>();

    public static void setPantoState(long sidingId, int state) {
        DEFAULT_PANTO_STATES.put(sidingId, state);
    }

    public static int getPantoState(long sidingId) {
        return DEFAULT_PANTO_STATES.getOrDefault(sidingId, 0);
    }

    public static void save(Level world) {
        if (world.isClientSide) return;
        try {
            // world/manual_enchance/sidings.nbt に保存
            File dir = new File(world.getServer().getWorldPath(LevelResource.ROOT).toFile(), "manual_enchance");
            if (!dir.exists()) dir.mkdirs();

            CompoundTag nbt = new CompoundTag();
            DEFAULT_PANTO_STATES.forEach((id, state) -> {
                nbt.putInt(String.valueOf(id), state);
            });

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
            for (String key : nbt.getAllKeys()) {
                DEFAULT_PANTO_STATES.put(Long.parseLong(key), nbt.getInt(key));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}