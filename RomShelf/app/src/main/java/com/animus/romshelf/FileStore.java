package com.animus.romshelf;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

final class FileStore {
    static final String ROMS = "roms", SHARED = "shared_saves", JOHN = "john", PIZZA = "pizza";
    private static final int MAX_SCAN = 3500;
    private static final int MAX_SAVE_BYTES = 8 * 1024 * 1024;
    private final Context context;
    private final SharedPreferences prefs;
    private int duplicateCount;
    int duplicateCount() { return duplicateCount; }

    FileStore(Context c) {
        context = c.getApplicationContext();
        prefs = context.getSharedPreferences("rom_shelf", Context.MODE_PRIVATE);
    }

    Uri getUri(String key) {
        if (ROMS.equals(key)) {
            String roms = prefs.getString("folder_" + ROMS, null);
            return roms == null ? null : Uri.parse(roms);
        }
        // One selected battery-save directory is canonical for both emulators.
        // Older installs may have only the John GBA or Pizza Boy tree saved.
        String s = prefs.getString("folder_" + SHARED, null);
        if (s == null) s = prefs.getString("folder_" + JOHN, null);
        if (s == null) s = prefs.getString("folder_" + PIZZA, null);
        return s == null ? null : Uri.parse(s);
    }

    void saveUri(String key, Uri uri) {
        prefs.edit().putString("folder_" + key, uri.toString()).apply();
    }

    DocumentFile folder(String key) {
        Uri uri = getUri(key);
        return uri == null ? null : DocumentFile.fromTreeUri(context, uri);
    }

    List<Game> scan() {
        List<Game> scanned = new ArrayList<>();
        duplicateCount = 0;
        DocumentFile roms = folder(ROMS);
        if (roms == null) return scanned;
        walkRoms(roms, scanned, 0, new int[] {0});
        scanned.sort(Comparator.comparing(g -> g.rom.getUri().toString()));
        for (Game g : scanned) {
            g.setFingerprint(romDigest(g));
            migrateIdentity(g);
            g.title = prefs.getString("title_" + g.key, g.title);
            g.version = prefs.getString("version_" + g.key, g.version);
        }
        Map<String, Map<String, DocumentFile>> saves = loadSaveMaps();
        applySaveState(scanned, saves);
        // Content hashes, not similar-looking names, decide which files are exact duplicates.
        Map<String, Game> unique = new LinkedHashMap<>();
        for (Game g : scanned) {
            String digest = g.fingerprint;
            String id = digest == null ? "unreadable:" + g.key : "sha256:" + digest;
            Game previous = unique.get(id);
            if (previous == null) unique.put(id, g);
            else {
                duplicateCount++;
                // Prefer the filename that currently has the latest emulator save.
                if (g.lastPlayedEstimate() > previous.lastPlayedEstimate()) {
                    g.alias(previous);
                    unique.put(id, g);
                } else previous.alias(g);
            }
        }
        List<Game> games = new ArrayList<>(unique.values());
        applySaveState(games, saves);
        return games;
    }

    private void migrateIdentity(Game game) {
        String stableKey = game.key;
        if (!stableKey.equals(game.uriKey)) {
            migrateString("title_" + game.uriKey, "title_" + stableKey);
            migrateString("version_" + game.uriKey, "version_" + stableKey);
        }
        String oldDigest = "sha_" + game.uriKey;
        if (prefs.contains(oldDigest)) prefs.edit().remove(oldDigest).apply();
    }

    private void migrateString(String oldKey, String newKey) {
        if (prefs.contains(newKey)) return;
        String old = prefs.getString(oldKey, "");
        if (!old.isEmpty()) prefs.edit().putString(newKey, old).apply();
    }

    void refreshSaveState(List<Game> games) {
        applySaveState(games, loadSaveMaps());
    }

    private Map<String, Map<String, DocumentFile>> loadSaveMaps() {
        Map<String, Map<String, DocumentFile>> result = new HashMap<>();
        result.put(SHARED, findSaves(folder(SHARED)));
        return result;
    }

    private void applySaveState(List<Game> games, Map<String, Map<String, DocumentFile>> saves) {
        for (Game game : games) {
            game.sharedSave = null; game.sharedStamp = 0;
            for (String stem : game.saveStems) {
                String key = stem.toLowerCase(Locale.ROOT);
                considerSave(game, SHARED, saves.get(SHARED).get(key));
            }
        }
    }

    private void considerSave(Game game, String destination, DocumentFile save) {
        if (save == null || !SHARED.equals(destination)) return;
        long time = originalActivityTime(game, destination, save);
        if (game.sharedSave == null || time > game.sharedStamp) {
            game.sharedSave = save; game.sharedStamp = time;
        }
    }

    void setMetadata(Game g, String title, String version) {
        prefs.edit().putString("title_" + g.key, title).putString("version_" + g.key, version).apply();
        g.title = title;
        g.version = version;
    }

