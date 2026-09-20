package com.dan.sternbild.render;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/** Vorgerenderte Leuchtpunkte, Beugungskreuze und Nebelflecken. */
final class Sprites {

    static final int GLOW_SIZE = 64;
    private static final Map<Integer, BufferedImage> GLOWS = new HashMap<>();
    static final BufferedImage SPIKE = makeSpike(128);
    static final BufferedImage[] NEBULA = {
        makeNebula(new Color(120, 150, 255)), makeNebula(new Color(170, 125, 240)), makeNebula(new Color(255, 205, 170))
    };
    static final BufferedImage DUST = makeNebula(new Color(4, 6, 18));

    private Sprites() { }

    /** Weicher Leuchtpunkt, zur Sternfarbe hin getönt. */
    static BufferedImage glow(Color c) {
        return GLOWS.computeIfAbsent(c.getRGB(), k -> {
            BufferedImage img = new BufferedImage(GLOW_SIZE, GLOW_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            float h = GLOW_SIZE / 2f;
            Color mid = new Color((c.getRed() + 190) / 2, (c.getGreen() + 210) / 2, (c.getBlue() + 255) / 2);
            g.setPaint(new RadialGradientPaint(h, h, h,
                    new float[] { 0f, .10f, .32f, 1f },
                    new Color[] { new Color(255, 255, 255, 255), new Color(c.getRed(), c.getGreen(), c.getBlue(), 220),
                                  new Color(mid.getRed(), mid.getGreen(), mid.getBlue(), 55), new Color(mid.getRed(), mid.getGreen(), mid.getBlue(), 0) }));
            g.fillRect(0, 0, GLOW_SIZE, GLOW_SIZE);
            g.dispose();
            return img;
        });
    }

    private static BufferedImage makeSpike(int n) {
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Color clear = new Color(200, 220, 255, 0), core = new Color(255, 255, 255, 240);
        g.setPaint(new GradientPaint(0, 0, clear, n / 2f, 0, core, true));
        g.fillRect(0, n / 2 - 1, n, 2);
        g.setPaint(new GradientPaint(0, 0, clear, 0, n / 2f, core, true));
        g.fillRect(n / 2 - 1, 0, 2, n);
        g.dispose();
        return img;
    }

    /**
     * Frische, nie auf dem Bildschirm gezeichnete Nebel-Sprites für das Offscreen-Rendering des Hintergrunds.
     * Hintergrund: Bereits auf den Bildschirm gezeichnete Bilder cacht Java2D als beschleunigte Oberfläche;
     * unter XRender ging dabei beim Zeichnen in ein BufferedImage der Alphakanal verloren (weiße Flächen).
     */
    static BufferedImage[] freshNebulae() {
        BufferedImage[] n = { makeNebula(new Color(120, 150, 255)), makeNebula(new Color(170, 125, 240)),
                              makeNebula(new Color(255, 205, 170)), makeNebula(new Color(4, 6, 18)) };
        for (BufferedImage b : n) {
            b.setAccelerationPriority(0f);
        }
        return n;
    }

    private static BufferedImage makeNebula(Color c) {
        int n = 64;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new RadialGradientPaint(n / 2f, n / 2f, n / 2f, new float[] { 0f, .5f, 1f },
                new Color[] { new Color(c.getRed(), c.getGreen(), c.getBlue(), 255),
                              new Color(c.getRed(), c.getGreen(), c.getBlue(), 90),
                              new Color(c.getRed(), c.getGreen(), c.getBlue(), 0) }));
        g.fillRect(0, 0, n, n);
        g.dispose();
        return img;
    }
}
