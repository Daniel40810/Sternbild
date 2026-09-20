package com.dan.sternbild.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Mondphase aus der mittleren synodischen Monatslänge, bezogen auf den Neumond vom 6. Januar 2000, 18:14 UT.
 * Die Genauigkeit liegt bei etwa einem halben bis einem Tag – für die Darstellung reicht das.
 */
public record MoonPhase(double ageDays, double illumination, boolean waxing, String name,
                        LocalDate nextFullMoon, LocalDate nextNewMoon) {

    public static final double SYNODIC_MONTH = 29.530588853;
    private static final double REFERENCE_NEW_MOON_JD = 2451550.09766;

    public static MoonPhase at(Instant when, ZoneId zone) {
        double jd = when.toEpochMilli() / 86400000.0 + 2440587.5;
        double age = (jd - REFERENCE_NEW_MOON_JD) % SYNODIC_MONTH;
        if (age < 0) {
            age += SYNODIC_MONTH;
        }
        double illum = (1 - Math.cos(2 * Math.PI * age / SYNODIC_MONTH)) / 2;
        boolean waxing = age < SYNODIC_MONTH / 2;
        double toFull = age < SYNODIC_MONTH / 2 ? SYNODIC_MONTH / 2 - age : 1.5 * SYNODIC_MONTH - age;
        double toNew = SYNODIC_MONTH - age;
        return new MoonPhase(age, illum, waxing, nameFor(age),
                dateAt(jd + toFull, zone), dateAt(jd + toNew, zone));
    }

    /** Phasenwinkel 0..2π (0 = Neumond, π = Vollmond). */
    public double phaseAngle() {
        return 2 * Math.PI * ageDays / SYNODIC_MONTH;
    }

    private static String nameFor(double age) {
        double p = age / SYNODIC_MONTH;
        if (p < 0.0339 || p >= 0.9661) {
            return "Neumond";
        }
        if (p < 0.2161) {
            return "Zunehmende Sichel";
        }
        if (p < 0.2839) {
            return "Erstes Viertel";
        }
        if (p < 0.4661) {
            return "Zunehmender Mond";
        }
        if (p < 0.5339) {
            return "Vollmond";
        }
        if (p < 0.7161) {
            return "Abnehmender Mond";
        }
        if (p < 0.7839) {
            return "Letztes Viertel";
        }
        return "Abnehmende Sichel";
    }

    private static LocalDate dateAt(double jd, ZoneId zone) {
        long ms = Math.round((jd - 2440587.5) * 86400000.0);
        return Instant.ofEpochMilli(ms).atZone(zone).toLocalDate();
    }
}
