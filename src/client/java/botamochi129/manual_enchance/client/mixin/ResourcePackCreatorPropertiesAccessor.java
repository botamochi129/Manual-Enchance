package botamochi129.manual_enchance.client.mixin;

import mtr.client.ResourcePackCreatorProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ResourcePackCreatorProperties.class)
public interface ResourcePackCreatorPropertiesAccessor {
    @Invoker("updateModel")
    void callUpdateModel();
}