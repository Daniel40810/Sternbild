package com.dan.sternbild.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Rohzeilen, wie sie aus Oracle oder dem eingebauten Katalog kommen.
 * Beide Quellen liefern dieselbe Form; {@link SkyDataAssembler} baut daraus das Modell.
 */
public final class CatalogRows {

    public record ConstellationRow(String code, String nameDe, String nameLa, char kind, int zodiacOrder,
                                   String glyph, String description) { }

    public record StarRow(long id, String code, int no, String name, String bayer, double raHours, double decDeg,
                          double mag, String colorHex, String spectral, int distLy) { }

    public record LineRow(String code, long fromStarId, long toStarId, int drawOrder) { }

    public record SceneRow(String code, String name, double width, double height, double horizonY,
                           double moonX, double moonY) { }

    public record StrokeRow(String code, int strokeNo, String points) { }

    public record ShowerRow(String code, String name, String refCode, double raHours, double decDeg, int startMmdd,
                            int peakMmdd, int endMmdd, int zhr, int speedKms, String colorHex, String description) { }

    public record SceneItemRow(String code, double x, double y, double scale, double rotationDeg) { }

    public record PeriodRow(String code, String system, int startMmdd, int endMmdd) { }

    public final List<ConstellationRow> constellations = new ArrayList<>();
    public final List<StarRow> stars = new ArrayList<>();
    public final List<LineRow> lines = new ArrayList<>();
    public final List<SceneItemRow> sceneItems = new ArrayList<>();
    public final List<PeriodRow> periods = new ArrayList<>();
    public final List<StrokeRow> strokes = new ArrayList<>();
    public final List<ShowerRow> showers = new ArrayList<>();
    public SceneRow scene;
}
