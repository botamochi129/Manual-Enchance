package botamochi129.manual_enchance.mixin;

import botamochi129.manual_enchance.client.IResourcePackCreatorPropertiesHelper;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mtr.client.IDrawing;
import mtr.mappings.ScreenMapper;
import mtr.mappings.Text;
import mtr.render.RenderTrains;
import mtr.screen.ResourcePackCreatorScreen;
import mtr.screen.WidgetBetterCheckbox;
import mtr.screen.WidgetBetterTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ResourcePackCreatorScreen.class)
public abstract class ResourcePackCreatorScreenMixin extends ScreenMapper {
    @Shadow(remap = false) private int editingPartIndex;
    @Shadow(remap = false) protected abstract void updateControls(boolean formatTextFields);
    @Shadow(remap = false) public abstract boolean isEditing();
    @Shadow(remap = false) private WidgetBetterCheckbox checkboxIsDisplay;

    @Unique private WidgetBetterCheckbox checkboxIsRollsign;
    @Unique private WidgetBetterCheckbox checkboxIsBogie;
    @Unique private WidgetBetterCheckbox checkboxRollsignAnimation;
    @Unique private WidgetBetterTextField textFieldRollsignId;
    @Unique private WidgetBetterTextField textFieldRollsignTexture;
    @Unique private WidgetBetterTextField textFieldRollsignSteps;

    protected ResourcePackCreatorScreenMixin() { super(null); }

    @Inject(
            method = {"init", "method_25426"},
            at = @At("TAIL"),
            remap = true,
            require = 0
    )
    private void onInit(CallbackInfo ci) {
        this.checkboxIsRollsign = new WidgetBetterCheckbox(0, 0, 0, 20, Text.translatable("gui.manual_enchance.is_rollsign"), (checked) -> {
            if (this.isEditing() && this.editingPartIndex >= 0 && RenderTrains.creatorProperties instanceof IResourcePackCreatorPropertiesHelper helper) {
                helper.editPartRollsign(this.editingPartIndex);
            }
            this.updateControls(true);
        });

        this.checkboxIsBogie = new WidgetBetterCheckbox(0, 0, 0, 20, Text.translatable("gui.manual_enchance.is_bogie"), (checked) -> {
            if (this.isEditing() && this.editingPartIndex >= 0 && RenderTrains.creatorProperties instanceof IResourcePackCreatorPropertiesHelper helper) {
                helper.editPartBogie(this.editingPartIndex);
            }
            this.updateControls(true);
        });

        this.checkboxRollsignAnimation = new WidgetBetterCheckbox(0, 0, 0, 20, Text.translatable("gui.manual_enchance.rollsign_animation"), (checked) -> {
            if (this.isEditing() && this.editingPartIndex >= 0) {
                RenderTrains.creatorProperties.getPropertiesPartsArray().get(this.editingPartIndex).getAsJsonObject().addProperty("rollsign_animation", checked);
                this.updateControls(true);
            }
        });

        this.textFieldRollsignId = new WidgetBetterTextField("", Integer.MAX_VALUE);
        this.textFieldRollsignTexture = new WidgetBetterTextField("", Integer.MAX_VALUE);
        this.textFieldRollsignSteps = new WidgetBetterTextField("", 3);

        this.textFieldRollsignSteps.setSuggestion(Text.translatable("gui.manual_enchance.rollsign_steps_hint").getString());
        this.textFieldRollsignId.setSuggestion(Text.translatable("gui.manual_enchance.rollsign_id_hint").getString());
        this.textFieldRollsignTexture.setSuggestion(Text.translatable("gui.manual_enchance.rollsign_texture_hint").getString());

        this.textFieldRollsignSteps.setResponder(text -> {
            if (this.isEditing() && this.editingPartIndex >= 0) {
                try {
                    int steps = text.isEmpty() ? 1 : Integer.parseInt(text);
                    RenderTrains.creatorProperties.getPropertiesPartsArray().get(this.editingPartIndex).getAsJsonObject().addProperty("rollsign_steps", steps);
                } catch (NumberFormatException ignored) {}
            }
        });

        this.textFieldRollsignId.setResponder(text -> {
            if (this.isEditing() && this.editingPartIndex >= 0) {
                RenderTrains.creatorProperties.getPropertiesPartsArray().get(this.editingPartIndex).getAsJsonObject().addProperty("rollsign_id", text);
            }
        });

        this.textFieldRollsignTexture.setResponder(text -> {
            if (this.isEditing() && this.editingPartIndex >= 0) {
                RenderTrains.creatorProperties.getPropertiesPartsArray().get(this.editingPartIndex).getAsJsonObject().addProperty("rollsign_texture", text);
            }
        });

        this.addDrawableChild(this.checkboxIsRollsign);
        this.addDrawableChild(this.checkboxIsBogie);
        this.addDrawableChild(this.textFieldRollsignSteps);
        this.addDrawableChild(this.checkboxRollsignAnimation);
        this.addDrawableChild(this.textFieldRollsignId);
        this.addDrawableChild(this.textFieldRollsignTexture);

        this.setCustomPartWidgetsVisible(false);
    }

