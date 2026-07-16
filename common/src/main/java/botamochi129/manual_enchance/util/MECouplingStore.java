package botamochi129.manual_enchance.util;

import net.minecraft.server.level.ServerLevel;
import java.io.*;
import java.io.File;
import java.util.*;

public class MECouplingStore {
    private static final String FILE_NAME = "manual_enchance_couplings.dat";

    // 💡 引数に ServerLevel を追加し、そのワールドのフォルダー内に保存する
    public static void save(ServerLevel level, Map<Long, CouplingInfo> map) {
        // level.getServer().getWorldPath(...) 等でも取れますが、一番手軽なのはレベルのデータ保存先から取得
        File worldDir = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile();
        File modDir = new File(worldDir, "manual_enchance");
        if (!modDir.exists()) modDir.mkdirs(); // ディレクトリがなければ作成

        File saveFile = new File(modDir, FILE_NAME);

        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(saveFile))) {
            out.writeInt(map.size());
            for (Map.Entry<Long, CouplingInfo> entry : map.entrySet()) {
                out.writeLong(entry.getKey()); // Slave ID
                out.writeLong(entry.getValue().masterId);
                out.writeDouble(entry.getValue().offset);
                out.writeInt(entry.getValue().type.ordinal());
            }
            System.out.println("[ManualEnchance] 連結データをワールドに保存しました: " + saveFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Map<Long, CouplingInfo> load(ServerLevel level) {
        Map<Long, CouplingInfo> map = new HashMap<>();
        File worldDir = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile();
        File saveFile = new File(new File(worldDir, "manual_enchance"), FILE_NAME);

        if (!saveFile.exists()) return map;

        try (DataInputStream in = new DataInputStream(new FileInputStream(saveFile))) {
            int size = in.readInt();
            for (int i = 0; i < size; i++) {
                long slaveId = in.readLong();
                long masterId = in.readLong();
                double offset = in.readDouble();
                int typeOrdinal = in.available() > 0 ? in.readInt() : 0;
                CouplingManager.ConnectionType type = CouplingManager.ConnectionType.values()[typeOrdinal];
                map.put(slaveId, new CouplingInfo(masterId, offset, type));
            }
            System.out.println("[ManualEnchance] 連結データをワールドから読み込みました: " + saveFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return map;
    }
}