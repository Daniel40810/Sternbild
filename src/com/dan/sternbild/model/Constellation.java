package com.dan.sternbild.model;

import java.util.Collections;
import java.util.List;

/**
 * Ein Sternbild (oder Sternhaufen) mit seiner Figur und seiner Platzierung in der Szene.
 * <p>
 * {@link #getStars()} enthält die Figurensterne – eigene und über Linien geliehene (Alpheratz).
 * {@link #getLocalX(int)}/{@link #getLocalY(int)} sind die fertig projizierten, skalierten und gedrehten
 * Positionen in Szeneneinheiten relativ zum Platzierungspunkt; y wächst nach oben.
 */
public final class Constellation {

    private final String code;
    private final String nameDe;
    private final String nameLa;
    private final boolean cluster;
    private final int zodiacOrder;
    private final String glyph;
    private final String description;
    private final List<Star> stars;
    private final List<int[]> lines;
    private final double posX;
    private final double posY;
    private final double[] localX;
    private final double[] localY;
    private final double ra0;
    private final double dec0;
    private final double scale;
    private final double rotation;
    private List<double[][]> figureStrokes = List.of();
    private ZodiacPeriod realPeriod;
    private ZodiacPeriod astroPeriod;

    public Constellation(String code, String nameDe, String nameLa, boolean cluster, int zodiacOrder,
                         String glyph, String description, List<Star> stars, List<int[]> lines,
                         double posX, double posY, double[] localX, double[] localY,
                         double ra0, double dec0, double scale, double rotationRad) {
        this.code = code;
        this.nameDe = nameDe;
        this.nameLa = nameLa;
        this.cluster = cluster;
        this.zodiacOrder = zodiacOrder;
        this.glyph = glyph;
        this.description = description;
        this.stars = Collections.unmodifiableList(stars);
        this.lines = Collections.unmodifiableList(lines);
        this.posX = posX;
        this.posY = posY;
        this.localX = localX.clone();
        this.localY = localY.clone();
        this.ra0 = ra0;
        this.dec0 = dec0;
        this.scale = scale;
        this.rotation = rotationRad;
    }

    /**
     * Projiziert eine beliebige Himmelsposition mit derselben Abbildung wie die Figurensterne.
     * Ergebnis relativ zum Platzierungspunkt, y nach oben – wie {@link #getLocalX(int)}/{@link #getLocalY(int)}.
     */
    public double[] projectLocal(double raHours, double decDeg) {
        double[] p = SkyMath.gnomonic(raHours, decDeg, ra0, dec0);
        double x = p[0] * scale, y = p[1] * scale;
        double c = Math.cos(rotation), s = Math.sin(rotation);
        return new double[] { x * c - y * s, x * s + y * c };
    }

    /** Strichzüge der Sagenfigur: Punkte relativ zum Platzierungspunkt, x nach rechts, y nach unten. */
    public List<double[][]> getFigureStrokes() { return figureStrokes; }

    void setFigureStrokes(List<double[][]> strokes) {
        figureStrokes = Collections.unmodifiableList(strokes);
    }

    public String getCode() { return code; }
    public String getNameDe() { return nameDe; }
    public String getNameLa() { return nameLa; }
    public boolean isCluster() { return cluster; }
    public boolean isZodiac() { return zodiacOrder > 0; }
    public int getZodiacOrder() { return zodiacOrder; }
    /** Tierkreissymbol oder null. */
    public String getGlyph() { return glyph; }
    public String getDescription() { return description; }
    public List<Star> getStars() { return stars; }
    /** Linien als Indexpaare in {@link #getStars()}, in Zeichenreihenfolge. */
    public List<int[]> getLines() { return lines; }
    public double getPosX() { return posX; }
    public double getPosY() { return posY; }
    public double getLocalX(int i) { return localX[i]; }
    public double getLocalY(int i) { return localY[i]; }
    public ZodiacPeriod getRealPeriod() { return realPeriod; }
    public ZodiacPeriod getAstroPeriod() { return astroPeriod; }

    void setPeriod(ZodiacPeriod p) {
        if (p.system() == ZodiacSystem.REAL) {
            realPeriod = p;
        } else {
            astroPeriod = p;
        }
    }

    /** Hellster Stern der Figur. */
    public Star getBrightestStar() {
        Star best = stars.get(0);
        for (Star s : stars) {
            if (s.mag() < best.mag()) {
                best = s;
            }
        }
        return best;
    }

    public ZodiacPeriod getPeriod(ZodiacSystem system) {
        return system == ZodiacSystem.REAL ? realPeriod : astroPeriod;
    }

    @Override
    public String toString() { return nameDe; }
}
