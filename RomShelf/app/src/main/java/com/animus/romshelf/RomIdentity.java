package com.animus.romshelf;

/** Stable preference key based on ROM bytes, with a URI fallback for unreadable files. */
final class RomIdentity {
    private RomIdentity() { }

    static String key(String fingerprint, String uriKey) {
        if (fingerprint != null && fingerprint.matches("[0-9a-f]{64}")) return "rom_" + fingerprint;
        return uriKey;
    }
}
