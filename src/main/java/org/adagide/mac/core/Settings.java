package org.adagide.mac.core;

import java.io.File;
import java.util.prefs.Preferences;

/** Persistent user settings, stored in the standard macOS preferences domain. */
public final class Settings {

    private static final Preferences NODE = Preferences.userRoot().node("org/adagide/mac");

    private Settings() {
    }

    public static String gnatDirectory() {
        return NODE.get("gnatDirectory", "");
    }

    public static void setGnatDirectory(String dir) {
        NODE.put("gnatDirectory", dir == null ? "" : dir);
    }

    public static String fontFamily() {
        return NODE.get("fontFamily", "Menlo");
    }

    public static void setFontFamily(String family) {
        NODE.put("fontFamily", family);
    }

    public static int fontSize() {
        return NODE.getInt("fontSize", 13);
    }

    public static void setFontSize(int size) {
        NODE.putInt("fontSize", Math.max(8, Math.min(48, size)));
    }

    public static int tabSize() {
        return NODE.getInt("tabSize", 3);
    }

    public static void setTabSize(int size) {
        NODE.putInt("tabSize", Math.max(1, Math.min(16, size)));
    }

    public static boolean insertSpaces() {
        return NODE.getBoolean("insertSpaces", true);
    }

    public static void setInsertSpaces(boolean value) {
        NODE.putBoolean("insertSpaces", value);
    }

    public static boolean showLineNumbers() {
        return NODE.getBoolean("showLineNumbers", true);
    }

    public static void setShowLineNumbers(boolean value) {
        NODE.putBoolean("showLineNumbers", value);
    }

    public static boolean autoIndent() {
        return NODE.getBoolean("autoIndent", true);
    }

    public static void setAutoIndent(boolean value) {
        NODE.putBoolean("autoIndent", value);
    }

    public static boolean saveBeforeBuild() {
        return NODE.getBoolean("saveBeforeBuild", true);
    }

    public static void setSaveBeforeBuild(boolean value) {
        NODE.putBoolean("saveBeforeBuild", value);
    }

    public static boolean runInTerminal() {
        return NODE.getBoolean("runInTerminal", false);
    }

    public static void setRunInTerminal(boolean value) {
        NODE.putBoolean("runInTerminal", value);
    }

    /** Extra switches appended to every gcc/gnatmake invocation. */
    public static String compilerSwitches() {
        return NODE.get("compilerSwitches", "-gnatwa -gnatQ");
    }

    public static void setCompilerSwitches(String switches) {
        NODE.put("compilerSwitches", switches == null ? "" : switches.trim());
    }

    public static String lastDirectory() {
        String dir = NODE.get("lastDirectory", System.getProperty("user.home"));
        return new File(dir).isDirectory() ? dir : System.getProperty("user.home");
    }

    public static void setLastDirectory(String dir) {
        if (dir != null) {
            NODE.put("lastDirectory", dir);
        }
    }

    public static String recentFiles() {
        return NODE.get("recentFiles", "");
    }

    public static void setRecentFiles(String value) {
        NODE.put("recentFiles", value);
    }
}
