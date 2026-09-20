package com.dan.sternbild.model;

import java.awt.Color;

/**
 * Ein Figurenstern mit Katalogwerten (J2000, gerundet).
 *
 * @param id       Schlüssel aus SB_STAR
 * @param name     Eigenname oder Bayer-Bezeichnung
 * @param bayer    Bayer-Bezeichnung, z. B. "α Lyr" (kann null sein)
 * @param raHours  Rektaszension in Stunden
 * @param decDeg   Deklination in Grad
 * @param mag      scheinbare Helligkeit in mag
 * @param color    Sternfarbe
 * @param spectral Spektralklasse (kann null sein)
 * @param distLy   Entfernung in Lichtjahren, 0 = unbekannt
 */
public record Star(long id, String name, String bayer, double raHours, double decDeg,
                   double mag, Color color, String spectral, int distLy) {

    /** Standardfarbe für Sterne ohne gepflegte Farbe. */
    public static final Color DEFAULT_COLOR = new Color(0xEEF2FF);
}
