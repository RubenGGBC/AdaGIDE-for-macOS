package org.adagide.mac.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

import org.adagide.mac.build.Diagnostic;
import org.adagide.mac.core.Settings;

/** One open file: the text pane, its gutter, and the file state around them. */
public class EditorPane extends JPanel {

    /** Words that open a block, used by the auto-indent. */
    private static final List<String> BLOCK_OPENERS =
        List.of("is", "begin", "loop", "then", "else", "declare", "record", "do", "select", "generic");

    private final JTextPane textPane;
    private final AdaSyntaxDocument document;
    private final LineNumberGutter gutter;
    private final JScrollPane scrollPane;
    private final UndoManager undo = new UndoManager();

    private File file;
    private boolean modified;
    private Theme theme;
    private Runnable stateListener = () -> { };

    public EditorPane(Theme theme) {
        super(new BorderLayout());
        this.theme = theme;
        this.document = new AdaSyntaxDocument(theme);

        textPane = new JTextPane(document) {
            @Override
            protected void paintComponent(Graphics g) {
                paintCurrentLine(g);
                super.paintComponent(g);
            }
        };
        textPane.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 6, 4, 6));

        JPanel noWrap = new JPanel(new BorderLayout());
        noWrap.add(textPane, BorderLayout.CENTER);

        gutter = new LineNumberGutter(textPane, theme);
        scrollPane = new JScrollPane(noWrap);
        scrollPane.setRowHeaderView(gutter);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        applyTheme(theme);
        applySettings();
        installUndo();
        installKeyBindings();

        document.addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                markModified();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                markModified();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // Attribute-only change from the highlighter; not a user edit.
            }
        });
        textPane.addCaretListener(e -> {
            stateListener.run();
            textPane.repaint();
        });
    }

    // ---------------------------------------------------------------- state

    public JTextPane textPane() {
        return textPane;
    }

    public File file() {
        return file;
    }

    public boolean isModified() {
        return modified;
    }

    public void setStateListener(Runnable listener) {
        this.stateListener = listener == null ? () -> { } : listener;
    }

    public String title() {
        String name = file == null ? "Untitled" : file.getName();
        return modified ? name + " •" : name;
    }

    public int caretLine() {
        Element root = document.getDefaultRootElement();
        return root.getElementIndex(textPane.getCaretPosition()) + 1;
    }

    public int caretColumn() {
        Element root = document.getDefaultRootElement();
        int line = root.getElementIndex(textPane.getCaretPosition());
        return textPane.getCaretPosition() - root.getElement(line).getStartOffset() + 1;
    }

    private void markModified() {
        if (!modified) {
            modified = true;
        }
        stateListener.run();
    }

    // ----------------------------------------------------------- file input

    public void load(File source) throws IOException {
        String content = Files.readString(source.toPath(), StandardCharsets.UTF_8);
        textPane.setText(content);
        textPane.setCaretPosition(0);
        this.file = source;
        this.modified = false;
        undo.discardAllEdits();
        document.setHighlightingEnabled(isAdaFile(source));
        document.rehighlightAll();
        stateListener.run();
    }

    public void save(File target) throws IOException {
        Files.writeString(target.toPath(), textPane.getText(), StandardCharsets.UTF_8);
        this.file = target;
        this.modified = false;
        document.setHighlightingEnabled(isAdaFile(target));
        stateListener.run();
    }

    public void setContent(String content, File suggestedFile) {
        textPane.setText(content);
        textPane.setCaretPosition(0);
        this.file = suggestedFile;
        // A freshly created document counts as unmodified until the user types in it.
        this.modified = false;
        undo.discardAllEdits();
        document.setHighlightingEnabled(suggestedFile == null || isAdaFile(suggestedFile));
        stateListener.run();
    }

    public static boolean isAdaFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".adb") || name.endsWith(".ads") || name.endsWith(".ada");
    }

    // ------------------------------------------------------------ appearance

    public final void applyTheme(Theme theme) {
        this.theme = theme;
        document.applyTheme(theme);
        textPane.setBackground(theme.background);
        textPane.setForeground(theme.foreground);
        textPane.setCaretColor(theme.caret);
        textPane.setSelectionColor(theme.selection);
        textPane.getParent().setBackground(theme.background);
        gutter.applyTheme(theme);
        repaint();
    }

    public final void applySettings() {
        textPane.setFont(new Font(Settings.fontFamily(), Font.PLAIN, Settings.fontSize()));
        gutter.setFont(textPane.getFont());
        scrollPane.setRowHeaderView(Settings.showLineNumbers() ? gutter : null);
        revalidate();
        repaint();
    }

    private void paintCurrentLine(Graphics g) {
        try {
            Rectangle line = textPane.modelToView2D(textPane.getCaretPosition()).getBounds();
            g.setColor(theme.currentLine);
            g.fillRect(0, line.y, textPane.getWidth(), line.height);
        } catch (BadLocationException | NullPointerException e) {
            // No view yet; nothing to highlight.
        }
    }

    // ------------------------------------------------------------- markers

    public void showDiagnostics(List<Diagnostic> diagnostics) {
        Map<Integer, Diagnostic.Severity> markers = new HashMap<>();
        for (Diagnostic diagnostic : diagnostics) {
            if (file != null && diagnostic.file().getAbsolutePath().equals(file.getAbsolutePath())) {
                markers.merge(diagnostic.line(), diagnostic.severity(),
                              (a, b) -> a == Diagnostic.Severity.ERROR ? a : b);
            }
        }
        gutter.setMarkers(markers);
    }

    public void clearDiagnostics() {
        gutter.clearMarkers();
    }

    // -------------------------------------------------------------- editing

    public void goTo(int line, int column) {
        Element root = document.getDefaultRootElement();
        int index = Math.max(0, Math.min(line - 1, root.getElementCount() - 1));
        Element element = root.getElement(index);
        int offset = element.getStartOffset() + Math.max(0, column - 1);
        offset = Math.min(offset, element.getEndOffset() - 1);
        textPane.setCaretPosition(offset);
        try {
            Rectangle view = textPane.modelToView2D(offset).getBounds();
            view.height = Math.max(view.height, 60);
            textPane.scrollRectToVisible(view);
        } catch (BadLocationException e) {
            // Out of view; the caret move above is enough.
        }
        textPane.requestFocusInWindow();
    }

    public void undo() {
        try {
            if (undo.canUndo()) {
                undo.undo();
            }
        } catch (CannotUndoException e) {
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }

    public void redo() {
        try {
            if (undo.canRedo()) {
                undo.redo();
            }
        } catch (CannotRedoException e) {
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }

    /** Comments the selected lines, or uncomments them when they are already commented. */
    public void toggleComment() {
        Element root = document.getDefaultRootElement();
        int startLine = root.getElementIndex(textPane.getSelectionStart());
        int endLine = root.getElementIndex(textPane.getSelectionEnd());
        try {
            boolean allCommented = true;
            for (int line = startLine; line <= endLine; line++) {
                if (!lineText(root, line).stripLeading().startsWith("--")) {
                    allCommented = false;
                    break;
                }
            }
            for (int line = endLine; line >= startLine; line--) {
                Element element = root.getElement(line);
                String content = lineText(root, line);
                int indent = content.length() - content.stripLeading().length();
                if (allCommented) {
                    int at = element.getStartOffset() + indent;
                    int length = content.stripLeading().startsWith("-- ") ? 3 : 2;
                    document.remove(at, Math.min(length, element.getEndOffset() - at - 1));
                } else if (!content.isBlank()) {
                    document.insertString(element.getStartOffset() + indent, "-- ", null);
                }
            }
        } catch (BadLocationException e) {
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }

    private String lineText(Element root, int line) throws BadLocationException {
        Element element = root.getElement(line);
        int start = element.getStartOffset();
        int end = Math.min(element.getEndOffset(), document.getLength());
        return document.getText(start, Math.max(0, end - start)).replace("\n", "");
    }

    private void installUndo() {
        document.addUndoableEditListener(event -> {
            // Ignore the attribute changes produced by the syntax highlighter.
            if (event.getEdit() instanceof AbstractDocument.DefaultDocumentEvent edit
                && edit.getType() == DocumentEvent.EventType.CHANGE) {
                return;
            }
            undo.addEdit(event.getEdit());
        });
        undo.setLimit(500);
    }

    private void installKeyBindings() {
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "adagide-tab", e -> insertIndent());
        bind(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "adagide-enter", e -> insertNewLine());
    }

    private void bind(KeyStroke stroke, String name, java.util.function.Consumer<ActionEvent> action) {
        textPane.getInputMap(JComponent.WHEN_FOCUSED).put(stroke, name);
        textPane.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.accept(e);
            }
        });
    }

    private void insertIndent() {
        try {
            if (Settings.insertSpaces()) {
                document.insertString(textPane.getCaretPosition(), " ".repeat(Settings.tabSize()), null);
            } else {
                document.insertString(textPane.getCaretPosition(), "\t", null);
            }
        } catch (BadLocationException e) {
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }

    private void insertNewLine() {
        try {
            int caret = textPane.getCaretPosition();
            Element root = document.getDefaultRootElement();
            int line = root.getElementIndex(caret);
            String current = lineText(root, line);
            String indent = "";
            if (Settings.autoIndent()) {
                int width = current.length() - current.stripLeading().length();
                indent = current.substring(0, Math.min(width, Math.max(0, caret - root.getElement(line).getStartOffset())));
                if (opensBlock(current)) {
                    indent += Settings.insertSpaces() ? " ".repeat(Settings.tabSize()) : "\t";
                }
            }
            document.insertString(caret, "\n" + indent, null);
        } catch (BadLocationException e) {
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }

    /** True when the line ends with a word that opens an Ada block. */
    private static boolean opensBlock(String line) {
        String code = line;
        int comment = code.indexOf("--");
        if (comment >= 0) {
            code = code.substring(0, comment);
        }
        code = code.stripTrailing().toLowerCase(Locale.ROOT);
        if (code.endsWith("=>") || code.endsWith("(")) {
            return true;
        }
        for (String opener : BLOCK_OPENERS) {
            if (code.endsWith(" " + opener) || code.equals(opener)) {
                return true;
            }
        }
        return false;
    }

    /** Background used while no file is open, so the empty area matches the theme. */
    @Override
    public Color getBackground() {
        return theme == null ? super.getBackground() : theme.background;
    }
}
