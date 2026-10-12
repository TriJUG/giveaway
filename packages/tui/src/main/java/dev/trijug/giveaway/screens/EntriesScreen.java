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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.spacer;
import static dev.tamboui.toolkit.Toolkit.table;
import static dev.tamboui.toolkit.Toolkit.text;
import static dev.tamboui.toolkit.Toolkit.textInput;

public class EntriesScreen implements ScreenView {

    private final AppState state;
    private final TableState tableState = new TableState();
    private final TextInputState nameState = new TextInputState();
    private final TextInputState emailState = new TextInputState();
    private final TextInputElement nameField;
    private final TextInputElement emailField;
    private ToolkitRunner runner;
    private Consumer<AppState.Screen> navigate;
    private Mode mode = Mode.TABLE;
    private boolean maskEmails = true;
    private List<Map<String, Object>> entries = List.of();
    // entry_id → kept (true = winner, false = excluded); absent = never drawn
    private Map<String, Boolean> selectionByEntry = Map.of();
    private String statusMsg = "";
    private final GlobalEventHandler handler = this::handleKey;
    public EntriesScreen(AppState state) {
        this.state = state;
        nameField = textInput(nameState)
                .placeholder("Full name")
                .focusable()
                .id("add-name")
                .onSubmit(() -> {
                    mode = Mode.ADD_EMAIL;
                    runner.focusManager().setFocus("add-email");
                });
        emailField = textInput(emailState)
                .placeholder("email@example.com")
                .focusable()
                .id("add-email")
                .onSubmit(this::saveNewEntry);
    }

    private static String maskEmail(String email) {
        if (email == null) {
            return "";
        }
        int at = email.indexOf('@');
        if (at < 0) {
            return email;
        }
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        int dot = domain.lastIndexOf('.');
        String masked = (local.isEmpty() ? "" : local.charAt(0) + "***")
                + "@"
                + (dot < 0 ? "*".repeat(domain.length())
                : domain.charAt(0) + "*".repeat(dot - 1) + domain.substring(dot));
        return masked;
    }

    private static String shortDate(String iso) {
        if (iso == null || iso.length() < 10) {
            return iso == null ? "" : iso;
        }
        return iso.substring(0, 10);
    }

    @Override
    public void activate(ToolkitRunner runner, Consumer<AppState.Screen> navigate) {
        this.runner = runner;
        this.navigate = navigate;
        runner.eventRouter().addGlobalHandler(handler);
        runner.runOnRenderThread(this::loadEntries);
    }

