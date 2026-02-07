package botamochi129.manual_enchance.client.mixin;

import botamochi129.manual_enchance.client.util.IPartInfo;
import mtr.client.DynamicTrainModel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// 内部クラスを指定するために $ を使用
@Mixin(targets = "mtr.client.DynamicTrainModel$PartInfo")
public abstract class PartInfoMixin implements IPartInfo {
    @Shadow @Final private double originX;
    @Shadow @Final private double originY;
    @Shadow @Final private double originZ;
    @Shadow @Final private double offsetX;
    @Shadow @Final private double offsetY;
    @Shadow @Final private double offsetZ;
    @Shadow @Final private float rotationX;
    @Shadow @Final private float rotationY;
    @Shadow @Final private float rotationZ;
    @Shadow @Final private float width;
    @Shadow @Final private float height;

    @Override public double getOriginX() { return originX; }
    @Override public double getOriginY() { return originY; }
    @Override public double getOriginZ() { return originZ; }
    @Override public double getOffsetX() { return offsetX; }
    @Override public double getOffsetY() { return offsetY; }
    @Override public double getOffsetZ() { return offsetZ; }
    @Override public float getRotationX() { return rotationX; }
    @Override public float getRotationY() { return rotationY; }
    @Override public float getRotationZ() { return rotationZ; }
    @Override public float getWidth() { return width; }
    @Override public float getHeight() { return height; }
}