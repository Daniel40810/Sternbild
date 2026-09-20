package com.dan.sternbild;

import com.dan.sternbild.data.BuiltinSkyRepository;
import com.dan.sternbild.data.DbConfig;
import com.dan.sternbild.data.OracleSkyRepository;
import com.dan.sternbild.model.Constellation;
import com.dan.sternbild.model.MeteorShower;
import com.dan.sternbild.model.MoonPhase;
import com.dan.sternbild.model.SkyData;
import com.dan.sternbild.model.Star;
import com.dan.sternbild.model.ZodiacSystem;
import com.dan.sternbild.render.SkyCanvas;
import com.dan.sternbild.render.SkyMood;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/** Headless-Checks für Daten, Tierkreis-Logik, SQL-Konsistenz und Renderer. Nicht Teil des JARs. */
public final class SternbildSmokeTest {

    private static int ok, fail;

    public static void main(String[] args) throws Exception {
        SkyData d = new BuiltinSkyRepository().load("OSTSEE");
        check("24 Sternbilder platziert", d.getConstellations().size() == 24);
        check("13 Tierkreis-Sternbilder", d.getConstellations().stream().filter(Constellation::isZodiac).count() == 13);
        Set<Long> ids = new HashSet<>();
        int lines = 0;
        boolean indicesOk = true;
        for (Constellation c : d.getConstellations()) {
            for (Star s : c.getStars()) ids.add(s.id());
            for (int[] l : c.getLines()) {
                lines++;
                indicesOk &= l[0] >= 0 && l[1] >= 0 && l[0] < c.getStars().size() && l[1] < c.getStars().size() && l[0] != l[1];
            }
        }
        check("169 verschiedene Sterne", ids.size() == 169);
        check("156 Linien", lines == 156);
        check("Linienindizes gültig", indicesOk);
        Constellation and = d.find("And").orElseThrow(), peg = d.find("Peg").orElseThrow();
        check("Andromeda leiht Alpheratz von Pegasus",
                and.getStars().size() == 4 && and.getStars().stream().anyMatch(s -> s.name().equals("Alpheratz"))
                && peg.getStars().stream().filter(s -> s.name().equals("Alpheratz")).findFirst().get().id()
                   == and.getStars().stream().filter(s -> s.name().equals("Alpheratz")).findFirst().get().id());
        boolean inside = true;
        for (Constellation c : d.getConstellations()) {
            for (int i = 0; i < c.getStars().size(); i++) {
                double x = c.getPosX() + c.getLocalX(i), y = c.getPosY() - c.getLocalY(i);
                inside &= x > 0.5 && x < d.getScene().width() - 0.5 && y > 2 && y < d.getScene().horizonY() - 4;
            }
        }
        check("alle Figurensterne innerhalb der Szene über dem Horizont", inside);

        int strokes = 0;
        boolean strokesOk = true;
        for (Constellation c : d.getConstellations()) {
            for (double[][] st : c.getFigureStrokes()) {
                strokes++;
                strokesOk &= st.length >= 2;
                for (double[] p : st) {
                    double x = c.getPosX() + p[0], y = c.getPosY() + p[1];
                    strokesOk &= x > -2 && x < d.getScene().width() + 2 && y > -1 && y < d.getScene().horizonY();
                }
            }
        }
        check("122 Strichzüge der Sagenfiguren, alle im Himmel über dem Horizont", strokes == 122 && strokesOk);
        int tokens = BuiltinTokenCount.count();
        int parsed = d.getConstellations().stream().flatMap(c -> c.getFigureStrokes().stream()).mapToInt(a -> a.length).sum();
        check("alle Figurenpunkte gültig (" + parsed + " / " + tokens + ")", parsed == tokens);
        check("10 Meteorströme", d.getShowers().size() == 10);
        check("20.09. → Südliche Tauriden aktiv", shower(d, LocalDate.of(2026, 9, 20)).equals("STA"));
        check("12.08. → Perseiden im Maximum", shower(d, LocalDate.of(2026, 8, 12)).equals("PER")
                && d.getShowers().stream().filter(m -> m.code().equals("PER")).findFirst().get().activity(LocalDate.of(2026, 8, 12)) == 1.0);
        check("14.12. → Geminiden", shower(d, LocalDate.of(2026, 12, 14)).equals("GEM"));
        check("22.12. → Ursiden", shower(d, LocalDate.of(2026, 12, 22)).equals("URS"));
        check("01.01. → kein Strom aktiv", shower(d, LocalDate.of(2026, 1, 1)).equals("-"));
        boolean radiantsInside = true;
        for (MeteorShower m : d.getShowers()) {
            double[] r = m.radiantScene();
            radiantsInside &= r[0] > 0 && r[0] < d.getScene().width() && r[1] > 0 && r[1] < d.getScene().horizonY();
            if (!(r[0] > 0 && r[0] < d.getScene().width() && r[1] > 0 && r[1] < d.getScene().horizonY())) {
                System.out.println("   Radiant außerhalb: " + m.name() + " " + r[0] + "/" + r[1]);
            }
        }
        check("alle Radianten liegen im sichtbaren Himmel", radiantsInside);
        MoonPhase full = MoonPhase.at(java.time.Instant.parse("2026-01-03T12:00:00Z"), java.time.ZoneOffset.UTC);
        check("Mond 03.01.2026 → Vollmond", full.name().equals("Vollmond") && full.illumination() > 0.97);
        MoonPhase sep = MoonPhase.at(java.time.Instant.parse("2026-09-20T00:00:00Z"), java.time.ZoneOffset.UTC);
        check("Mond 20.09.2026 → zunehmend, nächster Vollmond 25.–27.09. (" + sep.name() + ", " + sep.nextFullMoon() + ")",
                sep.waxing() && !sep.nextFullMoon().isBefore(LocalDate.of(2026, 9, 25)) && !sep.nextFullMoon().isAfter(LocalDate.of(2026, 9, 27)));
        check("Szene hat einen Mond", d.getScene().hasMoon());

        check("10.04. → Sonne in Fische", code(d, LocalDate.of(1988, 4, 10), ZodiacSystem.REAL).equals("Psc"));
        check("10.04. → astrologisch Widder", code(d, LocalDate.of(1988, 4, 10), ZodiacSystem.ASTRO).equals("Ari"));
        check("30.11. → Sonne im Schlangenträger", code(d, LocalDate.of(2026, 11, 30), ZodiacSystem.REAL).equals("Oph"));
        check("01.01. → Schütze / Steinbock", code(d, LocalDate.of(2026, 1, 1), ZodiacSystem.REAL).equals("Sgr")
                && code(d, LocalDate.of(2026, 1, 1), ZodiacSystem.ASTRO).equals("Cap"));
        check("20.09. → Jungfrau / Jungfrau", code(d, LocalDate.of(2026, 9, 20), ZodiacSystem.REAL).equals("Vir")
                && code(d, LocalDate.of(2026, 9, 20), ZodiacSystem.ASTRO).equals("Vir"));
        boolean exact = true;
        for (LocalDate day = LocalDate.of(2024, 1, 1); day.getYear() == 2024; day = day.plusDays(1)) {
            for (ZodiacSystem sys : ZodiacSystem.values()) {
                final LocalDate dd = day;
                long n = d.getConstellations().stream().filter(c -> c.getPeriod(sys) != null && c.getPeriod(sys).contains(dd)).count();
                if (n != 1) { exact = false; System.out.println("   Lücke/Überschneidung " + sys + " " + day + " n=" + n); }
            }
        }
        check("jeder Tag (Schaltjahr) genau einem Zeichen je System zugeordnet", exact);

        Path sql = Path.of("sql/02_sb_data.sql");
        if (Files.exists(sql)) {
            String s = Files.readString(sql);
            check("SQL: 24 Sternbilder", count(s, "INSERT INTO SB_CONSTELLATION ") == 24);
            check("SQL: 169 Sterne", count(s, "INSERT INTO SB_STAR ") == 169);
            check("SQL: 156 Linien", count(s, "INSERT INTO SB_CONST_LINE ") == 156);
            check("SQL: 24 Szenen-Einträge", count(s, "INSERT INTO SB_SCENE_ITEM ") == 24);
            check("SQL: 25 Tierkreis-Perioden", count(s, "INSERT INTO SB_ZODIAC_PERIOD ") == 25);
            check("SQL: 122 Strichzüge", count(s, "INSERT INTO SB_FIGURE_STROKE ") == 122);
            check("SQL: 10 Meteorströme", count(s, "INSERT INTO SB_METEOR_SHOWER ") == 10);
            check("SQL: kein Text nach ';' (sonst verschluckt der Skript-Runner die Anweisung)",
                    s.lines().filter(l -> !l.startsWith("--")).noneMatch(l -> l.matches(".*;\\s*\\S.*") && !l.contains("'")));
            check("SQL: nur ASCII (Umlaute über UNISTR, Kommentare ausgenommen)",
                    s.lines().filter(l -> !l.startsWith("--")).map(l -> l)
                     .allMatch(l -> l.chars().allMatch(ch -> ch < 128)));
        }

        DbConfig cfg = DbConfig.load();
        check("DbConfig: PDBORCL / DEMO", cfg.url().endsWith("/PDBORCL") && cfg.user().equals("DEMO"));
        boolean threw = false;
        try {
            new OracleSkyRepository(new DbConfig(true, "jdbc:oracle:thin:@//127.0.0.1:1/NIX", "DEMO", "x", "OSTSEE", 2)).load("OSTSEE");
        } catch (Exception e) {
            threw = true;
        }
        check("Oracle nicht erreichbar → Exception (App fällt auf eingebaute Daten zurück)", threw);

        SkyCanvas cv = new SkyCanvas();
        cv.setSize(1400, 780);
        cv.setData(d);
        cv.finishIntroForTest();
        cv.advanceForTest(3);
        BufferedImage img = new BufferedImage(1400, 780, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        cv.paint(g);
        cv.setMood(SkyMood.NACHT);
        cv.paint(g);
        g.dispose();
        check("Renderer zeichnet Dämmerung und Nacht ohne Fehler", true);
        Constellation ori = d.find("Ori").orElseThrow();
        Point p = cv.centerOnScreenForTest(ori);
        cv.simulateMoveForTest(p.x, p.y);
        check("Hover über Orion trifft Orion", cv.getFocused() == ori);
        cv.simulateClickForTest(p.x, p.y);
        check("Klick hält Orion fest", cv.getPinned() == ori);
        cv.simulateClickForTest(5, 5);
        check("Klick in leeren Himmel löst", cv.getPinned() == null);
        cv.setBirthday(LocalDate.of(1988, 4, 10));
        check("Geburtstag hält Fische fest, Widder als astrologisches Zeichen",
                cv.getPinned() == d.find("Psc").orElseThrow() && cv.getBirthdayAstro() == d.find("Ari").orElseThrow());
        cv.setPinned(null);
        Point mp = cv.moonOnScreenForTest();
        cv.simulateMoveForTest(mp.x, mp.y);
        check("Hover über dem Mond erkannt", cv.isHoverMoon() && cv.getFocused() == null);
        MeteorShower per = d.getShowers().stream().filter(m -> m.code().equals("PER")).findFirst().get();
        cv.setMeteorShower(per, 1.0);
        Point rp = cv.radiantOnScreenForTest();
        cv.simulateMoveForTest(rp.x, rp.y);
        check("Hover über dem Perseiden-Radianten erkannt", cv.isHoverRadiant());
        cv.advanceForTest(200);
        Graphics2D g2 = img.createGraphics();
        cv.paint(g2);
        g2.dispose();
        check("Meteorstrom + Mond + Figur zeichnen ohne Fehler", true);
        cv.simulateMoveForTest(5, 5);
        cv.setTour(true);
        cv.advanceForTest(2);
        check("Tour wählt ein Sternbild", cv.getFocused() != null);

        System.out.println();
        System.out.println(ok + " / " + (ok + fail) + " Checks bestanden");
        System.exit(fail == 0 ? 0 : 1);
    }

    private static String shower(SkyData d, LocalDate date) {
        return d.activeShower(date).map(MeteorShower::code).orElse("-");
    }

    private static String code(SkyData d, LocalDate date, ZodiacSystem s) {
        return d.zodiacFor(date, s).map(Constellation::getCode).orElse("-");
    }

    private static int count(String s, String needle) {
        int n = 0, i = 0;
        while ((i = s.indexOf(needle, i)) >= 0) { n++; i += needle.length(); }
        return n;
    }

    private static void check(String name, boolean cond) {
        if (cond) { ok++; System.out.println("  ✓ " + name); } else { fail++; System.out.println("  ✗ " + name); }
    }
}
