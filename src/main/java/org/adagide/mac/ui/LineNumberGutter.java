package org.adagide.mac.ui;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;

import org.adagide.mac.build.Diagnostic;

/** Row header showing line numbers, plus a marker next to lines the compiler complained about. */
public class LineNumberGutter extends JComponent {

    private static final int PADDING = 8;

    private final JTextComponent text;
    private final Map<Integer, Diagnostic.Severity> markers = new HashMap<>();
    private Theme theme;

    public LineNumberGutter(JTextComponent text, Theme theme) {
        this.text = text;
        this.theme = theme;
        setFont(text.getFont());
    }

    public void applyTheme(Theme theme) {
        this.theme = theme;
        repaint();
    }

    public void setMarkers(Map<Integer, Diagnostic.Severity> newMarkers) {
        markers.clear();
        markers.putAll(newMarkers);
        repaint();
    }

    public void clearMarkers() {
        markers.clear();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(text.getFont());
        int lines = Math.max(text.getDocument().getDefaultRootElement().getElementCount(), 99);
        int width = metrics.stringWidth(Integer.toString(lines)) + 2 * PADDING + 6;
        return new Dimension(width, Math.max(text.getHeight(), 1));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Rectangle clip = g.getClipBounds();

        g.setColor(theme.gutterBackground);
        g.fillRect(clip.x, clip.y, clip.width, clip.height);

        g.setFont(text.getFont());
        FontMetrics metrics = g.getFontMetrics();
        Element root = text.getDocument().getDefaultRootElement();

        int firstLine = root.getElementIndex(text.viewToModel2D(new java.awt.Point(0, clip.y)));
        int lastLine = root.getElementIndex(text.viewToModel2D(new java.awt.Point(0, clip.y + clip.height)));

        for (int line = firstLine; line <= lastLine && line < root.getElementCount(); line++) {
            try {
                Rectangle2DBounds bounds = boundsOf(root, line);
                if (bounds == null) {
                    continue;
                }
                String label = Integer.toString(line + 1);
                int baseline = bounds.y + metrics.getAscent()
                    + (bounds.height - metrics.getHeight()) / 2;

                Diagnostic.Severity severity = markers.get(line + 1);
                if (severity != null) {
                    g.setColor(severity == Diagnostic.Severity.ERROR ? theme.error : theme.warning);
                    g.fillOval(3, bounds.y + bounds.height / 2 - 3, 6, 6);
                }

                g.setColor(severity == Diagnostic.Severity.ERROR ? theme.error : theme.gutterForeground);
                int x = getWidth() - PADDING - metrics.stringWidth(label);
                g.drawString(label, x, baseline);
            } catch (BadLocationException e) {
                // The view is mid-update; the next repaint will draw this line.
            }
        }
        g.dispose();
    }

    private record Rectangle2DBounds(int y, int height) {
    }

    private Rectangle2DBounds boundsOf(Element root, int line) throws BadLocationException {
        int offset = root.getElement(line).getStartOffset();
        java.awt.geom.Rectangle2D rectangle = text.modelToView2D(offset);
        if (rectangle == null) {
            return null;
        }
        return new Rectangle2DBounds((int) rectangle.getY(), (int) rectangle.getHeight());
    }
}
