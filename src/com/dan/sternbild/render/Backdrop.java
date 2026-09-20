package com.dan.sternbild.render;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Statische Ebenen, einmal pro Fenstergröße und Stimmung gerendert:
 * Himmel, Milchstraße, schwache Sterne, Meer ({@link #renderSky}) und die Silhouetten ({@link #renderForeground}).
 */
final class Backdrop {

    private Backdrop() { }

    static BufferedImage renderSky(SceneTransform t, SkyMood mood, boolean milkyWay, long seed) {
        int w = t.width, h = t.height;
        double hz = t.horizonScreen;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        Random rnd = new Random(seed);
        BufferedImage[] neb = Sprites.freshNebulae();

        // Himmel
        g.setPaint(new LinearGradientPaint(0f, (float) Math.min(0, hz - h * 1.25), 0f, (float) hz + 1,
                mood.fractions, mood.colors));
        g.fillRect(0, 0, w, (int) Math.ceil(hz) + 1);

        // Milchstraße: diagonales Band wie in Sternbild1.jpg
        float mw = milkyWay ? mood.milkyWay : 0.12f * mood.milkyWay;
        Composite old = g.getComposite();
        for (int i = 0; i < 900; i++) {
            double sx = rnd.nextDouble() * (t.sceneWidth + 40) - 20;
            double sy = bandY(sx) + rnd.nextGaussian() * (5.5 + 3 * Math.sin(sx / 23));
            double size = (8 + rnd.nextDouble() * 22) * t.scale;
            double x = t.x(sx), y = t.y(sy);
            if (y > hz + size * 0.2) {
                continue;
            }
            int k = rnd.nextDouble() < 0.18 ? 2 : (rnd.nextDouble() < 0.45 ? 1 : 0);
            float a = (float) (0.028 + 0.05 * rnd.nextDouble()) * mw;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, a)));
            g.drawImage(neb[k], (int) (x - size / 2), (int) (y - size / 2), (int) size, (int) size, null);
        }
        // Dunkelwolken (Staubband mitten im Band)
        for (int i = 0; i < 160; i++) {
            double sx = rnd.nextDouble() * t.sceneWidth;
            double sy = bandY(sx) + 1.5 + rnd.nextGaussian() * 1.8;
            double size = (3 + rnd.nextDouble() * 7) * t.scale;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.10f * mw));
            g.drawImage(neb[3], (int) (t.x(sx) - size / 2), (int) (t.y(sy) - size / 2), (int) size, (int) size, null);
        }
        // Sternstaub im Band
        for (int i = 0; i < (int) (4200 * Math.max(0.25, mw)); i++) {
            double sx = rnd.nextDouble() * t.sceneWidth;
            double sy = bandY(sx) + rnd.nextGaussian() * 6.5;
            dot(g, t.x(sx), t.y(sy), hz, (float) (0.15 + 0.5 * rnd.nextDouble()) * Math.max(0.35f, mw),
                    rnd.nextDouble() < 0.2 ? new Color(232, 216, 255) : new Color(222, 231, 255), rnd.nextDouble() < 0.1 ? 1.6 : 1.0);
        }
        // Schwache Sterne überall
        for (int i = 0; i < 2600; i++) {
            double x = rnd.nextDouble() * w, y = rnd.nextDouble() * hz;
            double fade = mood == SkyMood.DAEMMERUNG ? Math.max(0, 1 - Math.pow(y / hz, 3) * 1.1) : 1;
            float a = (float) ((0.12 + 0.55 * Math.pow(rnd.nextDouble(), 2)) * fade);
            double pick = rnd.nextDouble();
            Color c = pick < 0.1 ? new Color(255, 220, 185) : (pick < 0.28 ? new Color(214, 226, 255) : new Color(242, 245, 255));
            dot(g, x, y, hz, a, c, rnd.nextDouble() < 0.07 ? 1.7 : 1.1);
        }
        g.setComposite(old);

        // Horizontglühen
        int glowH = (int) (h * 0.16);
        Color hl = mood.horizonLight;
        g.setPaint(new GradientPaint(0, (float) (hz - glowH), new Color(hl.getRed(), hl.getGreen(), hl.getBlue(), 0),
                0, (float) hz, new Color(hl.getRed(), hl.getGreen(), hl.getBlue(), mood == SkyMood.NACHT ? 45 : 90)));
        g.fill(new Rectangle2D.Double(0, hz - glowH, w, glowH + 1));

        // Meer
        g.setPaint(new GradientPaint(0, (float) hz, mood.seaTop, 0, h, mood.seaBottom));
        g.fill(new Rectangle2D.Double(0, hz, w, h - hz));
        // Widerschein des Horizontlichts auf dem Wasser
        g.setPaint(new GradientPaint(0, (float) hz, new Color(hl.getRed(), hl.getGreen(), hl.getBlue(), mood == SkyMood.NACHT ? 40 : 75),
                0, (float) (hz + (h - hz) * 0.45), new Color(hl.getRed(), hl.getGreen(), hl.getBlue(), 0)));
        g.fill(new Rectangle2D.Double(0, hz, w, (h - hz) * 0.45));
        g.setColor(new Color(hl.getRed(), hl.getGreen(), hl.getBlue(), 150));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(0, (int) hz, w, (int) hz);
        g.dispose();
        return img;
    }

    /** Mittellinie der Milchstraße in Szeneneinheiten: von unten links nach oben rechts. */
    static double bandY(double sx) {
        return 96 - 0.5 * sx + 7 * Math.sin(sx / 37.0 + 0.4);
    }

    private static void dot(Graphics2D g, double x, double y, double hz, float a, Color c, double size) {
        if (y > hz - 1 || a <= 0.01f) {
            return;
        }
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, a)));
        g.setColor(c);
        g.fill(new Rectangle2D.Double(x - size / 2, y - size / 2, size, size));
    }

    /** Transparente Vordergrund-Ebene: Küste mit Leuchtturm, Düne mit Strandkorb und Mensch. */
    static BufferedImage renderForeground(SceneTransform t) {
        int w = t.width, h = t.height;
        double hz = t.horizonScreen;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color land = new Color(0x03050E);

        // Küste
        Path2D coast = new Path2D.Double();
        coast.moveTo(0, hz + 1.5);
        for (double x = 0; x <= w * Landscape.COAST_END; x += 2) {
            double y = Landscape.coastTop(x, w, hz, h);
            coast.lineTo(x, Double.isNaN(y) ? hz : Math.min(hz, y));
        }
        coast.lineTo(w * Landscape.COAST_END, hz + 1.5);
        coast.closePath();
        g.setColor(land);
        g.fill(coast);
        // Leuchtturm
        double lx = w * Landscape.LIGHTHOUSE_X, ly = Landscape.coastTop(lx, w, hz, h) + 1;
        double th = h * 0.085, tw = th * 0.15;
        Path2D tower = new Path2D.Double();
        tower.moveTo(lx - tw, ly);
        tower.lineTo(lx - tw * 0.62, ly - th);
        tower.lineTo(lx + tw * 0.62, ly - th);
        tower.lineTo(lx + tw, ly);
        tower.closePath();
        g.fill(tower);
        g.fill(new Rectangle2D.Double(lx - tw * 0.9, ly - th - tw * 0.45, tw * 1.8, tw * 0.45));
        Path2D roof = new Path2D.Double();
        roof.moveTo(lx - tw * 0.62, ly - th - tw * 1.35);
        roof.lineTo(lx, ly - th - tw * 2.2);
        roof.lineTo(lx + tw * 0.62, ly - th - tw * 1.35);
        roof.closePath();
        g.fill(roof);
        g.setColor(new Color(0x2A0E14));
        g.fill(new Rectangle2D.Double(lx - tw * 0.72, ly - th * 0.55, tw * 1.44, th * 0.12));
        // Wärterhaus
        g.setColor(land);
        g.fill(new Rectangle2D.Double(lx + tw * 1.2, ly - th * 0.2, tw * 2.4, th * 0.2 + 1));
        Path2D house = new Path2D.Double();
        house.moveTo(lx + tw * 1.0, ly - th * 0.2);
        house.lineTo(lx + tw * 2.4, ly - th * 0.33);
        house.lineTo(lx + tw * 3.8, ly - th * 0.2);
        house.closePath();
        g.fill(house);

        // Düne
        Path2D dune = new Path2D.Double();
        dune.moveTo(w * Landscape.DUNE_START, h + 2);
        for (double x = w * Landscape.DUNE_START; x <= w + 2; x += 2) {
            dune.lineTo(x, Math.min(h + 2, Landscape.duneTop(x, w, hz, h)));
        }
        dune.lineTo(w + 2, h + 2);
        dune.closePath();
        g.setColor(new Color(0x020309));
        g.fill(dune);

        // Strandkorb
        double kx = w * Landscape.KORB_X, ky = Landscape.duneTop(kx, w, hz, h) + 3;
        double ks = h * 0.075;
        Path2D korb = new Path2D.Double();
        korb.moveTo(kx - ks * 0.55, ky);
        korb.lineTo(kx - ks * 0.55, ky - ks * 0.52);
        korb.lineTo(kx - ks * 0.38, ky - ks * 0.56);
        korb.lineTo(kx - ks * 0.33, ky - ks * 1.02);
        korb.quadTo(kx - ks * 0.26, ky - ks * 1.30, kx + ks * 0.08, ky - ks * 1.33);
        korb.quadTo(kx + ks * 0.46, ky - ks * 1.34, kx + ks * 0.50, ky - ks * 1.06);
        korb.lineTo(kx + ks * 0.53, ky);
        korb.closePath();
        g.fill(korb);
        // Fußstütze und Kufen
        g.fill(new Rectangle2D.Double(kx - ks * 0.82, ky - ks * 0.17, ks * 0.3, ks * 0.06));
        g.fill(new Rectangle2D.Double(kx - ks * 0.6, ky - ks * 0.04, ks * 1.18, ks * 0.07));
        // Korbgeflecht, nur als Hauch sichtbar
        g.setColor(new Color(143, 180, 255, 24));
        g.setStroke(new BasicStroke(1f));
        for (int i = 1; i < 8; i++) {
            double yy = ky - ks * 0.14 * i;
            double left = yy < ky - ks * 0.56 ? kx - ks * 0.31 : kx - ks * 0.52;
            g.draw(new java.awt.geom.Line2D.Double(left + 2, yy, kx + ks * 0.48, yy));
        }
        // Mensch als Maßstab (wie in Sternbild.jpg)
        g.setColor(new Color(0x020309));
        double px = w * Landscape.PERSON_X, py = Landscape.duneTop(px, w, hz, h) + 2, ph = h * 0.05;
        g.fill(new java.awt.geom.Ellipse2D.Double(px - ph * 0.09, py - ph, ph * 0.18, ph * 0.18));
        Path2D body = new Path2D.Double();
        body.moveTo(px - ph * 0.12, py - ph * 0.8);
        body.lineTo(px + ph * 0.12, py - ph * 0.8);
        body.lineTo(px + ph * 0.1, py - ph * 0.42);
        body.lineTo(px + ph * 0.09, py);
        body.lineTo(px + ph * 0.02, py);
        body.lineTo(px, py - ph * 0.4);
        body.lineTo(px - ph * 0.02, py);
        body.lineTo(px - ph * 0.09, py);
        body.lineTo(px - ph * 0.1, py - ph * 0.42);
        body.closePath();
        g.fill(body);
        g.dispose();
        return img;
    }
}
