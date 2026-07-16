package botamochi129.manual_enchance.util;

import mtr.data.TrainServer;
import java.util.Set;

public interface SidingAccessor {
    Set<TrainServer> getTrains();
    int manualEnchance$getDefaultPantographState();
    void manualEnchance$setDefaultPantographState(int state);
    long manualEnchance$getMasterSidingId();
    void manualEnchance$setMasterSidingId(long masterSidingId);
}