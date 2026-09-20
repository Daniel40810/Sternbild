package com.dan.sternbild.model;

/** Zuordnungssystem für den Tierkreis. */
public enum ZodiacSystem {
    /** Sonne steht astronomisch im Sternbild (IAU-Grenzen). */
    REAL("Sonne stand wirklich in"),
    /** Klassisches astrologisches Zeichen. */
    ASTRO("Astrologisch");

    private final String label;

    ZodiacSystem(String label) { this.label = label; }

    public String getLabel() { return label; }
}
