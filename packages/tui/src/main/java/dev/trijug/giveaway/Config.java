package dev.trijug.giveaway;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public record Config(String apiUrl, String adminToken) {

    public static Config load() {
        Map<String, String> props = new HashMap<>();

        // Load file first, then let env override
        Path configFile = Path.of(System.getProperty("user.home"), ".config", "trijug", "config");
        if (Files.exists(configFile)) {
            try {
                for (String line : Files.readAllLines(configFile)) {
                    line = line.strip();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        props.put(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
                    }
                }
            } catch (IOException ignored) {
            }
        }

        // Env vars override file
        String url = System.getenv("TRIJUG_API_URL");
        String token = System.getenv("TRIJUG_ADMIN_TOKEN");
        if (url != null) {
            props.put("TRIJUG_API_URL", url);
        }
        if (token != null) {
            props.put("TRIJUG_ADMIN_TOKEN", token);
        }

        String resolvedUrl = props.get("TRIJUG_API_URL");
        String resolvedToken = props.get("TRIJUG_ADMIN_TOKEN");

        if (resolvedUrl == null || resolvedUrl.isBlank()) {
            System.err.println("Error: TRIJUG_API_URL is not set.");
            System.err.println("Set it in ~/.config/trijug/config or as an environment variable.");
            System.exit(1);
        }
        if (resolvedToken == null || resolvedToken.isBlank()) {
            System.err.println("Error: TRIJUG_ADMIN_TOKEN is not set.");
            System.err.println("Set it in ~/.config/trijug/config or as an environment variable.");
            System.exit(1);
        }

        return new Config(resolvedUrl, resolvedToken);
    }
}
