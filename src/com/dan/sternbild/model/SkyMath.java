package com.dan.sternbild.model;

import java.util.List;

/**
 * Reine Rechenfunktionen: Figurenmittelpunkt und lokale gnomonische Projektion.
 * Osten liegt links – wie beim Blick nach Süden.
 */
public final class SkyMath {

    private SkyMath() { }

    /** Mittelpunkt einer Figur: zirkulärer Mittelwert der RA, arithmetischer Mittelwert der Dek. */
    public static double[] center(List<Star> stars) {
        double sx = 0, sy = 0, dec = 0;
        for (Star s : stars) {
            double a = Math.toRadians(s.raHours() * 15);
            sx += Math.cos(a);
            sy += Math.sin(a);
            dec += s.decDeg();
        }
        double ra = Math.toDegrees(Math.atan2(sy, sx)) / 15.0;
        ra = ((ra % 24) + 24) % 24;
        return new double[] { ra, dec / stars.size() };
    }

    /**
     * Gnomonische Projektion um (ra0, dec0).
     *
     * @return {x, y} in Grad; x wächst nach Westen (rechts), y nach Norden (oben)
     */
    public static double[] gnomonic(double raHours, double decDeg, double ra0Hours, double dec0Deg) {
        double a = Math.toRadians(raHours * 15), d = Math.toRadians(decDeg);
        double a0 = Math.toRadians(ra0Hours * 15), d0 = Math.toRadians(dec0Deg);
        double cosc = Math.sin(d0) * Math.sin(d) + Math.cos(d0) * Math.cos(d) * Math.cos(a - a0);
        double xi = Math.cos(d) * Math.sin(a - a0) / cosc;
        double eta = (Math.cos(d0) * Math.sin(d) - Math.sin(d0) * Math.cos(d) * Math.cos(a - a0)) / cosc;
        return new double[] { -Math.toDegrees(xi), Math.toDegrees(eta) };
    }
}
