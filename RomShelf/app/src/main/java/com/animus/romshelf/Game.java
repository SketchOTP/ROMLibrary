package com.animus.romshelf;

import androidx.documentfile.provider.DocumentFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Game {
    final DocumentFile rom;
    final String fileName;
    final String stem;
    String title;
    String version;
    final String uriKey;
    String key;
    final List<String> legacyKeys = new ArrayList<>();
    final List<String> saveStems = new ArrayList<>();
    DocumentFile johnSave;
    DocumentFile pizzaSave;
    long johnStamp;
    long pizzaStamp;
    DocumentFile sharedSave;
    long sharedStamp;
    String fingerprint;

    private static final Pattern VERSION = Pattern.compile("(?i)(?<![a-z0-9])v(?:er(?:sion)?)?[ ._-]*(\\d+(?:\\.\\d+){0,3}(?:[-_ ]?(?:alpha|beta|rc)\\d*)?)");
    private static final Pattern REV = Pattern.compile("(?i)\\b(?:rev(?:ision)?\\.?|beta|alpha)\\s*[0-9]+\\b");
    private static final Pattern REGION = Pattern.compile("(?i)\\b(USA|Europe|Japan|World|Australia|Korea)\\b");

    Game(DocumentFile file) {
        rom = file;
        fileName = file.getName() == null ? "Untitled.gba" : file.getName();
        stem = fileName.replaceFirst("(?i)\\.(gba|zip)$", "");
        uriKey = FileStore.hash(file.getUri().toString());
        key = uriKey;
        legacyKeys.add(uriKey);
        saveStems.add(stem);
        Matcher v = VERSION.matcher(stem);
        Matcher r = REV.matcher(stem);
        String tag = v.find() ? "v" + v.group(1).replace(" ", "") : (r.find() ? r.group().trim() : null);
        Matcher region = REGION.matcher(stem);
        String area = region.find() ? region.group(1) : null;
        version = tag == null ? (area == null ? "GBA" : "GBA · " + area) : (area == null ? tag : tag + " · " + area);
        title = cleanTitle(stem);
    }

    static String cleanTitle(String input) {
        String s = input.replaceFirst("(?i)\\.(gba|zip)$", "");
        s = s.replaceAll("(?i)^\\s*\\d{3,6}\\s*[-_.]\\s*", "");
        s = s.replaceAll("(?i)\\([^)]*\\)", " ").replaceAll("\\[[^]]*]", " ");
        s = s.replaceAll("(?i)(?<![a-z])v(?:er(?:sion)?)?[ ._-]*\\d+(?:\\.\\d+){0,3}(?:[-_ ]?(?:alpha|beta|rc)\\d*)?", " ");
        s = s.replaceAll("(?i)\\b(?:final|patched|clean|fixed|no-intro|decrypted|multilang|dump|release)\\b", " ");
        s = s.replace('_', ' ').replaceAll("(?<=[a-z])(?=[A-Z])", " ");
        s = s.replaceAll("\\s*[-]+\\s*", " - ").replaceAll("\\s+", " ").replaceAll("(?:\\s*-\\s*)+$", "").trim();
        if (s.length() == 0) return input;
        StringBuilder b = new StringBuilder();
        for (String word : s.split(" ")) {
            if (b.length() > 0) b.append(' ');
            if (word.length() == 0) continue;
            if (word.equals("-")) { b.append(word); continue; }
            String lower = word.toLowerCase(Locale.ROOT);
            if (lower.equals("gba") || lower.equals("dx") || lower.equals("rpg") || lower.equals("nes")) b.append(lower.toUpperCase(Locale.ROOT));
            else if (lower.equals("pokemon")) b.append("Pokémon");
            else if (word.matches("[IVX]{2,5}")) b.append(word);
            else b.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
        }
        return b.toString().replace(" - The ", ": The ").trim();
    }

    void alias(Game other) {
        for (String oldKey : other.legacyKeys) if (!legacyKeys.contains(oldKey)) legacyKeys.add(oldKey);
        for (String s : other.saveStems) if (!saveStems.contains(s)) saveStems.add(s);
        if (other.sharedSave != null && (sharedSave == null || other.sharedStamp > sharedStamp)) { sharedSave = other.sharedSave; sharedStamp = other.sharedStamp; }
        if (other.johnSave != null && (johnSave == null || other.johnStamp > johnStamp)) {
            johnSave = other.johnSave; johnStamp = other.johnStamp;
        }
        if (other.pizzaSave != null && (pizzaSave == null || other.pizzaStamp > pizzaStamp)) {
            pizzaSave = other.pizzaSave; pizzaStamp = other.pizzaStamp;
        }
    }

    void setFingerprint(String digest) {
        fingerprint = digest;
        key = RomIdentity.key(digest, uriKey);
    }

    Game copyForSaveRefresh() {
        Game copy = new Game(rom);
        copy.key = key;
        copy.title = title;
        copy.version = version;
        copy.fingerprint = fingerprint;
        copy.saveStems.clear(); copy.saveStems.addAll(saveStems);
        copy.legacyKeys.clear(); copy.legacyKeys.addAll(legacyKeys);
        return copy;
    }

    long lastPlayedEstimate() { return Math.max(sharedStamp, Math.max(johnStamp, pizzaStamp)); }
    String latestSide() { return johnStamp >= pizzaStamp ? "John GBA" : "Pizza Boy"; }
    String saveStatus() { return sharedSave != null || johnSave != null || pizzaSave != null ? "SAVED" : "NO SAVE"; }
    String nameForSave() { return stem + ".sav"; }
    String keyForSave() { return stem.toLowerCase(Locale.ROOT); }
}
