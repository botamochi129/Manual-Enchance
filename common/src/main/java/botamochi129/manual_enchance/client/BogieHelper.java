package botamochi129.manual_enchance.client;

import botamochi129.manual_enchance.util.TrainAccessor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mtr.data.TrainClient;
import net.minecraft.world.phys.Vec3;

public final class BogieHelper {
    private BogieHelper() {}

    public static boolean isBogiePart(JsonObject partObject) {
        if (!partObject.has("bogie")) return false;
        JsonElement bogie = partObject.get("bogie");
        if (bogie.isJsonObject()) return true;
        // backward compat: old boolean format
        return bogie.isJsonPrimitive() && bogie.getAsBoolean();
    }

    public static boolean isJacobsBogie(JsonObject partObject) {
        if (!partObject.has("bogie") || !partObject.get("bogie").isJsonObject()) return false;
        JsonObject bogieObj = partObject.getAsJsonObject("bogie");
        return bogieObj.has("is_jacobs_bogie") && bogieObj.get("is_jacobs_bogie").getAsBoolean();
    }

    public static JsonArray getBogiePositions(JsonObject partObject) {
        if (!partObject.has("bogie")) return null;
        JsonElement bogie = partObject.get("bogie");
        if (bogie.isJsonObject()) {
            JsonObject bogieObj = bogie.getAsJsonObject();
            if (bogieObj.has("bogie_position")) {
                return bogieObj.getAsJsonArray("bogie_position");
            }
        }
        // backward compat: old format uses top-level positions
        if (partObject.has("positions")) {
            return partObject.getAsJsonArray("positions");
        }
        return null;
    }

    public static float getLongitudinalOffsetBlocks(JsonObject partObject) {
        JsonArray positions = getBogiePositions(partObject);
        if (positions == null || positions.isEmpty()) return 0.0f;
        JsonArray first = positions.get(0).getAsJsonArray();
        if (first.size() < 2) return 0.0f;
        return first.get(1).getAsFloat() / 16.0f;
    }

    public static float[] computeRailRotationDelta(TrainClient train, int currentCar, JsonObject partObject) {
        if (!(train instanceof TrainAccessor accessor)) return new float[]{0.0f, 0.0f};

        float bogieOffsetBlocks = getLongitudinalOffsetBlocks(partObject);
        double centerProgress = accessor.manualEnchance$getRailProgressAtCar(currentCar, 0.0);
        double bogieProgress = accessor.manualEnchance$getRailProgressAtCar(currentCar, bogieOffsetBlocks);

        return rotationDelta(accessor, centerProgress, bogieProgress);
    }

    private static float[] rotationDelta(TrainAccessor accessor, double centerProgress, double bogieProgress) {
        Vec3 centerDir = accessor.manualEnchance$getDirectionVector(centerProgress);
        Vec3 bogieDir = accessor.manualEnchance$getDirectionVector(bogieProgress);

        float centerYaw = yawFromDirection(centerDir);
        float bogieYaw = yawFromDirection(bogieDir);
        float centerPitch = pitchFromDirection(centerDir);
        float bogiePitch = pitchFromDirection(bogieDir);

        return new float[]{normalizeAngle(bogieYaw - centerYaw), bogiePitch - centerPitch};
    }

    private static float yawFromDirection(Vec3 direction) {
        if (direction.lengthSqr() <= 0.0001) return 0.0f;
        return (float) Math.toDegrees(Math.atan2(direction.x, direction.z));
    }

    private static float pitchFromDirection(Vec3 direction) {
        if (direction.lengthSqr() <= 0.0001) return 0.0f;
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        return (float) Math.toDegrees(Math.atan2(-direction.y, horizontal));
    }

    private static float normalizeAngle(float angle) {
        while (angle > 180.0f) angle -= 360.0f;
        while (angle < -180.0f) angle += 360.0f;
        return angle;
    }
}
