package org.adagide.mac.build;

import java.io.File;

/**
 * One message emitted by the GNAT toolchain, tied both to a place in a source file and to the
 * line of the output pane that showed it.
 */
public record Diagnostic(File file, int line, int column, Severity severity, String message, int outputLine) {

    /** How serious the compiler considered the message. */
    public enum Severity { ERROR, WARNING, STYLE, INFO }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    @Override
    public String toString() {
        return file.getName() + ":" + line + ":" + column + ": " + message;
    }
}
