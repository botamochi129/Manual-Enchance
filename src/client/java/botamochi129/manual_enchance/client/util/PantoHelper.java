package botamochi129.manual_enchance.client.util;

import mtr.data.TrainClient;

public class PantoHelper {
    private static final ThreadLocal<TrainClient> CURRENT_TRAIN = new ThreadLocal<>();

    public static void setCurrentTrain(TrainClient train) {
        CURRENT_TRAIN.set(train);
    }

    public static TrainClient getCurrentTrain() {
        return CURRENT_TRAIN.get();
    }

    public static void clear() {
        CURRENT_TRAIN.remove();
    }
}