package org.adagide.mac.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** The "Open Recent" list, capped at {@value #MAX} entries. */
public final class RecentFiles {

    public static final int MAX = 10;

    private RecentFiles() {
    }

    public static List<File> list() {
        List<File> files = new ArrayList<>();
        for (String path : Settings.recentFiles().split("\n")) {
            if (!path.isBlank()) {
                File file = new File(path);
                if (file.isFile()) {
                    files.add(file);
                }
            }
        }
        return files;
    }

    public static void add(File file) {
        List<File> files = list();
        files.removeIf(existing -> existing.getAbsolutePath().equals(file.getAbsolutePath()));
        files.add(0, file.getAbsoluteFile());
        while (files.size() > MAX) {
            files.remove(files.size() - 1);
        }
        store(files);
    }

    public static void clear() {
        store(List.of());
    }

    private static void store(List<File> files) {
        StringBuilder builder = new StringBuilder();
        for (File file : files) {
            builder.append(file.getAbsolutePath()).append('\n');
        }
        Settings.setRecentFiles(builder.toString());
    }
}
