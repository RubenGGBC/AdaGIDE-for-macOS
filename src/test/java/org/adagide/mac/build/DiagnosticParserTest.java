package org.adagide.mac.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

class DiagnosticParserTest {

    private static final File DIRECTORY = new File("/tmp/project");

    @Test
    void readsFileLineAndColumn() {
        Diagnostic diagnostic = DiagnosticParser.parse("hello.adb:7:14: missing \";\"", DIRECTORY, 3);
        assertEquals(new File(DIRECTORY, "hello.adb"), diagnostic.file());
        assertEquals(7, diagnostic.line());
        assertEquals(14, diagnostic.column());
        assertEquals(3, diagnostic.outputLine());
        assertTrue(diagnostic.isError());
    }

    @Test
    void defaultsToColumnOneWhenGnatOmitsIt() {
        assertEquals(1, DiagnosticParser.parse("hello.adb:7: file not found", DIRECTORY, 0).column());
    }

    @Test
    void recognisesTheSeverityPrefixes() {
        assertEquals(Diagnostic.Severity.WARNING,
                     DiagnosticParser.parse("a.adb:1:1: warning: variable \"X\" is never read", DIRECTORY, 0).severity());
        assertEquals(Diagnostic.Severity.STYLE,
                     DiagnosticParser.parse("a.adb:1:1: (style) bad indentation", DIRECTORY, 0).severity());
        assertEquals(Diagnostic.Severity.INFO,
                     DiagnosticParser.parse("a.adb:1:1: info: in instantiation at b.ads:4", DIRECTORY, 0).severity());
        assertEquals(Diagnostic.Severity.ERROR,
                     DiagnosticParser.parse("a.adb:1:1: \"Foo\" is undefined", DIRECTORY, 0).severity());
    }

    @Test
    void keepsAbsolutePathsAsTheyAre() {
        assertEquals(new File("/src/hello.adb"),
                     DiagnosticParser.parse("/src/hello.adb:2:3: oops", DIRECTORY, 0).file());
    }

    @Test
    void ignoresLinesThatCarryNoSourcePosition() {
        assertNull(DiagnosticParser.parse("gnatmake -g hello.adb", DIRECTORY, 0));
        assertNull(DiagnosticParser.parse("", DIRECTORY, 0));
        assertNull(DiagnosticParser.parse("\"hello\" up to date.", DIRECTORY, 0));
        assertNull(DiagnosticParser.parse("Elapsed: 00:01:02", DIRECTORY, 0));
    }
}