    @Inject(method = "updateControls", at = @At("TAIL"), remap = false)
    private void onUpdateControls(boolean formatTextFields, CallbackInfo ci) {
        if (this.checkboxIsRollsign == null || this.checkboxIsDisplay == null) return;

        int baseX = (int) this.checkboxIsDisplay.x;
        int baseY = (int) this.checkboxIsDisplay.y;

        if (this.isEditing() && this.editingPartIndex >= 0) {
            JsonObject partObject = RenderTrains.creatorProperties.getPropertiesPartsArray().get(this.editingPartIndex).getAsJsonObject();

            boolean isDisplay = partObject.has("display");
            boolean isRollsign = partObject.has("rollsign") && partObject.get("rollsign").getAsBoolean();
            boolean isBogie;
            if (!partObject.has("bogie")) {
                isBogie = false;
            } else {
                JsonElement bogie = partObject.get("bogie");
                isBogie = bogie.isJsonObject()
                        || (bogie.isJsonPrimitive() && bogie.getAsBoolean());
            }

            this.checkboxIsDisplay.visible = !isRollsign && !isBogie;
            this.checkboxIsDisplay.setChecked(isDisplay);

            this.checkboxIsRollsign.visible = !isDisplay && !isBogie;
            this.checkboxIsRollsign.setChecked(isRollsign);
            IDrawing.setPositionAndWidth(this.checkboxIsRollsign, baseX, baseY + 24, 150);

            this.checkboxIsBogie.visible = !isDisplay && !isRollsign;
            this.checkboxIsBogie.setChecked(isBogie);
            IDrawing.setPositionAndWidth(this.checkboxIsBogie, baseX, baseY + 48, 150);

            boolean showRollsignDetails = isRollsign && !isDisplay && !isBogie;
            this.textFieldRollsignSteps.visible = showRollsignDetails;
            this.checkboxRollsignAnimation.visible = showRollsignDetails;
            this.textFieldRollsignId.visible = showRollsignDetails;
            this.textFieldRollsignTexture.visible = showRollsignDetails;

            if (showRollsignDetails) {
                int detailX = baseX;
                int detailY = baseY + 72;
                IDrawing.setPositionAndWidth(this.textFieldRollsignSteps, detailX + 2, detailY, 146);
                detailY += 22;
                IDrawing.setPositionAndWidth(this.checkboxRollsignAnimation, detailX, detailY, 150);
                detailY += 20;
                IDrawing.setPositionAndWidth(this.textFieldRollsignId, detailX + 2, detailY, 146);
                detailY += 22;
                IDrawing.setPositionAndWidth(this.textFieldRollsignTexture, detailX + 2, detailY, 146);

                if (formatTextFields) {
                    this.textFieldRollsignSteps.setValue(partObject.has("rollsign_steps") ? String.valueOf(partObject.get("rollsign_steps").getAsInt()) : "");
                    this.checkboxRollsignAnimation.setChecked(!partObject.has("rollsign_animation") || partObject.get("rollsign_animation").getAsBoolean());
                    this.textFieldRollsignId.setValue(partObject.has("rollsign_id") ? partObject.get("rollsign_id").getAsString() : "");
                    this.textFieldRollsignTexture.setValue(partObject.has("rollsign_texture") ? partObject.get("rollsign_texture").getAsString() : "");
                }
            }
        } else {
            this.setCustomPartWidgetsVisible(false);
        }
    }

    @Unique
    private void setCustomPartWidgetsVisible(boolean visible) {
        if (this.checkboxIsBogie != null) this.checkboxIsBogie.visible = visible;
        if (this.checkboxIsRollsign != null) this.checkboxIsRollsign.visible = visible;
        if (this.textFieldRollsignSteps != null) this.textFieldRollsignSteps.visible = visible;
        if (this.checkboxRollsignAnimation != null) this.checkboxRollsignAnimation.visible = visible;
        if (this.textFieldRollsignId != null) this.textFieldRollsignId.visible = visible;
        if (this.textFieldRollsignTexture != null) this.textFieldRollsignTexture.visible = visible;
    }
}
