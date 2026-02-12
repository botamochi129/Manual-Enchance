package botamochi129.manual_enchance.mixin;

import botamochi129.manual_enchance.client.IResourcePackCreatorPropertiesHelper;
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

    // ユニークなフィールドとして定義（初期化は init 内で行う）
    @Unique private WidgetBetterCheckbox checkboxIsRollsign;
    @Unique private WidgetBetterCheckbox checkboxRollsignAnimation;
    @Unique private WidgetBetterTextField textFieldRollsignId;
    @Unique private WidgetBetterTextField textFieldRollsignTexture;
    @Unique private WidgetBetterTextField textFieldRollsignSteps;

    protected ResourcePackCreatorScreenMixin() { super(null); }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        // --- ウィジェットの生成 ---
        this.checkboxIsRollsign = new WidgetBetterCheckbox(0, 0, 0, 20, Text.translatable("gui.manual_enchance.is_rollsign"), (checked) -> {
            // 安全チェック: 編集モードかつインデックスが正当な場合のみ処理
            if (this.isEditing() && this.editingPartIndex >= 0 && RenderTrains.creatorProperties instanceof IResourcePackCreatorPropertiesHelper helper) {
                helper.editPartRollsign(this.editingPartIndex);
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

        // --- 座標とヒントの設定 ---
        int xStart = 0;
        IDrawing.setPositionAndWidth(this.checkboxIsRollsign, xStart, 80, 144);
        int detailY = 100;
        IDrawing.setPositionAndWidth(this.textFieldRollsignSteps, xStart + 2, detailY, 140);
        detailY += 22;
        IDrawing.setPositionAndWidth(this.checkboxRollsignAnimation, xStart, detailY, 144);
        detailY += 20;
        IDrawing.setPositionAndWidth(this.textFieldRollsignId, xStart + 2, detailY, 140);
        detailY += 22;
        IDrawing.setPositionAndWidth(this.textFieldRollsignTexture, xStart + 2, detailY, 140);

        this.textFieldRollsignSteps.setSuggestion(Text.translatable("gui.manual_enchance.rollsign_steps_hint").getString());
        this.textFieldRollsignId.setSuggestion(Text.translatable("gui.manual_enchance.rollsign_id_hint").getString());
        this.textFieldRollsignTexture.setSuggestion(Text.translatable("gui.manual_enchance.rollsign_texture_hint").getString());

        // --- リスナーの設定 (setResponder/setValueは1.19.2環境に合わせる) ---
        // 既存の ChangedListener でも動きますが、念のため安全チェックを追加
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

        // --- 画面への登録 ---
        this.addDrawableChild(this.checkboxIsRollsign);
        this.addDrawableChild(this.textFieldRollsignSteps);
        this.addDrawableChild(this.checkboxRollsignAnimation);
        this.addDrawableChild(this.textFieldRollsignId);
        this.addDrawableChild(this.textFieldRollsignTexture);

        // 最初はすべて非表示にしておく（updateControlsで制御するため）
        this.setRollsignWidgetsVisible(false);
    }

    @Inject(method = "updateControls", at = @At("TAIL"), remap = false)
    private void onUpdateControls(boolean formatTextFields, CallbackInfo ci) {
        if (this.checkboxIsRollsign == null) return;

        if (this.isEditing() && this.editingPartIndex >= 0) {
            JsonObject partObject = RenderTrains.creatorProperties.getPropertiesPartsArray().get(this.editingPartIndex).getAsJsonObject();

            boolean isDisplay = partObject.has("display");
            boolean isRollsign = partObject.has("rollsign") && partObject.get("rollsign").getAsBoolean();

            // 排他制御
            if (this.checkboxIsDisplay != null) {
                this.checkboxIsDisplay.visible = !isRollsign;
            }
            this.checkboxIsRollsign.visible = !isDisplay;
            this.checkboxIsRollsign.setChecked(isRollsign);

            // 詳細表示の制御
            boolean showDetails = isRollsign && !isDisplay;
            this.textFieldRollsignSteps.visible = showDetails;
            this.checkboxRollsignAnimation.visible = showDetails;
            this.textFieldRollsignId.visible = showDetails;
            this.textFieldRollsignTexture.visible = showDetails;

            if (showDetails && formatTextFields) {
                this.textFieldRollsignSteps.setValue(partObject.has("rollsign_steps") ? String.valueOf(partObject.get("rollsign_steps").getAsInt()) : "");
                this.checkboxRollsignAnimation.setChecked(!partObject.has("rollsign_animation") || partObject.get("rollsign_animation").getAsBoolean());
                this.textFieldRollsignId.setValue(partObject.has("rollsign_id") ? partObject.get("rollsign_id").getAsString() : "");
                this.textFieldRollsignTexture.setValue(partObject.has("rollsign_texture") ? partObject.get("rollsign_texture").getAsString() : "");
            }
        } else {
            // 編集モードでないなら確実に隠す
            this.setRollsignWidgetsVisible(false);
        }
    }

    @Unique
    private void setRollsignWidgetsVisible(boolean visible) {
        if (this.checkboxIsRollsign != null) this.checkboxIsRollsign.visible = visible;
        if (this.textFieldRollsignSteps != null) this.textFieldRollsignSteps.visible = visible;
        if (this.checkboxRollsignAnimation != null) this.checkboxRollsignAnimation.visible = visible;
        if (this.textFieldRollsignId != null) this.textFieldRollsignId.visible = visible;
        if (this.textFieldRollsignTexture != null) this.textFieldRollsignTexture.visible = visible;
    }
}