package org.adagide.mac.build;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.adagide.mac.core.Settings;

/**
 * Locates the GNAT tools on this Mac.
 *
 * <p>The directory configured in Preferences wins, then {@code PATH}, then the usual Homebrew,
 * Alire and standalone GNAT locations. Results are cached until {@link #invalidate()} is called,
 * which Preferences does whenever the configured directory changes.</p>
 */
public final class GnatToolchain {

    /** The tools AdaGIDE drives, in the order the information dialog lists them. */
    public static final List<String> TOOLS =
        List.of("gnatmake", "gcc", "gprbuild", "gnatclean", "gprclean", "gnatls", "gdb");

    private static final List<String> FALLBACK_DIRECTORIES =
        List.of("/opt/homebrew/bin", "/usr/local/bin", "/opt/gnat/bin", "/opt/local/bin", "/usr/bin");

    private static final Map<String, File> CACHE = new LinkedHashMap<>();

    private GnatToolchain() {
    }

    /** Forgets everything that was found, so the next lookup searches again. */
    public static synchronized void invalidate() {
        CACHE.clear();
    }

    /** @return the executable, or {@code null} when it is not installed anywhere we look. */
    public static synchronized File find(String tool) {
        if (CACHE.containsKey(tool)) {
            return CACHE.get(tool);
        }
        File found = locate(tool);
        CACHE.put(tool, found);
        return found;
    }

    public static boolean isAvailable(String tool) {
        return find(tool) != null;
    }

    /** @return the absolute path of {@code tool}, falling back to the bare name for the PATH to resolve. */
    public static String pathOf(String tool) {
        File file = find(tool);
        return file != null ? file.getAbsolutePath() : tool;
    }

    /** The directories searched, in order. */
    public static List<String> searchPath() {
        Set<String> directories = new LinkedHashSet<>();

        String configured = Settings.gnatDirectory();
        if (configured != null && !configured.isBlank()) {
            directories.add(new File(configured.trim()).getAbsolutePath());
        }

        String path = System.getenv("PATH");
        if (path != null) {
            for (String entry : path.split(File.pathSeparator)) {
                if (!entry.isBlank()) {
                    directories.add(new File(entry).getAbsolutePath());
                }
            }
        }

        directories.addAll(FALLBACK_DIRECTORIES);
        directories.addAll(alireDirectories());
        return List.copyOf(directories);
    }

    /** The bin directories of every toolchain Alire has installed. */
    private static List<String> alireDirectories() {
        List<String> result = new ArrayList<>();
        String home = System.getProperty("user.home");
        List<File> roots = List.of(
            new File(home, ".local/share/alire/toolchains"),
            new File(home, "Library/Application Support/alire/toolchains"),
            new File(home, ".alire/toolchains"),
            new File(home, ".config/alire/toolchains"));

        for (File root : roots) {
            File[] children = root.listFiles(File::isDirectory);
            if (children == null) {
                continue;
            }
            for (File child : children) {
                File bin = new File(child, "bin");
                if (bin.isDirectory()) {
                    result.add(bin.getAbsolutePath());
                }
            }
        }
        return result;
    }

    private static File locate(String tool) {
        // The Ada-capable gcc lives next to gnatmake; /usr/bin/gcc on macOS is Apple's clang and
        // cannot compile Ada, so for gcc we look beside gnatmake first and skip /usr/bin.
        if ("gcc".equals(tool)) {
            File gnatmake = find("gnatmake");
            if (gnatmake != null) {
                File sibling = executable(gnatmake.getParentFile(), "gcc");
                if (sibling != null) {
                    return sibling;
                }
                sibling = executable(gnatmake.getParentFile(), "gnatgcc");
                if (sibling != null) {
                    return sibling;
                }
            }
            File gnatgcc = scan("gnatgcc", true);
            return gnatgcc != null ? gnatgcc : scan("gcc", true);
        }
        return scan(tool, false);
    }

    private static File scan(String tool, boolean skipUsrBin) {
        for (String directory : searchPath()) {
            if (skipUsrBin && "/usr/bin".equals(directory)) {
                continue;
            }
            File candidate = executable(new File(directory), tool);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static File executable(File directory, String tool) {
        if (directory == null) {
            return null;
        }
        File candidate = new File(directory, tool);
        return candidate.isFile() && candidate.canExecute() ? candidate : null;
    }

    /** The bin directories the tools actually live in, deduplicated, in lookup order. */
    public static List<String> binDirectories() {
        Set<String> directories = new LinkedHashSet<>();
        for (String tool : TOOLS) {
            File file = find(tool);
            if (file != null && file.getParentFile() != null) {
                directories.add(file.getParentFile().getAbsolutePath());
            }
        }
        return List.copyOf(directories);
    }

    /**
     * {@code PATH} with the toolchain's own directories in front.
     *
     * <p>gprbuild locates the Ada compiler through {@code PATH}, and Alire installs gprbuild and
     * GNAT in separate directories, so without this gprbuild reports "no compiler for language
     * Ada" even though gcc is right there.</p>
     */
    public static String augmentedPath() {
        StringBuilder path = new StringBuilder(String.join(File.pathSeparator, binDirectories()));
        String inherited = System.getenv("PATH");
        if (inherited != null && !inherited.isBlank()) {
            if (path.length() > 0) {
                path.append(File.pathSeparator);
            }
            path.append(inherited);
        }
        return path.toString();
    }

    /** A human-readable report of what was found, shown by Tools ▸ GNAT Information. */
    public static String describe() {
        StringBuilder text = new StringBuilder("GNAT tools\n\n");
        for (String tool : TOOLS) {
            File file = find(tool);
            text.append(String.format("  %-10s %s%n", tool, file != null ? file.getAbsolutePath() : "not found"));
        }

        File gnatmake = find("gnatmake");
        if (gnatmake == null) {
            text.append("\nNo GNAT compiler found. Install one, for example:\n")
                .append("    brew install gnat\n")
                .append("    alr toolchain --select      (Alire, https://alire.ada.dev)\n")
                .append("then point Preferences at the directory holding gnatmake.\n");
        } else {
            text.append('\n').append(version(gnatmake)).append('\n');
        }
        return text.toString();
    }

    private static String version(File gnatmake) {
        try {
            Process process = new ProcessBuilder(gnatmake.getAbsolutePath(), "--version")
                .redirectErrorStream(true)
                .start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor();
            return output.lines().findFirst().orElse("(no version reported)");
        } catch (Exception e) {
            return "(could not run " + gnatmake.getName() + ": " + e.getMessage() + ")";
        }
    }
}
