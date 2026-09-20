package com.dan.sternbild.model;

import java.awt.Color;
import java.time.LocalDate;

/**
 * Ein Meteorstrom (SB_METEOR_SHOWER). Der Radiant wird über das Bezugssternbild in die Szene projiziert.
 *
 * @param startMmdd erster Tag der Aktivität (Monat*100+Tag)
 * @param peakMmdd  Maximum
 * @param endMmdd   letzter Tag der Aktivität
 * @param zhr       Zenithal Hourly Rate im Maximum
 * @param speedKms  Eintrittsgeschwindigkeit in km/s
 */
public record MeteorShower(String code, String name, Constellation reference, double radiantRa, double radiantDec,
                           int startMmdd, int peakMmdd, int endMmdd, int zhr, int speedKms, Color color,
                           String description) {

    /** Radiant in Szeneneinheiten {x, y}. */
    public double[] radiantScene() {
        double[] l = reference.projectLocal(radiantRa, radiantDec);
        return new double[] { reference.getPosX() + l[0], reference.getPosY() - l[1] };
    }

    /**
     * Aktivität 0..1 am Datum: steigt vom Beginn linear zum Maximum und fällt bis zum Ende ab
     * (nicht linear in der Wirklichkeit, aber als Rate für die Animation gut genug).
     */
    public double activity(LocalDate date) {
        int d = dayOfYear(date.getMonthValue() * 100 + date.getDayOfMonth());
        int s = dayOfYear(startMmdd), p = dayOfYear(peakMmdd), e = dayOfYear(endMmdd);
        // Zeitraum über den Jahreswechsel „aufklappen“
        if (e < s) {
            e += 365;
            if (p < s) {
                p += 365;
            }
            if (d < s) {
                d += 365;
            }
        }
        if (d < s || d > e) {
            return 0;
        }
        if (d == p) {
            return 1;
        }
        if (d <= p) {
            return p == s ? 1 : 0.15 + 0.85 * (d - s) / (double) (p - s);
        }
        return e == p ? 1 : 0.15 + 0.85 * (e - d) / (double) (e - p);
    }

    public boolean isActive(LocalDate date) {
        return activity(date) > 0;
    }

    /** z. B. "17.07. – 24.08." */
    public String formatPeriod() {
        return fmt(startMmdd) + " – " + fmt(endMmdd);
    }

    public String formatPeak() {
        return fmt(peakMmdd);
    }

    private static String fmt(int mmdd) {
        return String.format("%02d.%02d.", mmdd % 100, mmdd / 100);
    }

    private static int dayOfYear(int mmdd) {
        int[] cum = { 0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334 };
        return cum[mmdd / 100 - 1] + mmdd % 100;
    }

    @Override
    public String toString() {
        return name;
    }
}
