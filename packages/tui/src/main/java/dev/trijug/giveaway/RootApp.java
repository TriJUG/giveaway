package dev.trijug.giveaway;

import dev.tamboui.toolkit.app.ToolkitApp;
import dev.tamboui.toolkit.element.Element;
import dev.trijug.giveaway.screens.EntriesScreen;
import dev.trijug.giveaway.screens.GiveawaysScreen;
import dev.trijug.giveaway.screens.HomeScreen;
import dev.trijug.giveaway.screens.PickScreen;

public class RootApp extends ToolkitApp {

    private final AppState state;
    private final HomeScreen homeScreen;
    private ScreenView current;

    public RootApp(AppState state) {
        this.state = state;
        this.homeScreen = new HomeScreen(state);
    }

    private void navigateTo(AppState.Screen screen) {
        if (current != null) {
            current.deactivate();
        }
        current = switch (screen) {
            case HOME -> homeScreen;
            case PICK -> new PickScreen(state);
            case ENTRIES -> new EntriesScreen(state);
            case GIVEAWAYS -> new GiveawaysScreen(state);
        };
        current.activate(runner(), this::navigateTo);
    }

    @Override
    protected void onStart() {
        navigateTo(AppState.Screen.HOME);
    }

    @Override
    protected Element render() {
        return current.render();
    }
}
