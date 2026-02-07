package botamochi129.manual_enchance.client.mixin;

import botamochi129.manual_enchance.client.util.IResourcePackCreatorPropertiesHelper;
import com.google.gson.JsonObject;
import mtr.client.IDrawing;
import mtr.mappings.ScreenMapper;
import mtr.mappings.Text;
import mtr.render.RenderTrains;
import mtr.screen.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.Consumer;

@Mixin(value = ResourcePackCreatorScreen.class, remap = false)
public abstract class ResourcePackCreatorScreenMixin extends ScreenMapper {
    @Shadow private int editingPartIndex;

    @Unique private final WidgetBetterCheckbox checkboxIsRollsign = new WidgetBetterCheckbox(0, 0, 0, 20, Text.translatable("gui.manual_enchance.is_rollsign"), (checked) -> {
        if (RenderTrains.creatorProperties instanceof IResourcePackCreatorPropertiesHelper helper) {
            helper.editPartRollsign(this.editingPartIndex);
        }
        this.updateControls(true);
    });

    @Unique private final WidgetBetterCheckbox checkboxRollsignAnimation = new WidgetBetterCheckbox(0, 0, 0, 20, Text.translatable("gui.manual_enchance.rollsign_animation"), (checked) -> {
        if (this.isEditing()) {
            RenderTrains.creatorProperties.getPropertiesPartsArray().get(this.editingPartIndex).getAsJsonObject().addProperty("rollsign_animation", checked);
            this.updateControls(true);
        }
    });

    // テキストフィールド群
    @Unique private final WidgetBetterTextField textFieldRollsignId = new WidgetBetterTextField("", Integer.MAX_VALUE);
    @Unique private final WidgetBetterTextField textFieldRollsignTexture = new WidgetBetterTextField("", Integer.MAX_VALUE);
    @Unique private final WidgetBetterTextField textFieldRollsignSteps = new WidgetBetterTextField("", 3); // 数値用なので短めでOK

    protected ResourcePackCreatorScreenMixin() { super(null); }

    @Shadow protected abstract void updateControls(boolean formatTextFields);
    @Shadow public abstract boolean isEditing();
    // MTR標準のDisplayチェックボックスを操作するためにShadowで取得（フィールド名が異なる場合は適宜読み替えてください）
    @Shadow private WidgetBetterCheckbox checkboxIsDisplay;

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        int xStart = 0;

        // --- 座標設定 ---
        // 既存の Is Display (y=60) のすぐ下に配置
        IDrawing.setPositionAndWidth(this.checkboxIsRollsign, xStart, 80, 144);

        // その他の詳細設定（Rollsign ON時のみ）は y=100 以降に配置
        int detailY = 100;
        IDrawing.setPositionAndWidth(this.textFieldRollsignSteps, xStart + 2, detailY, 140);
        detailY += 22;
        IDrawing.setPositionAndWidth(this.checkboxRollsignAnimation, xStart, detailY, 144);
        detailY += 20;
        IDrawing.setPositionAndWidth(this.textFieldRollsignId, xStart + 2, detailY, 140);
        detailY += 22;
        IDrawing.setPositionAndWidth(this.textFieldRollsignTexture, xStart + 2, detailY, 140);

        // ヒントテキスト
        this.textFieldRollsignSteps.setSuggestion(Text.translatable("gui.manual_enchance.rollsign_steps_hint").getString());
        this.textFieldRollsignId.setSuggestion(Text.translatable("gui.manual_enchance.rollsign_id_hint").getString());
        this.textFieldRollsignTexture.setSuggestion(Text.translatable("gui.manual_enchance.rollsign_texture_hint").getString());

