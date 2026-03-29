package dev.trijug.giveaway.screens;

import dev.tamboui.layout.Constraint;
import dev.tamboui.style.Color;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.TextInputElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.event.GlobalEventHandler;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.TableState;
import dev.trijug.giveaway.AppState;
import dev.trijug.giveaway.ScreenView;
import dev.trijug.giveaway.api.Json;
import dev.trijug.giveaway.components.Header;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.spacer;
import static dev.tamboui.toolkit.Toolkit.table;
import static dev.tamboui.toolkit.Toolkit.text;
import static dev.tamboui.toolkit.Toolkit.textInput;

public class GiveawaysScreen implements ScreenView {

    private final AppState state;
    private final TableState tableState = new TableState();
    private final TextInputState nameState = new TextInputState();
    private final TextInputState dateState = new TextInputState();
    private final TextInputElement nameField;
    private final TextInputElement dateField;
    private ToolkitRunner runner;
    private Consumer<AppState.Screen> navigate;
    private Mode mode = Mode.TABLE;
    private List<Map<String, Object>> giveaways = List.of();
    private String statusMsg = "";
    private boolean createActive = false;
    private final GlobalEventHandler handler = this::handleKey;
    public GiveawaysScreen(AppState state) {
        this.state = state;
        nameField = textInput(nameState)
                .placeholder("e.g. April 2026 Meetup")
                .focusable()
                .id("create-name")
                .onSubmit(() -> {
                    mode = Mode.CREATE_DATE;
                    runner.focusManager().setFocus("create-date");
                });
        dateField = textInput(dateState)
                .placeholder("YYYY-MM-DD")
                .focusable()
                .id("create-date")
                .onSubmit(() -> mode = Mode.CREATE_ACTIVE);
    }

    @Override
    public void activate(ToolkitRunner runner, Consumer<AppState.Screen> navigate) {
        this.runner = runner;
        this.navigate = navigate;
        runner.eventRouter().addGlobalHandler(handler);
        runner.runOnRenderThread(this::loadGiveaways);
    }

    @Override
    public void deactivate() {
        runner.eventRouter().removeGlobalHandler(handler);
    }

    private EventResult handleKey(Event event) {
        if (!(event instanceof KeyEvent key)) {
            return EventResult.UNHANDLED;
        }
        return switch (mode) {
            case TABLE -> {
                if (key.isUp()) {
                    tableState.selectPrevious();
                    yield EventResult.HANDLED;
                }
                if (key.isDown()) {
                    tableState.selectNext(giveaways.size());
                    yield EventResult.HANDLED;
                }
                if (key.isCharIgnoreCase('n')) {
                    nameState.clear();
                    dateState.clear();
                    createActive = false;
                    mode = Mode.CREATE_NAME;
                    runner.focusManager().setFocus("create-name");
                    yield EventResult.HANDLED;
                }
                if (key.isChar(' ')) {
                    toggleActive();
                    yield EventResult.HANDLED;
                }
                if (key.isCancel() || key.isQuit()) {
                    runner.runOnRenderThread(() -> navigate.accept(AppState.Screen.HOME));
                    yield EventResult.HANDLED;
                }
                yield EventResult.UNHANDLED;
            }
            case CREATE_NAME, CREATE_DATE -> {
                if (key.isCancel()) {
                    mode = Mode.TABLE;
                    yield EventResult.HANDLED;
                }
                yield EventResult.UNHANDLED;
            }
            case CREATE_ACTIVE -> {
                if (key.isCharIgnoreCase('y')) {
                    createActive = true;
                    yield EventResult.HANDLED;
                }
                if (key.isCharIgnoreCase('n')) {
                    createActive = false;
                    yield EventResult.HANDLED;
                }
                if (key.isSelect() || key.isConfirm()) {
                    saveGiveaway();
                    yield EventResult.HANDLED;
                }
                if (key.isCancel()) {
                    mode = Mode.TABLE;
                    yield EventResult.HANDLED;
                }
                yield EventResult.UNHANDLED;
            }
        };
    }

