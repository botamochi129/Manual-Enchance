package botamochi129.manual_enchance.fabric;

import botamochi129.manual_enchance.Main;
import net.fabricmc.api.ModInitializer;

public class MainFabric implements ModInitializer {

	@Override
	public void onInitialize() {
		Main.init(new RegistriesWrapperImpl());
	}

}
