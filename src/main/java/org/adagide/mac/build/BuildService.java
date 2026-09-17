package org.adagide.mac.build;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.adagide.mac.core.Settings;

/**
 * Builds the GNAT command lines behind the Compile menu and runs one of them at a time.
 *
 * <ul>
 *   <li>Check Syntax  — {@code gcc -c -gnatc}</li>
 *   <li>Compile Unit  — {@code gcc -c -g}</li>
 *   <li>Build         — {@code gnatmake -g}, or {@code gprbuild -P} when the folder holds a project file</li>
 *   <li>Clean         — {@code gnatclean}, or {@code gprclean -P}</li>
 * </ul>
 */
public class BuildService {

    /** What a command is for; drives the status line and whether program input is accepted. */
    public enum Kind { CHECK, COMPILE, BUILD, RUN, CLEAN }

    /** One ready-to-run command line. */
    public record Command(Kind kind, String description, List<String> arguments, File directory) {
    }

    private volatile ExternalProcess current;

    // ------------------------------------------------------------- commands

    public Command checkSyntax(File source) {
        List<String> arguments = new ArrayList<>(List.of(GnatToolchain.pathOf("gcc"), "-c", "-gnatc"));
        arguments.addAll(userSwitches());
        arguments.add(source.getName());
        return new Command(Kind.CHECK, "Check syntax of " + source.getName(), arguments, directoryOf(source));
    }

    public Command compile(File source) {
        List<String> arguments = new ArrayList<>(List.of(GnatToolchain.pathOf("gcc"), "-c", "-g"));
        arguments.addAll(userSwitches());
        arguments.add(source.getName());
        return new Command(Kind.COMPILE, "Compile " + source.getName(), arguments, directoryOf(source));
    }

    public Command build(File source) {
        File directory = directoryOf(source);
        File project = projectFile(directory);

        List<String> arguments = new ArrayList<>();
        String description;
        if (project != null && GnatToolchain.isAvailable("gprbuild")) {
            arguments.add(GnatToolchain.pathOf("gprbuild"));
            arguments.add("-P");
            arguments.add(project.getName());
            List<String> switches = userSwitches();
            if (!switches.isEmpty()) {
                arguments.add("-cargs");
                arguments.addAll(switches);
            }
            description = "Build project " + project.getName();
        } else {
            arguments.add(GnatToolchain.pathOf("gnatmake"));
            arguments.add("-g");
            arguments.addAll(userSwitches());
            arguments.add(source.getName());
            description = "Build " + source.getName();
        }
        return new Command(Kind.BUILD, description, arguments, directory);
    }

    public Command run(File source) {
        File executable = executableFor(source);
        return new Command(Kind.RUN, "Run " + executable.getName(),
                           List.of(executable.getAbsolutePath()), directoryOf(source));
    }

    /** Hands the program to Terminal.app instead of running it inside the output panel. */
    public Command runInTerminal(File source) {
        File executable = executableFor(source);
        File directory = directoryOf(source);
        String script = "cd " + quoteForShell(directory.getAbsolutePath())
                        + " && " + quoteForShell(executable.getAbsolutePath());
        List<String> arguments = List.of(
            "/usr/bin/osascript",
            "-e", "tell application \"Terminal\" to do script " + quoteForAppleScript(script),
            "-e", "tell application \"Terminal\" to activate");
        return new Command(Kind.RUN, "Run " + executable.getName() + " in Terminal", arguments, directory);
    }

    public Command clean(File source) {
        File directory = directoryOf(source);
        File project = projectFile(directory);

        List<String> arguments = new ArrayList<>();
        String description;
        if (project != null && GnatToolchain.isAvailable("gprclean")) {
            arguments.add(GnatToolchain.pathOf("gprclean"));
            arguments.add("-P");
            arguments.add(project.getName());
            description = "Clean project " + project.getName();
        } else {
            arguments.add(GnatToolchain.pathOf("gnatclean"));
            arguments.add(source.getName());
            description = "Clean " + source.getName();
        }
        return new Command(Kind.CLEAN, description, arguments, directory);
    }

    // ------------------------------------------------------------ execution

    /** Runs {@code command}, reporting every output line and the exit code to {@code listener}. */
    public void execute(Command command, ExternalProcess.Listener listener) throws IOException {
        if (isBusy()) {
            throw new IOException("another command is still running");
        }
        current = ExternalProcess.start(command.arguments(), command.directory(), toolchainEnvironment(),
                                        new ExternalProcess.Listener() {
            @Override
            public void onLine(String line) {
                listener.onLine(line);
            }

            @Override
            public void onFinished(int exitCode) {
                current = null;
                listener.onFinished(exitCode);
            }
        });
    }

    /** Puts the GNAT directories on PATH so the tools can find each other. */
    private static java.util.Map<String, String> toolchainEnvironment() {
        return java.util.Map.of("PATH", GnatToolchain.augmentedPath());
    }

    public boolean isBusy() {
        ExternalProcess process = current;
        return process != null && process.isAlive();
    }

    /** Stops whatever is running; harmless when nothing is. */
    public void stop() {
        ExternalProcess process = current;
        if (process != null) {
            process.stop();
        }
    }

    /** Feeds a line to the running program's standard input. */
    public void sendInput(String text) {
        ExternalProcess process = current;
        if (process != null) {
            process.send(text);
        }
    }

    // --------------------------------------------------------------- paths

    /** The executable gnatmake produces for a source file: same folder, name without the extension. */
    public static File executableFor(File source) {
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return new File(directoryOf(source), base);
    }

    /** The first {@code .gpr} project file in the folder, or {@code null}. */
    public static File projectFile(File directory) {
        File[] projects = directory == null ? null
            : directory.listFiles(file -> file.isFile() && file.getName().toLowerCase().endsWith(".gpr"));
        if (projects == null || projects.length == 0) {
            return null;
        }
        Arrays.sort(projects);
        return projects[0];
    }

    private static File directoryOf(File source) {
        File parent = source.getAbsoluteFile().getParentFile();
        return parent != null ? parent : new File(System.getProperty("user.home"));
    }

    private static List<String> userSwitches() {
        String switches = Settings.compilerSwitches();
        if (switches == null || switches.isBlank()) {
            return List.of();
        }
        return Arrays.stream(switches.trim().split("\\s+")).filter(s -> !s.isBlank()).toList();
    }

    private static String quoteForShell(String text) {
        return "'" + text.replace("'", "'\\''") + "'";
    }

    private static String quoteForAppleScript(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
