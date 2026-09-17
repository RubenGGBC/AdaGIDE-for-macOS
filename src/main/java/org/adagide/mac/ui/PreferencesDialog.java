package org.adagide.mac.ui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.Arrays;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

import org.adagide.mac.build.GnatToolchain;
import org.adagide.mac.core.Settings;

/** Preferences, reachable from the application menu with the usual Command-comma. */
public class PreferencesDialog extends JDialog {

    private final JTextField gnatDirectory = new JTextField(26);
    private final JComboBox<String> fontFamily = new JComboBox<>();
    private final JSpinner fontSize = new JSpinner(new SpinnerNumberModel(Settings.fontSize(), 8, 48, 1));
    private final JSpinner tabSize = new JSpinner(new SpinnerNumberModel(Settings.tabSize(), 1, 16, 1));
    private final JTextField compilerSwitches = new JTextField(26);
    private final JCheckBox insertSpaces = new JCheckBox("Insert spaces instead of tabs");
    private final JCheckBox showLineNumbers = new JCheckBox("Show line numbers");
    private final JCheckBox autoIndent = new JCheckBox("Auto indent");
    private final JCheckBox saveBeforeBuild = new JCheckBox("Save before compiling or building");
    private final JCheckBox runInTerminal = new JCheckBox("Run programs in Terminal.app");

    private final Runnable onApply;

    public PreferencesDialog(java.awt.Frame owner, Runnable onApply) {
        super(owner, "Preferences", true);
        this.onApply = onApply;

        String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        String[] monospaced = Arrays.stream(families)
            .filter(PreferencesDialog::looksMonospaced)
            .toArray(String[]::new);
        fontFamily.setModel(new DefaultComboBoxModel<>(monospaced.length > 0 ? monospaced : families));

        load();

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(14, 14, 8, 14));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.anchor = GridBagConstraints.LINE_START;
        int row = 0;

        JButton browse = new JButton("Choose…");
        browse.addActionListener(e -> chooseGnatDirectory());
        JButton detect = new JButton("Detect");
        detect.addActionListener(e -> showDetectedToolchain());

        JPanel gnatRow = new JPanel(new BorderLayout(6, 0));
        gnatRow.add(gnatDirectory, BorderLayout.CENTER);
        JPanel gnatButtons = new JPanel();
        gnatButtons.add(browse);
        gnatButtons.add(detect);
        gnatRow.add(gnatButtons, BorderLayout.EAST);

        row = addRow(form, c, row, "GNAT bin directory:", gnatRow);
        row = addRow(form, c, row, "Compiler switches:", compilerSwitches);
        row = addRow(form, c, row, "Editor font:", fontFamily);
        row = addRow(form, c, row, "Font size:", fontSize);
        row = addRow(form, c, row, "Tab width:", tabSize);

        JPanel toggles = new JPanel(new GridBagLayout());
        GridBagConstraints t = new GridBagConstraints();
        t.gridx = 0;
        t.anchor = GridBagConstraints.LINE_START;
        t.gridy = 0;
        toggles.add(insertSpaces, t);
        t.gridy++;
        toggles.add(autoIndent, t);
        t.gridy++;
        toggles.add(showLineNumbers, t);
        t.gridy++;
        toggles.add(saveBeforeBuild, t);
        t.gridy++;
        toggles.add(runInTerminal, t);
        addRow(form, c, row, "Options:", toggles);

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> setVisible(false));
        JButton save = new JButton("Save");
        save.addActionListener(e -> {
            store();
            setVisible(false);
        });

        JPanel buttons = new JPanel();
        buttons.add(cancel);
        buttons.add(save);

        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(save);
        pack();
        setLocationRelativeTo(owner);
    }

    private static int addRow(JPanel form, GridBagConstraints c, int row, String label, java.awt.Component field) {
        c.gridx = 0;
        c.gridy = row;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        form.add(new JLabel(label, SwingConstants.RIGHT), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        form.add(field, c);
        return row + 1;
    }

    private static boolean looksMonospaced(String family) {
        Font font = new Font(family, Font.PLAIN, 12);
        java.awt.FontMetrics metrics = new JLabel().getFontMetrics(font);
        return metrics.charWidth('i') == metrics.charWidth('W');
    }

    private void load() {
        gnatDirectory.setText(Settings.gnatDirectory());
        compilerSwitches.setText(Settings.compilerSwitches());
        fontFamily.setSelectedItem(Settings.fontFamily());
        fontSize.setValue(Settings.fontSize());
        tabSize.setValue(Settings.tabSize());
        insertSpaces.setSelected(Settings.insertSpaces());
        showLineNumbers.setSelected(Settings.showLineNumbers());
        autoIndent.setSelected(Settings.autoIndent());
        saveBeforeBuild.setSelected(Settings.saveBeforeBuild());
        runInTerminal.setSelected(Settings.runInTerminal());
    }

    private void store() {
        Settings.setGnatDirectory(gnatDirectory.getText().trim());
        Settings.setCompilerSwitches(compilerSwitches.getText());
        Object family = fontFamily.getSelectedItem();
        if (family != null) {
            Settings.setFontFamily(family.toString());
        }
        Settings.setFontSize((Integer) fontSize.getValue());
        Settings.setTabSize((Integer) tabSize.getValue());
        Settings.setInsertSpaces(insertSpaces.isSelected());
        Settings.setShowLineNumbers(showLineNumbers.isSelected());
        Settings.setAutoIndent(autoIndent.isSelected());
        Settings.setSaveBeforeBuild(saveBeforeBuild.isSelected());
        Settings.setRunInTerminal(runInTerminal.isSelected());
        GnatToolchain.invalidate();
        onApply.run();
    }

    private void chooseGnatDirectory() {
        JFileChooser chooser = new JFileChooser(gnatDirectory.getText().isBlank()
                                                ? new File("/usr/local")
                                                : new File(gnatDirectory.getText()));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select the directory holding gnatmake");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            gnatDirectory.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void showDetectedToolchain() {
        Settings.setGnatDirectory(gnatDirectory.getText().trim());
        GnatToolchain.invalidate();
        JOptionPane.showMessageDialog(this, GnatToolchain.describe(), "Detected GNAT tools",
                                      JOptionPane.INFORMATION_MESSAGE);
    }
}
