package botamochi129.manual_enchance.client.mixin;

import botamochi129.manual_enchance.client.util.IResourcePackCreatorPropertiesHelper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mtr.client.ResourcePackCreatorProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.List;

@Mixin(ResourcePackCreatorProperties.class)
public abstract class ResourcePackCreatorPropertiesMixin implements IResourcePackCreatorPropertiesHelper {

    @Shadow public abstract JsonArray getPropertiesPartsArray();

    @Shadow protected abstract void updateModel();

    @Unique
    private static final List<String> CUSTOM_LIST = Arrays.asList(
            "PANTAGRAPH_DOWN",
            "PANTAGRAPH_5M",
            "PANTAGRAPH_W51",
            "PANTAGRAPH_6M"
    );

    @Inject(method = "editPartRenderCondition", at = @At("HEAD"), cancellable = true, remap = false)
    private void onEditPartRenderConditionHead(int index, CallbackInfo ci) {
        ResourcePackCreatorProperties self = (ResourcePackCreatorProperties)(Object)this;
        JsonObject partObject = self.getPropertiesPartsArray().get(index).getAsJsonObject();
        String current = partObject.get("render_condition").getAsString();

        String next;
        // 1. 標準Enumの最後(MOVING_BACKWARDS)なら、独自リストの最初へ
        if (current.equals("MOVING_BACKWARDS")) {
            next = CUSTOM_LIST.get(0);
        }
        // 2. 独自リストの中にいる場合
        else if (CUSTOM_LIST.contains(current)) {
            int customIdx = CUSTOM_LIST.indexOf(current);
            if (customIdx < CUSTOM_LIST.size() - 1) {
                // リストの次へ
                next = CUSTOM_LIST.get(customIdx + 1);
            } else {
                // 独自リストの最後なら、標準の最初(ALL)へ
                next = "ALL";
            }
        } else {
            // 3. それ以外（標準Enumの途中）は、そのまま標準の処理(cycleEnumProperty)に任せる
            return;
        }

        // 自前で書き換えて、元のメソッドの実行をキャンセルする
        partObject.addProperty("render_condition", next);
        ((ResourcePackCreatorPropertiesAccessor)self).callUpdateModel();
        ci.cancel();
    }

    @Unique
    public void editPartRollsign(int index) {
        JsonObject partObject = this.getPropertiesPartsArray().get(index).getAsJsonObject();
        boolean isRollsign = partObject.has("rollsign") && partObject.get("rollsign").getAsBoolean();

        if (!isRollsign) {
            // ON にした時の初期値
            partObject.addProperty("rollsign", true);
            if (!partObject.has("rollsign_id")) partObject.addProperty("rollsign_id", "example_id");
            if (!partObject.has("rollsign_steps")) partObject.addProperty("rollsign_steps", 1);
            if (!partObject.has("rollsign_texture")) partObject.addProperty("rollsign_texture", "mtr:example/example.png");
        } else {
            // OFF にする
            partObject.addProperty("rollsign", false);
        }

        this.updateModel(); // モデルの再生成
    }

    @Unique
    public void editPartRollsignAnimation(int index) {
        ResourcePackCreatorProperties self = (ResourcePackCreatorProperties)(Object)this;
        JsonObject partObject = self.getPropertiesPartsArray().get(index).getAsJsonObject();
        // デフォルトは true なので、逆をセット
        boolean current = !partObject.has("rollsign_animation") || partObject.get("rollsign_animation").getAsBoolean();
        partObject.addProperty("rollsign_animation", !current);
        ((ResourcePackCreatorPropertiesAccessor)self).callUpdateModel();
    }

    @Unique
    public void editPartRollsignSteps(int index, boolean isUp) {
        ResourcePackCreatorProperties self = (ResourcePackCreatorProperties)(Object)this;
        JsonObject partObject = self.getPropertiesPartsArray().get(index).getAsJsonObject();
        int current = partObject.has("rollsign_steps") ? partObject.get("rollsign_steps").getAsInt() : 1;
        partObject.addProperty("rollsign_steps", Math.max(1, current + (isUp ? 1 : -1)));
        ((ResourcePackCreatorPropertiesAccessor)self).callUpdateModel();
    }

    @Unique
    public void cycleRollsignSteps(int index) {
        ResourcePackCreatorProperties self = (ResourcePackCreatorProperties)(Object)this;
        JsonObject partObject = self.getPropertiesPartsArray().get(index).getAsJsonObject();

        if (partObject.has("rollsign") && partObject.get("rollsign").getAsBoolean()) {
            int steps = partObject.has("rollsign_steps") ? partObject.get("rollsign_steps").getAsInt() : 1;
            // 1ずつ増やして、例えば32コマでループ
            int nextSteps = (steps % 32) + 1;
            partObject.addProperty("rollsign_steps", nextSteps);
            ((ResourcePackCreatorPropertiesAccessor)self).callUpdateModel();
        }
    }
}