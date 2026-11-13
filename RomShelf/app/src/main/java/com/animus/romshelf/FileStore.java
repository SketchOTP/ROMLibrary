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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

final class FileStore {
    static final String ROMS = "roms", SHARED = "shared_saves", JOHN = "john", PIZZA = "pizza";
    private static final int MAX_SCAN = 3500;
    private static final int MAX_SAVE_BYTES = 8 * 1024 * 1024;
    private static final long MAX_ROM_FILE_BYTES = 128L * 1024L * 1024L;
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
        result.put(SHARED, findSaves(folder(SHARED), SHARED));
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

    String importSelectedFiles(List<Uri> selected, List<Game> knownGames) throws Exception {
        if (selected == null || selected.isEmpty()) return "No files selected";
        DocumentFile romRoot = folder(ROMS);
        DocumentFile saveRoot = folder(SHARED);
        Set<String> knownHashes = new HashSet<>();
        if (knownGames != null) for (Game game : knownGames) {
            if (game.fingerprint != null && !game.fingerprint.isEmpty()) knownHashes.add(game.fingerprint);
        }
        // Re-read the selected tree so a prior import that has not reached the next UI scan yet
        // cannot create another copy of the same ROM on a rapid second add.
        if (romRoot != null) collectRomHashes(romRoot, knownHashes, 0, new int[] {0});
        Set<String> knownNames = new HashSet<>();
        if (romRoot != null) collectRomNames(romRoot, knownNames, 0, new int[] {0});

        Map<String, SourceSave> selectedSaves = new LinkedHashMap<>();
        List<String> issues = new ArrayList<>();
        int addedRoms = 0, duplicateRoms = 0, addedSaves = 0, olderSaves = 0, unknownSaveTimes = 0, ignored = 0;
        for (Uri uri : selected) {
            DocumentFile source = DocumentFile.fromSingleUri(context, uri);
            String name = source == null ? null : source.getName();
            if (name == null) { ignored++; continue; }
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".sav")) {
                String stem = lower.substring(0, lower.length() - 4);
                if (stem.endsWith(".gba")) stem = stem.substring(0, stem.length() - 4);
                else if (stem.endsWith(".zip")) stem = stem.substring(0, stem.length() - 4);
                SourceSave previous = selectedSaves.get(stem);
                long modified = Math.max(0, source.lastModified());
                if (previous == null || modified > previous.modified) {
                    selectedSaves.put(stem, new SourceSave(source, stem, modified));
                }
                continue;
            }
            boolean zip = lower.endsWith(".zip");
            if (!zip && !lower.endsWith(".gba")) { ignored++; continue; }
            if (romRoot == null || !romRoot.canWrite()) {
                issues.add("ROM folder is not writable; ROM files were not copied");
                break;
            }
            try {
                String digest = digestRom(uri, name);
                if (digest == null) throw new IllegalArgumentException("No GBA ROM found in " + name);
                if (!knownHashes.add(digest)) { duplicateRoms++; continue; }
                String destinationName = uniqueRomName(romRoot, safeRomName(name), knownNames);
                copyRom(uri, romRoot, destinationName, digest);
                knownNames.add(destinationName.toLowerCase(Locale.ROOT));
                addedRoms++;
            } catch (Exception e) {
                issues.add(name + ": " + message(e));
            }
        }

        if (!selectedSaves.isEmpty()) {
            if (saveRoot == null || !saveRoot.canWrite()) {
                issues.add("Shared save folder is not writable; selected .sav files were not copied");
            } else {
                Map<String, DocumentFile> currentSaves = findSaves(saveRoot, SHARED);
                for (SourceSave source : selectedSaves.values()) {
                    try {
                        DocumentFile existing = currentSaves.get(source.stem);
                        long existingTime = existing == null ? 0 : saveFileActivityTime(SHARED, existing);
                        if (existing != null && source.modified == 0) {
                            unknownSaveTimes++;
                            continue;
                        }
                        if (existing != null && source.modified <= existingTime) {
                            olderSaves++;
                            continue;
                        }
                        byte[] contents = readDocument(source.file);
                        String saveName = saveFileName(source.file.getName());
                        if (existing != null) backup(source.stem, SHARED, existing);
                        DocumentFile written = writeSaveFile(SHARED, existing,
                            existing == null ? saveName : existing.getName(), contents);
                        recordImportedSaveTime(SHARED, source.stem, source.modified, written);
                        currentSaves.put(source.stem, written);
                        addedSaves++;
                        if (source.modified == 0) unknownSaveTimes++;
                    } catch (Exception e) {
                        issues.add(source.file.getName() + ": " + message(e));
                    }
                }
            }
        }

        StringBuilder result = new StringBuilder();
        result.append("Added ").append(addedRoms).append(" ROM").append(addedRoms == 1 ? "" : "s");
        if (duplicateRoms > 0) result.append(" · ").append(duplicateRoms).append(" exact duplicate ROM").append(duplicateRoms == 1 ? "" : "s").append(" skipped");
        if (addedSaves > 0) result.append(" · imported ").append(addedSaves).append(" newest matching .sav").append(addedSaves == 1 ? "" : " files");
        if (olderSaves > 0) result.append(" · kept the newer shared save for ").append(olderSaves).append(" game").append(olderSaves == 1 ? "" : "s");
        if (unknownSaveTimes > 0) result.append(" · timestamp unavailable for ").append(unknownSaveTimes).append(" selected save").append(unknownSaveTimes == 1 ? "" : "s");
        if (ignored > 0) result.append(" · ignored ").append(ignored).append(" unsupported file").append(ignored == 1 ? "" : "s");
        if (!issues.isEmpty()) result.append(" · ").append(String.join("; ", issues.subList(0, Math.min(2, issues.size()))));
        return result.toString();
    }

    private static final class SourceSave {
        final DocumentFile file;
        final String stem;
        final long modified;
        SourceSave(DocumentFile file, String stem, long modified) {
            this.file = file; this.stem = stem; this.modified = modified;
        }
    }

    private static String message(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private String digestRom(Uri uri, String name) throws Exception {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IllegalStateException("Cannot read selected file");
            return GbaRomDigest.digest(input, name.toLowerCase(Locale.ROOT).endsWith(".zip"));
        }
    }

    private String digestRom(DocumentFile file) {
        try {
            String name = file.getName();
            if (name == null) return null;
            return digestRom(file.getUri(), name);
        } catch (Exception ignored) { return null; }
    }

    private void copyRom(Uri sourceUri, DocumentFile destinationFolder, String name, String expectedDigest) throws Exception {
        DocumentFile created = destinationFolder.createFile("application/octet-stream", name);
        if (created == null) throw new IllegalStateException("Could not create ROM in the selected folder");
        if (created.getName() == null || !name.equalsIgnoreCase(created.getName())) {
            created.delete();
            throw new IllegalStateException("Storage provider changed the ROM filename");
        }
        try {
            ContentResolver resolver = context.getContentResolver();
            long total = 0;
            try (InputStream in = resolver.openInputStream(sourceUri);
                 OutputStream out = resolver.openOutputStream(created.getUri(), "wt")) {
                if (in == null || out == null) throw new IllegalStateException("ROM could not be opened for copying");
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_ROM_FILE_BYTES) throw new IllegalArgumentException("ROM archive exceeds the 128 MiB import limit");
                    out.write(buffer, 0, count);
                }
                if (total == 0) throw new IllegalArgumentException("Selected ROM is empty");
                out.flush();
            }
            String copiedDigest = digestRom(created);
            if (!expectedDigest.equals(copiedDigest)) throw new IllegalStateException("Copied ROM failed its content check");
        } catch (Exception failure) {
            created.delete();
            throw failure;
        }
    }

    private static String safeRomName(String original) {
        String name = original.replaceAll("[/\\\\\\p{Cntrl}]", "_").trim();
        if (!name.matches("(?i).+\\.(gba|zip)$")) throw new IllegalArgumentException("Choose a .gba or .zip game");
        return name;
    }

    private static String uniqueRomName(DocumentFile root, String proposed, Set<String> names) {
        String lower = proposed.toLowerCase(Locale.ROOT);
        if (!names.contains(lower) && root.findFile(proposed) == null) return proposed;
        int dot = proposed.lastIndexOf('.');
        String base = dot > 0 ? proposed.substring(0, dot) : proposed;
        String extension = dot > 0 ? proposed.substring(dot) : "";
        for (int suffix = 2; suffix < 10000; suffix++) {
            String candidate = base + " (" + suffix + ")" + extension;
            if (!names.contains(candidate.toLowerCase(Locale.ROOT)) && root.findFile(candidate) == null) return candidate;
        }
        throw new IllegalStateException("Could not find an unused ROM filename");
    }

    private void collectRomNames(DocumentFile dir, Set<String> names, int depth, int[] count) {
        if (depth > 8 || count[0] > MAX_SCAN) return;
        for (DocumentFile item : dir.listFiles()) {
            if (++count[0] > MAX_SCAN) return;
            if (item.isDirectory()) collectRomNames(item, names, depth + 1, count);
            else if (item.isFile() && item.getName() != null && item.getName().matches("(?i).*\\.(gba|zip)$")) {
                names.add(item.getName().toLowerCase(Locale.ROOT));
            }
        }
    }

    private void collectRomHashes(DocumentFile dir, Set<String> hashes, int depth, int[] count) {
        if (depth > 8 || count[0] > MAX_SCAN) return;
        for (DocumentFile item : dir.listFiles()) {
            if (++count[0] > MAX_SCAN) return;
            if (item.isDirectory()) collectRomHashes(item, hashes, depth + 1, count);
            else if (item.isFile() && item.getName() != null && item.getName().matches("(?i).*\\.(gba|zip)$")) {
                String digest = digestRom(item);
                if (digest != null) hashes.add(digest);
            }
        }
    }

    private static String saveFileName(String original) {
        String safe = original.replaceAll("[/\\\\\\p{Cntrl}]", "_").trim();
        if (!safe.toLowerCase(Locale.ROOT).endsWith(".sav")) throw new IllegalArgumentException("Choose a .sav battery save");
        String stem = safe.substring(0, safe.length() - 4);
        if (stem.toLowerCase(Locale.ROOT).endsWith(".gba") || stem.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            stem = stem.substring(0, stem.lastIndexOf('.'));
        }
        if (stem.trim().isEmpty()) throw new IllegalArgumentException("Save filename has no game name");
        return stem + ".sav";
    }

    private String importedSaveKey(String destination, String stem) {
        return "imported_save_" + destination + "_" + hash(stem.toLowerCase(Locale.ROOT));
    }

    private long saveFileActivityTime(String destination, DocumentFile save) {
        if (save == null) return 0;
        long modified = Math.max(0, save.lastModified());
        Long imported = importedSourceActivityTime(destination, save);
        return imported == null ? modified : imported;
    }

    private Long importedSourceActivityTime(String destination, DocumentFile save) {
        String name = save.getName();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".sav")) return null;
        String stem = name.substring(0, name.length() - 4).toLowerCase(Locale.ROOT);
        String[] parts = prefs.getString(importedSaveKey(destination, stem), "").split(":", -1);
        if (parts.length == 2) {
            try {
                long written = Long.parseLong(parts[0]);
                long sourceTime = Long.parseLong(parts[1]);
                if (sourceTime > 0 && Math.abs(Math.max(0, save.lastModified()) - written) <= 3000) return sourceTime;
            } catch (NumberFormatException ignored) { }
        }
        return null;
    }

    private void recordImportedSaveTime(String destination, String stem, long sourceTime, DocumentFile file) {
        long written = file == null ? 0 : Math.max(0, file.lastModified());
        prefs.edit().putString(importedSaveKey(destination, stem), written + ":" + Math.max(0, sourceTime)).apply();
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
        Long imported = importedSourceActivityTime(destination, save);
        if (imported != null) return imported;
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
        return saveFileActivityTime(destination, save);
    }

    private void recordCopiedTime(Game g, String destination, long sourceTime, DocumentFile f) {
        long timestamp = f == null ? 0 : f.lastModified();
        prefs.edit().putString(copiedKey(g, destination), timestamp + ":" + Math.max(0, sourceTime)).apply();
        if (f != null && f.getName() != null && f.getName().toLowerCase(Locale.ROOT).endsWith(".sav")) {
            recordImportedSaveTime(destination, f.getName().substring(0, f.getName().length() - 4), sourceTime, f);
        }
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

    private Map<String, DocumentFile> findSaves(DocumentFile root, String destination) {
        Map<String, DocumentFile> files = new HashMap<>();
        if (root != null) walkSaves(root, files, destination, 0, new int[] {0});
        return files;
    }

    private void walkSaves(DocumentFile dir, Map<String, DocumentFile> found, String destination, int depth, int[] count) {
        if (depth > 4 || count[0] > MAX_SCAN) return;
        for (DocumentFile file : dir.listFiles()) {
            if (++count[0] > MAX_SCAN) return;
            if (file.isDirectory()) walkSaves(file, found, destination, depth + 1, count);
            else {
                String name = file.getName();
                if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".sav")) {
                    String stem = name.substring(0, name.length() - 4).toLowerCase(Locale.ROOT);
                    DocumentFile previous = found.get(stem);
                    if (previous == null || saveFileActivityTime(destination, file) > saveFileActivityTime(destination, previous)) found.put(stem, file);
                }
            }
        }
    }

    DocumentFile findExactSave(Game game, String folderKey) {
        Map<String, DocumentFile> saves = findSaves(folder(folderKey), folderKey);
        DocumentFile newest = null;
        for (String stem : game.saveStems) {
            DocumentFile f = saves.get(stem.toLowerCase(Locale.ROOT));
            if (f != null && (newest == null || saveFileActivityTime(folderKey, f) > saveFileActivityTime(folderKey, newest))) newest = f;
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
        File file = new File(dir, System.currentTimeMillis() + "_" + UUID.randomUUID() + "_" + destinationKey + "_" + hash(stem.toLowerCase(Locale.ROOT)) + ".sav");
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
        String expected = selectedTarget != null && selectedTarget.getName() != null ? selectedTarget.getName() : game.nameForSave();
        return writeSaveFile(destinationKey, selectedTarget, expected, bytes);
    }

    private DocumentFile writeSaveFile(String destinationKey, DocumentFile selectedTarget, String expected, byte[] bytes) throws Exception {
        DocumentFile dir = folder(destinationKey);
        if (dir == null || !dir.canWrite()) throw new IllegalStateException("Destination folder not writable");
        DocumentFile existing = selectedTarget;
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
        String normalized = "_" + destinationKey + "_" + hash(game.stem.toLowerCase(Locale.ROOT)) + ".sav";
        String legacy = "_" + destinationKey + "_" + hash(game.stem) + ".sav";
        File[] files = dir.listFiles((d, n) -> n.endsWith(normalized) || n.endsWith(legacy));
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
