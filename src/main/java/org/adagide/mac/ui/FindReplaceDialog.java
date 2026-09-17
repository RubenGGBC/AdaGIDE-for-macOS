package org.adagide.mac.ui;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.text.JTextComponent;

/** Find and Replace, kept open while the user works, like AdaGIDE's search dialog. */
public class FindReplaceDialog extends JDialog {

    private final Supplier<EditorPane> editorSupplier;
    private final JTextField findField = new JTextField(24);
    private final JTextField replaceField = new JTextField(24);
    private final JCheckBox matchCase = new JCheckBox("Match case");
    private final JCheckBox wholeWord = new JCheckBox("Whole word");
    private final JCheckBox wrapAround = new JCheckBox("Wrap around", true);
    private final JCheckBox regularExpression = new JCheckBox("Regular expression");
    private final JLabel status = new JLabel(" ");

    public FindReplaceDialog(java.awt.Frame owner, Supplier<EditorPane> editorSupplier) {
        super(owner, "Find and Replace", false);
        this.editorSupplier = editorSupplier;

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.anchor = GridBagConstraints.LINE_START;

        constraints.gridx = 0;
        constraints.gridy = 0;
        fields.add(new JLabel("Find:", SwingConstants.RIGHT), constraints);
        constraints.gridx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        fields.add(findField, constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 0;
        fields.add(new JLabel("Replace with:", SwingConstants.RIGHT), constraints);
        constraints.gridx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        fields.add(replaceField, constraints);

        JPanel options = new JPanel();
        options.add(matchCase);
        options.add(wholeWord);
        options.add(wrapAround);
        options.add(regularExpression);
        constraints.gridx = 1;
        constraints.gridy = 2;
        fields.add(options, constraints);

        JButton findNext = new JButton("Find Next");
        JButton findPrevious = new JButton("Find Previous");
        JButton replace = new JButton("Replace");
        JButton replaceAll = new JButton("Replace All");
        JButton close = new JButton("Close");

        findNext.addActionListener(e -> find(true));
        findPrevious.addActionListener(e -> find(false));
        replace.addActionListener(e -> replaceCurrent());
        replaceAll.addActionListener(e -> replaceAll());
        close.addActionListener(e -> setVisible(false));

        JPanel buttons = new JPanel();
        buttons.add(findPrevious);
        buttons.add(findNext);
        buttons.add(replace);
        buttons.add(replaceAll);
        buttons.add(close);

        status.setBorder(BorderFactory.createEmptyBorder(0, 16, 8, 16));

        JPanel south = new JPanel(new BorderLayout());
        south.add(buttons, BorderLayout.CENTER);
        south.add(status, BorderLayout.SOUTH);

        add(fields, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(findNext);
        pack();
        setLocationRelativeTo(owner);
    }

    public void showFor(String selection) {
        if (selection != null && !selection.isBlank() && !selection.contains("\n")) {
            findField.setText(selection);
        }
        status.setText(" ");
        setVisible(true);
        toFront();
        findField.requestFocusInWindow();
        findField.selectAll();
    }

    private void find(boolean forward) {
        EditorPane editor = editorSupplier.get();
        if (editor == null || findField.getText().isEmpty()) {
            return;
        }
        JTextComponent text = editor.textPane();
        String content = text.getText();
        Pattern pattern = compile();
        if (pattern == null) {
            return;
        }
        Matcher matcher = pattern.matcher(content);

        int caret = forward ? text.getSelectionEnd() : text.getSelectionStart();
        Integer start = null;
        Integer end = null;

        if (forward) {
            if (matcher.find(caret)) {
                start = matcher.start();
                end = matcher.end();
            } else if (wrapAround.isSelected() && matcher.find(0)) {
                start = matcher.start();
                end = matcher.end();
            }
        } else {
            matcher.reset();
            while (matcher.find()) {
                if (matcher.end() <= caret) {
                    start = matcher.start();
                    end = matcher.end();
                } else if (start != null) {
                    break;
                }
            }
            if (start == null && wrapAround.isSelected()) {
                matcher.reset();
                while (matcher.find()) {
                    start = matcher.start();
                    end = matcher.end();
                }
            }
        }

        if (start == null) {
            status.setText("Not found: " + findField.getText());
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        status.setText(" ");
        text.select(start, end);
        text.requestFocusInWindow();
    }

    private void replaceCurrent() {
        EditorPane editor = editorSupplier.get();
        if (editor == null) {
            return;
        }
        JTextComponent text = editor.textPane();
        Pattern pattern = compile();
        String selected = text.getSelectedText();
        if (pattern != null && selected != null && pattern.matcher(selected).matches()) {
            text.replaceSelection(replaceField.getText());
        }
        find(true);
    }

    private void replaceAll() {
        EditorPane editor = editorSupplier.get();
        Pattern pattern = compile();
        if (editor == null || pattern == null) {
            return;
        }
        JTextComponent text = editor.textPane();
        Matcher matcher = pattern.matcher(text.getText());
        String replacement = regularExpression.isSelected()
            ? replaceField.getText()
            : Matcher.quoteReplacement(replaceField.getText());

        StringBuilder result = new StringBuilder();
        int count = 0;
        while (matcher.find()) {
            matcher.appendReplacement(result, replacement);
            count++;
        }
        matcher.appendTail(result);

        if (count > 0) {
            int caret = text.getCaretPosition();
            text.setText(result.toString());
            text.setCaretPosition(Math.min(caret, text.getDocument().getLength()));
        }
        status.setText(count + (count == 1 ? " replacement" : " replacements"));
    }

    private Pattern compile() {
        String needle = findField.getText();
        String expression = regularExpression.isSelected() ? needle : Pattern.quote(needle);
        if (wholeWord.isSelected()) {
            expression = "\\b(?:" + expression + ")\\b";
        }
        int flags = matchCase.isSelected() ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        try {
            return Pattern.compile(expression, flags);
        } catch (PatternSyntaxException e) {
            JOptionPane.showMessageDialog(this, "Invalid regular expression:\n" + e.getDescription(),
                                          "Find", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }
}
