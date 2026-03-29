package dev.trijug.giveaway.screens;

import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.event.GlobalEventHandler;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyEvent;
import dev.trijug.giveaway.AppState;
import dev.trijug.giveaway.ScreenView;
import dev.trijug.giveaway.components.Header;

import java.util.function.Consumer;

import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.list;
import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.text;

public class HomeScreen implements ScreenView {

    private final AppState state;
    private final ListElement<?> menu;
    private ToolkitRunner runner;
    private Consumer<AppState.Screen> navigate;
    private final GlobalEventHandler handler = this::handleKey;

    public HomeScreen(AppState state) {
        this.state = state;
        this.menu = list("Pick Winner", "Manage Entries", "Manage Giveaways", "Quit")
                .focusable()
                .id("menu");
    }

    @Override
    public void activate(ToolkitRunner runner, Consumer<AppState.Screen> navigate) {
        this.runner = runner;
        this.navigate = navigate;
        runner.focusManager().setFocus("menu");
        runner.eventRouter().addGlobalHandler(handler);
    }

    @Override
    public void deactivate() {
        runner.eventRouter().removeGlobalHandler(handler);
    }

    private void doNavigate(int idx) {
        switch (idx) {
            case 0 -> navigate.accept(AppState.Screen.PICK);
            case 1 -> navigate.accept(AppState.Screen.ENTRIES);
            case 2 -> navigate.accept(AppState.Screen.GIVEAWAYS);
            case 3 -> System.exit(0);
        }
    }

    private EventResult handleKey(Event event) {
        if (!(event instanceof KeyEvent key)) {
            return EventResult.UNHANDLED;
        }
        if (key.isSelect() || key.isConfirm()) {
            int idx = menu.selected();
            runner.runOnRenderThread(() -> doNavigate(idx));
            return EventResult.HANDLED;
        }
        if (key.isQuit() || key.isCharIgnoreCase('q')) {
            System.exit(0);
        }
        return EventResult.UNHANDLED;
    }

    @Override
    public Element render() {
        return column(
                Header.render(state.activeName(), state.activeDate()),
                panel(" Menu", menu).rounded().fill(),
                text("↑/↓  navigate     Enter  select     q  quit").dim().length(1)
        );
    }
}
