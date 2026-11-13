package com.animus.romshelf;

import java.net.URI;
import java.util.Locale;

/** Policy for automatically fetched artwork; discovered AI URLs are untrusted input. */
final class ArtworkUrlPolicy {
    private static final String ART_HOST = "thumbnails.libretro.com";

    private ArtworkUrlPolicy() { }

    static boolean isAllowedImageUrl(String value) {
        URI uri = httpsUri(value);
        return uri != null && ART_HOST.equals(uri.getHost().toLowerCase(Locale.ROOT));
    }

    /** Source links are opened only after a user taps them; they are never fetched by the app. */
    static boolean isAllowedSourceUrl(String value) {
        return httpsUri(value) != null;
    }

    private static URI httpsUri(String value) {
        if (value == null || value.length() > 2048) return null;
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null ||
                uri.getUserInfo() != null || uri.getPort() != -1 && uri.getPort() != 443) return null;
            return uri;
        } catch (Exception ignored) {
            return null;
        }
    }
}
