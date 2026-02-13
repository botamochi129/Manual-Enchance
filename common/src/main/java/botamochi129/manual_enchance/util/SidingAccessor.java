package botamochi129.manual_enchance.util;

import mtr.data.TrainServer;
import java.util.Set;

public interface SidingAccessor {
    // 既存の取得用
    Set<TrainServer> getTrains();

    // パンタグラフ設定用
    int manualEnchance$getDefaultPantographState();
    void manualEnchance$setDefaultPantographState(int state);
}