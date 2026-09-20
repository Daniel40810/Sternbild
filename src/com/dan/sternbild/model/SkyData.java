package com.dan.sternbild.model;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Vollständiger, unveränderlicher Datenstand einer Himmelsszene.
 */
public final class SkyData {

    private final SceneLayout scene;
    private final List<Constellation> constellations;
    private final String source;
    private final List<MeteorShower> showers;

    public SkyData(SceneLayout scene, List<Constellation> constellations, List<MeteorShower> showers, String source) {
        this.scene = scene;
        this.constellations = Collections.unmodifiableList(constellations);
        this.showers = Collections.unmodifiableList(showers);
        this.source = source;
    }

    /** Meteorströme, nach Maximum im Jahr sortiert. */
    public List<MeteorShower> getShowers() { return showers; }

    /** Der am Datum stärkste aktive Meteorstrom. */
    public Optional<MeteorShower> activeShower(LocalDate date) {
        MeteorShower best = null;
        double bestRate = 0;
        for (MeteorShower m : showers) {
            double r = m.activity(date) * m.zhr();
            if (r > bestRate) {
                bestRate = r;
                best = m;
            }
        }
        return Optional.ofNullable(best);
    }

    public SceneLayout getScene() { return scene; }
    public List<Constellation> getConstellations() { return constellations; }
    /** Herkunft für die Statusanzeige, z. B. "Oracle · DEMO". */
    public String getSource() { return source; }

    public Optional<Constellation> find(String code) {
        return constellations.stream().filter(c -> c.getCode().equals(code)).findFirst();
    }

    /** Tierkreis-Sternbild, in dem die Sonne am Datum steht (REAL) bzw. astrologisches Zeichen (ASTRO). */
    public Optional<Constellation> zodiacFor(LocalDate date, ZodiacSystem system) {
        for (Constellation c : constellations) {
            ZodiacPeriod p = c.getPeriod(system);
            if (p != null && p.contains(date)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    /** Zugriff für den Assembler. */
    public static void attachPeriod(Constellation c, ZodiacPeriod p) {
        c.setPeriod(p);
    }

    /** Zugriff für den Assembler. */
    public static void attachFigure(Constellation c, List<double[][]> strokes) {
        c.setFigureStrokes(strokes);
    }
}
