package botamochi129.manual_enchance.util;

import java.util.HashMap;
import java.util.Map;

public class CouplingManager {
    private static final Map<Long, CouplingInfo> COUPLING_MAP = new HashMap<>();

    public static Map<Long, CouplingInfo> getCouplingMap() {
        return COUPLING_MAP;
    }

    public static void loadAll() {
        COUPLING_MAP.clear();
        COUPLING_MAP.putAll(MECouplingStore.load());
    }

    public static void saveAll() {
        MECouplingStore.save(COUPLING_MAP);
    }
}