package org.adagide.mac.ui;

import java.util.Set;

import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

/** A styled document that colours Ada source as it is typed. */
public class AdaSyntaxDocument extends DefaultStyledDocument {

    /** The 73 reserved words of Ada 2012, matched without regard to case. */
    private static final Set<String> KEYWORDS = Set.of(
        "abort", "abs", "abstract", "accept", "access", "aliased", "all", "and", "array", "at",
        "begin", "body", "case", "constant", "declare", "delay", "delta", "digits", "do", "else",
        "elsif", "end", "entry", "exception", "exit", "for", "function", "generic", "goto", "if",
        "in", "interface", "is", "limited", "loop", "mod", "new", "not", "null", "of",
        "or", "others", "out", "overriding", "package", "pragma", "private", "procedure", "protected", "raise",
        "range", "record", "rem", "renames", "requeue", "return", "reverse", "select", "separate", "some",
        "subtype", "synchronized", "tagged", "task", "terminate", "then", "type", "until", "use", "when",
        "while", "with", "xor");

    private final MutableAttributeSet plainStyle = new SimpleAttributeSet();
    private final MutableAttributeSet keywordStyle = new SimpleAttributeSet();
    private final MutableAttributeSet commentStyle = new SimpleAttributeSet();
    private final MutableAttributeSet stringStyle = new SimpleAttributeSet();
    private final MutableAttributeSet numberStyle = new SimpleAttributeSet();
    private final MutableAttributeSet attributeStyle = new SimpleAttributeSet();

    private boolean highlightingEnabled = true;

    public AdaSyntaxDocument(Theme theme) {
        applyTheme(theme);
    }

    public final void applyTheme(Theme theme) {
        StyleConstants.setForeground(plainStyle, theme.foreground);
        StyleConstants.setBold(plainStyle, false);

        StyleConstants.setForeground(keywordStyle, theme.keyword);
        StyleConstants.setBold(keywordStyle, true);

        StyleConstants.setForeground(commentStyle, theme.comment);
        StyleConstants.setItalic(commentStyle, true);

        StyleConstants.setForeground(stringStyle, theme.string);
        StyleConstants.setForeground(numberStyle, theme.number);
        StyleConstants.setForeground(attributeStyle, theme.attribute);

        rehighlightAll();
    }

    /** Syntax colouring is turned off for non-Ada files such as project or text files. */
    public void setHighlightingEnabled(boolean enabled) {
        highlightingEnabled = enabled;
        rehighlightAll();
    }

    @Override
    public void insertString(int offset, String text, AttributeSet attributes) throws BadLocationException {
        super.insertString(offset, text, attributes);
        scheduleHighlight(offset, text.length());
    }

    @Override
    public void remove(int offset, int length) throws BadLocationException {
        super.remove(offset, length);
        scheduleHighlight(offset, 0);
    }

    public void rehighlightAll() {
        scheduleHighlight(0, getLength());
    }

    /** Colouring runs after the edit so the document lock is never held while styles change. */
    private void scheduleHighlight(int offset, int length) {
        SwingUtilities.invokeLater(() -> {
            Element root = getDefaultRootElement();
            int firstLine = root.getElementIndex(Math.max(0, Math.min(offset, getLength())));
            int lastLine = root.getElementIndex(Math.max(0, Math.min(offset + length, getLength())));
            int start = root.getElement(firstLine).getStartOffset();
            int end = Math.min(root.getElement(lastLine).getEndOffset(), getLength());
            highlight(start, end);
        });
    }

    private void highlight(int start, int end) {
        if (end <= start) {
            return;
        }
        String text;
        try {
            text = getText(start, end - start);
        } catch (BadLocationException e) {
            return;
        }
        setCharacterAttributes(start, end - start, plainStyle, true);
        if (!highlightingEnabled) {
            return;
        }

        int index = 0;
        while (index < text.length()) {
            char current = text.charAt(index);

            if (current == '-' && index + 1 < text.length() && text.charAt(index + 1) == '-') {
                int stop = text.indexOf('\n', index);
                int commentEnd = stop < 0 ? text.length() : stop;
                setCharacterAttributes(start + index, commentEnd - index, commentStyle, true);
                index = commentEnd;
            } else if (current == '"') {
                int cursor = index + 1;
                while (cursor < text.length() && text.charAt(cursor) != '\n') {
                    if (text.charAt(cursor) == '"') {
                        // "" inside a string literal is an escaped quote.
                        if (cursor + 1 < text.length() && text.charAt(cursor + 1) == '"') {
                            cursor++;
                        } else {
                            cursor++;
                            break;
                        }
                    }
                    cursor++;
                }
                setCharacterAttributes(start + index, cursor - index, stringStyle, true);
                index = cursor;
            } else if (current == '\'' && index + 2 < text.length() && text.charAt(index + 2) == '\''
                       && text.charAt(index + 1) != '\n') {
                setCharacterAttributes(start + index, 3, stringStyle, true);
                index += 3;
            } else if (current == '\'' ) {
                // An attribute tick: 'Range, 'Last, 'Image ...
                int cursor = index + 1;
                while (cursor < text.length() && (Character.isLetterOrDigit(text.charAt(cursor))
                                                  || text.charAt(cursor) == '_')) {
                    cursor++;
                }
                setCharacterAttributes(start + index, cursor - index, attributeStyle, true);
                index = cursor;
            } else if (Character.isDigit(current)) {
                int cursor = index;
                while (cursor < text.length() && isNumberPart(text.charAt(cursor))) {
                    cursor++;
                }
                setCharacterAttributes(start + index, cursor - index, numberStyle, true);
                index = cursor;
            } else if (Character.isLetter(current)) {
                int cursor = index;
                while (cursor < text.length() && (Character.isLetterOrDigit(text.charAt(cursor))
                                                  || text.charAt(cursor) == '_')) {
                    cursor++;
                }
                String word = text.substring(index, cursor);
                if (KEYWORDS.contains(word.toLowerCase())) {
                    setCharacterAttributes(start + index, cursor - index, keywordStyle, true);
                }
                index = cursor;
            } else {
                index++;
            }
        }
    }

    /** Covers decimal, based (16#FF#) and exponent forms. */
    private static boolean isNumberPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '#' || c == '.';
    }

    public static boolean isKeyword(String word) {
        return KEYWORDS.contains(word.toLowerCase());
    }
}
