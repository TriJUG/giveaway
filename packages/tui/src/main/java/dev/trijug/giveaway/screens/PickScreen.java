package dev.trijug.giveaway.screens;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.elements.Spacer;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.event.GlobalEventHandler;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyEvent;
import dev.trijug.giveaway.AppState;
import dev.trijug.giveaway.ScreenView;
import dev.trijug.giveaway.api.Json;
import dev.trijug.giveaway.components.Header;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.list;
import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.richText;
import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.spacer;
import static dev.tamboui.toolkit.Toolkit.text;

public class PickScreen implements ScreenView {

    private static final String[] SPARKLE_CHARS = {"✦", "★", "✧", "✺"};
    private final AppState state;
    private final List<String> history = new ArrayList<>();
    private final Random rng = new Random();
    private ToolkitRunner runner;
    private Consumer<AppState.Screen> navigate;

    private Phase phase = Phase.LOADING;
    private List<Map<String, Object>> pool = new ArrayList<>();
    private Map<String, Object> winner = null;
    private int winnerIdx = 0;
    private int spinIndex = 0;
    private long spinStartMs = 0;
    private long spinMinEndMs = 0;
    private int celebrationTick = 0;
    private int shuffleCount = 0;
    private ToolkitRunner.ScheduledAction spinSchedule = null;
    private ToolkitRunner.ScheduledAction celebrationSchedule = null;
    private final GlobalEventHandler handler = this::handleKey;
    public PickScreen(AppState state) {
        this.state = state;
    }

    @Override
    public void activate(ToolkitRunner runner, Consumer<AppState.Screen> navigate) {
        this.runner = runner;
        this.navigate = navigate;
        runner.eventRouter().addGlobalHandler(handler);
        runner.runOnRenderThread(this::loadEntries);
    }

    // Chasing-lights sparkle row sized to fill innerWidth exactly.
    // 1-space margin on each side is baked into the spans so richText fills the panel border-to-border.
    private Element buildSparkleRow(int tick, int innerWidth) {
        int units = (innerWidth - 2) / 2; // -2 for the 1-space margin on each side
        var spans = new ArrayList<Span>();
        spans.add(Span.raw(" "));
        for (int i = 0; i < units; i++) {
            boolean lit = (i % 2) == (tick % 2);
            if (lit) {
                String ch = SPARKLE_CHARS[tick % SPARKLE_CHARS.length];
                spans.add(Span.styled(ch + " ", Style.create().bold().fg(Color.indexed(220))));
            } else {
                spans.add(Span.styled("· ", Style.create().dim()));
            }
        }
        spans.add(Span.raw(" "));
        return richText(Text.from(List.of(Line.from(spans))));
    }

    private void cancelSpin() {
        if (spinSchedule != null && !spinSchedule.isCancelled()) {
            spinSchedule.cancel();
        }
        spinSchedule = null;
    }

    @Override
    public void deactivate() {
        cancelSpin();
        stopCelebration();
        runner.eventRouter().removeGlobalHandler(handler);
    }

    // ── Spin view (READY / SPINNING / AFTER_KEEP) ────────────────────────────

    private void goBack() {
        navigate.accept(AppState.Screen.HOME);
    }

    // ── Winner celebration view ───────────────────────────────────────────────

    private EventResult handleKey(Event event) {
        if (!(event instanceof KeyEvent key)) {
            return EventResult.UNHANDLED;
        }

        if (key.isCancel() || key.isQuit() || key.isCharIgnoreCase('q')) {
            runner.runOnRenderThread(this::goBack);
            return EventResult.HANDLED;
        }

        return switch (phase) {
            case READY -> {
                if (key.isSelect() || key.isConfirm() || key.isChar(' ')) {
                    runner.runOnRenderThread(this::startSpin);
                    yield EventResult.HANDLED;
                }
                if (key.isCharIgnoreCase('s')) {
                    runner.runOnRenderThread(() -> {
                        Collections.shuffle(pool, rng);
                        shuffleCount++;
                    });
                    yield EventResult.HANDLED;
                }
                yield EventResult.UNHANDLED;
            }
            case WINNER -> {
                if (key.isCharIgnoreCase('k')) {
                    runner.runOnRenderThread(this::keepWinner);
                    yield EventResult.HANDLED;
                }
                if (key.isCharIgnoreCase('r')) {
                    runner.runOnRenderThread(this::reroll);
                    yield EventResult.HANDLED;
                }
                yield EventResult.UNHANDLED;
            }
            case AFTER_KEEP -> {
                if (key.isCharIgnoreCase('a')) {
                    runner.runOnRenderThread(() -> {
                        winner = null;
                        loadEntries();
                    });
                    yield EventResult.HANDLED;
                }
                yield EventResult.UNHANDLED;
            }
            default -> EventResult.UNHANDLED;
        };
    }

