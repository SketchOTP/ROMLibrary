package com.animus.romshelf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Validates a staged cover before atomically replacing an existing custom image. */
final class AtomicCoverFile {
    interface Validator { boolean valid(File file); }

    private AtomicCoverFile() { }

    static void replaceFrom(InputStream input, File destination, int maxBytes, Validator validator) throws Exception {
        File parent = destination.getParentFile();
        if (parent == null || !parent.isDirectory() && !parent.mkdirs()) throw new IllegalStateException("Cover directory is unavailable");
        File staged = File.createTempFile(destination.getName() + ".", ".tmp", parent);
        try {
            int total = 0;
            try (InputStream in = input; FileOutputStream out = new FileOutputStream(staged)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    total += count;
                    if (total > maxBytes) throw new IllegalArgumentException("Image must be under 10 MB");
                    out.write(buffer, 0, count);
                }
                out.getFD().sync();
            }
            if (!validator.valid(staged)) throw new IllegalArgumentException("Not a readable image");
            Files.move(staged.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (staged.exists()) staged.delete();
        }
    }
}
