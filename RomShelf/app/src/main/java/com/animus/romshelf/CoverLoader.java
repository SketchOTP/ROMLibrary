package com.animus.romshelf;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import androidx.documentfile.provider.DocumentFile;

final class CoverLoader {
    private static final String BASE = "https://thumbnails.libretro.com/Nintendo%20-%20Game%20Boy%20Advance/Named_Boxarts/";
    private static final Pattern ENTRY = Pattern.compile("(?i)href=\\\"([^\\\"]+\\.png)\\\"");
    private final Context context;
    private final SharedPreferences prefs;
    private final ExecutorService workers = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final File cached;
    private final File custom;
    private final File legacyCustom;
    private final Object stateIndexLock = new Object();
    private Map<String, DocumentFile> stateIndex = Collections.emptyMap();
    private String stateIndexUri = "";
    private long stateIndexAt;
    private List<String> catalog;
    private long attemptedCatalog;

    CoverLoader(Context c) {
        context = c.getApplicationContext();
        prefs = c.getSharedPreferences("rom_shelf_covers", Context.MODE_PRIVATE);
        cached = new File(c.getFilesDir(), "covers/autofetch");
        File externalCovers = c.getExternalFilesDir("covers");
        custom = new File(externalCovers == null ? new File(c.getFilesDir(), "covers") : externalCovers, "custom");
        legacyCustom = new File(c.getFilesDir(), "covers/custom");
        cached.mkdirs(); custom.mkdirs();
    }

    void show(Game game, ImageView image) {
        final String tag = game.key;
        image.setTag(tag);
        File screenshot = new File(cached, tag + ".state.img");
        File manual = customFile(tag);
        File auto = new File(cached, tag + ".png");
        boolean useScreenshots = screenshotsEnabled();
        File file = useScreenshots && screenshot.isFile() ? screenshot : (manual.isFile() ? manual : auto);
        if (file.isFile()) {
            Bitmap bitmap = readScaled(file);
            if (bitmap != null) {
                image.setImageBitmap(bitmap);
                if (!useScreenshots) return;
            }
        }
        File failed = new File(cached, tag + ".missing");
        if (!useScreenshots && failed.exists() && System.currentTimeMillis() - failed.lastModified() < 12L * 3600 * 1000) return;
        workers.execute(() -> {
            if (useScreenshots) {
                File latest = loadLatestScreenshot(game);
                Bitmap stateBitmap = latest == null ? null : readScaled(latest);
                if (stateBitmap != null) {
                    main.post(() -> {
                        if (tag.equals(image.getTag()) && image.isAttachedToWindow()) image.setImageBitmap(stateBitmap);
                    });
                    return;
                }
                // Keep existing manual or downloaded cover art when no matching
                // state image exists; only fetch art when there is no fallback.
                if (manual.isFile() || auto.isFile() || screenshot.isFile()) return;
            }
            File target = new File(cached, tag + ".png");
            boolean ok = target.isFile();
            if (!ok) {
                Set<String> names = new LinkedHashSet<>(Arrays.asList(game.stem, game.title, game.title + " (USA)", game.title + " (USA, Europe)", game.title + " (Europe)", game.title + " (Japan)"));
                for (String s : names) {
                    if (download(BASE + urlName(s + ".png"), target)) { ok = true; break; }
                }
                if (!ok) {
                    String match = bestCatalogFile(game.title, game.stem);
                    if (match != null) ok = download(BASE + urlName(match), target);
                }
            }
            if (!ok) {
                try { failed.createNewFile(); } catch (Exception ignored) { }
                return;
            }
            failed.delete();
            Bitmap bitmap = readScaled(target);
            if (bitmap != null) main.post(() -> {
                if (tag.equals(image.getTag()) && image.isAttachedToWindow()) image.setImageBitmap(bitmap);
            });
        });
    }

