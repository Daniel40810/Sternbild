package com.dan.sternbild.render;

import java.awt.Color;

/**
 * Himmelsstimmung – die beiden Vorlagenbilder: Dämmerung (Sternbild.jpg) und Milchstraßennacht (Sternbild1.jpg).
 */
public enum SkyMood {

    DAEMMERUNG("Dämmerung",
            new float[] { 0f, .45f, .70f, .84f, .92f, .975f, 1f },
            new Color[] { new Color(0x040A22), new Color(0x0B1A4A), new Color(0x1E3372), new Color(0x46508A),
                          new Color(0x8C7496), new Color(0xD7A2A6), new Color(0xF6DEC4) },
            0.38f, new Color(0x2A2C5C), new Color(0x0A1030), new Color(255, 226, 196)),

    NACHT("Nacht",
            new float[] { 0f, .5f, .78f, .9f, .97f, 1f },
            new Color[] { new Color(0x02040F), new Color(0x060D26), new Color(0x0C1A40), new Color(0x1A2856),
                          new Color(0x33396A), new Color(0x5A5580) },
            1.0f, new Color(0x151B3C), new Color(0x03050E), new Color(200, 205, 255));

    private final String label;
    final float[] fractions;
    final Color[] colors;
    /** Stärke der Milchstraße 0..1. */
    final float milkyWay;
    final Color seaTop;
    final Color seaBottom;
    final Color horizonLight;

    SkyMood(String label, float[] fractions, Color[] colors, float milkyWay, Color seaTop, Color seaBottom,
            Color horizonLight) {
        this.label = label;
        this.fractions = fractions;
        this.colors = colors;
        this.milkyWay = milkyWay;
        this.seaTop = seaTop;
        this.seaBottom = seaBottom;
        this.horizonLight = horizonLight;
    }

    public String getLabel() { return label; }

    @Override
    public String toString() { return label; }
}
