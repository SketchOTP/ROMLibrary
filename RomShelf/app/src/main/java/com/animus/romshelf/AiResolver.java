package com.animus.romshelf;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Uses only user-authorized ChatGPT plan OAuth. No CLI token import or embedded API keys. */
final class AiResolver {
    private final SharedPreferences prefs;
    private final ChatGPTAuth auth;
    private String preferredModel = "";

    AiResolver(Context context, ChatGPTAuth auth) {
        prefs = context.getSharedPreferences("rom_shelf_ai", Context.MODE_PRIVATE);
        this.auth = auth;
    }
    boolean configured() { return auth.connected(); }
    boolean processed(Game g) { return prefs.getBoolean("ai_done_" + g.key, false); }
    void reset(Game g) { prefs.edit().remove("ai_done_" + g.key).apply(); }
    void markProcessed(Game g) { prefs.edit().putBoolean("ai_done_" + g.key, true).apply(); }
    void migrateIdentity(Game game) {
        String current = "ai_done_" + game.key;
        if (prefs.contains(current)) return;
        for (String oldKey : game.legacyKeys) {
            String old = "ai_done_" + oldKey;
            if (prefs.getBoolean(old, false)) {
                prefs.edit().putBoolean(current, true).apply();
                return;
            }
        }
    }
    static final class Answer {
        final String title, version, imageUrl, sourceUrl;
        Answer(String title, String version, String imageUrl, String sourceUrl) {
            this.title = title; this.version = version; this.imageUrl = imageUrl; this.sourceUrl = sourceUrl;
        }
    }
    private String resolveModel(String token) throws Exception {
        if (!preferredModel.isEmpty()) return preferredModel;
        HttpURLConnection c = (HttpURLConnection) new URL("https://api.openai.com/v1/models").openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(20000);
        c.setRequestProperty("Authorization", "Bearer " + token);
        try {
            if (c.getResponseCode() >= 400) throw new IllegalStateException("Cannot load models (HTTP " + c.getResponseCode() + ")");
            StringBuilder out = new StringBuilder();
            try (InputStream in = c.getInputStream()) { byte[] b = new byte[8192]; int n; while ((n = in.read(b)) != -1) { out.append(new String(b,0,n,StandardCharsets.UTF_8)); if (out.length()>1024*1024) throw new IllegalStateException("Model list too large"); } }
            JSONObject response = new JSONObject(out.toString());
            JSONArray models = response.optJSONArray("models");
            if (models == null) models = response.optJSONArray("data");
            if (models == null || models.length() == 0) throw new IllegalStateException("No models available to ChatGPT plan");
            String first = "";
            for (int i = 0; i < models.length(); i++) {
                JSONObject m = models.optJSONObject(i); if (m == null) continue;
                if (!"list".equals(m.optString("visibility"))) continue;
                String slug = m.optString("slug", m.optString("id", ""));
                if (slug.isEmpty()) continue;
                if (first.isEmpty()) first = slug;
            }
            // The plan-specific catalog supplies the models available to this user.
            // Preserve its order and ignore models that are not marked for display.
            preferredModel = first;
            if (preferredModel.isEmpty()) throw new IllegalStateException("No usable ChatGPT model listed");
            return preferredModel;
        } finally { c.disconnect(); }
    }
    Answer identify(Game g) throws Exception {
        String token = auth.accessToken();
        String model = resolveModel(token);
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("store", false);
        body.put("stream", true);
        body.put("instructions", "Identify GBA games accurately, including ROM hacks. Never mislabel a hack as its base game. Return only JSON {title,version,image_url,source_url}. Prefer genuine online box art and trustworthy HTTPS image URLs; omit unverified URLs. Never invent versions or websites.");
        body.put("input", new JSONArray().put(new JSONObject().put("role", "user").put("content",
            "GBA ROM filename: " + g.fileName + ". Current display title: " + g.title +
            ". ROM version inferred from filename: " + g.version +
            ". Identify the real game's best display name, keep hack name when present, and if available find a specific verified actual GBA cover-art image URL from online research. If no verified image, return empty image_url/source_url.")));
        body.put("tools", new JSONArray().put(new JSONObject().put("type", "web_search")));
        HttpURLConnection conn = (HttpURLConnection) new URL("https://api.openai.com/v1/responses").openConnection();
        conn.setRequestMethod("POST"); conn.setConnectTimeout(12000); conn.setReadTimeout(90000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "text/event-stream");
        try {
            try (OutputStream out = conn.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            int code = conn.getResponseCode();
            if (code >= 400) throw new IllegalStateException("AI HTTP " + code + " (verify ChatGPT plan usage and supported web search)");
            StringBuilder deltas = new StringBuilder();
            JSONObject completedResponse = null;
            boolean completed = false;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    if (!line.startsWith("data: ")) continue;
                    String data = line.substring(6);
                    if ("[DONE]".equals(data)) break;
                    JSONObject event;
                    try { event = new JSONObject(data); } catch (Exception ignored) { continue; }
                    String type = event.optString("type", "");
                    if ("response.output_text.delta".equals(type)) {
                        deltas.append(event.optString("delta", ""));
                        if (deltas.length() > 100_000) throw new IllegalStateException("AI output too large");
                    } else if ("response.completed".equals(type)) {
                        completedResponse = event.optJSONObject("response");
                        completed = true;
                    } else if ("response.failed".equals(type) || "error".equals(type)) {
                        JSONObject error = event.optJSONObject("error");
                        if (error == null && event.optJSONObject("response") != null) error = event.getJSONObject("response").optJSONObject("error");
                        throw new IllegalStateException("AI request failed: " + (error == null ? "unknown error" : error.optString("code", error.optString("message", "error"))));
                    } else if ("response.incomplete".equals(type)) {
                        throw new IllegalStateException("AI request incomplete");
                    }
                }
            }
            if (!completed) throw new IllegalStateException("AI request ended without completion");
            String prose = deltas.toString();
            if (prose.isEmpty() && completedResponse != null) {
                JSONArray output = completedResponse.optJSONArray("output");
                if (output != null) for (int i=0;i<output.length();i++) {
                    JSONObject o=output.optJSONObject(i); if(o==null) continue;
                    JSONArray content=o.optJSONArray("content");
                    if(content!=null)for(int j=0;j<content.length();j++) {
                        JSONObject part=content.optJSONObject(j);
                        if(part!=null && "output_text".equals(part.optString("type"))) prose += part.optString("text", "");
                    }
                }
            }
            JSONObject parsed;
            try {
                int start=prose.indexOf('{'), end=prose.lastIndexOf('}');
                if (start < 0 || end < start) throw new IllegalArgumentException();
                parsed = new JSONObject(prose.substring(start, end + 1));
            } catch (Exception e) { throw new IllegalStateException("AI did not return usable game metadata"); }
            String title = parsed.optString("title", "").trim();
            String version = parsed.optString("version", "").trim();
            String image = parsed.optString("image_url", "").trim();
            String source = parsed.optString("source_url", "").trim();
            if (title.length()<2 || title.length()>90 || title.contains("http")) title=g.title;
            if (version.isEmpty() || version.length()>48 || version.contains("http")) version=g.version;
            if (!ArtworkUrlPolicy.isAllowedImageUrl(image)) { image=""; source=""; }
            else if (!ArtworkUrlPolicy.isAllowedSourceUrl(source)) source="";
            return new Answer(title,version,image,source);
        } finally { conn.disconnect(); }
    }
}