    private void keepWinner() {
        stopCelebration();
        state.api().recordSelection(state.activeId(), Json.str(winner, "id"), true);
        history.add("✓ " + Json.str(winner, "name"));
        pool.remove(winner);
        phase = Phase.AFTER_KEEP;
    }

    // ── History sidebar ───────────────────────────────────────────────────────

    private void loadEntries() {
        if (!state.hasActive()) {
            phase = Phase.NO_ENTRIES;
            return;
        }

        var entriesRes = state.api().listEntries(state.activeId());
        if (!entriesRes.ok()) {
            phase = Phase.NO_ENTRIES;
            return;
        }

        List<Map<String, Object>> all = Json.parseArray(entriesRes.body());

        var selRes = state.api().listSelections(state.activeId());
        if (selRes.ok()) {
            Set<String> usedIds = new HashSet<>();
            for (var sel : Json.parseArray(selRes.body())) {
                usedIds.add(Json.str(sel, "entry_id"));
            }
            all = all.stream().filter(e -> !usedIds.contains(Json.str(e, "id"))).toList();
        }

        pool = new ArrayList<>(all);
        if (pool.isEmpty()) {
            phase = Phase.EXHAUSTED;
            return;
        }

        Collections.shuffle(pool, rng);
        phase = Phase.READY;
    }

    // ── Key handling ──────────────────────────────────────────────────────────

