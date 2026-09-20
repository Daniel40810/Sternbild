package com.dan.sternbild.data;

import com.dan.sternbild.model.SkyData;

/** Eingebaute Daten – identisch mit den Oracle-Skripten, ohne Datenbank. */
public final class BuiltinSkyRepository implements SkyRepository {

    @Override
    public SkyData load(String sceneCode) {
        CatalogRows rows = new CatalogRows();
        for (Object[] r : BuiltinCatalog.CONSTELLATIONS) {
            rows.constellations.add(new CatalogRows.ConstellationRow((String) r[0], (String) r[1], (String) r[2],
                    (Character) r[3], (Integer) r[4], (String) r[5], (String) r[6]));
        }
        for (Object[] r : BuiltinCatalog.STARS) {
            rows.stars.add(new CatalogRows.StarRow((Long) r[0], (String) r[1], (Integer) r[2], (String) r[3],
                    (String) r[4], (Double) r[5], (Double) r[6], (Double) r[7], (String) r[8], (String) r[9],
                    (Integer) r[10]));
        }
        for (Object[] r : BuiltinCatalog.LINES) {
            rows.lines.add(new CatalogRows.LineRow((String) r[0], (Long) r[1], (Long) r[2], (Integer) r[3]));
        }
        Object[] s = BuiltinCatalog.SCENE;
        rows.scene = new CatalogRows.SceneRow((String) s[0], (String) s[1], (Double) s[2], (Double) s[3], (Double) s[4],
                (Double) s[5], (Double) s[6]);
        for (Object[] r : BuiltinCatalog.STROKES) {
            rows.strokes.add(new CatalogRows.StrokeRow((String) r[0], (Integer) r[1], (String) r[2]));
        }
        for (Object[] r : BuiltinCatalog.SHOWERS) {
            rows.showers.add(new CatalogRows.ShowerRow((String) r[0], (String) r[1], (String) r[2], (Double) r[3],
                    (Double) r[4], (Integer) r[5], (Integer) r[6], (Integer) r[7], (Integer) r[8], (Integer) r[9],
                    (String) r[10], (String) r[11]));
        }
        for (Object[] r : BuiltinCatalog.SCENE_ITEMS) {
            rows.sceneItems.add(new CatalogRows.SceneItemRow((String) r[0], (Double) r[1], (Double) r[2],
                    (Double) r[3], (Double) r[4]));
        }
        for (Object[] r : BuiltinCatalog.PERIODS) {
            rows.periods.add(new CatalogRows.PeriodRow((String) r[0], (String) r[1], (Integer) r[2], (Integer) r[3]));
        }
        return SkyDataAssembler.assemble(rows, "Eingebaute Daten");
    }
}
