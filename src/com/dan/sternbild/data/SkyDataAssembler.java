package com.dan.sternbild.data;

import com.dan.sternbild.model.Constellation;
import com.dan.sternbild.model.MeteorShower;
import com.dan.sternbild.model.SceneLayout;
import com.dan.sternbild.model.SkyData;
import com.dan.sternbild.model.SkyMath;
import com.dan.sternbild.model.Star;
import com.dan.sternbild.model.ZodiacPeriod;
import com.dan.sternbild.model.ZodiacSystem;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Baut aus {@link CatalogRows} das Modell: Figurensterne (inkl. geliehener), Linien als Indexpaare,
 * lokale Projektion, Skalierung und Drehung aus der Szene.
 */
public final class SkyDataAssembler {

    private SkyDataAssembler() { }

    public static SkyData assemble(CatalogRows rows, String source) {
        if (rows.scene == null) {
            throw new IllegalStateException("Keine Szene gefunden (SB_SCENE)");
        }
        Map<Long, Star> starsById = new HashMap<>();
        Map<String, List<CatalogRows.StarRow>> starsByCode = new HashMap<>();
        for (CatalogRows.StarRow r : rows.stars) {
            starsById.put(r.id(), new Star(r.id(), r.name(), r.bayer(), r.raHours(), r.decDeg(), r.mag(),
                    parseColor(r.colorHex()), r.spectral(), r.distLy()));
            starsByCode.computeIfAbsent(r.code(), k -> new ArrayList<>()).add(r);
        }
        Map<String, List<CatalogRows.LineRow>> linesByCode = new HashMap<>();
        for (CatalogRows.LineRow l : rows.lines) {
            linesByCode.computeIfAbsent(l.code(), k -> new ArrayList<>()).add(l);
        }
        Map<String, CatalogRows.SceneItemRow> items = new HashMap<>();
        for (CatalogRows.SceneItemRow i : rows.sceneItems) {
            items.put(i.code(), i);
        }

        Map<String, Constellation> result = new LinkedHashMap<>();
        for (CatalogRows.ConstellationRow c : rows.constellations) {
            CatalogRows.SceneItemRow item = items.get(c.code());
            List<CatalogRows.StarRow> own = starsByCode.getOrDefault(c.code(), List.of());
            if (item == null || own.isEmpty()) {
                continue; // nicht in dieser Szene platziert
            }
            List<Star> figure = new ArrayList<>();
            Map<Long, Integer> index = new HashMap<>();
            own.stream().sorted(Comparator.comparingInt(CatalogRows.StarRow::no)).forEach(r -> {
                index.put(r.id(), figure.size());
                figure.add(starsById.get(r.id()));
            });
            List<int[]> lines = new ArrayList<>();
            List<CatalogRows.LineRow> ls = new ArrayList<>(linesByCode.getOrDefault(c.code(), List.of()));
            ls.sort(Comparator.comparingInt(CatalogRows.LineRow::drawOrder));
            for (CatalogRows.LineRow l : ls) {
                int a = indexOf(l.fromStarId(), figure, index, starsById);
                int b = indexOf(l.toStarId(), figure, index, starsById);
                if (a >= 0 && b >= 0) {
                    lines.add(new int[] { a, b });
                }
            }
            double[] ctr = SkyMath.center(figure);
            double rot = Math.toRadians(item.rotationDeg());
            double cos = Math.cos(rot), sin = Math.sin(rot);
            double[] lx = new double[figure.size()], ly = new double[figure.size()];
            for (int i = 0; i < figure.size(); i++) {
                Star s = figure.get(i);
                double[] p = SkyMath.gnomonic(s.raHours(), s.decDeg(), ctr[0], ctr[1]);
                double x = p[0] * item.scale(), y = p[1] * item.scale();
                lx[i] = x * cos - y * sin;
                ly[i] = x * sin + y * cos;
            }
            result.put(c.code(), new Constellation(c.code(), c.nameDe(), c.nameLa(), c.kind() == 'H',
                    c.zodiacOrder(), c.glyph(), c.description(), figure, lines, item.x(), item.y(), lx, ly,
                    ctr[0], ctr[1], item.scale(), rot));
        }
        for (CatalogRows.PeriodRow p : rows.periods) {
            Constellation c = result.get(p.code());
            if (c != null) {
                SkyData.attachPeriod(c, new ZodiacPeriod(ZodiacSystem.valueOf(p.system()), p.startMmdd(), p.endMmdd()));
            }
        }
        // Sagenfiguren
        Map<String, List<CatalogRows.StrokeRow>> strokesByCode = new HashMap<>();
        for (CatalogRows.StrokeRow r : rows.strokes) {
            strokesByCode.computeIfAbsent(r.code(), k -> new ArrayList<>()).add(r);
        }
        for (Map.Entry<String, List<CatalogRows.StrokeRow>> e : strokesByCode.entrySet()) {
            Constellation c = result.get(e.getKey());
            if (c == null) {
                continue;
            }
            List<CatalogRows.StrokeRow> rs = new ArrayList<>(e.getValue());
            rs.sort(Comparator.comparingInt(CatalogRows.StrokeRow::strokeNo));
            List<double[][]> strokes = new ArrayList<>();
            for (CatalogRows.StrokeRow r : rs) {
                double[][] p = parseStroke(r.points(), c);
                if (p.length >= 2) {
                    strokes.add(p);
                }
            }
            SkyData.attachFigure(c, strokes);
        }
        // Meteorströme
        List<MeteorShower> showers = new ArrayList<>();
        for (CatalogRows.ShowerRow r : rows.showers) {
            Constellation ref = result.get(r.refCode());
            if (ref != null) {
                showers.add(new MeteorShower(r.code(), r.name(), ref, r.raHours(), r.decDeg(), r.startMmdd(),
                        r.peakMmdd(), r.endMmdd(), r.zhr(), r.speedKms(), parseColor(r.colorHex()), r.description()));
            }
        }
        showers.sort(Comparator.comparingInt(MeteorShower::peakMmdd));
        CatalogRows.SceneRow s = rows.scene;
        return new SkyData(new SceneLayout(s.code(), s.name(), s.width(), s.height(), s.horizonY(), s.moonX(), s.moonY()),
                new ArrayList<>(result.values()), showers, source);
    }

