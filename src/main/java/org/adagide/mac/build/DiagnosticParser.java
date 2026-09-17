package org.adagide.mac.build;

import java.io.File;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a line of GNAT output into a {@link Diagnostic}.
 *
 * <p>GNAT reports positions as {@code unit.adb:12:7: message}; the column is sometimes missing,
 * and the message may carry a {@code warning:}, {@code info:} or {@code (style)} prefix. Anything
 * that does not look like a position is not a diagnostic and is left alone.</p>
 */
public final class DiagnosticParser {

    private static final Pattern LOCATION =
        Pattern.compile("^\\s*(?<file>[^\\s:][^:]*):(?<line>\\d+)(?::(?<column>\\d+))?:\\s*(?<message>.*)$");

    private static final Set<String> SOURCE_EXTENSIONS =
        Set.of("adb", "ads", "ada", "adt", "gpr", "c", "h", "cpp", "s");

    private DiagnosticParser() {
    }

    /**
     * @param line       one line of compiler output
     * @param directory  the directory the command ran in, used to resolve relative file names
     * @param outputLine the index of this line inside the output pane
     * @return the diagnostic, or {@code null} when the line carries no source position
     */
    public static Diagnostic parse(String line, File directory, int outputLine) {
        if (line == null || line.isBlank()) {
            return null;
        }
        Matcher matcher = LOCATION.matcher(line);
        if (!matcher.matches()) {
            return null;
        }

        String name = matcher.group("file").trim();
        if (!looksLikeSource(name)) {
            return null;
        }

        int row;
        int column;
        try {
            row = Integer.parseInt(matcher.group("line"));
            column = matcher.group("column") == null ? 1 : Integer.parseInt(matcher.group("column"));
        } catch (NumberFormatException e) {
            return null;
        }

        String message = matcher.group("message").trim();
        return new Diagnostic(resolve(name, directory), Math.max(1, row), Math.max(1, column),
                              severityOf(message), message, outputLine);
    }

    private static boolean looksLikeSource(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return false;
        }
        return SOURCE_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static File resolve(String name, File directory) {
        File file = new File(name);
        if (file.isAbsolute() || directory == null) {
            return file;
        }
        return new File(directory, name);
    }

    private static Diagnostic.Severity severityOf(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.startsWith("(style)")) {
            return Diagnostic.Severity.STYLE;
        }
        if (lower.startsWith("warning:")) {
            return Diagnostic.Severity.WARNING;
        }
        if (lower.startsWith("info:") || lower.startsWith("note:")) {
            return Diagnostic.Severity.INFO;
        }
        return Diagnostic.Severity.ERROR;
    }
}
