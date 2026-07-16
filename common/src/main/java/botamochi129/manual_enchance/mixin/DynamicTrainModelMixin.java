package botamochi129.manual_enchance.mixin;

import botamochi129.manual_enchance.client.BogieHelper;
import botamochi129.manual_enchance.client.IPartInfo;
import botamochi129.manual_enchance.client.PantoHelper;
import botamochi129.manual_enchance.util.TrainAccessor;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mtr.client.DynamicTrainModel;
import mtr.client.IDrawing;
import mtr.client.ScrollingText;
import mtr.data.Route;
import mtr.data.Station;
import mtr.data.TrainClient;
import mtr.mappings.ModelMapper;
import mtr.mappings.UtilitiesClient;
import mtr.model.ModelTrainBase;
import mtr.render.MoreRenderLayers;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Mixin(value = DynamicTrainModel.class, remap = false)
public abstract class DynamicTrainModelMixin {

    @Shadow @Final public Map<String, ModelMapper> parts;

    @Inject(method = "shouldSkipRender", at = @At("HEAD"), cancellable = true)
    private void onShouldSkipRender(JsonObject partObject, CallbackInfoReturnable<Boolean> cir) {
        if (BogieHelper.isBogiePart(partObject)) {
            cir.setReturnValue(true);
            return;
        }

        if (!partObject.has("render_condition")) return;
        String condition = partObject.get("render_condition").getAsString();

        TrainClient train = PantoHelper.getCurrentTrain();
        if (train == null) return;

        int pantoState = ((TrainAccessor) train).getPantographState();
        switch (condition) {
            case "PANTAGRAPH_DOWN":  if (pantoState != 0) cir.setReturnValue(true); break;
            case "PANTAGRAPH_5M":   if (pantoState != 1) cir.setReturnValue(true); break;
            case "PANTAGRAPH_W51":  if (pantoState != 2) cir.setReturnValue(true); break;
            case "PANTAGRAPH_6M":   if (pantoState != 3) cir.setReturnValue(true); break;
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void manualEnchance$renderBogies(
            PoseStack matrices,
            VertexConsumer vertexConsumer,
            ModelTrainBase.RenderStage stage,
            int light,
            float doorLeftX,
            float doorRightX,
            float doorLeftZ,
            float doorRightZ,
            int currentCar,
            int totalCars,
            boolean head1IsFront,
            boolean skipRenderingIfTooFar,
            CallbackInfo ci
    ) {
        TrainClient train = PantoHelper.getCurrentTrain();

        this.iterateParts(currentCar, totalCars, partObject -> {
            if (!BogieHelper.isBogiePart(partObject)) return;
            if (!manualEnchance$matchesRenderStage(partObject, stage)) return;
            if (manualEnchance$shouldSkipByRenderCondition(partObject)) return;

            // Jacobs bogie: skip on the last car to avoid double-rendering
            if (BogieHelper.isJacobsBogie(partObject) && currentCar >= totalCars - 1) return;

            String name = partObject.get("name").getAsString();
            ModelMapper model = this.parts.get(name);
            if (model == null) return;

            float offsetX = this.getOffsetX(partObject);
            float offsetZ = this.getOffsetZ(partObject);
            boolean mirror = partObject.has("mirror") && partObject.get("mirror").getAsBoolean();
            float[] rotationDelta = train != null
                    ? BogieHelper.computeRailRotationDelta(train, currentCar, partObject)
                    : new float[]{0.0f, 0.0f};

            JsonArray positions = BogieHelper.getBogiePositions(partObject);
            if (positions == null) return;

            positions.forEach(positionElement -> {
                float posX = positionElement.getAsJsonArray().get(0).getAsFloat() + offsetX;
                float posZ = positionElement.getAsJsonArray().get(1).getAsFloat() + offsetZ;
                manualEnchance$renderBogieModel(
                        model, matrices, vertexConsumer, light, mirror,
                        posX, posZ, rotationDelta[0], rotationDelta[1]
                );
            });
        });
    }

    @Unique
    private void manualEnchance$renderBogieModel(
            ModelMapper model,
            PoseStack matrices,
            VertexConsumer vertexConsumer,
            int light,
            boolean mirror,
            float posX,
            float posZ,
            float yawDelta,
            float pitchDelta
    ) {
        matrices.pushPose();
        matrices.translate(posX / 16.0, 0.0, posZ / 16.0);
        if (mirror) UtilitiesClient.rotateYDegrees(matrices, 180.0F);
        if (Math.abs(yawDelta) > 0.001f) UtilitiesClient.rotateYDegrees(matrices, yawDelta);
        if (Math.abs(pitchDelta) > 0.001f) UtilitiesClient.rotateXDegrees(matrices, pitchDelta);
        if (mirror) {
            ModelTrainBaseAccessor.invokeRenderOnceFlipped(model, matrices, vertexConsumer, light, 0.0f, 0.0f);
        } else {
            ModelTrainBaseAccessor.invokeRenderOnce(model, matrices, vertexConsumer, light, 0.0f, 0.0f);
        }
        matrices.popPose();
    }

    @Unique
    private boolean manualEnchance$shouldSkipByRenderCondition(JsonObject partObject) {
        if (!partObject.has("render_condition")) return false;
        String condition = partObject.get("render_condition").getAsString();

        TrainClient train = PantoHelper.getCurrentTrain();
        if (train == null) return false;

        int pantoState = ((TrainAccessor) train).getPantographState();
        switch (condition) {
            case "PANTAGRAPH_DOWN":
                return pantoState != 0;
            case "PANTAGRAPH_5M":
                return pantoState != 1;
            case "PANTAGRAPH_W51":
                return pantoState != 2;
            case "PANTAGRAPH_6M":
                return pantoState != 3;
            default:
                return false;
        }
    }

    @Unique
    private boolean manualEnchance$matchesRenderStage(JsonObject partObject, ModelTrainBase.RenderStage stage) {
        if (!partObject.has("stage")) return true;
        return stage.toString().equalsIgnoreCase(partObject.get("stage").getAsString());
    }

    @Shadow protected abstract boolean shouldSkipRender(JsonObject partObject);
    @Shadow protected abstract float getOffsetX(JsonObject partObject);
    @Shadow protected abstract float getOffsetZ(JsonObject partObject);
    @Shadow protected abstract void iterateParts(int currentCar, int trainCars, java.util.function.Consumer<JsonObject> callback);

    @Inject(method = "renderTextDisplays", at = @At("TAIL"))
    private void onRenderRollsigns(PoseStack matrices, MultiBufferSource vertexConsumers, Font font, MultiBufferSource.BufferSource immediate, Route thisRoute, Route nextRoute, Station thisStation, Station nextStation, Station lastStation, String customDestination, int car, int totalCars, boolean atPlatform, List<ScrollingText> scrollingTexts, CallbackInfo ci) {

        TrainClient train = PantoHelper.getCurrentTrain();
        if (!(train instanceof TrainAccessor accessor)) return;

        this.iterateParts(car, totalCars, (partObject) -> {
            if (!partObject.has("name")) return;
            String name = partObject.get("name").getAsString();

            if (partObject.has("rollsign") && partObject.get("rollsign").getAsBoolean()) {
                String rollsignId = partObject.has("rollsign_id") ? partObject.get("rollsign_id").getAsString() : name;
                int totalSteps = partObject.has("rollsign_steps") ? partObject.get("rollsign_steps").getAsInt() : 1;

                accessor.setRollsignSteps(rollsignId, totalSteps);
                if (!accessor.getRollsignIndices().containsKey(rollsignId)) accessor.setRollsignIndex(rollsignId, 0);
                // Read rollsign_names from JSON: an array of display names for each step
                if (partObject.has("rollsign_names") && partObject.get("rollsign_names").isJsonArray()) {
                    JsonArray namesArray = partObject.getAsJsonArray("rollsign_names");
                    java.util.List<String> names = new java.util.ArrayList<>();
                    for (int i = 0; i < namesArray.size(); i++) {
                        names.add(namesArray.get(i).getAsString());
                    }
                    accessor.setRollsignNames(rollsignId, names);
                }
                if (this.shouldSkipRender(partObject)) return;
                if (!this.partsInfo.containsKey(name)) return;

                String texturePath = partObject.has("rollsign_texture") ? partObject.get("rollsign_texture").getAsString() : "mtr:example/example.png";
                boolean mirror = partObject.has("mirror") && partObject.get("mirror").getAsBoolean();
                boolean enableAnimation = !partObject.has("rollsign_animation") || partObject.get("rollsign_animation").getAsBoolean();

                float vStep = 1.0f / totalSteps;
                float displayOffset = enableAnimation ? accessor.getRollsignOffset(rollsignId) : (float) accessor.getRollsignIndex(rollsignId);
                float v1 = displayOffset * vStep;
                float v2 = v1 + vStep;

                ResourceLocation textureId = new ResourceLocation(texturePath);
                float xOffset = this.getOffsetX(partObject);
                float zOffset = this.getOffsetZ(partObject);

                partObject.getAsJsonArray("positions").forEach((positionElement) -> {
                    float posX = positionElement.getAsJsonArray().get(0).getAsFloat() + xOffset;
                    float posZ = positionElement.getAsJsonArray().get(1).getAsFloat() + zOffset;

                    Set<?> partInfoSet = this.partsInfo.get(name);
                    if (partInfoSet == null) return;
                    partInfoSet.forEach((partInfoObj) -> {
                        IPartInfo partInfo = (IPartInfo) partInfoObj;
                        matrices.pushPose();
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
                        matrices.popPose();
                    });
                });
            }
        });
    }

    @Shadow @Final private Map<String, Set<?>> partsInfo;
}
