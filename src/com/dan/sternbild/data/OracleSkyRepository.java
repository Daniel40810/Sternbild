package com.dan.sternbild.data;

import com.dan.sternbild.model.SkyData;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Liest die SB_-Tabellen aus dem Schema DEMO über ojdbc11.
 */
public final class OracleSkyRepository implements SkyRepository {

    private final DbConfig config;

    public OracleSkyRepository(DbConfig config) {
        this.config = config;
    }

    @Override
    public SkyData load(String sceneCode) throws SQLException {
        DriverManager.setLoginTimeout(config.timeoutSeconds());
        try (Connection con = DriverManager.getConnection(config.url(), config.user(), config.password())) {
            con.setReadOnly(true);
            CatalogRows rows = new CatalogRows();
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT IAU_CODE, NAME_DE, NAME_LA, KIND, ZODIAC_ORDER, ZODIAC_GLYPH, DESCRIPTION "
                    + "FROM SB_CONSTELLATION ORDER BY CONST_ID");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String kind = rs.getString(4);
                    rows.constellations.add(new CatalogRows.ConstellationRow(rs.getString(1), rs.getString(2),
                            rs.getString(3), kind == null || kind.isEmpty() ? 'S' : kind.charAt(0),
                            rs.getInt(5), rs.getString(6), rs.getString(7)));
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT s.STAR_ID, c.IAU_CODE, s.STAR_NO, s.NAME, s.BAYER, s.RA_HOURS, s.DEC_DEG, s.MAG, "
                    + "s.COLOR_HEX, s.SPECTRAL, s.DIST_LY "
                    + "FROM SB_STAR s JOIN SB_CONSTELLATION c ON c.CONST_ID = s.CONST_ID "
                    + "ORDER BY c.CONST_ID, s.STAR_NO");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.stars.add(new CatalogRows.StarRow(rs.getLong(1), rs.getString(2), rs.getInt(3),
                            rs.getString(4), rs.getString(5), rs.getDouble(6), rs.getDouble(7), rs.getDouble(8),
                            rs.getString(9), rs.getString(10), rs.getInt(11)));
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT c.IAU_CODE, l.STAR_FROM, l.STAR_TO, l.DRAW_ORDER "
                    + "FROM SB_CONST_LINE l JOIN SB_CONSTELLATION c ON c.CONST_ID = l.CONST_ID "
                    + "ORDER BY c.CONST_ID, l.DRAW_ORDER");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.lines.add(new CatalogRows.LineRow(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getInt(4)));
                }
            }
            rows.scene = loadScene(con, sceneCode);
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT c.IAU_CODE, i.POS_X, i.POS_Y, i.ITEM_SCALE, i.ROTATION_DEG "
                    + "FROM SB_SCENE_ITEM i JOIN SB_SCENE sc ON sc.SCENE_ID = i.SCENE_ID "
                    + "JOIN SB_CONSTELLATION c ON c.CONST_ID = i.CONST_ID WHERE sc.CODE = ?")) {
                ps.setString(1, sceneCode);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.sceneItems.add(new CatalogRows.SceneItemRow(rs.getString(1), rs.getDouble(2),
                                rs.getDouble(3), rs.getDouble(4), rs.getDouble(5)));
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT c.IAU_CODE, p.SIGN_SYSTEM, p.START_MMDD, p.END_MMDD "
                    + "FROM SB_ZODIAC_PERIOD p JOIN SB_CONSTELLATION c ON c.CONST_ID = p.CONST_ID");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.periods.add(new CatalogRows.PeriodRow(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4)));
                }
            }
            loadOptional(con, rows);
            if (rows.scene == null) {
                throw new SQLException("Szene '" + sceneCode + "' fehlt in SB_SCENE – 02_sb_data.sql ausgeführt?");
            }
            return SkyDataAssembler.assemble(rows, "Oracle · " + config.describe());
        }
    }

    /** Szene inkl. Mondposition; fehlen die Mondspalten (ältere Installation), ohne Mond. */
    private static CatalogRows.SceneRow loadScene(Connection con, String sceneCode) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT CODE, NAME, WIDTH_UNITS, HEIGHT_UNITS, HORIZON_Y, MOON_X, MOON_Y FROM SB_SCENE WHERE CODE = ?")) {
            ps.setString(1, sceneCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                double mx = rs.getDouble(6);
                boolean noX = rs.wasNull();
                double my = rs.getDouble(7);
                boolean noY = rs.wasNull();
                return new CatalogRows.SceneRow(rs.getString(1), rs.getString(2), rs.getDouble(3), rs.getDouble(4),
                        rs.getDouble(5), noX ? Double.NaN : mx, noY ? Double.NaN : my);
            }
        } catch (SQLException e) {
            if (e.getErrorCode() != 904) {   // ORA-00904: Spalte unbekannt
                throw e;
            }
        }
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT CODE, NAME, WIDTH_UNITS, HEIGHT_UNITS, HORIZON_Y FROM SB_SCENE WHERE CODE = ?")) {
            ps.setString(1, sceneCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new CatalogRows.SceneRow(rs.getString(1), rs.getString(2), rs.getDouble(3),
                        rs.getDouble(4), rs.getDouble(5), Double.NaN, Double.NaN) : null;
            }
        }
    }

    /** Sagenfiguren und Meteorströme: fehlen die Tabellen (ORA-00942), läuft die App ohne sie. */
    private static void loadOptional(Connection con, CatalogRows rows) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT c.IAU_CODE, f.STROKE_NO, f.POINTS FROM SB_FIGURE_STROKE f "
                + "JOIN SB_CONSTELLATION c ON c.CONST_ID = f.CONST_ID ORDER BY c.CONST_ID, f.STROKE_NO");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.strokes.add(new CatalogRows.StrokeRow(rs.getString(1), rs.getInt(2), rs.getString(3)));
            }
        } catch (SQLException e) {
            if (e.getErrorCode() != 942) {
                throw e;
            }
        }
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT m.CODE, m.NAME_DE, c.IAU_CODE, m.RADIANT_RA, m.RADIANT_DEC, m.START_MMDD, m.PEAK_MMDD, "
                + "m.END_MMDD, m.ZHR, m.SPEED_KMS, m.COLOR_HEX, m.DESCRIPTION "
                + "FROM SB_METEOR_SHOWER m JOIN SB_CONSTELLATION c ON c.CONST_ID = m.REF_CONST_ID ORDER BY m.PEAK_MMDD");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.showers.add(new CatalogRows.ShowerRow(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getDouble(4), rs.getDouble(5), rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getInt(9),
                        rs.getInt(10), rs.getString(11), rs.getString(12)));
            }
        } catch (SQLException e) {
            if (e.getErrorCode() != 942) {
                throw e;
            }
        }
    }
}
