package com.animus.romshelf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class SafetyRegressionTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void artworkFetchOnlyAllowsExactCatalogHost() {
        assertTrue(ArtworkUrlPolicy.isAllowedImageUrl("https://thumbnails.libretro.com/cover.png"));
        assertTrue(ArtworkUrlPolicy.isAllowedImageUrl("https://THUMBNAILS.LIBRETRO.COM:443/cover.png"));
        assertFalse(ArtworkUrlPolicy.isAllowedImageUrl("https://image.example/cover.png"));
        assertFalse(ArtworkUrlPolicy.isAllowedImageUrl("https://thumbnails.libretro.com.example/cover.png"));
        assertFalse(ArtworkUrlPolicy.isAllowedImageUrl("https://user@thumbnails.libretro.com/cover.png"));
        assertFalse(ArtworkUrlPolicy.isAllowedImageUrl("http://thumbnails.libretro.com/cover.png"));
        assertTrue(ArtworkUrlPolicy.isAllowedSourceUrl("https://art-source.example/game"));
        assertFalse(ArtworkUrlPolicy.isAllowedSourceUrl("http://art-source.example/game"));
    }

    @Test public void zipDigestMatchesRawGbaAndSkipsBoundedPrelude() throws Exception {
        byte[] rom = "test gba bytes".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("readme.txt"));
            zip.write("short metadata".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("folder/game.gba"));
            zip.write(rom);
            zip.closeEntry();
        }
        assertEquals(sha256(rom), GbaRomDigest.digest(new ByteArrayInputStream(rom), false));
        assertEquals(sha256(rom), GbaRomDigest.digest(new ByteArrayInputStream(bytes.toByteArray()), true));
    }

    @Test public void zipDigestRejectsUnboundedPreludeAndEntryWalk() throws Exception {
        byte[] largePrelude = new byte[(int) GbaRomDigest.MAX_PRELUDE_BYTES + 1];
        ByteArrayOutputStream preludeArchive = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(preludeArchive)) {
            zip.putNextEntry(new ZipEntry("metadata.bin"));
            zip.write(largePrelude);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("game.gba"));
            zip.write(1);
            zip.closeEntry();
        }
        assertDigestRejected(preludeArchive.toByteArray());

        ByteArrayOutputStream directoryArchive = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(directoryArchive)) {
            zip.putNextEntry(new ZipEntry("malformed-directory/"));
            zip.write(largePrelude);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("game.gba"));
            zip.write(1);
            zip.closeEntry();
        }
        assertDigestRejected(directoryArchive.toByteArray());

        ByteArrayOutputStream manyEntries = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(manyEntries)) {
            for (int i = 0; i <= GbaRomDigest.MAX_ZIP_ENTRIES_BEFORE_ROM; i++) {
                zip.putNextEntry(new ZipEntry("meta-" + i));
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("game.gba"));
            zip.write(1);
            zip.closeEntry();
        }
        assertDigestRejected(manyEntries.toByteArray());
    }

    @Test public void gameIdentityFollowsContentAcrossUris() {
        String digest = "a".repeat(64);
        assertEquals(RomIdentity.key(digest, "uri-a"), RomIdentity.key(digest, "uri-b"));
        assertNotEquals(RomIdentity.key(null, "uri-a"), RomIdentity.key(null, "uri-b"));
        assertTrue(RomIdentity.key(digest.toUpperCase(Locale.ROOT), "uri-a").equals("uri-a"));
    }

    @Test public void recentActivityUsesNewestBatterySaveQuickstateOrPizzaSession() {
        assertEquals(300L, Game.newestActivityTime(200L, 300L, 250L));
        assertEquals(400L, Game.newestActivityTime(400L, 300L, 250L));
        assertEquals(500L, Game.newestActivityTime(200L, 500L, 250L));
        assertEquals(0L, Game.newestActivityTime(-10L, 0L, 0L));
    }

    @Test public void rejectedCoverReplacementPreservesExistingImage() throws Exception {
        File target = temporary.newFile("cover.img");
        Files.write(target.toPath(), "old cover".getBytes(StandardCharsets.UTF_8));
        try {
            AtomicCoverFile.replaceFrom(new ByteArrayInputStream("new but invalid".getBytes(StandardCharsets.UTF_8)), target, 1024, file -> false);
            fail("invalid cover should be rejected");
        } catch (IllegalArgumentException expected) { }
        assertEquals("old cover", new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8));
    }

    @Test public void oversizedCoverReplacementPreservesExistingImage() throws Exception {
        File target = temporary.newFile("cover.img");
        Files.write(target.toPath(), "old cover".getBytes(StandardCharsets.UTF_8));
        try {
            AtomicCoverFile.replaceFrom(new ByteArrayInputStream(new byte[16]), target, 8, file -> true);
            fail("oversized cover should be rejected");
        } catch (IllegalArgumentException expected) { }
        assertEquals("old cover", new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8));
    }

    @Test public void validCoverReplacementCommitsStagedFile() throws Exception {
        File target = temporary.newFile("cover.img");
        Files.write(target.toPath(), "old cover".getBytes(StandardCharsets.UTF_8));
        AtomicCoverFile.replaceFrom(new ByteArrayInputStream("new cover".getBytes(StandardCharsets.UTF_8)), target, 1024,
            file -> file.length() > 0);
        assertEquals("new cover", new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8));
    }

    private static void assertDigestRejected(byte[] archive) throws Exception {
        try {
            GbaRomDigest.digest(new ByteArrayInputStream(archive), true);
            fail("malformed or over-budget ZIP should be rejected");
        } catch (java.io.IOException expected) { }
    }

    private static String sha256(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder out = new StringBuilder();
        for (byte b : digest) out.append(String.format(Locale.ROOT, "%02x", b & 255));
        return out.toString();
    }
}