    @Override
    public Element render() {
        return switch (phase) {
            case LOADING -> column(
                    Header.render(state.activeName(), state.activeDate()),
                    text("Loading entries…").dim()
            );

            case NO_ENTRIES -> column(
                    Header.render(state.activeName(), state.activeDate()),
                    text("No eligible entries for this giveaway.").yellow(),
                    spacer(1),
                    text("Esc  back").dim()
            );

            case READY, SPINNING, AFTER_KEEP -> renderSpinView();

            case WINNER -> renderWinnerView();

            case EXHAUSTED -> column(
                    Header.render(state.activeName(), state.activeDate()),
                    text("No eligible entries remaining.").dim(),
                    spacer(1),
                    text("Esc  back").dim()
            );
        };
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private Element renderHistory() {
        if (history.isEmpty()) {
            spacer();
            return Spacer.length(1);
        }
        var items = history.stream().map(s -> (Element) text(s).dim()).toList();
        return panel(" Drawn this session",
                column(items.toArray(new Element[0]))
        ).rounded().length(Math.min(history.size() + 2, 8));
    }

    // ── Spin logic ────────────────────────────────────────────────────────────

    private Element renderSpinView() {
        String winnerId = winner != null ? Json.str(winner, "id") : null;

        Element nameContent;
        if (phase == Phase.READY) {
            var rows = new ArrayList<Element>();
            rows.add(text(""));
            for (var entry : pool) {
                rows.add(text("    " + Json.str(entry, "name")));
            }
            nameContent = column(rows.toArray(new Element[0]));
        } else {
            ListElement<?> nameList = list();
            nameList.add(text(""));
            for (int i = 0; i < pool.size(); i++) {
                String name = "  " + Json.str(pool.get(i), "name");
                if (phase == Phase.SPINNING) {
                    nameList.add(i == spinIndex ? text(name).bold().red() : text(name));
                } else {
                    boolean isTarget = Json.str(pool.get(i), "id").equals(winnerId);
                    nameList.add(isTarget ? text(name).bold().red() : text(name).dim());
                }
            }
            int highlightIdx = phase == Phase.SPINNING
                    ? spinIndex + 1
                    : (winner != null ? pool.indexOf(winner) + 1 : 1);
            nameList.selected(highlightIdx).autoScroll();
            nameContent = nameList;
        }

        String title = switch (phase) {
            case READY -> " Ready — " + pool.size() + " eligible";
            case SPINNING -> " Drawing…";
            case AFTER_KEEP -> " Winner kept!";
            default -> "";
        };

        String hint = switch (phase) {
            case READY -> "Space  draw    S  shuffle    Esc  back";
            case SPINNING -> "Esc  cancel";
            case AFTER_KEEP -> "[A] Pick another    [Esc] done";
            default -> "";
        };

        boolean showEd = phase == Phase.READY && shuffleCount > 0 && shuffleCount % 10 == 0;
        Element hintLine = showEd
                ? row(text(hint).dim(), spacer(), text("Hi Ed, happy now?").dim()).length(1)
                : text(hint).dim().length(1);

        var elems = new ArrayList<Element>();
        elems.add(Header.render(state.activeName(), state.activeDate()));
        elems.add(panel(title, nameContent).rounded().fill());
        elems.add(hintLine);
        elems.add(renderHistory());
        return column(elems.toArray(new Element[0]));
    }

    private Element renderWinnerView() {
        String winnerName = Json.str(winner, "name");
        int panelWidth = Math.max(winnerName.length() + 16, 32); // +6 for ★  …  ★, +10 for borders/margins

        // Left: all names dimmed (no selection)
        var rows = new ArrayList<Element>();
        rows.add(text(""));
        for (var entry : pool) {
            rows.add(text("    " + Json.str(entry, "name")).dim());
        }
        Element dimmedList = column(rows.toArray(new Element[0]));

        // Center celebration panel with animated sparkles
        // panelWidth - 2 for borders; sparkle fills the full inner width with 1-space margin baked in
        int sparkleInnerWidth = panelWidth - 2;

        Element celebPanel = panel(" ★ Winner! ★",
                column(
                        buildSparkleRow(celebrationTick, sparkleInnerWidth),
                        spacer(1),
                        row(spacer(), text("★  " + winnerName + "  ★").bold().fg(Color.indexed(226)), spacer()),
                        spacer(1),
                        buildSparkleRow(celebrationTick + 2, sparkleInnerWidth)
                )
        ).rounded().length(panelWidth);

        Element rightArea = column(
                spacer(),
                row(spacer(), celebPanel, spacer()),
                spacer()
        ).fill();

        return column(
                Header.render(state.activeName(), state.activeDate()),
                row(
                        panel(" Entries ", dimmedList).rounded().length(26),
                        rightArea
                ).fill(),
                text("[K] Keep    [R] Re-roll    [Esc] done").dim().length(1),
                renderHistory()
        );
    }

    // ── Celebration logic ─────────────────────────────────────────────────────

    private void reroll() {
        stopCelebration();
        state.api().recordSelection(state.activeId(), Json.str(winner, "id"), false);
        history.add("✗ " + Json.str(winner, "name"));
        pool.remove(winner);
        winner = null;
        startSpin();
    }

    private void startCelebration() {
        celebrationTick = 0;
        celebrationSchedule = runner.scheduleRepeating(() -> runner.runOnRenderThread(() -> {
            if (phase == Phase.WINNER) {
                celebrationTick++;
            }
        }), Duration.ofMillis(150));
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void startSpin() {
        if (pool.isEmpty()) {
            phase = Phase.EXHAUSTED;
            return;
        }
        cancelSpin();

        winnerIdx = rng.nextInt(pool.size());
        winner = pool.get(winnerIdx);

        spinStartMs = System.currentTimeMillis();
        spinMinEndMs = spinStartMs + 4000 + rng.nextInt(1000);
        spinIndex = 0;
        phase = Phase.SPINNING;

        spinSchedule = runner.scheduleRepeating(() -> runner.runOnRenderThread(() -> {
            if (phase != Phase.SPINNING) {
                return;
            }
            long now = System.currentTimeMillis();
            spinIndex = (int) ((now - spinStartMs) / 80) % pool.size();
            if (now >= spinMinEndMs && spinIndex == winnerIdx) {
                cancelSpin();
                phase = Phase.WINNER;
                startCelebration();
            }
        }), Duration.ofMillis(80));
    }

    private void stopCelebration() {
        if (celebrationSchedule != null && !celebrationSchedule.isCancelled()) {
            celebrationSchedule.cancel();
        }
        celebrationSchedule = null;
    }

    private enum Phase {LOADING, NO_ENTRIES, READY, SPINNING, WINNER, AFTER_KEEP, EXHAUSTED}
}
