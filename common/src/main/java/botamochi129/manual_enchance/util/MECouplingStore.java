package botamochi129.manual_enchance.util;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class MECouplingStore {
    private static final Path SAVE_PATH = Paths.get("config", "manual_enchance_couplings.dat");

    public static void save(Map<Long, CouplingInfo> map) {
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(SAVE_PATH.toFile()))) {
            out.writeInt(map.size());
            for (Map.Entry<Long, CouplingInfo> entry : map.entrySet()) {
                out.writeLong(entry.getKey()); // Slave ID
                out.writeLong(entry.getValue().masterId);
                out.writeDouble(entry.getValue().offset);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Map<Long, CouplingInfo> load() {
        Map<Long, CouplingInfo> map = new HashMap<>();
        if (!Files.exists(SAVE_PATH)) return map;
        try (DataInputStream in = new DataInputStream(new FileInputStream(SAVE_PATH.toFile()))) {
            int size = in.readInt();
            for (int i = 0; i < size; i++) {
                long slaveId = in.readLong();
                long masterId = in.readLong();
                double offset = in.readDouble();
                map.put(slaveId, new CouplingInfo(masterId, offset));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return map;
    }
}