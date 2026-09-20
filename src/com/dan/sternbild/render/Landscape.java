package com.dan.sternbild.render;

/**
 * Ostsee-Vordergrund: Steilküste links mit Leuchtturm, Düne rechts mit Strandkorb.
 * Alle Formen sind Funktionen der Bildbreite und -höhe, damit Leuchtfeuer, Gras und Spiegelungen
 * zur statischen Silhouette passen.
 */
final class Landscape {

    static final double COAST_END = 0.36;
    static final double LIGHTHOUSE_X = 0.215;
    static final double DUNE_START = 0.60;
    static final double KORB_X = 0.815;
    static final double PERSON_X = 0.885;

    private Landscape() { }

    /** Oberkante der Küste bei x (Bildschirm) oder NaN, wenn dort Wasser ist. */
    static double coastTop(double x, int w, double horizon, int h) {
        double t = x / (w * COAST_END);
        if (t >= 1) {
            return Double.NaN;
        }
        double env = Math.pow(Math.sin(Math.PI * Math.min(1, (t + 0.18) / 1.18)), 0.55);
        double u = x / w * 100;
        double hill = 0.028 + 0.012 * Math.sin(u * 0.21 + 0.6) + 0.006 * Math.sin(u * 0.83 + 2) + 0.0025 * Math.sin(u * 2.9);
        return horizon - env * hill * h;
    }

    /** Oberkante der Düne bei x oder NaN links vom Dünenanfang. */
    static double duneTop(double x, int w, double horizon, int h) {
        double t = (x - w * DUNE_START) / (w * (1 - DUNE_START));
        if (t < 0) {
            return Double.NaN;
        }
        double s = t * t * (3 - 2 * Math.min(1, t));
        double base = horizon + (h - horizon) * (1.05 - 0.78 * s);
        double u = x / w * 100;
        return base - h * (0.006 * Math.sin(u * 0.35) + 0.003 * Math.sin(u * 1.7 + 1));
    }

    /** Wasser (keine Küste, keine Düne) an Position (x, y)? */
    static boolean isWater(double x, double y, int w, double horizon, int h) {
        if (y <= horizon) {
            return false;
        }
        double d = duneTop(x, w, horizon, h);
        return Double.isNaN(d) || y < d;
    }
}
