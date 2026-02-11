package botamochi129.manual_enchance.fabric;

import botamochi129.manual_enchance.MainClient;
import net.fabricmc.api.ClientModInitializer;

public class MainFabricClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		MainClient.init();
	}

}