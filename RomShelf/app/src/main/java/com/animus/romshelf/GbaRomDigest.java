package com.animus.romshelf;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Bounded content hashing for raw GBA ROMs and the first GBA member of a ZIP. */
final class GbaRomDigest {
    static final long MAX_ROM_BYTES = 96L * 1024L * 1024L;
    static final long MAX_PRELUDE_BYTES = 16L * 1024L * 1024L;
    static final int MAX_ZIP_ENTRIES_BEFORE_ROM = 256;
    static final int MAX_ENTRY_NAME_CHARS = 4096;

    private GbaRomDigest() { }

    static String digest(InputStream input, boolean zipArchive) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (zipArchive) return digestZip(input, digest);
            long size = copyToDigest(input, digest, MAX_ROM_BYTES);
            return size == 0 ? null : hex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String digestZip(InputStream input, MessageDigest digest) throws IOException {
        ZipInputStream zip = new ZipInputStream(input);
        int entries = 0;
        long preludeBytes = 0;
        byte[] buffer = new byte[32768];
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            if (++entries > MAX_ZIP_ENTRIES_BEFORE_ROM) throw new IOException("ZIP has too many entries before its GBA ROM");
            String name = entry.getName();
            if (name == null || name.length() > MAX_ENTRY_NAME_CHARS) throw new IOException("ZIP entry name is too long");
            if (name.toLowerCase(Locale.ROOT).endsWith(".gba")) {
                long size = copyToDigest(zip, digest, MAX_ROM_BYTES);
                return size == 0 ? null : hex(digest.digest());
            }
            // A ZIP entry named as a directory can still contain payload bytes in
            // malformed archives. Drain it under the same prelude budget instead
            // of letting getNextEntry() implicitly decompress it without a cap.
            int count;
            while ((count = zip.read(buffer)) != -1) {
                preludeBytes += count;
                if (preludeBytes > MAX_PRELUDE_BYTES) throw new IOException("ZIP data before its GBA ROM exceeds the scan limit");
            }
        }
        return null;
    }

    private static long copyToDigest(InputStream input, MessageDigest digest, long limit) throws IOException {
        byte[] buffer = new byte[32768];
        long total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > limit) throw new IOException("ROM exceeds the 96 MiB hashing limit");
            digest.update(buffer, 0, count);
        }
        return total;
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format(Locale.ROOT, "%02x", value & 255));
        return out.toString();
    }
}