        // 入力リスナー (Stepsは数値のみ受け付ける)
        // 入力リスナー (Stepsは数値のみ受け付ける)
        this.textFieldRollsignSteps.setChangedListener(text -> {
            if (this.isEditing()) {
                try {
                    // 空文字でなければ数値を保存、空ならデフォルトの1を保存
                    int steps = text.isEmpty() ? 1 : Integer.parseInt(text);
                    RenderTrains.creatorProperties.getPropertiesPartsArray().get(this.editingPartIndex).getAsJsonObject().addProperty("rollsign_steps", steps);
                } catch (NumberFormatException ignored) {
                    // 数字以外が入力された場合は何もしない（または1にする）
                }
            }
        });

        this.textFieldRollsignId.setChangedListener(text -> {
            if (this.isEditing()) {
                RenderTrains.creatorProperties.getPropertiesPartsArray().get(this.editingPartIndex).getAsJsonObject().addProperty("rollsign_id", text);
            }
        });

        this.textFieldRollsignTexture.setChangedListener(text -> {
            if (this.isEditing()) {
                RenderTrains.creatorProperties.getPropertiesPartsArray().get(this.editingPartIndex).getAsJsonObject().addProperty("rollsign_texture", text);
            }
        });

        this.addDrawableChild(this.checkboxIsRollsign);
        this.addDrawableChild(this.textFieldRollsignSteps);
        this.addDrawableChild(this.checkboxRollsignAnimation);
        this.addDrawableChild(this.textFieldRollsignId);
        this.addDrawableChild(this.textFieldRollsignTexture);
    }

    @Inject(method = "updateControls", at = @At("TAIL"))
    private void onUpdateControls(boolean formatTextFields, CallbackInfo ci) {
        // 1. まず、パーツ編集モード(isEditing) かどうかを確認
        // checkboxIsRollsign が null でないことも確認（初期化直後の呼び出し対策）
        if (this.isEditing() && this.checkboxIsRollsign != null) {
            JsonObject partObject = RenderTrains.creatorProperties.getPropertiesPartsArray().get(this.editingPartIndex).getAsJsonObject();

            boolean isDisplay = partObject.has("display");
            boolean isRollsign = partObject.has("rollsign") && partObject.get("rollsign").getAsBoolean();

            // --- 排他制御ロジック ---
            // DisplayがONならRollsignを隠し、RollsignがONならDisplayを隠す
            if (this.checkboxIsDisplay != null) {
                this.checkboxIsDisplay.visible = !isRollsign;
            }
            this.checkboxIsRollsign.visible = !isDisplay;
            this.checkboxIsRollsign.setChecked(isRollsign);

            // --- 詳細設定の表示制御 ---
            // パーツ編集モード かつ RollsignがON かつ DisplayがOFF の時だけ表示
            boolean showRollsignDetails = isRollsign && !isDisplay;

            this.textFieldRollsignSteps.visible = showRollsignDetails;
            this.checkboxRollsignAnimation.visible = showRollsignDetails;
            this.textFieldRollsignId.visible = showRollsignDetails;
            this.textFieldRollsignTexture.visible = showRollsignDetails;

            if (showRollsignDetails && formatTextFields) {
                this.textFieldRollsignSteps.setText(partObject.has("rollsign_steps") ? String.valueOf(partObject.get("rollsign_steps").getAsInt()) : "");
                this.checkboxRollsignAnimation.setChecked(!partObject.has("rollsign_animation") || partObject.get("rollsign_animation").getAsBoolean());
                this.textFieldRollsignId.setText(partObject.has("rollsign_id") ? partObject.get("rollsign_id").getAsString() : "");
                this.textFieldRollsignTexture.setText(partObject.has("rollsign_texture") ? partObject.get("rollsign_texture").getAsString() : "");
            }
        } else if (this.checkboxIsRollsign != null) {
            // 2. パーツ編集モードでない（車両設定画面など）場合は、すべて強制的に非表示にする
            this.checkboxIsRollsign.visible = false;
            this.textFieldRollsignSteps.visible = false;
            this.checkboxRollsignAnimation.visible = false;
            this.textFieldRollsignId.visible = false;
            this.textFieldRollsignTexture.visible = false;
        }
    }
}