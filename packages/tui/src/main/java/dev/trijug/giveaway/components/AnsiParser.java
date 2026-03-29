package dev.trijug.giveaway.components;

import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;

import java.util.ArrayList;
import java.util.List;

public class AnsiParser {

    private static Color applyParams(String params, Color current) {
        if (params.isEmpty() || params.equals("0")) {
            return null;
        }
        String[] parts = params.split(";");
        for (int i = 0; i < parts.length; i++) {
            switch (parts[i]) {
                case "0", "39" -> {
                    return null;
                }
                case "49" -> { /* reset bg — no-op, we don't track bg */ }
                case "38" -> {
                    if (i + 2 < parts.length && parts[i + 1].equals("5")) {
                        return Color.indexed(Integer.parseInt(parts[i + 2]));
                    }
                }
                // 48;5;N = bg color — ignored, banner uses fg only
            }
        }
        return current;
    }

    public static Text parse(String input) {
        String[] rawLines = input.replace("\r", "").split("\n");
        int last = rawLines.length - 1;
        while (last > 0 && rawLines[last].isBlank()) {
            last--;
        }

        List<Line> lines = new ArrayList<>();
        lines.add(Line.empty());
        for (int i = 0; i <= last; i++) {
            lines.add(parseLine(" " + rawLines[i] + " "));
        }
        lines.add(Line.empty());
        return Text.from(lines);
    }

    private static Line parseLine(String line) {
        List<Span> spans = new ArrayList<>();
        Color fg = null;
        int pos = 0;

        while (pos < line.length()) {
            int esc = line.indexOf('\u001B', pos);
            if (esc == -1) {
                String tail = line.substring(pos);
                if (!tail.isEmpty()) {
                    spans.add(toSpan(tail, fg));
                }
                break;
            }
            if (esc > pos) {
                spans.add(toSpan(line.substring(pos, esc), fg));
            }
            // Expect CSI: ESC followed by '['
            if (esc + 1 >= line.length() || line.charAt(esc + 1) != '[') {
                pos = esc + 1;
                continue;
            }
            int m = line.indexOf('m', esc + 2);
            if (m == -1) {
                break;
            }
            fg = applyParams(line.substring(esc + 2, m), fg);
            pos = m + 1;
        }

        return spans.isEmpty() ? Line.empty() : Line.from(spans);
    }

    private static Span toSpan(String text, Color fg) {
        return fg == null ? Span.raw(text) : Span.styled(text, Style.create().fg(fg));
    }
}
