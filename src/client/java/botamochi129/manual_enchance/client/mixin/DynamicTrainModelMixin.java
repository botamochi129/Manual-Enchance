package botamochi129.manual_enchance.client.mixin;

import botamochi129.manual_enchance.client.util.IPartInfo;
import com.google.gson.JsonObject;
import botamochi129.manual_enchance.client.util.PantoHelper;
import botamochi129.manual_enchance.util.TrainAccessor;
import mtr.client.DynamicTrainModel;
import mtr.client.IDrawing;
import mtr.data.TrainClient;
import mtr.mappings.UtilitiesClient;
import mtr.render.MoreRenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Set;

@Mixin(DynamicTrainModel.class)
public abstract class DynamicTrainModelMixin {
    @Inject(method = "shouldSkipRender", at = @At("HEAD"), cancellable = true, remap = false)
    private void onShouldSkipRender(JsonObject partObject, CallbackInfoReturnable<Boolean> cir) {
        if (!partObject.has("render_condition")) return;
        String condition = partObject.get("render_condition").getAsString();

        TrainClient train = PantoHelper.getCurrentTrain();
        // もし列車が特定できない場合は、デフォルトの描画（条件無視）をさせる
        if (train == null) return;

        int pantoState = ((TrainAccessor) train).getPantographState();
        // 条件判定
        switch (condition) {
            case "PANTAGRAPH_DOWN":
                if (pantoState != 0) cir.setReturnValue(true);
                break;
            case "PANTAGRAPH_5M":
                if (pantoState != 1) cir.setReturnValue(true);
                break;
            case "PANTAGRAPH_W51":
                if (pantoState != 2) cir.setReturnValue(true);
                break;
            case "PANTAGRAPH_6M":
                if (pantoState != 3) cir.setReturnValue(true);
                break;
        }
    }


    @Shadow
    @Final
    public Map<String, Set<?>> partsInfo;

    @Shadow
    protected abstract boolean shouldSkipRender(JsonObject partObject);

    @Shadow
    protected abstract float getOffsetX(JsonObject partObject);

    @Shadow
    protected abstract float getOffsetZ(JsonObject partObject);

    @Shadow
    protected abstract void iterateParts(int currentCar, int trainCars, java.util.function.Consumer<JsonObject> callback);

    @Inject(method = "renderTextDisplays", at = @At("TAIL"), remap = false)
    private void onRenderRollsigns(MatrixStack matrices, VertexConsumerProvider vertexConsumers, net.minecraft.client.font.TextRenderer font, VertexConsumerProvider.Immediate immediate, mtr.data.Route thisRoute, mtr.data.Route nextRoute, mtr.data.Station thisStation, mtr.data.Station nextStation, mtr.data.Station lastStation, String customDestination, int car, int totalCars, boolean atPlatform, java.util.List<mtr.client.ScrollingText> scrollingTexts, CallbackInfo ci) {

        TrainClient train = PantoHelper.getCurrentTrain();
        if (!(train instanceof TrainAccessor accessor)) return;

        this.iterateParts(car, totalCars, (partObject) -> {
            if (!partObject.has("name")) return;
            String name = partObject.get("name").getAsString();

            if (partObject.has("rollsign") && partObject.get("rollsign").getAsBoolean()) {
                String rollsignId = partObject.has("rollsign_id") ? partObject.get("rollsign_id").getAsString() : name;
                int totalSteps = partObject.has("rollsign_steps") ? partObject.get("rollsign_steps").getAsInt() : 1;

                // ステップ数をAccessorに登録（GUIで利用するため）
                accessor.setRollsignSteps(rollsignId, totalSteps);

                if (!accessor.getRollsignIndices().containsKey(rollsignId)) {
                    accessor.setRollsignIndex(rollsignId, 0);
                }


                // 3. 表示条件などのチェック
                if (this.shouldSkipRender(partObject)) return;
                if (!this.partsInfo.containsKey(name)) return;

                // 5. パラメータの取得
                String texturePath = partObject.has("rollsign_texture") ? partObject.get("rollsign_texture").getAsString() : "mtr:example/example.png";
                boolean mirror = partObject.has("mirror") && partObject.get("mirror").getAsBoolean();
                boolean enableAnimation = !partObject.has("rollsign_animation") || partObject.get("rollsign_animation").getAsBoolean();

                // 6. UV計算（ここで引数 rollsignId を正しく渡す）
                float vStep = 1.0f / totalSteps;
                float displayOffset = enableAnimation ? accessor.getRollsignOffset(rollsignId) : (float) accessor.getRollsignIndex(rollsignId);

                float v1 = displayOffset * vStep;
                float v2 = v1 + vStep;

                // 7. テクスチャIdentifierの生成
                String[] pathParts = texturePath.split(":", 2);
                Identifier textureId = (pathParts.length == 2) ? new Identifier(pathParts[0], pathParts[1]) : new Identifier(texturePath);

                float xOffset = this.getOffsetX(partObject);
                float zOffset = this.getOffsetZ(partObject);

                // 8. 実際の描画処理
                partObject.getAsJsonArray("positions").forEach((positionElement) -> {
                    float posX = positionElement.getAsJsonArray().get(0).getAsFloat() + xOffset;
                    float posZ = positionElement.getAsJsonArray().get(1).getAsFloat() + zOffset;

                    this.partsInfo.get(name).forEach((partInfoObj) -> {
                        IPartInfo partInfo = (IPartInfo) partInfoObj;

                        matrices.push();
                        matrices.translate(posX / 16.0, 0.0, posZ / 16.0);
                        if (mirror) UtilitiesClient.rotateYDegrees(matrices, 180.0F);

                        matrices.translate(-partInfo.getOriginX(), -partInfo.getOriginY(), partInfo.getOriginZ());
                        UtilitiesClient.rotateZDegrees(matrices, partInfo.getRotationZ());
                        UtilitiesClient.rotateYDegrees(matrices, partInfo.getRotationY());
                        UtilitiesClient.rotateXDegrees(matrices, partInfo.getRotationX());
                        matrices.translate(-partInfo.getOffsetX(), -partInfo.getOffsetY(), partInfo.getOffsetZ() - 0.001);

                        IDrawing.drawTexture(
                                matrices,
                                vertexConsumers.getBuffer(MoreRenderLayers.getLight(textureId, false)),
                                -partInfo.getWidth() / 2, -partInfo.getHeight() / 2,
                                partInfo.getWidth(), partInfo.getHeight(),
                                0.0f, v1, 1.0f, v2,
                                Direction.UP, -1, 15728880
                        );
                        matrices.pop();
                    });
                });
            }
        });
    }
}