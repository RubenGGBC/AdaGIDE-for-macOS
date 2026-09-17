package org.adagide.mac.ui;

import java.awt.Color;

import javax.swing.UIManager;

/** Editor colours, in a light and a dark flavour that follow the system appearance. */
public final class Theme {

    public final Color background;
    public final Color foreground;
    public final Color caret;
    public final Color selection;
    public final Color currentLine;
    public final Color gutterBackground;
    public final Color gutterForeground;
    public final Color keyword;
    public final Color comment;
    public final Color string;
    public final Color number;
    public final Color attribute;
    public final Color error;
    public final Color warning;

    private Theme(boolean dark) {
        if (dark) {
            background = new Color(0x1E1F22);
            foreground = new Color(0xE6E6E6);
            caret = new Color(0xF0F0F0);
            selection = new Color(0x3A5273);
            currentLine = new Color(0x2A2C31);
            gutterBackground = new Color(0x26282C);
            gutterForeground = new Color(0x7F848E);
            keyword = new Color(0x7FB2F0);
            comment = new Color(0x7EC699);
            string = new Color(0xE0A277);
            number = new Color(0xD4A5F0);
            attribute = new Color(0xC8A0E0);
            error = new Color(0xFF7B72);
            warning = new Color(0xE3B341);
        } else {
            background = Color.WHITE;
            foreground = new Color(0x1A1A1A);
            caret = new Color(0x1A1A1A);
            selection = new Color(0xB4D5FE);
            currentLine = new Color(0xF2F6FC);
            gutterBackground = new Color(0xF0F0F0);
            gutterForeground = new Color(0x808080);
            keyword = new Color(0x0000C0);
            comment = new Color(0x1B7F2D);
            string = new Color(0xA31515);
            number = new Color(0x0B6E4F);
            attribute = new Color(0x7A3E9D);
            error = new Color(0xC02020);
            warning = new Color(0xB07000);
        }
    }

    public static Theme current() {
        return new Theme(isDarkAppearance());
    }

    /** macOS dark mode reaches Swing through the control colours of the system look and feel. */
    public static boolean isDarkAppearance() {
        Color panel = UIManager.getColor("Panel.background");
        if (panel == null) {
            return false;
        }
        double luminance = (0.299 * panel.getRed() + 0.587 * panel.getGreen() + 0.114 * panel.getBlue()) / 255.0;
        return luminance < 0.5;
    }
}
