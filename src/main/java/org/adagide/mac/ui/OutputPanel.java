package org.adagide.mac.ui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

import org.adagide.mac.build.Diagnostic;

/** The bottom pane: compiler output, program output, and the error list behind it. */
public class OutputPanel extends JPanel {

    /** How a line is coloured in the output pane. */
    public enum LineKind { PLAIN, HEADER, ERROR, WARNING, SUCCESS }

    private final JTextPane output = new JTextPane();
    private final JTextField input = new JTextField();
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    private Theme theme;
    private int lineCount;
    private int currentDiagnostic = -1;
    private Consumer<Diagnostic> diagnosticListener = diagnostic -> { };
    private Consumer<String> inputListener = text -> { };

    public OutputPanel(Theme theme) {
        super(new BorderLayout());
        this.theme = theme;

        output.setEditable(false);
        output.setFont(new Font("Menlo", Font.PLAIN, 12));
        output.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        output.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    activateDiagnosticAt(output.viewToModel2D(event.getPoint()));
                }
            }
        });

        input.setFont(new Font("Menlo", Font.PLAIN, 12));
        input.setBorder(BorderFactory.createTitledBorder("Program input (press Return to send)"));
        input.setEnabled(false);
        input.addActionListener(e -> {
            String text = input.getText();
            input.setText("");
            append("> " + text, LineKind.PLAIN);
            inputListener.accept(text);
        });

        add(new JScrollPane(output), BorderLayout.CENTER);
        add(input, BorderLayout.SOUTH);
        applyTheme(theme);
    }

    public final void applyTheme(Theme theme) {
        this.theme = theme;
        output.setBackground(theme.background);
        output.setForeground(theme.foreground);
        output.setCaretColor(theme.caret);
        repaint();
    }

    public void setDiagnosticListener(Consumer<Diagnostic> listener) {
        this.diagnosticListener = listener;
    }

    public void setInputListener(Consumer<String> listener) {
        this.inputListener = listener;
    }

    public void setInputEnabled(boolean enabled) {
        input.setEnabled(enabled);
        if (enabled) {
            input.requestFocusInWindow();
        }
    }

    public void clear() {
        output.setText("");
        diagnostics.clear();
        lineCount = 0;
        currentDiagnostic = -1;
    }

    public int lineCount() {
        return lineCount;
    }

    public List<Diagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    public long errorCount() {
        return diagnostics.stream().filter(Diagnostic::isError).count();
    }

    public long warningCount() {
        return diagnostics.stream().filter(d -> d.severity() == Diagnostic.Severity.WARNING).count();
    }

    public void addDiagnostic(Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }

    public void append(String text, LineKind kind) {
        SimpleAttributeSet style = new SimpleAttributeSet();
        switch (kind) {
            case HEADER -> {
                StyleConstants.setForeground(style, theme.keyword);
                StyleConstants.setBold(style, true);
            }
            case ERROR -> StyleConstants.setForeground(style, theme.error);
            case WARNING -> StyleConstants.setForeground(style, theme.warning);
            case SUCCESS -> StyleConstants.setForeground(style, theme.comment);
            default -> StyleConstants.setForeground(style, theme.foreground);
        }
        try {
            output.getDocument().insertString(output.getDocument().getLength(), text + "\n", style);
            output.setCaretPosition(output.getDocument().getLength());
            lineCount++;
        } catch (BadLocationException e) {
            // The pane is being replaced; drop the line.
        }
    }

    /** Moves to the next (or previous) compiler message and reports it to the listener. */
    public Diagnostic step(int direction) {
        if (diagnostics.isEmpty()) {
            return null;
        }
        currentDiagnostic += direction;
        if (currentDiagnostic < 0) {
            currentDiagnostic = diagnostics.size() - 1;
        }
        if (currentDiagnostic >= diagnostics.size()) {
            currentDiagnostic = 0;
        }
        Diagnostic diagnostic = diagnostics.get(currentDiagnostic);
        highlightOutputLine(diagnostic.outputLine());
        diagnosticListener.accept(diagnostic);
        return diagnostic;
    }

    private void activateDiagnosticAt(int offset) {
        Element root = output.getDocument().getDefaultRootElement();
        int line = root.getElementIndex(offset);
        for (int index = 0; index < diagnostics.size(); index++) {
            if (diagnostics.get(index).outputLine() == line) {
                currentDiagnostic = index;
                diagnosticListener.accept(diagnostics.get(index));
                return;
            }
        }
    }

    private void highlightOutputLine(int line) {
        Element root = output.getDocument().getDefaultRootElement();
        if (line < 0 || line >= root.getElementCount()) {
            return;
        }
        Element element = root.getElement(line);
        output.setCaretPosition(element.getStartOffset());
        output.select(element.getStartOffset(), Math.max(element.getStartOffset(), element.getEndOffset() - 1));
        output.getCaret().setSelectionVisible(true);
    }
}
