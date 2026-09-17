import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/**
 * Draws the application icon at every size macOS asks for.
 *
 * Run with: java packaging/GenerateIcon.java packaging/AdaGIDE.iconset
 */
public final class GenerateIcon {

    private static final int[][] SIZES = {
        {16, 1}, {16, 2}, {32, 1}, {32, 2}, {128, 1}, {128, 2}, {256, 1}, {256, 2}, {512, 1}, {512, 2}
    };

    public static void main(String[] args) throws Exception {
        File directory = new File(args.length > 0 ? args[0] : "packaging/AdaGIDE.iconset");
        directory.mkdirs();
        for (int[] size : SIZES) {
            int points = size[0];
            int scale = size[1];
            BufferedImage image = render(points * scale);
            String name = "icon_" + points + "x" + points + (scale == 2 ? "@2x" : "") + ".png";
            ImageIO.write(image, "png", new File(directory, name));
        }
        System.out.println("Icon written to " + directory);
    }

    static BufferedImage render(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        double margin = size * 0.055;
        double side = size - 2 * margin;
        double radius = size * 0.225;

        g.setPaint(new GradientPaint(0, (float) margin, new Color(0x2B4C7E),
                                     0, (float) (margin + side), new Color(0x13233F)));
        g.fill(new RoundRectangle2D.Double(margin, margin, side, side, radius, radius));

        // A soft highlight along the top edge, the way macOS icons catch the light.
        g.setPaint(new GradientPaint(0, (float) margin, new Color(255, 255, 255, 60),
                                     0, (float) (margin + side * 0.45), new Color(255, 255, 255, 0)));
        g.fill(new RoundRectangle2D.Double(margin, margin, side, side * 0.5, radius, radius));

        drawLetter(g, size);
        g.dispose();
        return image;
    }

    /** The Ada "A", drawn as a glyph outline so it scales down cleanly. */
    private static void drawLetter(Graphics2D g, int size) {
        Font font = new Font("Serif", Font.BOLD, (int) (size * 0.62));
        FontRenderContext context = g.getFontRenderContext();
        GlyphVector glyphs = font.createGlyphVector(context, "A");
        java.awt.geom.Rectangle2D bounds = glyphs.getVisualBounds();

        double x = (size - bounds.getWidth()) / 2 - bounds.getX();
        double y = (size - bounds.getHeight()) / 2 - bounds.getY() - size * 0.03;

        g.translate(x, y);
        g.setColor(new Color(0x9BD1A0));
        g.fill(glyphs.getOutline());
        g.translate(-x, -y);

        // The compile marker: a small chevron in the lower right corner.
        g.setColor(new Color(0xE9F2EC));
        g.setStroke(new java.awt.BasicStroke((float) Math.max(1.0, size * 0.035),
                                             java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        int left = (int) (size * 0.70);
        int mid = (int) (size * 0.81);
        int base = (int) (size * 0.79);
        g.drawLine(left, (int) (size * 0.67), mid, base);
        g.drawLine(mid, base, left, (int) (size * 0.91));
    }
}
