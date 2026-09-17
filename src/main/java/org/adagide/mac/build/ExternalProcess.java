package org.adagide.mac.build;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * A child process whose merged stdout/stderr is streamed back line by line, and whose stdin stays
 * open so a running Ada program can be fed from the output panel.
 */
public final class ExternalProcess {

    /** Receives the process output. Both callbacks run on the reader thread. */
    public interface Listener {
        void onLine(String line);

        void onFinished(int exitCode);
    }

    private final Process process;
    private final Writer stdin;

    private ExternalProcess(Process process) {
        this.process = process;
        this.stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
    }

    /** Starts {@code command} in {@code directory} and pumps its output to {@code listener}. */
    public static ExternalProcess start(List<String> command, File directory, Listener listener) throws IOException {
        return start(command, directory, Map.of(), listener);
    }

    /** As above, with {@code environment} overlaid on the inherited one. */
    public static ExternalProcess start(List<String> command, File directory, Map<String, String> environment,
                                        Listener listener) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (directory != null && directory.isDirectory()) {
            builder.directory(directory);
        }
        builder.environment().putAll(environment);
        builder.redirectErrorStream(true);

        ExternalProcess external = new ExternalProcess(builder.start());
        Thread pump = new Thread(() -> external.pump(listener), "adagide-process");
        pump.setDaemon(true);
        pump.start();
        return external;
    }

    private void pump(Listener listener) {
        try (BufferedReader reader =
                 new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                listener.onLine(line);
            }
        } catch (IOException e) {
            listener.onLine("adagide: " + e.getMessage());
        }
        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            exitCode = -1;
        }
        closeQuietly();
        listener.onFinished(exitCode);
    }

    /** Writes a line to the process' standard input. */
    public void send(String text) {
        if (!process.isAlive()) {
            return;
        }
        try {
            stdin.write(text);
            stdin.write(System.lineSeparator());
            stdin.flush();
        } catch (IOException e) {
            // The program closed its input; nothing to do.
        }
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    /** Asks the process to quit, then kills it if it will not. */
    public void stop() {
        if (!process.isAlive()) {
            return;
        }
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private void closeQuietly() {
        try {
            stdin.close();
        } catch (IOException e) {
            // Already gone.
        }
    }
}
