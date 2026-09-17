package org.adagide.mac.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;

import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyleConstants;

import org.junit.jupiter.api.Test;

class AdaSyntaxDocumentTest {

    @Test
    void recognisesReservedWordsWithoutRegardToCase() {
        assertTrue(AdaSyntaxDocument.isKeyword("procedure"));
        assertTrue(AdaSyntaxDocument.isKeyword("PROCEDURE"));
        assertTrue(AdaSyntaxDocument.isKeyword("Overriding"));
        assertFalse(AdaSyntaxDocument.isKeyword("Put_Line"));
    }

    @Test
    void coloursKeywordsAndComments() throws BadLocationException, InterruptedException, InvocationTargetException {
        Theme theme = Theme.current();
        AdaSyntaxDocument document = new AdaSyntaxDocument(theme);

        document.insertString(0, "procedure Main is -- start\n", null);
        flushEventQueue();

        assertEquals(theme.keyword, StyleConstants.getForeground(document.getCharacterElement(0).getAttributes()));
        assertEquals(theme.comment, StyleConstants.getForeground(document.getCharacterElement(20).getAttributes()));
    }

    @Test
    void highlightingCanBeTurnedOffForNonAdaFiles()
            throws BadLocationException, InterruptedException, InvocationTargetException {
        Theme theme = Theme.current();
        AdaSyntaxDocument document = new AdaSyntaxDocument(theme);
        document.insertString(0, "package Sample is\n", null);
        flushEventQueue();

        document.setHighlightingEnabled(false);
        flushEventQueue();

        assertEquals(theme.foreground, StyleConstants.getForeground(document.getCharacterElement(0).getAttributes()));
    }

    /** Highlighting is posted to the event queue, so tests wait for it to drain. */
    private static void flushEventQueue() throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
    }
}
