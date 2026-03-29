package dev.trijug.giveaway.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class ApiClient {

    private final String baseUrl;
    private final String adminToken;
    private final HttpClient http;

    public ApiClient(String baseUrl, String adminToken) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.adminToken = adminToken;
        this.http = HttpClient.newHttpClient();
    }

    // ── Giveaways ──────────────────────────────────────────────────────────────

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String toJson(Map<String, Object> map) {
        var sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append(quote(entry.getKey())).append(":");
            Object v = entry.getValue();
            if (v instanceof String s) {
                sb.append(quote(s));
            } else {
                sb.append(v);
            }
        }
        sb.append("}");
        return sb.toString();
    }

    public ApiResponse addEntry(String giveawayId, String name, String email) {
        String body = "{\"name\":" + quote(name) + ",\"email\":" + quote(email) + "}";
        return post("/api/giveaways/" + giveawayId + "/entries", body, true);
    }

    private HttpRequest.Builder builder(String path, boolean auth) {
        var b = HttpRequest.newBuilder(URI.create(baseUrl + path));
        if (auth) {
            b.header("Authorization", "Bearer " + adminToken);
        }
        return b;
    }

    // ── Entries ────────────────────────────────────────────────────────────────

    public ApiResponse createGiveaway(String name, String date, boolean active) {
        String body = "{\"name\":" + quote(name) + ",\"date\":" + quote(date) + ",\"active\":" + active + "}";
        return post("/api/giveaways", body, true);
    }

    private ApiResponse delete(String path) {
        var req = builder(path, true).DELETE().build();
        return send(req);
    }

    public ApiResponse deleteEntry(String entryId) {
        return delete("/api/entries/" + entryId);
    }

    // ── Selections ─────────────────────────────────────────────────────────────

    private ApiResponse get(String path, boolean auth) {
        var req = builder(path, auth).GET().build();
        return send(req);
    }

    public ApiResponse getActiveGiveaway() {
        return get("/api/giveaways/active", false);
    }

    // ── HTTP helpers ───────────────────────────────────────────────────────────

    public ApiResponse listEntries(String giveawayId) {
        return get("/api/giveaways/" + giveawayId + "/entries", true);
    }

    public ApiResponse listGiveaways() {
        return get("/api/giveaways", true);
    }

    public ApiResponse listSelections(String giveawayId) {
        return get("/api/giveaways/" + giveawayId + "/selections", true);
    }

    private ApiResponse patch(String path, String json, boolean auth) {
        var req = builder(path, auth)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                .build();
        return send(req);
    }

    private ApiResponse post(String path, String json, boolean auth) {
        var req = builder(path, auth)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return send(req);
    }

    public ApiResponse recordSelection(String giveawayId, String entryId, boolean kept) {
        String body = "{\"entry_id\":" + quote(entryId) + ",\"kept\":" + kept + "}";
        return post("/api/giveaways/" + giveawayId + "/selections", body, true);
    }

    private ApiResponse send(HttpRequest req) {
        try {
            var res = http.send(req, HttpResponse.BodyHandlers.ofString());
            return new ApiResponse(res.statusCode(), res.body());
        } catch (IOException | InterruptedException e) {
            return new ApiResponse(-1, e.getMessage());
        }
    }

    public ApiResponse updateGiveaway(String id, Map<String, Object> fields) {
        return patch("/api/giveaways/" + id, toJson(fields), true);
    }
}
