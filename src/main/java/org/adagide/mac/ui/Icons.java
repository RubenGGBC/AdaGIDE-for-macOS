package org.adagide.mac.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.Icon;

/** Small vector glyphs for the toolbar, drawn so the app needs no bitmap resources. */
public final class Icons {

    public enum Glyph { NEW, OPEN, SAVE, CHECK, COMPILE, BUILD, RUN, STOP, FIND, NEXT_ERROR, PREVIOUS_ERROR, CLEAN }

    private static final int SIZE = 18;

    private Icons() {
    }

    public static Icon of(Glyph glyph, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.translate(x, y);
                g.setColor(color);
                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                draw(g, glyph, color);
                g.dispose();
            }

            @Override
            public int getIconWidth() {
                return SIZE;
            }

            @Override
            public int getIconHeight() {
                return SIZE;
            }
        };
    }

    private static void draw(Graphics2D g, Glyph glyph, Color color) {
        switch (glyph) {
            case NEW -> {
                g.drawRect(3, 2, 11, 14);
                g.drawLine(9, 2, 14, 7);
                g.drawLine(6, 9, 11, 9);
                g.drawLine(6, 12, 11, 12);
            }
            case OPEN -> {
                g.drawLine(2, 5, 7, 5);
                g.drawLine(7, 5, 8, 7);
                g.drawRect(2, 7, 14, 8);
                g.drawLine(2, 5, 2, 7);
            }
            case SAVE -> {
                g.drawRect(2, 3, 14, 12);
                g.fillRect(5, 3, 8, 5);
                g.drawRect(5, 10, 8, 5);
            }
            case CHECK -> {
                g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(3, 9, 7, 13);
                g.drawLine(7, 13, 15, 4);
            }
            case COMPILE -> {
                g.drawLine(3, 5, 7, 9);
                g.drawLine(7, 9, 3, 13);
                g.drawLine(9, 13, 15, 13);
            }
            case BUILD -> {
                g.drawRect(2, 9, 6, 6);
                g.drawRect(10, 9, 6, 6);
                g.drawRect(6, 2, 6, 6);
            }
            case RUN -> {
                g.fillPolygon(new int[] {4, 15, 4}, new int[] {3, 9, 15}, 3);
            }
            case STOP -> g.fillRect(4, 4, 10, 10);
            case FIND -> {
                g.drawOval(3, 3, 9, 9);
                g.drawLine(11, 11, 15, 15);
            }
            case NEXT_ERROR -> {
                g.drawOval(2, 2, 6, 6);
                g.drawLine(11, 4, 15, 9);
                g.drawLine(15, 9, 11, 14);
            }
            case PREVIOUS_ERROR -> {
                g.drawOval(10, 10, 6, 6);
                g.drawLine(7, 4, 3, 9);
                g.drawLine(3, 9, 7, 14);
            }
            case CLEAN -> {
                g.drawLine(4, 15, 9, 4);
                g.drawLine(8, 15, 13, 4);
                g.drawLine(4, 15, 8, 15);
                g.drawLine(10, 3, 14, 5);
            }
            default -> g.drawRect(3, 3, 12, 12);
        }
    }
}
