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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        File manual = customFile(tag);
        File auto = new File(cached, tag + ".png");
        File file = manual.isFile() ? manual : auto;
        if (file.isFile()) {
            Bitmap bitmap = readScaled(file);
            if (bitmap != null) {
                image.setImageBitmap(bitmap);
                return;
            }
        }
        File failed = new File(cached, tag + ".missing");
        if (failed.exists() && System.currentTimeMillis() - failed.lastModified() < 12L * 3600 * 1000) return;
        workers.execute(() -> {
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

    void clearRetiredFeatureState() {
        SharedPreferences.Editor edit = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith("url_") || key.startsWith("source_") || key.startsWith("state_signature_")) edit.remove(key);
        }
        String screenshotFolder = prefs.getString("state_screenshot_folder", null);
        if (screenshotFolder != null) releaseScreenshotFolderGrant(Uri.parse(screenshotFolder));
        edit.remove("use_state_screenshots").remove("state_screenshot_folder");
        edit.apply();
        removeCachedScreenshots();
    }

    private void releaseScreenshotFolderGrant(Uri uri) {
        if (isActiveLibraryFolder(uri)) return;
        for (android.content.UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
            if (!uri.equals(permission.getUri())) continue;
            int flags = 0;
            if (permission.isReadPermission()) flags |= android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (permission.isWritePermission()) flags |= android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            if (flags == 0) continue;
            try { context.getContentResolver().releasePersistableUriPermission(uri, flags); }
            catch (SecurityException ignored) { }
        }
    }

    private boolean isActiveLibraryFolder(Uri uri) {
        if (uri.toString().equals(prefs.getString("artwork_folder_uri", null))) return true;
        SharedPreferences folders = context.getSharedPreferences("rom_shelf", Context.MODE_PRIVATE);
        String target = uri.toString();
        String[] keys = {
            "folder_" + FileStore.ROMS,
            "folder_" + FileStore.SHARED,
            "folder_" + FileStore.JOHN,
            "folder_" + FileStore.PIZZA
        };
        for (String key : keys) if (target.equals(folders.getString(key, null))) return true;
        return false;
    }

    private void removeCachedScreenshots() {
        File[] files = cached.listFiles((dir, name) -> name.endsWith(".state.img"));
        if (files != null) for (File file : files) file.delete();
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

    Uri artworkFolderUri() {
        String value = prefs.getString("artwork_folder_uri", null);
        return value == null || value.isEmpty() ? null : Uri.parse(value);
    }

    String artworkFolderName() {
        Uri uri = artworkFolderUri();
        try {
            DocumentFile folder = uri == null ? null : DocumentFile.fromTreeUri(context, uri);
            return folder == null || folder.getName() == null ? "Choose box art folder" : folder.getName();
        } catch (SecurityException ignored) {
            return "Re-select box art folder";
        }
    }

    void setArtworkFolderUri(Uri uri) {
        prefs.edit().putString("artwork_folder_uri", uri == null ? "" : uri.toString()).apply();
    }

    String importArtworkFiles(List<Uri> selected) throws Exception {
        Uri tree = artworkFolderUri();
        DocumentFile folder = tree == null ? null : DocumentFile.fromTreeUri(context, tree);
        if (folder == null || !folder.isDirectory() || !folder.canWrite()) {
            throw new IllegalStateException("Choose a writable box art folder in Settings first");
        }
        if (selected == null || selected.isEmpty()) return "No box art selected";

        Set<String> names = new HashSet<>();
        Set<String> checksums = new HashSet<>();
        for (DocumentFile file : folder.listFiles()) {
            String name = file.getName();
            if (name != null) names.add(name.toLowerCase(Locale.ROOT));
            if (file.isFile() && isImageName(name)) {
                try { checksums.add(sha256(readImage(file.getUri()))); }
                catch (Exception ignored) { }
            }
        }

        int added = 0, duplicates = 0, ignored = 0;
        List<String> failures = new ArrayList<>();
        for (Uri uri : selected) {
            DocumentFile source = DocumentFile.fromSingleUri(context, uri);
            String originalName = source == null ? null : source.getName();
            if (!isImageName(originalName)) { ignored++; continue; }
            try {
                byte[] bytes = readImage(uri);
                String checksum = sha256(bytes);
                if (!checksums.add(checksum)) { duplicates++; continue; }
                String name = uniqueImageName(originalName, names);
                DocumentFile created = folder.createFile(imageMime(name), name);
                if (created == null || created.getName() == null || !name.equalsIgnoreCase(created.getName())) {
                    if (created != null) created.delete();
                    throw new IllegalStateException("Storage provider changed the image filename");
                }
                try {
                    try (OutputStream out = context.getContentResolver().openOutputStream(created.getUri(), "wt")) {
                        if (out == null) throw new IllegalStateException("Could not write into the box art folder");
                        out.write(bytes);
                        out.flush();
                    }
                    if (!checksum.equals(sha256(readImage(created.getUri())))) {
                        throw new IllegalStateException("Copied image failed its content check");
                    }
                } catch (Exception failure) {
                    created.delete();
                    throw failure;
                }
                names.add(name.toLowerCase(Locale.ROOT));
                added++;
            } catch (Exception e) {
                failures.add(originalName + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }
        StringBuilder result = new StringBuilder("Added ").append(added).append(" box art image").append(added == 1 ? "" : "s");
        if (duplicates > 0) result.append(" · ").append(duplicates).append(" identical image").append(duplicates == 1 ? "" : "s").append(" skipped");
        if (ignored > 0) result.append(" · ").append(ignored).append(" unsupported file").append(ignored == 1 ? "" : "s").append(" ignored");
        if (!failures.isEmpty()) result.append(" · ").append(failures.get(0));
        return result.toString();
    }

    private static boolean isImageName(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|webp)$");
    }

    private static String imageMime(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private static String uniqueImageName(String proposed, Set<String> names) {
        String safe = proposed.replaceAll("[/\\\\\\p{Cntrl}]", "_").trim();
        if (!names.contains(safe.toLowerCase(Locale.ROOT))) return safe;
        int dot = safe.lastIndexOf('.');
        String base = dot > 0 ? safe.substring(0, dot) : safe;
        String extension = dot > 0 ? safe.substring(dot) : "";
        for (int suffix = 2; suffix < 10000; suffix++) {
            String candidate = base + " (" + suffix + ")" + extension;
            if (!names.contains(candidate.toLowerCase(Locale.ROOT))) return candidate;
        }
        throw new IllegalStateException("Could not find an unused image filename");
    }

    private byte[] readImage(Uri uri) throws Exception {
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IllegalStateException("Image could not be opened");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                if (output.size() > 10 * 1024 * 1024) throw new IllegalArgumentException("Image must be under 10 MB");
            }
            byte[] bytes = output.toByteArray();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || (long) bounds.outWidth * bounds.outHeight > 100_000_000L) {
                throw new IllegalArgumentException("File is not a supported image");
            }
            return bytes;
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte value : digest) out.append(String.format(Locale.ROOT, "%02x", value & 255));
        return out.toString();
    }

    boolean removeCustom(Game game) {
        boolean removed = new File(custom, game.key + ".img").delete();
        return new File(legacyCustom, game.key + ".img").delete() || removed;
    }
}
