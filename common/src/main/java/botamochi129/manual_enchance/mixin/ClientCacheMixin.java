package botamochi129.manual_enchance.mixin;

import botamochi129.manual_enchance.util.CouplingInfo;
import botamochi129.manual_enchance.util.RailwayDataAccessor;
import mtr.client.ClientCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashMap;
import java.util.Map;

@Mixin(value = ClientCache.class, remap = false)
public abstract class ClientCacheMixin implements RailwayDataAccessor {
    @Unique
    private final Map<Long, CouplingInfo> manualEnchance$couplingMap = new HashMap<>();

    @Override
    public Map<Long, CouplingInfo> manualEnchance$getCouplingMap() {
        return manualEnchance$couplingMap;
    }
}