    private static int indexOf(long starId, List<Star> figure, Map<Long, Integer> index, Map<Long, Star> all) {
        Integer i = index.get(starId);
        if (i != null) {
            return i;
        }
        Star borrowed = all.get(starId);
        if (borrowed == null) {
            return -1;
        }
        index.put(starId, figure.size());
        figure.add(borrowed);
        return figure.size() - 1;
    }

    /**
     * Punktliste einer Sagenfigur: "sI" = Figurenstern I, "sI:dx:dy" = Stern plus Versatz,
     * "px:y" = frei relativ zur Figur. Ergebnis in Szeneneinheiten relativ zum Platzierungspunkt, y nach unten.
     * Ungültige Punkte werden übersprungen.
     */
    static double[][] parseStroke(String points, Constellation c) {
        List<double[]> out = new ArrayList<>();
        if (points == null) {
            return new double[0][];
        }
        for (String tok : points.split(";")) {
            String t = tok.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                String[] parts = t.substring(1).split(":");
                if (t.charAt(0) == 's') {
                    int i = Integer.parseInt(parts[0]);
                    if (i < 0 || i >= c.getStars().size()) {
                        continue;
                    }
                    double dx = parts.length > 2 ? Double.parseDouble(parts[1]) : 0;
                    double dy = parts.length > 2 ? Double.parseDouble(parts[2]) : 0;
                    out.add(new double[] { c.getLocalX(i) + dx, -c.getLocalY(i) + dy });
                } else if (t.charAt(0) == 'p' && parts.length == 2) {
                    out.add(new double[] { Double.parseDouble(parts[0]), Double.parseDouble(parts[1]) });
                }
            } catch (RuntimeException ignored) {
                // Punkt überspringen
            }
        }
        return out.toArray(new double[0][]);
    }

    static Color parseColor(String hex) {
        if (hex == null || hex.length() != 7 || hex.charAt(0) != '#') {
            return Star.DEFAULT_COLOR;
        }
        try {
            return new Color(Integer.parseInt(hex.substring(1), 16));
        } catch (NumberFormatException e) {
            return Star.DEFAULT_COLOR;
        }
    }
}
