package com.dan.sternbild.render;

import com.dan.sternbild.model.MoonPhase;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * Zeichnet die Mondscheibe mit Randverdunkelung, Mondmeeren und Kratern und legt die Phase darüber.
 * Die Nachtseite bleibt als Erdschein schwach sichtbar.
 */
final class MoonPainter {

    /** Mondmeere als {x, y, Radius} in Einheiten des Scheibenradius (grob nach der sichtbaren Mondseite). */
    private static final double[][] MARIA = {
        { -0.30, -0.38, 0.30 },   // Mare Imbrium
        { 0.12, -0.34, 0.20 },    // Mare Serenitatis
        { 0.36, -0.08, 0.20 },    // Mare Tranquillitatis
        { 0.62, -0.26, 0.13 },    // Mare Crisium
        { -0.56, 0.02, 0.30 },    // Oceanus Procellarum
        { 0.40, 0.28, 0.15 },     // Mare Fecunditatis
        { 0.10, 0.18, 0.13 },     // Mare Nubium/Sinus Medii (vereinfacht)
        { -0.22, 0.38, 0.16 },    // Mare Nubium
    };
    /** Krater {x, y, Radius}. */
    private static final double[][] CRATERS = {
        { -0.08, 0.72, 0.06 },    // Tycho
        { -0.36, -0.12, 0.05 },   // Kopernikus
        { -0.70, -0.20, 0.04 },   // Kepler
        { 0.55, 0.55, 0.035 },
        { -0.45, 0.62, 0.04 },
        { 0.25, 0.70, 0.03 },
    };

    private MoonPainter() { }

    static BufferedImage render(int size, MoonPhase phase) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        double r = (size - 2) / 2.0, cx = size / 2.0, cy = size / 2.0;
        Shape disc = new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);

        // Scheibe mit Randverdunkelung
        g.setPaint(new RadialGradientPaint((float) (cx - r * 0.25), (float) (cy - r * 0.25), (float) (r * 1.25),
                new float[] { 0f, 0.7f, 1f },
                new Color[] { new Color(0xF6F2E6), new Color(0xE1DCCB), new Color(0xB8B2A2) }));
        g.fill(disc);
        g.setClip(disc);
        for (double[] m : MARIA) {
            soft(g, cx + m[0] * r, cy + m[1] * r, m[2] * r, new Color(120, 118, 112), 0.55f);
        }
        for (double[] c : CRATERS) {
            soft(g, cx + c[0] * r, cy + c[1] * r, c[2] * r * 2.2, new Color(255, 252, 240), 0.55f);
            soft(g, cx + c[0] * r + c[2] * r * 0.3, cy + c[1] * r + c[2] * r * 0.3, c[2] * r, new Color(140, 136, 126), 0.45f);
        }

        // Phase: beleuchtete Fläche aus Halbscheibe und Terminator-Ellipse
        double k = Math.cos(phase.phaseAngle());        // 1 = Neumond, -1 = Vollmond
        double tw = Math.abs(k) * r;
        Area lit = new Area(new Rectangle2D.Double(phase.waxing() ? cx : cx - r, cy - r, r, 2 * r));
        lit.intersect(new Area(disc));
        Area term = new Area(new Ellipse2D.Double(cx - tw, cy - r, 2 * tw, 2 * r));
        if (k >= 0) {
            lit.subtract(term);
        } else {
            lit.add(term);
        }
        Area dark = new Area(disc);
        dark.subtract(lit);
        g.setClip(null);
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new Color(10, 16, 40, 222));        // Erdschein bleibt schwach sichtbar
        g.fill(dark);
        g.dispose();
        return img;
    }

    private static void soft(Graphics2D g, double x, double y, double rad, Color c, float alpha) {
        if (rad <= 0.5) {
            return;
        }
        g.setPaint(new RadialGradientPaint((float) x, (float) y, (float) rad, new float[] { 0f, 0.6f, 1f },
                new Color[] { new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (255 * alpha)),
                              new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (160 * alpha)),
                              new Color(c.getRed(), c.getGreen(), c.getBlue(), 0) }));
        g.fill(new Ellipse2D.Double(x - rad, y - rad, rad * 2, rad * 2));
    }
}
