package botamochi129.manual_enchance.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class RouteCouplingStore {

    private static final Logger LOGGER = LogManager.getLogger("manual_enchance");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final List<RouteCouplingAction> ACTIONS = new ArrayList<>();

    public static List<RouteCouplingAction> getActions() {
        return ACTIONS;
    }

    public static RouteCouplingAction getAction(long routeId, int stationIndex) {
        for (RouteCouplingAction a : ACTIONS) {
            if (a.routeId == routeId && a.stationIndex == stationIndex) {
                return a;
            }
        }
        return null;
    }

    public static void setAction(RouteCouplingAction action) {
        removeAction(action.routeId, action.stationIndex);
        if (action.doCouple || action.doUncouple) {
            ACTIONS.add(action);
        }
    }

    public static void removeAction(long routeId, int stationIndex) {
        ACTIONS.removeIf(a -> a.routeId == routeId && a.stationIndex == stationIndex);
    }

    public static void load(ServerLevel level) {
        ACTIONS.clear();
        File file = getSaveFile(level);
        if (!file.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<List<RouteCouplingAction>>() {}.getType();
            List<RouteCouplingAction> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                ACTIONS.addAll(loaded);
                LOGGER.info("[RouteCoupling] loaded {} actions from {}", ACTIONS.size(), file.getAbsolutePath());
            }
        } catch (IOException e) {
            LOGGER.error("[RouteCoupling] failed to load from {}", file.getAbsolutePath(), e);
        }
    }

    public static void save(ServerLevel level) {
        File file = getSaveFile(level);
        file.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(ACTIONS, writer);
            LOGGER.info("[RouteCoupling] saved {} actions to {}", ACTIONS.size(), file.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("[RouteCoupling] failed to save to {}", file.getAbsolutePath(), e);
        }
    }

    private static File getSaveFile(ServerLevel level) {
        return new File(level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(),
                "manual_enchance/route_coupling.json");
    }

    public static class RouteCouplingAction {
        public long routeId;
        public int stationIndex;
        public boolean doCouple;
        public boolean doUncouple;
        public long targetSidingId;
        public boolean waitForever;
        public int splitTrainIndex = 1;

        public RouteCouplingAction() {}

        public RouteCouplingAction(long routeId, int stationIndex, boolean doCouple, boolean doUncouple, long targetSidingId, boolean waitForever) {
            this(routeId, stationIndex, doCouple, doUncouple, targetSidingId, waitForever, 1);
        }

        public RouteCouplingAction(long routeId, int stationIndex, boolean doCouple, boolean doUncouple, long targetSidingId, boolean waitForever, int splitTrainIndex) {
            this.routeId = routeId;
            this.stationIndex = stationIndex;
            this.doCouple = doCouple;
            this.doUncouple = doUncouple;
            this.targetSidingId = targetSidingId;
            this.waitForever = waitForever;
            this.splitTrainIndex = splitTrainIndex;
        }
    }
}
