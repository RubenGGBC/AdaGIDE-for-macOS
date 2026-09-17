package org.adagide.mac.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.adagide.mac.build.BuildService;
import org.adagide.mac.build.Diagnostic;
import org.adagide.mac.build.DiagnosticParser;
import org.adagide.mac.build.ExternalProcess;
import org.adagide.mac.build.GnatToolchain;
import org.adagide.mac.core.RecentFiles;
import org.adagide.mac.core.Settings;
import org.adagide.mac.core.Templates;

/** The AdaGIDE window: editor tabs on top, compiler output below, GNAT behind the Compile menu. */
public class MainWindow extends JFrame {

    private static final int COMMAND = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

    private final JTabbedPane tabs = new JTabbedPane();
    private final OutputPanel output;
    private final BuildService builder = new BuildService();
    private final FindReplaceDialog findDialog;
    private final JLabel caretStatus = new JLabel(" ");
    private final JLabel buildStatus = new JLabel(" ");
    private final JMenu recentMenu = new JMenu("Open Recent");
    private final List<JButton> runButtons = new ArrayList<>();

    private Theme theme = Theme.current();

    public MainWindow() {
        super("AdaGIDE");
        output = new OutputPanel(theme);
        findDialog = new FindReplaceDialog(this, this::currentEditor);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (confirmCloseAll()) {
                    dispose();
                    System.exit(0);
                }
            }
        });

        tabs.addChangeListener(e -> refreshState());
        output.setDiagnosticListener(this::jumpTo);
        output.setInputListener(builder::sendInput);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabs, output);
        split.setResizeWeight(0.75);
        split.setDividerLocation(520);
        split.setBorder(null);

        add(buildToolBar(), BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
        setJMenuBar(buildMenuBar());

        setSize(1080, 760);
        setMinimumSize(new Dimension(720, 520));
        setLocationRelativeTo(null);

        newFile();
        refreshRecentMenu();
        refreshToolchainStatus();
    }

    // --------------------------------------------------------------- layout

    private JToolBar buildToolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        bar.add(toolButton(Icons.Glyph.NEW, "New file", e -> newFile()));
        bar.add(toolButton(Icons.Glyph.OPEN, "Open file", e -> openDialog()));
        bar.add(toolButton(Icons.Glyph.SAVE, "Save file", e -> save()));
        bar.addSeparator();
        bar.add(toolButton(Icons.Glyph.FIND, "Find and replace", e -> showFind()));
        bar.addSeparator();
        bar.add(toolButton(Icons.Glyph.CHECK, "Check syntax (no code generated)", e -> checkSyntax()));
        bar.add(toolButton(Icons.Glyph.COMPILE, "Compile this unit", e -> compile()));
        bar.add(toolButton(Icons.Glyph.BUILD, "Build (gnatmake)", e -> build(false)));
        JButton run = toolButton(Icons.Glyph.RUN, "Build and run", e -> build(true));
        bar.add(run);
        JButton stop = toolButton(Icons.Glyph.STOP, "Stop the running program", e -> builder.stop());
        bar.add(stop);
        bar.addSeparator();
        bar.add(toolButton(Icons.Glyph.PREVIOUS_ERROR, "Previous error", e -> stepError(-1)));
        bar.add(toolButton(Icons.Glyph.NEXT_ERROR, "Next error", e -> stepError(1)));
        bar.add(toolButton(Icons.Glyph.CLEAN, "Clean build products", e -> clean()));
        bar.add(Box.createHorizontalGlue());

        stop.setEnabled(false);
        runButtons.add(stop);
        return bar;
    }

    private JButton toolButton(Icons.Glyph glyph, String tooltip, java.util.function.Consumer<ActionEvent> action) {
        Color color = theme.foreground;
        Icon icon = Icons.of(glyph, color);
        JButton button = new JButton(icon);
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        button.addActionListener(action::accept);
        return button;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(3, 10, 5, 10));
        caretStatus.setFont(caretStatus.getFont().deriveFont(11f));
        buildStatus.setFont(buildStatus.getFont().deriveFont(11f));
        bar.add(caretStatus, BorderLayout.WEST);
        bar.add(buildStatus, BorderLayout.EAST);
        return bar;
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        file.add(item("New", KeyEvent.VK_N, COMMAND, e -> newFile()));
        file.add(item("New From Template…", KeyEvent.VK_N, COMMAND | InputEvent.SHIFT_DOWN_MASK,
                      e -> newFromTemplate()));
        file.add(item("Open…", KeyEvent.VK_O, COMMAND, e -> openDialog()));
        file.add(recentMenu);
        file.addSeparator();
        file.add(item("Close Tab", KeyEvent.VK_W, COMMAND, e -> closeCurrentTab()));
        file.add(item("Save", KeyEvent.VK_S, COMMAND, e -> save()));
        file.add(item("Save As…", KeyEvent.VK_S, COMMAND | InputEvent.SHIFT_DOWN_MASK, e -> saveAs()));
        file.add(item("Revert to Saved", 0, 0, e -> revert()));
        file.addSeparator();
        file.add(item("Print…", KeyEvent.VK_P, COMMAND, e -> print()));
        bar.add(file);

        JMenu edit = new JMenu("Edit");
        edit.add(item("Undo", KeyEvent.VK_Z, COMMAND, e -> withEditor(EditorPane::undo)));
        edit.add(item("Redo", KeyEvent.VK_Z, COMMAND | InputEvent.SHIFT_DOWN_MASK, e -> withEditor(EditorPane::redo)));
        edit.addSeparator();
        edit.add(item("Cut", KeyEvent.VK_X, COMMAND, e -> withEditor(p -> p.textPane().cut())));
        edit.add(item("Copy", KeyEvent.VK_C, COMMAND, e -> withEditor(p -> p.textPane().copy())));
        edit.add(item("Paste", KeyEvent.VK_V, COMMAND, e -> withEditor(p -> p.textPane().paste())));
        edit.add(item("Select All", KeyEvent.VK_A, COMMAND, e -> withEditor(p -> p.textPane().selectAll())));
        edit.addSeparator();
        edit.add(item("Comment / Uncomment", KeyEvent.VK_SLASH, COMMAND, e -> withEditor(EditorPane::toggleComment)));
        bar.add(edit);

        JMenu search = new JMenu("Search");
        search.add(item("Find and Replace…", KeyEvent.VK_F, COMMAND, e -> showFind()));
        search.add(item("Go to Line…", KeyEvent.VK_L, COMMAND, e -> goToLine()));
        search.addSeparator();
        search.add(item("Next Error", KeyEvent.VK_E, COMMAND, e -> stepError(1)));
        search.add(item("Previous Error", KeyEvent.VK_E, COMMAND | InputEvent.SHIFT_DOWN_MASK, e -> stepError(-1)));
        bar.add(search);

        JMenu compile = new JMenu("Compile");
        compile.add(item("Check Syntax", KeyEvent.VK_K, COMMAND, e -> checkSyntax()));
        compile.add(item("Compile Unit", KeyEvent.VK_B, COMMAND, e -> compile()));
        compile.add(item("Build", KeyEvent.VK_B, COMMAND | InputEvent.SHIFT_DOWN_MASK, e -> build(false)));
        compile.add(item("Build and Run", KeyEvent.VK_R, COMMAND, e -> build(true)));
        compile.add(item("Run Without Building", KeyEvent.VK_R, COMMAND | InputEvent.SHIFT_DOWN_MASK,
                         e -> runExecutable()));
        compile.addSeparator();
        compile.add(item("Stop", KeyEvent.VK_PERIOD, COMMAND, e -> builder.stop()));
        compile.add(item("Clean", 0, 0, e -> clean()));
        JCheckBoxMenuItem terminal = new JCheckBoxMenuItem("Run in Terminal.app", Settings.runInTerminal());
        terminal.addActionListener(e -> Settings.setRunInTerminal(terminal.isSelected()));
        compile.addSeparator();
        compile.add(terminal);
        bar.add(compile);

        JMenu tools = new JMenu("Tools");
        tools.add(item("GNAT Information…", 0, 0, e -> showToolchainInformation()));
        tools.add(item("Open Terminal Here", 0, 0, e -> openTerminalHere()));
        tools.add(item("Preferences…", KeyEvent.VK_COMMA, COMMAND, e -> showPreferences()));
        bar.add(tools);

        JMenu window = new JMenu("Window");
        window.add(item("Next Tab", KeyEvent.VK_CLOSE_BRACKET, COMMAND | InputEvent.SHIFT_DOWN_MASK,
                        e -> cycleTab(1)));
        window.add(item("Previous Tab", KeyEvent.VK_OPEN_BRACKET, COMMAND | InputEvent.SHIFT_DOWN_MASK,
                        e -> cycleTab(-1)));
        window.add(item("Clear Output", 0, 0, e -> output.clear()));
        bar.add(window);

        JMenu help = new JMenu("Help");
        help.add(item("About AdaGIDE", 0, 0, e -> showAbout()));
        help.add(item("Ada Reference Manual", 0, 0, e -> browse("http://www.ada-auth.org/standards/rm12_w_tc1/html/RM-TTL.html")));
        help.add(item("GNAT User's Guide", 0, 0, e -> browse("https://gcc.gnu.org/onlinedocs/gnat_ugn/")));
        bar.add(help);

        return bar;
    }

    private JMenuItem item(String label, int key, int modifiers, java.util.function.Consumer<ActionEvent> action) {
        JMenuItem menuItem = new JMenuItem(new AbstractAction(label) {
            @Override
            public void actionPerformed(ActionEvent event) {
                action.accept(event);
            }
        });
        if (key != 0) {
            menuItem.setAccelerator(KeyStroke.getKeyStroke(key, modifiers));
        }
        return menuItem;
    }

    // ------------------------------------------------------------ tab state

    public EditorPane currentEditor() {
        JComponent component = (JComponent) tabs.getSelectedComponent();
        return component instanceof EditorPane editor ? editor : null;
    }

    private void withEditor(java.util.function.Consumer<EditorPane> action) {
        EditorPane editor = currentEditor();
        if (editor != null) {
            action.accept(editor);
        }
    }

    private EditorPane addTab(File file) {
        EditorPane editor = new EditorPane(theme);
        editor.setStateListener(this::refreshState);
        tabs.addTab("Untitled", editor);
        tabs.setSelectedComponent(editor);
        if (file != null) {
            try {
                editor.load(file);
                RecentFiles.add(file);
                Settings.setLastDirectory(file.getParent());
                refreshRecentMenu();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Could not open " + file.getName() + ":\n" + e.getMessage(),
                                              "Open", JOptionPane.ERROR_MESSAGE);
            }
        }
        refreshState();
        return editor;
    }

    private void refreshState() {
        for (int index = 0; index < tabs.getTabCount(); index++) {
            EditorPane editor = (EditorPane) tabs.getComponentAt(index);
            tabs.setTitleAt(index, editor.title());
            File file = editor.file();
            tabs.setToolTipTextAt(index, file == null ? "Unsaved file" : file.getAbsolutePath());
        }
        EditorPane editor = currentEditor();
        if (editor == null) {
            setTitle("AdaGIDE");
            caretStatus.setText(" ");
            return;
        }
        File file = editor.file();
        setTitle(file == null ? "AdaGIDE — Untitled" : "AdaGIDE — " + file.getAbsolutePath());
        getRootPane().putClientProperty("Window.documentModified", editor.isModified());
        caretStatus.setText(String.format("Line %d, Column %d%s", editor.caretLine(), editor.caretColumn(),
                                          editor.isModified() ? "  •  modified" : ""));
    }

    private void refreshRecentMenu() {
        recentMenu.removeAll();
        List<File> files = RecentFiles.list();
        for (File file : files) {
            JMenuItem entry = new JMenuItem(file.getName() + "  —  " + file.getParent());
            entry.addActionListener(e -> open(file));
            recentMenu.add(entry);
        }
        if (files.isEmpty()) {
            JMenuItem empty = new JMenuItem("No recent files");
            empty.setEnabled(false);
            recentMenu.add(empty);
        } else {
            recentMenu.addSeparator();
            JMenuItem clear = new JMenuItem("Clear Menu");
            clear.addActionListener(e -> {
                RecentFiles.clear();
                refreshRecentMenu();
            });
            recentMenu.add(clear);
        }
    }

    private void refreshToolchainStatus() {
        File gnatmake = GnatToolchain.find("gnatmake");
        if (gnatmake == null) {
            buildStatus.setText("GNAT not found — set it in Tools ▸ Preferences");
            buildStatus.setForeground(theme.error);
        } else {
            buildStatus.setText("GNAT: " + gnatmake.getParent());
            buildStatus.setForeground(theme.gutterForeground);
        }
    }

    // ------------------------------------------------------------ file menu

    public void newFile() {
        addTab(null).setContent(Templates.load("main_procedure.adb"), null);
    }

    private void newFromTemplate() {
        String[] labels = Templates.ALL.keySet().toArray(String[]::new);
        String choice = (String) JOptionPane.showInputDialog(this, "Choose a template:", "New From Template",
                                                             JOptionPane.PLAIN_MESSAGE, null, labels, labels[0]);
        if (choice == null) {
            return;
        }
        String unit = JOptionPane.showInputDialog(this, "Unit name:", "Main");
        if (unit == null) {
            return;
        }
        String resource = Templates.ALL.get(choice);
        String content = Templates.load(resource).replace("Main", unit.trim().isEmpty() ? "Main" : unit.trim());
        EditorPane editor = addTab(null);
        File suggested = new File(Settings.lastDirectory(), Templates.fileNameFor(resource, unit));
        editor.setContent(content, null);
        editor.putClientProperty("suggestedFile", suggested);
    }

    private void openDialog() {
        JFileChooser chooser = new JFileChooser(Settings.lastDirectory());
        chooser.setFileFilter(new FileNameExtensionFilter("Ada source (.adb, .ads, .ada)", "adb", "ads", "ada"));
        chooser.setAcceptAllFileFilterUsed(true);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            open(chooser.getSelectedFile());
        }
    }

    /** Opens a file, reusing its tab when it is already open. */
    public void open(File file) {
        for (int index = 0; index < tabs.getTabCount(); index++) {
            EditorPane editor = (EditorPane) tabs.getComponentAt(index);
            if (editor.file() != null && editor.file().getAbsolutePath().equals(file.getAbsolutePath())) {
                tabs.setSelectedIndex(index);
                return;
            }
        }
        EditorPane current = currentEditor();
        if (current != null && current.file() == null && !current.isModified()) {
            try {
                current.load(file);
                RecentFiles.add(file);
                Settings.setLastDirectory(file.getParent());
                refreshRecentMenu();
                refreshState();
                return;
            } catch (IOException e) {
                // Fall through and open a new tab instead.
            }
        }
        addTab(file);
    }

    public boolean save() {
        EditorPane editor = currentEditor();
        if (editor == null) {
            return false;
        }
        if (editor.file() == null) {
            return saveAs();
        }
        try {
            editor.save(editor.file());
            refreshState();
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not save:\n" + e.getMessage(), "Save",
                                          JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private boolean saveAs() {
        EditorPane editor = currentEditor();
        if (editor == null) {
            return false;
        }
        JFileChooser chooser = new JFileChooser(Settings.lastDirectory());
        Object suggested = editor.getClientProperty("suggestedFile");
        if (suggested instanceof File file) {
            chooser.setSelectedFile(file);
        } else if (editor.file() != null) {
            chooser.setSelectedFile(editor.file());
        }
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return false;
        }
        File target = chooser.getSelectedFile();
        try {
            editor.save(target);
            RecentFiles.add(target);
            Settings.setLastDirectory(target.getParent());
            refreshRecentMenu();
            refreshState();
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not save:\n" + e.getMessage(), "Save As",
                                          JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void revert() {
        EditorPane editor = currentEditor();
        if (editor == null || editor.file() == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
            "Discard changes to " + editor.file().getName() + "?", "Revert to Saved",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            editor.load(editor.file());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Revert", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void print() {
        EditorPane editor = currentEditor();
        if (editor == null) {
            return;
        }
        try {
            editor.textPane().print();
        } catch (java.awt.print.PrinterException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Print", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void closeCurrentTab() {
        EditorPane editor = currentEditor();
        if (editor == null) {
            return;
        }
        if (!confirmSave(editor)) {
            return;
        }
        tabs.remove(editor);
        if (tabs.getTabCount() == 0) {
            newFile();
        }
        refreshState();
    }

    private boolean confirmSave(EditorPane editor) {
        if (!editor.isModified()) {
            return true;
        }
        tabs.setSelectedComponent(editor);
        int answer = JOptionPane.showConfirmDialog(this,
            "Save changes to " + (editor.file() == null ? "Untitled" : editor.file().getName()) + "?",
            "AdaGIDE", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.CANCEL_OPTION || answer == JOptionPane.CLOSED_OPTION) {
            return false;
        }
        if (answer == JOptionPane.YES_OPTION) {
            return save();
        }
        return true;
    }

    public boolean confirmCloseAll() {
        for (int index = 0; index < tabs.getTabCount(); index++) {
            if (!confirmSave((EditorPane) tabs.getComponentAt(index))) {
                return false;
            }
        }
        builder.stop();
        return true;
    }

    private void cycleTab(int direction) {
        if (tabs.getTabCount() == 0) {
            return;
        }
        int next = (tabs.getSelectedIndex() + direction + tabs.getTabCount()) % tabs.getTabCount();
        tabs.setSelectedIndex(next);
    }

    // ---------------------------------------------------------- search menu

    private void showFind() {
        EditorPane editor = currentEditor();
        findDialog.showFor(editor == null ? null : editor.textPane().getSelectedText());
    }

    private void goToLine() {
        EditorPane editor = currentEditor();
        if (editor == null) {
            return;
        }
        String answer = JOptionPane.showInputDialog(this, "Go to line:", editor.caretLine());
        if (answer == null) {
            return;
        }
        try {
            editor.goTo(Integer.parseInt(answer.trim()), 1);
        } catch (NumberFormatException e) {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    private void stepError(int direction) {
        if (output.diagnostics().isEmpty()) {
            buildStatus.setText("No compiler messages");
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        output.step(direction);
    }

    private void jumpTo(Diagnostic diagnostic) {
        open(diagnostic.file());
        EditorPane editor = currentEditor();
        if (editor != null) {
            editor.goTo(diagnostic.line(), diagnostic.column());
            editor.showDiagnostics(output.diagnostics());
        }
        buildStatus.setText(diagnostic.message());
        buildStatus.setForeground(diagnostic.isError() ? theme.error : theme.warning);
    }

    // --------------------------------------------------------- compile menu

    private File sourceForBuild() {
        EditorPane editor = currentEditor();
        if (editor == null) {
            return null;
        }
        if (editor.file() == null || (Settings.saveBeforeBuild() && editor.isModified())) {
            if (!save()) {
                return null;
            }
        }
        return editor.file();
    }

    private void checkSyntax() {
        File source = sourceForBuild();
        if (source != null && ensureTool("gcc")) {
            execute(builder.checkSyntax(source), null);
        }
    }

    private void compile() {
        File source = sourceForBuild();
        if (source != null && ensureTool("gcc")) {
            execute(builder.compile(source), null);
        }
    }

    private void build(boolean thenRun) {
        File source = sourceForBuild();
        if (source == null || !ensureTool("gnatmake")) {
            return;
        }
        execute(builder.build(source), thenRun ? () -> runExecutable(source) : null);
    }

    private void runExecutable() {
        File source = sourceForBuild();
        if (source != null) {
            runExecutable(source);
        }
    }

    private void runExecutable(File source) {
        File executable = BuildService.executableFor(source);
        if (!executable.isFile()) {
            int answer = JOptionPane.showConfirmDialog(this,
                executable.getName() + " has not been built yet. Build it now?", "Run",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (answer == JOptionPane.OK_OPTION) {
                build(true);
            }
            return;
        }
        if (Settings.runInTerminal()) {
            execute(builder.runInTerminal(source), null);
        } else {
            execute(builder.run(source), null);
        }
    }

    private void clean() {
        File source = sourceForBuild();
        if (source != null && ensureTool("gnatclean")) {
            execute(builder.clean(source), null);
        }
    }

    private boolean ensureTool(String tool) {
        if (GnatToolchain.isAvailable(tool)) {
            return true;
        }
        Object[] options = {"Open Preferences", "Cancel"};
        int answer = JOptionPane.showOptionDialog(this,
            "AdaGIDE could not find \"" + tool + "\" on this Mac.\n\n"
            + "Install a GNAT toolchain, for example:\n"
            + "    brew install gnat            (Homebrew)\n"
            + "    alr toolchain --select       (Alire, https://alire.ada.dev)\n\n"
            + "Then point Preferences at the directory that holds gnatmake.",
            "GNAT not found", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        if (answer == JOptionPane.OK_OPTION) {
            showPreferences();
        }
        return false;
    }

    private void execute(BuildService.Command command, Runnable onSuccess) {
        if (builder.isBusy()) {
            JOptionPane.showMessageDialog(this, "Another command is still running. Stop it first.",
                                          "AdaGIDE", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        output.clear();
        output.append(command.description(), OutputPanel.LineKind.HEADER);
        output.append(String.join(" ", command.arguments()), OutputPanel.LineKind.PLAIN);
        output.append("", OutputPanel.LineKind.PLAIN);
        withEditor(EditorPane::clearDiagnostics);
        setRunning(true, command.kind());

        long started = System.currentTimeMillis();
        try {
            builder.execute(command, new ExternalProcess.Listener() {
                @Override
                public void onLine(String line) {
                    SwingUtilities.invokeLater(() -> appendOutput(line, command));
                }

                @Override
                public void onFinished(int exitCode) {
                    SwingUtilities.invokeLater(() -> finished(command, exitCode, started, onSuccess));
                }
            });
        } catch (IOException e) {
            setRunning(false, command.kind());
            output.append("adagide: " + e.getMessage(), OutputPanel.LineKind.ERROR);
        }
    }

    private void appendOutput(String line, BuildService.Command command) {
        int index = output.lineCount();
        Diagnostic diagnostic = DiagnosticParser.parse(line, command.directory(), index);
        OutputPanel.LineKind kind = OutputPanel.LineKind.PLAIN;
        if (diagnostic != null) {
            output.addDiagnostic(diagnostic);
            kind = switch (diagnostic.severity()) {
                case ERROR -> OutputPanel.LineKind.ERROR;
                case WARNING, STYLE -> OutputPanel.LineKind.WARNING;
                case INFO -> OutputPanel.LineKind.PLAIN;
            };
        }
        output.append(line, kind);
    }

    private void finished(BuildService.Command command, int exitCode, long started, Runnable onSuccess) {
        setRunning(false, command.kind());
        double seconds = (System.currentTimeMillis() - started) / 1000.0;
        long errors = output.errorCount();
        long warnings = output.warningCount();

        String summary = String.format("%s finished with exit code %d in %.2fs — %d error(s), %d warning(s)",
                                       command.kind().name().toLowerCase(Locale.ROOT), exitCode, seconds,
                                       errors, warnings);
        output.append("", OutputPanel.LineKind.PLAIN);
        output.append(summary, exitCode == 0 ? OutputPanel.LineKind.SUCCESS : OutputPanel.LineKind.ERROR);

        withEditor(editor -> editor.showDiagnostics(output.diagnostics()));
        buildStatus.setText(summary);
        buildStatus.setForeground(exitCode == 0 ? theme.comment : theme.error);

        if (errors > 0) {
            output.step(1);
        } else if (exitCode == 0 && onSuccess != null) {
            onSuccess.run();
        }
    }

    private void setRunning(boolean running, BuildService.Kind kind) {
        output.setInputEnabled(running && kind == BuildService.Kind.RUN && !Settings.runInTerminal());
        for (JButton button : runButtons) {
            button.setEnabled(running);
        }
        setCursor(running ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR)
                          : java.awt.Cursor.getDefaultCursor());
    }

    // ------------------------------------------------------------ tools menu

    public void showPreferences() {
        new PreferencesDialog(this, () -> {
            theme = Theme.current();
            for (int index = 0; index < tabs.getTabCount(); index++) {
                EditorPane editor = (EditorPane) tabs.getComponentAt(index);
                editor.applyTheme(theme);
                editor.applySettings();
            }
            output.applyTheme(theme);
            refreshToolchainStatus();
        }).setVisible(true);
    }

    private void showToolchainInformation() {
        JTextArea text = new JTextArea(GnatToolchain.describe() + "\nSearch path:\n  "
                                       + String.join("\n  ", GnatToolchain.searchPath()));
        text.setEditable(false);
        text.setFont(new java.awt.Font("Menlo", java.awt.Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(text);
        scroll.setPreferredSize(new Dimension(640, 360));
        JOptionPane.showMessageDialog(this, scroll, "GNAT Information", JOptionPane.INFORMATION_MESSAGE);
    }

    private void openTerminalHere() {
        EditorPane editor = currentEditor();
        File directory = editor != null && editor.file() != null
            ? editor.file().getParentFile()
            : new File(Settings.lastDirectory());
        try {
            new ProcessBuilder("/usr/bin/open", "-a", "Terminal", directory.getAbsolutePath()).start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Open Terminal", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void browse(String url) {
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (IOException | UnsupportedOperationException e) {
            JOptionPane.showMessageDialog(this, url, "Open in browser", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    public void showAbout() {
        String version = getClass().getPackage().getImplementationVersion();
        JOptionPane.showMessageDialog(this,
            "AdaGIDE for macOS " + (version == null ? "1.0.0" : version) + "\n\n"
            + "An Ada GUI Integrated Development Environment in the spirit of the\n"
            + "original AdaGIDE, rebuilt for macOS on top of the GNAT toolchain.\n\n"
            + "Editor, syntax colouring, compile, build, run and error navigation.",
            "About AdaGIDE", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Wires the standard macOS application menu entries to this window. */
    public void installMacHandlers() {
        if (!java.awt.Desktop.isDesktopSupported()) {
            return;
        }
        java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
        trySet(() -> desktop.setAboutHandler(event -> showAbout()));
        trySet(() -> desktop.setPreferencesHandler(event -> showPreferences()));
        trySet(() -> desktop.setQuitHandler((event, response) -> {
            if (confirmCloseAll()) {
                response.performQuit();
            } else {
                response.cancelQuit();
            }
        }));
        trySet(() -> desktop.setOpenFileHandler(event -> {
            for (File file : event.getFiles()) {
                open(file);
            }
            toFront();
        }));
    }

    private static void trySet(Runnable action) {
        try {
            action.run();
        } catch (UnsupportedOperationException | SecurityException e) {
            // The running platform does not offer this hook.
        }
    }
}