    private String romDigest(Game g) {
        try {
            try (InputStream raw = context.getContentResolver().openInputStream(g.rom.getUri())) {
                if (raw == null) return null;
                return GbaRomDigest.digest(raw, g.fileName.toLowerCase(Locale.ROOT).endsWith(".zip"));
            }
        } catch (Exception ignored) { return null; }
    }

    private String copiedKey(Game g, String destination) { return "copy_" + g.keyForSave() + "_" + destination; }
    private long originalActivityTime(Game g, String destination, DocumentFile save) {
        if (save == null) return 0;
        long modified = Math.max(0, save.lastModified());
        String data = prefs.getString(copiedKey(g, destination), "");
        String[] parts = data.split(":", -1);
        if (parts.length == 2) {
            try {
                long written = Long.parseLong(parts[0]);
                long prior = Long.parseLong(parts[1]);
                if (modified != 0 && Math.abs(modified - written) <= 3000) return prior;
            } catch (NumberFormatException ignored) { }
        }
        return modified;
    }

    private void recordCopiedTime(Game g, String destination, long sourceTime, DocumentFile f) {
        long timestamp = f == null ? 0 : f.lastModified();
        prefs.edit().putString(copiedKey(g, destination), timestamp + ":" + Math.max(0, sourceTime)).apply();
    }

    private void walkRoms(DocumentFile dir, List<Game> games, int depth, int[] count) {
        if (depth > 8 || count[0] > MAX_SCAN) return;
        for (DocumentFile item : dir.listFiles()) {
            if (++count[0] > MAX_SCAN) return;
            if (item.isDirectory()) walkRoms(item, games, depth + 1, count);
            else if (item.isFile() && item.getName() != null && item.getName().matches("(?i).*\\.(gba|zip)$")) {
                games.add(new Game(item));
            }
        }
    }

    private Map<String, DocumentFile> findSaves(DocumentFile root) {
        Map<String, DocumentFile> files = new HashMap<>();
        if (root != null) walkSaves(root, files, 0, new int[] {0});
        return files;
    }