    void clearLegacyAiState() {
        SharedPreferences.Editor edit = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith("url_") || key.startsWith("source_")) edit.remove(key);
        }
        edit.apply();
    }

    void migrateIdentity(Game game) {
        for (String oldKey : game.legacyKeys) {
            if (oldKey.equals(game.key)) continue;
            copyFile(new File(cached, oldKey + ".png"), new File(cached, game.key + ".png"));
            copyFile(new File(cached, oldKey + ".missing"), new File(cached, game.key + ".missing"));
            copyFile(customFile(oldKey), new File(custom, game.key + ".img"));
            copyFile(new File(legacyCustom, oldKey + ".img"), new File(custom, game.key + ".img"));
        }
    }

    private File customFile(String key) {
        File current = new File(custom, key + ".img");
        return current.isFile() ? current : new File(legacyCustom, key + ".img");
    }

    boolean screenshotsEnabled() {
        return prefs.getBoolean("use_state_screenshots", false);
    }

    void setScreenshotsEnabled(boolean enabled) {
        prefs.edit().putBoolean("use_state_screenshots", enabled).apply();
        clearScreenshotCache();
    }

    Uri screenshotFolder() {
        String value = prefs.getString("state_screenshot_folder", null);
        return value == null ? null : Uri.parse(value);
    }

    void setScreenshotFolder(Uri uri) {
        prefs.edit().putString("state_screenshot_folder", uri.toString()).apply();
        invalidateScreenshotIndex();
        clearScreenshotCache();
    }

    void invalidateScreenshotIndex() {
        synchronized (stateIndexLock) {
            stateIndex = Collections.emptyMap();
            stateIndexUri = "";
            stateIndexAt = 0;
        }
    }

    private void clearScreenshotCache() {
        File[] files = cached.listFiles((dir, name) -> name.endsWith(".state.img"));
        if (files != null) for (File file : files) file.delete();
    }

    private File loadLatestScreenshot(Game game) {
        Uri rootUri = screenshotFolder();
        if (rootUri == null) return null;
        DocumentFile root = DocumentFile.fromTreeUri(context, rootUri);
        if (root == null || !root.isDirectory()) return null;
        Map<String, DocumentFile> index = screenshotIndex(rootUri, root);
        DocumentFile best = null;
        for (String stem : game.saveStems) {
            DocumentFile candidate = index.get(normalizeStateStem(stem));
            if (candidate != null && (best == null || screenshotModified(candidate) > screenshotModified(best))) best = candidate;
        }
        if (best == null) return null;

        File target = copyScreenshot(game, rootUri, best);
        return target != null && target.isFile() ? target : null;
    }

    boolean captureLatestPizzaScreenshot(Game game, long launchedAt) {
        if (!screenshotsEnabled() || launchedAt <= 0) return false;
        Uri rootUri = screenshotFolder();
        if (rootUri == null) return false;
        DocumentFile root = DocumentFile.fromTreeUri(context, rootUri);
        if (root == null || !root.isDirectory()) return false;
        Map<String, DocumentFile> index = screenshotIndex(rootUri, root);
        DocumentFile best = null;
        for (DocumentFile item : index.values()) {
            String name = item.getName();
            if (!isPizzaBoyScreenshot(name) || screenshotModified(item) < launchedAt) continue;
            if (best == null || screenshotModified(item) > screenshotModified(best)) best = item;
        }
        if (best == null) return false;
        File target = copyScreenshot(game, rootUri, best);
        return target != null && target.isFile();
    }

    private File copyScreenshot(Game game, Uri rootUri, DocumentFile best) {
        long modified = screenshotModified(best);

        File target = new File(cached, game.key + ".state.img");
        String signature = rootUri + "|" + best.getUri() + "|" + modified + "|" + best.length();
        String signatureKey = "state_signature_" + game.key;
        if (target.isFile() && signature.equals(prefs.getString(signatureKey, ""))) return target;
        if (best.length() > 8L * 1024 * 1024) return null;
        try (InputStream in = context.getContentResolver().openInputStream(best.getUri())) {
            if (in == null) return null;
            byte[] bytes = readLimited(in, 8 * 1024 * 1024);
            if (BitmapFactory.decodeByteArray(bytes, 0, bytes.length) == null) return null;
            AtomicCoverFile.replaceFrom(new java.io.ByteArrayInputStream(bytes), target, 8 * 1024 * 1024,
                candidate -> readScaled(candidate) != null);
            prefs.edit().putString(signatureKey, signature).apply();
            return target.isFile() ? target : null;
        } catch (Exception ignored) { return null; }
    }

    private static boolean isPizzaBoyScreenshot(String name) {
        return name != null && Pattern.compile("(?i)^screenshot_\\d{14}\\.(?:jpe?g|png|webp)$").matcher(name).matches();
    }

    private static long screenshotModified(DocumentFile file) {
        long modified = file.lastModified();
        String name = file.getName();
        if (!isPizzaBoyScreenshot(name)) return modified;
        try {
            Date captured = new SimpleDateFormat("'screenshot_'yyyyMMddHHmmss", Locale.US).parse(name.substring(0, name.lastIndexOf('.')));
            if (captured != null) modified = Math.max(modified, captured.getTime());
        } catch (Exception ignored) { }
        return modified;
    }

    private Map<String, DocumentFile> screenshotIndex(Uri rootUri, DocumentFile root) {
        String uri = rootUri.toString();
        synchronized (stateIndexLock) {
            if (uri.equals(stateIndexUri) && System.currentTimeMillis() - stateIndexAt < 10000) return stateIndex;
            Map<String, DocumentFile> found = new HashMap<>();
            walkScreenshots(root, found, 0, new int[] {0});
            stateIndex = found;
            stateIndexUri = uri;
            stateIndexAt = System.currentTimeMillis();
            return stateIndex;
        }
    }

    private void walkScreenshots(DocumentFile directory, Map<String, DocumentFile> found, int depth, int[] count) {
        if (depth > 6 || count[0] > 5000) return;
        for (DocumentFile item : directory.listFiles()) {
            if (++count[0] > 5000) return;
            if (item.isDirectory()) {
                walkScreenshots(item, found, depth + 1, count);
                continue;
            }
            String name = item.getName();
            String stem = screenshotStem(name);
            if (stem == null) continue;
            DocumentFile previous = found.get(stem);
            if (previous == null || item.lastModified() > previous.lastModified()) found.put(stem, item);
        }
    }

    private static String screenshotStem(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase(Locale.ROOT);
        Matcher matcher = Pattern.compile("\\.(?:js\\d+|jsa|jpe?g|png|webp)$", Pattern.CASE_INSENSITIVE).matcher(lower);
        if (!matcher.find()) return null;
        String stem = lower.substring(0, matcher.start())
            .replaceFirst("(?i)\\.(?:gba|zip)$", "")
            .replaceFirst("(?i)\\.(?:js\\d+|jsa)$", "");
        return normalizeStateStem(stem);
    }

    private static String normalizeStateStem(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static void copyFile(File from, File to) {
        if (!from.isFile() || to.exists()) return;
        try { Files.copy(from.toPath(), to.toPath()); } catch (Exception ignored) { }
    }

    void invalidate(Game game) {
        new File(cached, game.key + ".png").delete();
        new File(cached, game.key + ".missing").delete();
    }

    private static String urlName(String name) {
        try { return URLEncoder.encode(name, "UTF-8").replace("+", "%20"); }
        catch (Exception e) { return name; }
    }

    private synchronized String bestCatalogFile(String title, String stem) {
        List<String> list = getCatalog();
        String target = normal(title), original = normal(stem);
        String best = null;
        double score = 0.0;
        for (String file : list) {
            String bare = file.replaceFirst("(?i)\\.png$", "").replaceAll("\\([^)]*\\)", " ").trim();
            double a = similarity(target, normal(bare));
            double b = similarity(original, normal(bare));
            double candidate = Math.max(a, b);
            if (candidate > score) { score = candidate; best = file; }
        }
        // Conservative threshold: do not attach misleading official art to similarly named fan hacks.
        return score >= 0.88 ? best : null;
    }

    private synchronized List<String> getCatalog() {
        if (catalog != null) return catalog;
        File local = new File(context.getFilesDir(), "gba-cover-index.txt");
        if (local.isFile() && System.currentTimeMillis() - local.lastModified() < 7L * 86400000L) {
            try (InputStream stream = new java.io.FileInputStream(local)) {
                String all = new String(readLimited(stream, 3 * 1024 * 1024), "UTF-8");
                catalog = Arrays.asList(all.split("\\n"));
                return catalog;
            } catch (Exception ignored) { }
        }
        if (System.currentTimeMillis() - attemptedCatalog < 60000) return new ArrayList<>();
        attemptedCatalog = System.currentTimeMillis();
        List<String> names = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(BASE).openConnection();
            conn.setConnectTimeout(5000); conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "ROM-Shelf/0.2");
            if (conn.getResponseCode() != 200) return names;
            String html = new String(readLimited(conn.getInputStream(), 4 * 1024 * 1024), "UTF-8");
            Matcher matcher = ENTRY.matcher(html);
            Set<String> unique = new LinkedHashSet<>();
            while (matcher.find()) {
                String name = URLDecoder.decode(matcher.group(1).replace("+", "%2B"), "UTF-8");
                name = Html.fromHtml(name, Html.FROM_HTML_MODE_LEGACY).toString();
                if (!name.contains("/") && name.endsWith(".png")) unique.add(name);
            }
            names.addAll(unique);
            if (names.size() >= 200) {
                try (FileOutputStream out = new FileOutputStream(local)) {
                    out.write(android.text.TextUtils.join("\n", names).getBytes("UTF-8"));
                }
                catalog = names;
            }
        } catch (Exception ignored) { }
        finally { if (conn != null) conn.disconnect(); }
        return names;
    }

    private static String normal(String s) {
        return s.toLowerCase(Locale.ROOT).replace("é", "e")
            .replaceAll("(?i)\\b(?:the|a|an)\\b", "")
            .replaceAll("[^a-z0-9]", "");
    }

    private static double similarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        if (a.equals(b)) return 1.0;
        if (a.length() < 5 || b.length() < 5) return 0;
        int[] prior = new int[b.length() + 1], next = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prior[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            next[0] = i;
            for (int j = 1; j <= b.length(); j++)
                next[j] = Math.min(Math.min(next[j-1] + 1, prior[j] + 1), prior[j-1] + (a.charAt(i-1) == b.charAt(j-1) ? 0 : 1));
            int[] tmp = prior; prior = next; next = tmp;
        }
        return 1.0 - ((double) prior[b.length()] / Math.max(a.length(), b.length()));
    }

    private boolean download(String uri, File target) {
        if (!ArtworkUrlPolicy.isAllowedImageUrl(uri)) return false;
        HttpURLConnection conn = null;
        try {
            String current = uri;
            for (int redirects = 0; redirects < 3; redirects++) {
                if (!ArtworkUrlPolicy.isAllowedImageUrl(current)) return false;
                conn = (HttpURLConnection) new URL(current).openConnection();
                conn.setConnectTimeout(4000); conn.setReadTimeout(6500);
                conn.setInstanceFollowRedirects(false);
                conn.setRequestProperty("User-Agent", "ROM-Shelf/0.2");
                int code = conn.getResponseCode();
                if (code == 301 || code == 302 || code == 307 || code == 308) {
                    String to = conn.getHeaderField("Location");
                    current = new URL(new URL(current), to).toString();
                    conn.disconnect(); conn = null;
                    continue;
                }
                if (code != 200 || conn.getContentLengthLong() > 5L * 1024 * 1024) return false;
                String mime = conn.getContentType();
                if (mime != null && !mime.toLowerCase(Locale.ROOT).startsWith("image/")) return false;
                byte[] data = readLimited(conn.getInputStream(), 5 * 1024 * 1024);
                if (BitmapFactory.decodeByteArray(data, 0, data.length) == null) return false;
                try (FileOutputStream out = new FileOutputStream(target)) { out.write(data); }
                return true;
            }
        } catch (Exception ignored) { }
        finally { if (conn != null) conn.disconnect(); }
        return false;
    }

    private static byte[] readLimited(InputStream in, int limit) throws Exception {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = input.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > limit) throw new IllegalStateException("Download is too large");
            }
            return out.toByteArray();
        }
    }

    private Bitmap readScaled(File f) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            int sample = 1;
            while (bounds.outWidth / sample > 650 || bounds.outHeight / sample > 900) sample *= 2;
            opts.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
        } catch (Exception e) { return null; }
    }

    void importCover(Game game, Uri uri) throws Exception {
        File selected = new File(custom, game.key + ".img");
        InputStream in = context.getContentResolver().openInputStream(uri);
        if (in == null) throw new IllegalStateException("Image could not be opened");
        AtomicCoverFile.replaceFrom(in, selected, 10 * 1024 * 1024, file -> readScaled(file) != null);
    }
    boolean removeCustom(Game game) {
        boolean removed = new File(custom, game.key + ".img").delete();
        return new File(legacyCustom, game.key + ".img").delete() || removed;
    }
}
