package dev.trijug.giveaway;

import dev.trijug.giveaway.api.ApiClient;
import dev.trijug.giveaway.components.Header;

public class Main {

    static void main(String[] args) throws Exception {
        for (String arg : args) {
            if (arg.startsWith("--banner=")) {
                Header.activeBanner = arg.substring("--banner=".length());
            }
        }
        Config config = Config.load();
        ApiClient api = new ApiClient(config.apiUrl(), config.adminToken());
        AppState state = new AppState(api);
        new RootApp(state).run();
    }
}