    private void walkSaves(DocumentFile dir, Map<String, DocumentFile> found, int depth, int[] count) {
        if (depth > 4 || count[0] > MAX_SCAN) return;
        for (DocumentFile file : dir.listFiles()) {
            if (++count[0] > MAX_SCAN) return;
            if (file.isDirectory()) walkSaves(file, found, depth + 1, count);
            else {
                String name = file.getName();
                if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".sav")) {
                    String stem = name.substring(0, name.length() - 4).toLowerCase(Locale.ROOT);
                    DocumentFile previous = found.get(stem);
                    if (previous == null || file.lastModified() > previous.lastModified()) found.put(stem, file);
                }
            }
        }
    }

    DocumentFile findExactSave(Game game, String folderKey) {
        Map<String, DocumentFile> saves = findSaves(folder(folderKey));
        DocumentFile newest = null;
        for (String stem : game.saveStems) {
            DocumentFile f = saves.get(stem.toLowerCase(Locale.ROOT));
            if (f != null && (newest == null || f.lastModified() > newest.lastModified())) newest = f;
        }
        return newest;
    }

    static String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12; i++) sb.append(String.format(Locale.US, "%02x", bytes[i] & 0xff));
            return sb.toString();
        } catch (Exception e) { return String.valueOf(value.hashCode()); }
    }

    private byte[] readDocument(DocumentFile file) throws Exception {
        if (file == null || !file.isFile()) throw new IllegalStateException("Save file not found");
        if (file.length() > MAX_SAVE_BYTES) throw new IllegalArgumentException("Save too large to transfer safely");
        ContentResolver resolver = context.getContentResolver();
        try (InputStream in = resolver.openInputStream(file.getUri()); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalStateException("Cannot read file");
            byte[] chunk = new byte[16 * 1024];
            int len;
            while ((len = in.read(chunk)) != -1) {
                out.write(chunk, 0, len);
                if (out.size() > MAX_SAVE_BYTES) throw new IllegalArgumentException("Save exceeds safety limit");
            }
            if (out.size() == 0) throw new IllegalStateException("Refusing to transfer an empty save");
            return out.toByteArray();
        }
    }

    private File backup(String stem, String destinationKey, DocumentFile destination) throws Exception {
        byte[] bytes = readDocument(destination);
        File dir = new File(context.getFilesDir(), "save-backups");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Could not create backup directory");
        File file = new File(dir, System.currentTimeMillis() + "_" + UUID.randomUUID() + "_" + destinationKey + "_" + hash(stem) + ".sav");
        File staged = File.createTempFile("save-backup-", ".tmp", dir);
        try {
            try (FileOutputStream out = new FileOutputStream(staged)) { out.write(bytes); out.getFD().sync(); }
            Files.move(staged.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } finally {
            if (staged.exists()) staged.delete();
        }
        return file;
    }

    private DocumentFile writeSave(Game game, String destinationKey, DocumentFile selectedTarget, byte[] bytes) throws Exception {
        DocumentFile dir = folder(destinationKey);
        if (dir == null || !dir.canWrite()) throw new IllegalStateException("Destination folder not writable");
        DocumentFile existing = selectedTarget;
        String expected = existing != null && existing.getName() != null ? existing.getName() : game.stem + ".sav";
        if (existing == null) {
            existing = dir.createFile("application/octet-stream", expected);
            if (existing == null) throw new IllegalStateException("Could not create destination save");
        }
        String actualName = existing.getName();
        if (!existing.isFile() || !existing.canWrite() || actualName == null || !expected.equalsIgnoreCase(actualName)) {
            if (selectedTarget == null) existing.delete();
            throw new IllegalStateException("Destination provider renamed the save unexpectedly");
        }
        ContentResolver resolver = context.getContentResolver();
        try {
            try (OutputStream out = resolver.openOutputStream(existing.getUri(), "wt")) {
                if (out == null) throw new IllegalStateException("Could not open destination for writing");
                out.write(bytes);
                out.flush();
            }
            // Some document providers report size late; verify actual bytes by reading back.
            byte[] verify = readDocument(existing);
            if (!MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(bytes), MessageDigest.getInstance("SHA-256").digest(verify))) {
                throw new IllegalStateException("Written save failed verification. A backup may be available.");
            }
        } catch (Exception failure) {
            if (selectedTarget == null) existing.delete();
            throw failure;
        }
        return existing;
    }

    String transfer(Game game, String sourceKey, String destinationKey) throws Exception {
        if (folder(sourceKey) == null || folder(destinationKey) == null) throw new IllegalStateException("Link both emulator save folders first");
        DocumentFile source = findExactSave(game, sourceKey);
        if (source == null) throw new IllegalStateException("Source .sav was not found");
        byte[] contents = readDocument(source);
        DocumentFile target = findExactSave(game, destinationKey);
        if (target != null) {
            backup(game.stem, destinationKey, target);
        }
        DocumentFile written = writeSave(game, destinationKey, target, contents);
        recordCopiedTime(game, destinationKey, source.lastModified(), written);
        return "Copied and verified " + contents.length + " bytes";
    }

    String exportSave(Game game, String sourceKey, Uri destination) throws Exception {
        DocumentFile source = findExactSave(game, sourceKey);
        if (source == null) throw new IllegalStateException("Source .sav was not found");
        byte[] contents = readDocument(source);
        try (OutputStream out = context.getContentResolver().openOutputStream(destination, "wt")) {
            if (out == null) throw new IllegalStateException("Could not open chosen export file");
            out.write(contents);
            out.flush();
        }
        return "Exported " + contents.length + " bytes to selected file";
    }

    String importSave(Game game, String destinationKey, Uri sourceUri) throws Exception {
        DocumentFile provided = DocumentFile.fromSingleUri(context, sourceUri);
        if (provided == null || provided.getName() == null ||
            !provided.getName().toLowerCase(Locale.ROOT).endsWith(".sav")) {
            throw new IllegalArgumentException("Choose a .sav battery save, not a save state");
        }
        byte[] contents;
        try (InputStream in = context.getContentResolver().openInputStream(sourceUri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalStateException("Cannot read the selected save");
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > MAX_SAVE_BYTES) throw new IllegalArgumentException("Save exceeds safety limit");
            }
            contents = out.toByteArray();
        }
        if (contents.length == 0) throw new IllegalArgumentException("Save is empty");
        DocumentFile existing = findExactSave(game, destinationKey);
        if (existing != null) backup(game.stem, destinationKey, existing);
        DocumentFile written = writeSave(game, destinationKey, existing, contents);
        recordCopiedTime(game, destinationKey, provided.lastModified(), written);
        return "Imported and verified " + contents.length + " bytes";
    }

    File newestBackup(Game game, String destinationKey) {
        File dir = new File(context.getFilesDir(), "save-backups");
        File[] files = dir.listFiles((d, n) -> n.endsWith("_" + destinationKey + "_" + hash(game.stem) + ".sav"));
        if (files == null || files.length == 0) return null;
        File newest = files[0];
        for (File f : files) if (f.lastModified() > newest.lastModified()) newest = f;
        return newest;
    }

    String restore(Game game, String destinationKey) throws Exception {
        File backup = newestBackup(game, destinationKey);
        if (backup == null) throw new IllegalStateException("No backup available");
        byte[] restored;
        try (InputStream in = new FileInputStream(backup); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[8192]; int n;
            while ((n = in.read(bytes)) != -1) {
                out.write(bytes, 0, n);
                if (out.size() > MAX_SAVE_BYTES) throw new IllegalStateException("Backup too large");
            }
            restored = out.toByteArray();
        }
        DocumentFile existing = findExactSave(game, destinationKey);
        if (existing != null) backup(game.stem, destinationKey, existing);
        DocumentFile written = writeSave(game, destinationKey, existing, restored);
        recordCopiedTime(game, destinationKey, 0, written);
        return "Restored and verified " + restored.length + " bytes";
    }
}
