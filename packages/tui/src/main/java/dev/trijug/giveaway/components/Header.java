package dev.trijug.giveaway.components;

import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.toolkit.element.Element;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.richText;
import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.spacer;
import static dev.tamboui.toolkit.Toolkit.text;

public class Header {

    public static String activeBanner = "default";

    private static Text logo;
    private static Text divider;

    private static Text buildDivider(int height) {
        var span = Span.styled("│", Style.create().white());
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < height; i++) {
            lines.add(Line.from(span));
        }
        return Text.from(lines);
    }

    private static Text getDivider() {
        getLogo(); // ensures both are initialized together
        return divider;
    }

    private static Text getLogo() {
        if (logo == null) {
            logo = loadLogo();
            divider = buildDivider(logo.height());
        }
        return logo;
    }

    private static Text loadLogo() {
        String name = activeBanner == null || activeBanner.isBlank() ? "default" : activeBanner;
        String path = "/banners/" + name + ".ansi";

        InputStream is = Header.class.getResourceAsStream(path);
        if (is == null) {
            is = Header.class.getResourceAsStream("/banners/default.ansi");
        }
        if (is == null) {
            throw new IllegalStateException("banners/default.ansi not found on classpath");
        }

        try (InputStream stream = is) {
            String raw = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return AnsiParser.parse(raw);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Element render(String giveawayName, String giveawayDate) {
        String activeInfo = (giveawayName == null || giveawayName.isBlank())
                ? "No active giveaway"
                : giveawayName + " · " + giveawayDate;

        return panel(" TriJUG Giveaway ",
                row(
                        richText(getLogo()).length(getLogo().width()),
                        richText(getDivider()).length(1),
                        spacer(1),
                        column(
                                spacer(),
                                text("Active Giveaway").bold().red(),
                                text(activeInfo).dim(),
                                spacer()
                        ).fill()
                )
        ).rounded().fit();
    }
}