    private void confirmDelete() {
        int idx = selectedRow();
        if (idx < 0 || idx >= entries.size()) {
            mode = Mode.TABLE;
            return;
        }
        var res = state.api().deleteEntry(Json.str(entries.get(idx), "id"));
        if (res.ok() || res.status() == 204) {
            statusMsg = "Entry deleted.";
        } else if (res.status() == 409) {
            statusMsg = "Can't delete: entry has already been drawn.";
        } else {
            statusMsg = "Delete failed.";
        }
        loadEntries();
        mode = Mode.TABLE;
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
                    tableState.selectNext(entries.size());
                    yield EventResult.HANDLED;
                }
                if (key.isCharIgnoreCase('d') && !entries.isEmpty()) {
                    mode = Mode.CONFIRM_DELETE;
                    yield EventResult.HANDLED;
                }
                if (key.isCharIgnoreCase('a')) {
                    nameState.clear();
                    emailState.clear();
                    mode = Mode.ADD_NAME;
                    runner.focusManager().setFocus("add-name");
                    yield EventResult.HANDLED;
                }
                if (key.isCharIgnoreCase('v')) {
                    maskEmails = !maskEmails;
                    yield EventResult.HANDLED;
                }
                if (key.isCancel() || key.isQuit()) {
                    runner.runOnRenderThread(() -> navigate.accept(AppState.Screen.HOME));
                    yield EventResult.HANDLED;
                }
                yield EventResult.UNHANDLED;
            }
            case CONFIRM_DELETE -> {
                if (key.isCharIgnoreCase('y')) {
                    confirmDelete();
                    yield EventResult.HANDLED;
                }
                if (key.isCharIgnoreCase('n') || key.isCancel()) {
                    mode = Mode.TABLE;
                    yield EventResult.HANDLED;
                }
                yield EventResult.UNHANDLED;
            }
            case ADD_NAME, ADD_EMAIL -> {
                if (key.isCancel()) {
                    mode = Mode.TABLE;
                    yield EventResult.HANDLED;
                }
                yield EventResult.UNHANDLED;
            }
        };
    }

    private void loadEntries() {
        if (!state.hasActive()) {
            return;
        }
        var res = state.api().listEntries(state.activeId());
        if (res.ok()) {
            entries = Json.parseArray(res.body());
        } else {
            statusMsg = "Failed to load entries.";
        }
        var selRes = state.api().listSelections(state.activeId());
        if (selRes.ok()) {
            Map<String, Boolean> map = new HashMap<>();
            for (var s : Json.parseArray(selRes.body())) {
                map.put(Json.str(s, "entry_id"), Json.bool(s, "kept"));
            }
            selectionByEntry = map;
        }
    }

    @Override
    public Element render() {
        String hint = switch (mode) {
            case TABLE -> "[D] delete    [A] add    [V] " + (maskEmails ? "reveal" : "mask") + " emails    [Esc] back";
            case CONFIRM_DELETE -> "[Y] Yes    [N] No";
            case ADD_NAME, ADD_EMAIL -> "[Enter] confirm    [Esc] cancel";
        };
        return column(
                Header.render(state.activeName(), state.activeDate()),
                renderContent(),
                text(hint).dim().length(1),
                text(statusMsg.isEmpty() ? " " : statusMsg).dim().length(1)
        );
    }

    private Element renderAdd() {
        return panel(mode == Mode.ADD_NAME ? " Add Entry — Name" : " Add Entry — Email",
                column(
                        text("Name:"),
                        nameField.length(3),
                        text("Email:"),
                        emailField.length(3)
                )
        ).rounded().fill();
    }

    private Element renderConfirm() {
        int idx = selectedRow();
        String name = idx >= 0 && idx < entries.size() ? Json.str(entries.get(idx), "name") : "?";
        return panel(" Confirm Delete",
                column(text("Delete entry for: " + name + "?"), spacer(1), text("[Y] Yes    [N] No").dim())
        ).rounded().fill();
    }

    private Element renderContent() {
        return switch (mode) {
            case TABLE -> renderTable();
            case CONFIRM_DELETE -> renderConfirm();
            case ADD_NAME, ADD_EMAIL -> renderAdd();
        };
    }

    private Element renderTable() {
        if (!state.hasActive()) {
            return panel("",
                    column(text("No active giveaway.").dim(), spacer(1), text("Esc  back").dim())
            ).rounded().fill();
        }

        List<Row> rows = entries.stream()
                .map(e -> {
                    String id = Json.str(e, "id");
                    Boolean sel = selectionByEntry.get(id);
                    String email = Json.str(e, "email");
                    return Row.from(
                            Json.str(e, "name"),
                            maskEmails ? maskEmail(email) : email,
                            shortDate(Json.str(e, "created_at")),
                            sel != null && sel ? "✓" : "",
                            sel != null && !sel ? "✓" : ""
                    );
                })
                .toList();

        return table()
                .title(" Entries ")
                .header("Name", "Email", "Entered", "Winner", "Excl")
                .rows(rows)
                .state(tableState)
                .highlightColor(Color.RED)
                .highlightSymbol("")
                .widths(Constraint.fill(), Constraint.fill(), Constraint.length(10), Constraint.length(8), Constraint.length(6))
                .rounded()
                .fill();
    }

    private void saveNewEntry() {
        String name = nameState.text().trim();
        String email = emailState.text().trim();
        if (name.isEmpty() || !email.contains("@")) {
            statusMsg = "Name and valid email are required.";
            mode = Mode.TABLE;
            return;
        }
        var res = state.api().addEntry(state.activeId(), name, email);
        statusMsg = (res.ok() || res.status() == 201) ? "Entry added." : "Failed to add entry.";
        loadEntries();
        mode = Mode.TABLE;
    }

    private int selectedRow() {
        Integer sel = tableState.selected();
        return sel == null ? -1 : sel;
    }

    private enum Mode {TABLE, CONFIRM_DELETE, ADD_NAME, ADD_EMAIL}
}
