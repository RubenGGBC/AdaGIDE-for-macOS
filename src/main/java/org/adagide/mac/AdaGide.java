package org.adagide.mac;

import java.io.File;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.adagide.mac.ui.MainWindow;

/** Entry point: sets up the macOS look and feel, then opens the AdaGIDE window. */
public final class AdaGide {

    public static final String APPLICATION_NAME = "AdaGIDE";

    private AdaGide() {
    }

    public static void main(String[] args) {
        // These must be set before the first AWT class is touched.
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.name", APPLICATION_NAME);
        System.setProperty("apple.awt.application.appearance", "system");
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", APPLICATION_NAME);

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
            // Falls back to the cross-platform look and feel.
        }

        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow();
            window.installMacHandlers();
            window.setVisible(true);
            for (String argument : args) {
                File file = new File(argument);
                if (file.isFile()) {
                    window.open(file);
                }
            }
        });
    }
}
