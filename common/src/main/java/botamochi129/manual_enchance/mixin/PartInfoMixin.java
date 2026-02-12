package botamochi129.manual_enchance.mixin;

import botamochi129.manual_enchance.client.IPartInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// 内部クラスを指定するために $ を使用
@Mixin(targets = "mtr.client.DynamicTrainModel$PartInfo")
public abstract class PartInfoMixin implements IPartInfo {
    @Shadow(remap = false) @Final private double originX;
    @Shadow(remap = false) @Final private double originY;
    @Shadow(remap = false) @Final private double originZ;
    @Shadow(remap = false) @Final private double offsetX;
    @Shadow(remap = false) @Final private double offsetY;
    @Shadow(remap = false) @Final private double offsetZ;
    @Shadow(remap = false) @Final private float rotationX;
    @Shadow(remap = false) @Final private float rotationY;
    @Shadow(remap = false) @Final private float rotationZ;
    @Shadow(remap = false) @Final private float width;
    @Shadow(remap = false) @Final private float height;

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