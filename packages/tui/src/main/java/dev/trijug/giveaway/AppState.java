package dev.trijug.giveaway;

import dev.trijug.giveaway.api.ApiClient;
import dev.trijug.giveaway.api.Json;

import java.util.Map;

public class AppState {

    private final ApiClient api;
    private String activeGiveawayId;
    private String activeGiveawayName;
    private String activeGiveawayDate;
    public AppState(ApiClient api) {
        this.api = api;
        refreshActive();
    }

    public String activeDate() {
        return activeGiveawayDate;
    }

    public String activeId() {
        return activeGiveawayId;
    }

    public String activeName() {
        return activeGiveawayName;
    }

    public ApiClient api() {
        return api;
    }

    public boolean hasActive() {
        return activeGiveawayId != null;
    }

    public void refreshActive() {
        var res = api.getActiveGiveaway();
        if (res.ok()) {
            Map<String, Object> g = Json.parseObject(res.body());
            activeGiveawayId = Json.str(g, "id");
            activeGiveawayName = Json.str(g, "name");
            activeGiveawayDate = Json.str(g, "date");
        } else {
            activeGiveawayId = null;
            activeGiveawayName = null;
            activeGiveawayDate = null;
        }
    }

    public enum Screen {HOME, PICK, ENTRIES, GIVEAWAYS}
}