    private void loadGiveaways() {
        var res = state.api().listGiveaways();
        if (res.ok()) {
            giveaways = Json.parseArray(res.body());
        } else {
            statusMsg = "Failed to load giveaways.";
        }
    }

    @Override
    public Element render() {
        String hint = switch (mode) {
            case TABLE -> "[N] new    [Space] toggle active    [Esc] back";
            case CREATE_NAME, CREATE_DATE -> "[Enter] next    [Esc] cancel";
            case CREATE_ACTIVE -> "[Y] Yes    [N] No    [Enter] save    [Esc] cancel";
        };
        return column(
                Header.render(state.activeName(), state.activeDate()),
                switch (mode) {
                    case TABLE -> renderTable();
                    case CREATE_NAME, CREATE_DATE, CREATE_ACTIVE -> renderCreate();
                },
                text(hint).dim().length(1),
                text(statusMsg.isEmpty() ? " " : statusMsg).dim().length(1)
        );
    }

    private Element renderCreate() {
        if (mode == Mode.CREATE_ACTIVE) {
            return panel(" New Giveaway — Step 3/3 — Set active?",
                    column(
                            text("Name:  " + nameState.text()).dim(),
                            text("Date:  " + dateState.text()).dim(),
                            spacer(1),
                            text("Set as active? [Y] Yes  [N] No  (currently: " + (createActive ? "Yes" : "No") + ")")
                    )
            ).rounded().fill();
        }

        String step = switch (mode) {
            case CREATE_NAME -> "Step 1/3 — Name";
            case CREATE_DATE -> "Step 2/3 — Date";
            default -> "New Giveaway";
        };

        return panel(" New Giveaway — " + step,
                column(
                        text("Name:"),
                        nameField.length(3),
                        text("Date (YYYY-MM-DD):"),
                        dateField.length(3),
                        spacer(1)
                )
        ).rounded().fill();
    }

    private Element renderTable() {
        List<Row> rows = giveaways.stream()
                .map(g -> Row.from(
                        Json.str(g, "name"),
                        Json.str(g, "date"),
                        Json.bool(g, "active") ? "✓" : ""
                ))
                .toList();

        return table()
                .title(" Giveaways ")
                .header("Name", "Date", "Active")
                .rows(rows)
                .state(tableState)
                .highlightColor(Color.RED)
                .highlightSymbol("")
                .widths(Constraint.fill(), Constraint.length(12), Constraint.length(8))
                .rounded()
                .fill();
    }

    private void saveGiveaway() {
        String name = nameState.text().trim();
        String date = dateState.text().trim();
        if (name.isEmpty()) {
            statusMsg = "Name is required.";
            mode = Mode.TABLE;
            return;
        }
        if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            statusMsg = "Date must be YYYY-MM-DD.";
            mode = Mode.TABLE;
            return;
        }
        var res = state.api().createGiveaway(name, date, createActive);
        statusMsg = (res.ok() || res.status() == 201) ? "Giveaway created." : "Failed to create giveaway.";
        if (res.ok() || res.status() == 201) {
            state.refreshActive();
        }
        loadGiveaways();
        mode = Mode.TABLE;
    }

    private int selectedRow() {
        Integer sel = tableState.selected();
        return sel == null ? -1 : sel;
    }

    private void toggleActive() {
        int idx = selectedRow();
        if (idx < 0 || idx >= giveaways.size()) {
            return;
        }
        var g = giveaways.get(idx);
        boolean current = Json.bool(g, "active");
        var res = state.api().updateGiveaway(Json.str(g, "id"), Map.of("active", !current));
        if (res.ok()) {
            statusMsg = "Updated.";
            state.refreshActive();
            loadGiveaways();
        } else {
            statusMsg = "Update failed.";
        }
    }

    private enum Mode {TABLE, CREATE_NAME, CREATE_DATE, CREATE_ACTIVE}
}
