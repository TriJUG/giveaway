package dev.trijug.giveaway;

import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.element.Element;

import java.util.function.Consumer;

public interface ScreenView {
    void activate(ToolkitRunner runner, Consumer<AppState.Screen> navigate);

    void deactivate();

    Element render();
}
