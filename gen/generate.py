# -*- coding: utf-8 -*-
"""Erzeugt aus data.py + layout.py:
   sql/01_sb_schema.sql, sql/02_sb_data.sql, sql/03_sb_check.sql, sql/99_sb_drop.sql, sql/00_sb_install.sql
   src/com/dan/sternbild/data/BuiltinCatalog.java"""
import os
from data import CONS, PERIODS, SCENE, METEORS
from figures import FIGURES
from layout import compute, BY

OUT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
os.makedirs(OUT + '/sql', exist_ok=True)
os.makedirs(OUT + '/src/com/dan/sternbild/data', exist_ok=True)
ITEMS = compute()

def sq(s):
    """SQL-String; Nicht-ASCII ueber UNISTR, damit die Skripte unabhaengig von der Editor-Kodierung laufen."""
    if s is None:
        return 'NULL'
    if all(ord(ch) < 128 for ch in s):
        return "'" + s.replace("'", "''") + "'"
    out = []
    for ch in s:
        o = ord(ch)
        if ch == "'":
            out.append("''")
        elif ch == '\\':
            out.append('\\005C')
        elif o < 128:
            out.append(ch)
        else:
            out.append('\\%04X' % o)
    return "UNISTR('" + ''.join(out) + "')"

def fmtn(v):
    return ('%g' % v)

def pts(stroke):
    out = []
    for t in stroke:
        if isinstance(t, int):
            out.append('s%d' % t)
        elif t[0] is None:
            out.append('p%s:%s' % (fmtn(t[1]), fmtn(t[2])))
        else:
            out.append('s%d:%s:%s' % (t[0], fmtn(t[1]), fmtn(t[2])))
    return ';'.join(out)

def num(v):
    return 'NULL' if v is None else repr(v)

HDR = """-- ============================================================================
--  Sternbild · Oracle 21c · Service PDBORCL · Schema DEMO
--  {title}
--  GENERIERT aus gen/data.py – nicht von Hand aendern, sondern neu generieren.
-- ============================================================================
"""

# ---------------------------------------------------------------- 01 Schema
schema = HDR.format(title='01 – Tabellen, Sequenzen, Trigger') + r"""
SET DEFINE OFF

-- ---------------------------------------------------------------- Sternbilder
CREATE TABLE SB_CONSTELLATION (
  CONST_ID      NUMBER(6)          CONSTRAINT SB_CONST_PK PRIMARY KEY,
  IAU_CODE      VARCHAR2(4)        NOT NULL CONSTRAINT SB_CONST_CODE_UK UNIQUE,
  NAME_DE       VARCHAR2(40 CHAR)  NOT NULL,
  NAME_LA       VARCHAR2(40 CHAR)  NOT NULL,
  KIND          CHAR(1)            DEFAULT 'S' NOT NULL
                                   CONSTRAINT SB_CONST_KIND_CK CHECK (KIND IN ('S','H')),
  ZODIAC_ORDER  NUMBER(2)          CONSTRAINT SB_CONST_ZOD_UK UNIQUE,
  ZODIAC_GLYPH  NVARCHAR2(2),
  DESCRIPTION   VARCHAR2(400 CHAR)
);
COMMENT ON TABLE  SB_CONSTELLATION              IS 'Sternbilder und Sternhaufen der Sternbild-App';
COMMENT ON COLUMN SB_CONSTELLATION.IAU_CODE     IS 'IAU-Kuerzel, z. B. UMa; M45 fuer die Plejaden';
COMMENT ON COLUMN SB_CONSTELLATION.KIND         IS 'S = Sternbild, H = Sternhaufen';
COMMENT ON COLUMN SB_CONSTELLATION.ZODIAC_ORDER IS '1..13 Reihenfolge im Tierkreis (Widder = 1), NULL = kein Tierkreis-Sternbild';
COMMENT ON COLUMN SB_CONSTELLATION.ZODIAC_GLYPH IS 'Tierkreissymbol (Unicode)';

-- ---------------------------------------------------------------- Sterne
CREATE TABLE SB_STAR (
  STAR_ID    NUMBER(8)          CONSTRAINT SB_STAR_PK PRIMARY KEY,
  CONST_ID   NUMBER(6)          NOT NULL CONSTRAINT SB_STAR_CONST_FK REFERENCES SB_CONSTELLATION (CONST_ID) ON DELETE CASCADE,
  STAR_NO    NUMBER(3)          NOT NULL,
  NAME       VARCHAR2(40 CHAR)  NOT NULL,
  BAYER      VARCHAR2(16 CHAR),
  RA_HOURS   NUMBER(8,5)        NOT NULL CONSTRAINT SB_STAR_RA_CK  CHECK (RA_HOURS >= 0 AND RA_HOURS < 24),
  DEC_DEG    NUMBER(8,4)        NOT NULL CONSTRAINT SB_STAR_DEC_CK CHECK (DEC_DEG BETWEEN -90 AND 90),
  MAG        NUMBER(5,2)        NOT NULL,
  COLOR_HEX  VARCHAR2(7)        CONSTRAINT SB_STAR_COLOR_CK CHECK (REGEXP_LIKE(COLOR_HEX, '^#[0-9A-Fa-f]{6}$')),
  SPECTRAL   VARCHAR2(16),
  DIST_LY    NUMBER(7),
  CONSTRAINT SB_STAR_NO_UK UNIQUE (CONST_ID, STAR_NO)
);
COMMENT ON TABLE  SB_STAR          IS 'Figurensterne (J2000, gerundet); Hintergrundsterne erzeugt die App prozedural';
COMMENT ON COLUMN SB_STAR.STAR_NO  IS 'laufende Nummer innerhalb des Sternbilds (1 = Hauptstern der Liste)';
COMMENT ON COLUMN SB_STAR.MAG      IS 'scheinbare Helligkeit in mag (kleiner = heller)';
COMMENT ON COLUMN SB_STAR.DIST_LY  IS 'Entfernung in Lichtjahren (gerundet), sofern gepflegt';

-- ---------------------------------------------------------------- Linien
CREATE TABLE SB_CONST_LINE (
  LINE_ID     NUMBER(8)  CONSTRAINT SB_LINE_PK PRIMARY KEY,
  CONST_ID    NUMBER(6)  NOT NULL CONSTRAINT SB_LINE_CONST_FK REFERENCES SB_CONSTELLATION (CONST_ID) ON DELETE CASCADE,
  STAR_FROM   NUMBER(8)  NOT NULL CONSTRAINT SB_LINE_FROM_FK  REFERENCES SB_STAR (STAR_ID) ON DELETE CASCADE,
  STAR_TO     NUMBER(8)  NOT NULL CONSTRAINT SB_LINE_TO_FK    REFERENCES SB_STAR (STAR_ID) ON DELETE CASCADE,
  DRAW_ORDER  NUMBER(3)  NOT NULL,
  CONSTRAINT SB_LINE_ORDER_UK UNIQUE (CONST_ID, DRAW_ORDER),
  CONSTRAINT SB_LINE_DIFF_CK  CHECK (STAR_FROM <> STAR_TO)
);
CREATE INDEX SB_LINE_FROM_IX ON SB_CONST_LINE (STAR_FROM);
CREATE INDEX SB_LINE_TO_IX   ON SB_CONST_LINE (STAR_TO);
COMMENT ON TABLE  SB_CONST_LINE            IS 'Figurenlinien; Sterne duerfen aus einem anderen Sternbild stammen (Alpheratz: Pegasus/Andromeda)';
COMMENT ON COLUMN SB_CONST_LINE.DRAW_ORDER IS 'Reihenfolge der Einzeichen-Animation';

-- ---------------------------------------------------------------- Szene (frei komponierter Himmel)
CREATE TABLE SB_SCENE (
  SCENE_ID      NUMBER(4)          CONSTRAINT SB_SCENE_PK PRIMARY KEY,
  CODE          VARCHAR2(20)       NOT NULL CONSTRAINT SB_SCENE_CODE_UK UNIQUE,
  NAME          VARCHAR2(60 CHAR)  NOT NULL,
  WIDTH_UNITS   NUMBER(7,2)        NOT NULL,
  HEIGHT_UNITS  NUMBER(7,2)        NOT NULL,
  HORIZON_Y     NUMBER(7,2)        NOT NULL,
  MOON_X        NUMBER(7,2),
  MOON_Y        NUMBER(7,2)
);
COMMENT ON TABLE SB_SCENE IS 'Himmelsszenen: Koordinatenraum in Szeneneinheiten (1 Einheit ~ 1 Grad bei Skalierung 1)';
COMMENT ON COLUMN SB_SCENE.MOON_X IS 'Position des Mondes in Szeneneinheiten (NULL = kein Mond)';

CREATE TABLE SB_SCENE_ITEM (
  SCENE_ID      NUMBER(4)    NOT NULL CONSTRAINT SB_ITEM_SCENE_FK REFERENCES SB_SCENE (SCENE_ID) ON DELETE CASCADE,
  CONST_ID      NUMBER(6)    NOT NULL CONSTRAINT SB_ITEM_CONST_FK REFERENCES SB_CONSTELLATION (CONST_ID) ON DELETE CASCADE,
  POS_X         NUMBER(7,2)  NOT NULL,
  POS_Y         NUMBER(7,2)  NOT NULL,
  ITEM_SCALE    NUMBER(7,4)  DEFAULT 1 NOT NULL CONSTRAINT SB_ITEM_SCALE_CK CHECK (ITEM_SCALE > 0),
  ROTATION_DEG  NUMBER(6,2)  DEFAULT 0 NOT NULL,
  CONSTRAINT SB_ITEM_PK PRIMARY KEY (SCENE_ID, CONST_ID)
);
CREATE INDEX SB_ITEM_CONST_IX ON SB_SCENE_ITEM (CONST_ID);
COMMENT ON TABLE SB_SCENE_ITEM IS 'Platzierung eines Sternbilds in einer Szene: Mittelpunkt, Skalierung, Drehung';

-- ---------------------------------------------------------------- Tierkreis-Perioden
CREATE TABLE SB_ZODIAC_PERIOD (
  PERIOD_ID   NUMBER(6)    CONSTRAINT SB_PERIOD_PK PRIMARY KEY,
  CONST_ID    NUMBER(6)    NOT NULL CONSTRAINT SB_PERIOD_CONST_FK REFERENCES SB_CONSTELLATION (CONST_ID) ON DELETE CASCADE,
  SIGN_SYSTEM VARCHAR2(5)  NOT NULL CONSTRAINT SB_PERIOD_SYS_CK CHECK (SIGN_SYSTEM IN ('REAL','ASTRO')),
  START_MMDD  NUMBER(4)    NOT NULL CONSTRAINT SB_PERIOD_START_CK CHECK (START_MMDD BETWEEN 101 AND 1231),
  END_MMDD    NUMBER(4)    NOT NULL CONSTRAINT SB_PERIOD_END_CK   CHECK (END_MMDD   BETWEEN 101 AND 1231),
  CONSTRAINT SB_PERIOD_UK UNIQUE (CONST_ID, SIGN_SYSTEM)
);
COMMENT ON TABLE  SB_ZODIAC_PERIOD        IS 'REAL = Sonne steht astronomisch im Sternbild (IAU-Grenzen, +-1 Tag); ASTRO = astrologisches Zeichen';
COMMENT ON COLUMN SB_ZODIAC_PERIOD.START_MMDD IS 'Monat*100+Tag, z. B. 1217 = 17.12.; Zeitraum darf ueber den Jahreswechsel gehen';

-- ---------------------------------------------------------------- Sagenfiguren
CREATE TABLE SB_FIGURE_STROKE (
  STROKE_ID  NUMBER(8)            CONSTRAINT SB_STROKE_PK PRIMARY KEY,
  CONST_ID   NUMBER(6)            NOT NULL CONSTRAINT SB_STROKE_CONST_FK REFERENCES SB_CONSTELLATION (CONST_ID) ON DELETE CASCADE,
  STROKE_NO  NUMBER(3)            NOT NULL,
  POINTS     VARCHAR2(2000 CHAR)  NOT NULL,
  CONSTRAINT SB_STROKE_NO_UK UNIQUE (CONST_ID, STROKE_NO)
);
COMMENT ON TABLE  SB_FIGURE_STROKE        IS 'Linienzeichnung der Sagenfigur: ein Spline je Zeile, durch die Punkte gelegt';
COMMENT ON COLUMN SB_FIGURE_STROKE.POINTS IS 'Punkte, durch Semikolon getrennt: sI = Figurenstern I, sI:dx:dy = Stern plus Versatz, px:y = frei relativ zur Figur (Szeneneinheiten, y nach unten)';

-- ---------------------------------------------------------------- Meteorströme
CREATE TABLE SB_METEOR_SHOWER (
  SHOWER_ID     NUMBER(4)          CONSTRAINT SB_SHOWER_PK PRIMARY KEY,
  CODE          VARCHAR2(4)        NOT NULL CONSTRAINT SB_SHOWER_CODE_UK UNIQUE,
  NAME_DE       VARCHAR2(40 CHAR)  NOT NULL,
  REF_CONST_ID  NUMBER(6)          NOT NULL CONSTRAINT SB_SHOWER_CONST_FK REFERENCES SB_CONSTELLATION (CONST_ID) ON DELETE CASCADE,
  RADIANT_RA    NUMBER(8,5)        NOT NULL CONSTRAINT SB_SHOWER_RA_CK  CHECK (RADIANT_RA >= 0 AND RADIANT_RA < 24),
  RADIANT_DEC   NUMBER(8,4)        NOT NULL CONSTRAINT SB_SHOWER_DEC_CK CHECK (RADIANT_DEC BETWEEN -90 AND 90),
  START_MMDD    NUMBER(4)          NOT NULL,
  PEAK_MMDD     NUMBER(4)          NOT NULL,
  END_MMDD      NUMBER(4)          NOT NULL,
  ZHR           NUMBER(4)          NOT NULL,
  SPEED_KMS     NUMBER(3)          NOT NULL,
  COLOR_HEX     VARCHAR2(7)        CONSTRAINT SB_SHOWER_COLOR_CK CHECK (REGEXP_LIKE(COLOR_HEX, '^#[0-9A-Fa-f]{6}$')),
  DESCRIPTION   VARCHAR2(400 CHAR)
);
CREATE INDEX SB_SHOWER_CONST_IX ON SB_METEOR_SHOWER (REF_CONST_ID);
COMMENT ON TABLE  SB_METEOR_SHOWER              IS 'Meteorströme (IMO-Kalender, gerundet); der Radiant wird relativ zu REF_CONST_ID in die Szene projiziert';
COMMENT ON COLUMN SB_METEOR_SHOWER.ZHR          IS 'Zenithal Hourly Rate im Maximum';

-- ---------------------------------------------------------------- Sequenzen + Trigger (kein IDENTITY)
CREATE SEQUENCE SB_STROKE_SEQ START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SB_SHOWER_SEQ START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SB_CONST_SEQ  START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SB_STAR_SEQ   START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SB_LINE_SEQ   START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SB_SCENE_SEQ  START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SB_PERIOD_SEQ START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE OR REPLACE TRIGGER SB_CONST_BI BEFORE INSERT ON SB_CONSTELLATION FOR EACH ROW
BEGIN
  IF :NEW.CONST_ID IS NULL THEN :NEW.CONST_ID := SB_CONST_SEQ.NEXTVAL; END IF;
END;
/
CREATE OR REPLACE TRIGGER SB_STAR_BI BEFORE INSERT ON SB_STAR FOR EACH ROW
BEGIN
  IF :NEW.STAR_ID IS NULL THEN :NEW.STAR_ID := SB_STAR_SEQ.NEXTVAL; END IF;
END;
/
CREATE OR REPLACE TRIGGER SB_LINE_BI BEFORE INSERT ON SB_CONST_LINE FOR EACH ROW
BEGIN
  IF :NEW.LINE_ID IS NULL THEN :NEW.LINE_ID := SB_LINE_SEQ.NEXTVAL; END IF;
END;
/
CREATE OR REPLACE TRIGGER SB_SCENE_BI BEFORE INSERT ON SB_SCENE FOR EACH ROW
BEGIN
  IF :NEW.SCENE_ID IS NULL THEN :NEW.SCENE_ID := SB_SCENE_SEQ.NEXTVAL; END IF;
END;
/
CREATE OR REPLACE TRIGGER SB_PERIOD_BI BEFORE INSERT ON SB_ZODIAC_PERIOD FOR EACH ROW
BEGIN
  IF :NEW.PERIOD_ID IS NULL THEN :NEW.PERIOD_ID := SB_PERIOD_SEQ.NEXTVAL; END IF;
END;
/
CREATE OR REPLACE TRIGGER SB_STROKE_BI BEFORE INSERT ON SB_FIGURE_STROKE FOR EACH ROW
BEGIN
  IF :NEW.STROKE_ID IS NULL THEN :NEW.STROKE_ID := SB_STROKE_SEQ.NEXTVAL; END IF;
END;
/
CREATE OR REPLACE TRIGGER SB_SHOWER_BI BEFORE INSERT ON SB_METEOR_SHOWER FOR EACH ROW
BEGIN
  IF :NEW.SHOWER_ID IS NULL THEN :NEW.SHOWER_ID := SB_SHOWER_SEQ.NEXTVAL; END IF;
END;
/
"""
open(OUT + '/sql/01_sb_schema.sql', 'w', encoding='utf-8').write(schema)

# ---------------------------------------------------------------- 02 Daten
L = [HDR.format(title='02 – Stammdaten: 24 Sternbilder, Sterne, Linien, Szene OSTSEE, Tierkreis-Perioden'),
     'SET DEFINE OFF', '']
cid = lambda code: "(SELECT CONST_ID FROM SB_CONSTELLATION WHERE IAU_CODE = '%s')" % code
def sid(code, idx):
    return ("(SELECT s.STAR_ID FROM SB_STAR s JOIN SB_CONSTELLATION c ON c.CONST_ID = s.CONST_ID "
            "WHERE c.IAU_CODE = '%s' AND s.STAR_NO = %d)" % (code, idx + 1))

L.append('-- ---------------------------------------------------------------- Sternbilder')
for c in CONS:
    glyph = 'UNISTR(\'\\%04X\')' % c['glyph'] if c['glyph'] else 'NULL'
    L.append('-- %s / %s' % (c['de'], c['la']))
    L.append('INSERT INTO SB_CONSTELLATION (IAU_CODE, NAME_DE, NAME_LA, KIND, ZODIAC_ORDER, ZODIAC_GLYPH, DESCRIPTION)\n'
             '  VALUES (%s, %s, %s, %s, %s, %s,\n          %s);' % (
             sq(c['code']), sq(c['de']), sq(c['la']), sq(c['kind']), num(c['zodiac']), glyph, sq(c['desc'])))
L.append('')
L.append('-- ---------------------------------------------------------------- Sterne')
for c in CONS:
    L.append('-- %s' % c['de'])
    for i, s in enumerate(c['stars']):
        name, bayer, ra, dec, mag, col, spec, dist = s
        # Kommentar in eigener Zeile: Text nach ';' verhindert, dass SQL*Plus/SQL Developer die Anweisung beendet
        L.append('-- %s' % name)
        L.append('INSERT INTO SB_STAR (CONST_ID, STAR_NO, NAME, BAYER, RA_HOURS, DEC_DEG, MAG, COLOR_HEX, SPECTRAL, DIST_LY) VALUES (%s, %d, %s, %s, %s, %s, %s, %s, %s, %s);' % (
            cid(c['code']), i + 1, sq(name), sq(bayer), repr(ra), repr(dec), repr(mag), sq(col), sq(spec), num(dist)))
L.append('')
L.append('-- ---------------------------------------------------------------- Linien')
def ref(c, r):
    if isinstance(r, str):
        code, k = r.split('.'); return sid(code, int(k))
    return sid(c['code'], r)
for c in CONS:
    if not c['lines']:
        continue
    L.append('-- %s' % c['de'])
    for o, (a, b) in enumerate(c['lines']):
        L.append('INSERT INTO SB_CONST_LINE (CONST_ID, STAR_FROM, STAR_TO, DRAW_ORDER) VALUES (%s,\n  %s,\n  %s, %d);' % (
            cid(c['code']), ref(c, a), ref(c, b), o + 1))
L.append('')
L.append('-- ---------------------------------------------------------------- Szene OSTSEE')
L.append("INSERT INTO SB_SCENE (CODE, NAME, WIDTH_UNITS, HEIGHT_UNITS, HORIZON_Y, MOON_X, MOON_Y) VALUES ('%s', %s, %s, %s, %s, %s, %s);" % (
    SCENE['code'], sq(SCENE['name']), SCENE['w'], SCENE['h'], SCENE['horizon'], SCENE['moon_x'], SCENE['moon_y']))
for c in CONS:
    x, y, s, r = ITEMS[c['code']]
    L.append("INSERT INTO SB_SCENE_ITEM (SCENE_ID, CONST_ID, POS_X, POS_Y, ITEM_SCALE, ROTATION_DEG) VALUES "
             "((SELECT SCENE_ID FROM SB_SCENE WHERE CODE = '%s'), %s, %s, %s, %s, %s);" % (
             SCENE['code'], cid(c['code']), x, y, s, r))
L.append('')
L.append('-- ---------------------------------------------------------------- Tierkreis-Perioden')
for code, (real, astro) in PERIODS.items():
    L.append("INSERT INTO SB_ZODIAC_PERIOD (CONST_ID, SIGN_SYSTEM, START_MMDD, END_MMDD) VALUES (%s, 'REAL', %d, %d);" % (cid(code), real[0], real[1]))
    if astro:
        L.append("INSERT INTO SB_ZODIAC_PERIOD (CONST_ID, SIGN_SYSTEM, START_MMDD, END_MMDD) VALUES (%s, 'ASTRO', %d, %d);" % (cid(code), astro[0], astro[1]))
L.append('')
L.append('-- ---------------------------------------------------------------- Sagenfiguren')
for c in CONS:
    for no, stroke in enumerate(FIGURES.get(c['code'], [])):
        L.append("INSERT INTO SB_FIGURE_STROKE (CONST_ID, STROKE_NO, POINTS) VALUES (%s, %d,\n  '%s');" % (cid(c['code']), no + 1, pts(stroke)))
L.append('')
L.append('-- ---------------------------------------------------------------- Meteorströme')
for m in METEORS:
    code, name, ref, ra, dec, st_, pk, en, zhr, v, col, desc = m
    L.append('-- %s' % name)
    L.append("INSERT INTO SB_METEOR_SHOWER (CODE, NAME_DE, REF_CONST_ID, RADIANT_RA, RADIANT_DEC, START_MMDD, PEAK_MMDD, END_MMDD, ZHR, SPEED_KMS, COLOR_HEX, DESCRIPTION)\n"
             "  VALUES ('%s', %s, %s, %s, %s, %d, %d, %d, %d, %d, '%s',\n          %s);" % (code, sq(name), cid(ref), ra, dec, st_, pk, en, zhr, v, col, sq(desc)))
L.append('')
L.append('COMMIT;')
open(OUT + '/sql/02_sb_data.sql', 'w', encoding='utf-8').write('\n'.join(L) + '\n')

n_const = len(CONS); n_star = sum(len(c['stars']) for c in CONS); n_line = sum(len(c['lines']) for c in CONS)
n_per = sum(2 if a else 1 for (_, a) in PERIODS.values())
n_str = sum(len(v) for v in FIGURES.values()); n_met = len(METEORS)
check = HDR.format(title='03 – Kontrolle nach der Installation') + """
-- Erwartet: SB_CONSTELLATION = {c}, SB_STAR = {s}, SB_CONST_LINE = {l}, SB_SCENE_ITEM = {c}, SB_ZODIAC_PERIOD = {p},
--           SB_FIGURE_STROKE = {fs}, SB_METEOR_SHOWER = {ms}
SELECT 'SB_CONSTELLATION' AS TABELLE, COUNT(*) AS ANZAHL, {c} AS ERWARTET FROM SB_CONSTELLATION
UNION ALL SELECT 'SB_STAR',          COUNT(*), {s} FROM SB_STAR
UNION ALL SELECT 'SB_CONST_LINE',    COUNT(*), {l} FROM SB_CONST_LINE
UNION ALL SELECT 'SB_SCENE_ITEM',    COUNT(*), {c} FROM SB_SCENE_ITEM
UNION ALL SELECT 'SB_ZODIAC_PERIOD', COUNT(*), {p} FROM SB_ZODIAC_PERIOD
UNION ALL SELECT 'SB_FIGURE_STROKE', COUNT(*), {fs} FROM SB_FIGURE_STROKE
UNION ALL SELECT 'SB_METEOR_SHOWER', COUNT(*), {ms} FROM SB_METEOR_SHOWER;

-- Meteorströme im Jahreslauf
SELECT m.NAME_DE, c.NAME_DE AS RADIANT_BEI, m.START_MMDD, m.PEAK_MMDD, m.END_MMDD, m.ZHR
  FROM SB_METEOR_SHOWER m JOIN SB_CONSTELLATION c ON c.CONST_ID = m.REF_CONST_ID
 ORDER BY m.PEAK_MMDD;

-- Tierkreis in Reihenfolge, mit astronomischem und astrologischem Zeitraum
SELECT c.ZODIAC_ORDER, c.NAME_DE,
       MAX(CASE p.SIGN_SYSTEM WHEN 'REAL'  THEN TO_CHAR(p.START_MMDD, 'FM0000') || '-' || TO_CHAR(p.END_MMDD, 'FM0000') END) AS SONNE_REAL,
       MAX(CASE p.SIGN_SYSTEM WHEN 'ASTRO' THEN TO_CHAR(p.START_MMDD, 'FM0000') || '-' || TO_CHAR(p.END_MMDD, 'FM0000') END) AS ASTROLOGISCH
  FROM SB_CONSTELLATION c JOIN SB_ZODIAC_PERIOD p ON p.CONST_ID = c.CONST_ID
 GROUP BY c.ZODIAC_ORDER, c.NAME_DE
 ORDER BY c.ZODIAC_ORDER;

-- Linien, die Sterne aus fremden Sternbildern verwenden (erwartet: Andromeda -> Alpheratz)
SELECT c.NAME_DE AS FIGUR, s.NAME AS STERN, o.NAME_DE AS GEHOERT_ZU
  FROM SB_CONST_LINE l
  JOIN SB_CONSTELLATION c ON c.CONST_ID = l.CONST_ID
  JOIN SB_STAR s          ON s.STAR_ID IN (l.STAR_FROM, l.STAR_TO)
  JOIN SB_CONSTELLATION o ON o.CONST_ID = s.CONST_ID
 WHERE s.CONST_ID <> l.CONST_ID;
""".format(c=n_const, s=n_star, l=n_line, p=n_per, fs=n_str, ms=n_met)
open(OUT + '/sql/03_sb_check.sql', 'w', encoding='utf-8').write(check)

drop = HDR.format(title='99 – Alles entfernen (Neuinstallation)') + """
BEGIN
  FOR r IN (SELECT 'DROP TABLE ' || t || ' CASCADE CONSTRAINTS PURGE' AS stmt
              FROM (SELECT 'SB_METEOR_SHOWER' t FROM DUAL UNION ALL SELECT 'SB_FIGURE_STROKE' FROM DUAL
                    UNION ALL SELECT 'SB_ZODIAC_PERIOD' FROM DUAL UNION ALL SELECT 'SB_SCENE_ITEM' FROM DUAL
                    UNION ALL SELECT 'SB_SCENE' FROM DUAL UNION ALL SELECT 'SB_CONST_LINE' FROM DUAL
                    UNION ALL SELECT 'SB_STAR' FROM DUAL UNION ALL SELECT 'SB_CONSTELLATION' FROM DUAL)
             WHERE t IN (SELECT TABLE_NAME FROM USER_TABLES)
            UNION ALL
            SELECT 'DROP SEQUENCE ' || SEQUENCE_NAME FROM USER_SEQUENCES
             WHERE SEQUENCE_NAME IN ('SB_CONST_SEQ','SB_STAR_SEQ','SB_LINE_SEQ','SB_SCENE_SEQ','SB_PERIOD_SEQ','SB_STROKE_SEQ','SB_SHOWER_SEQ'))
  LOOP
    EXECUTE IMMEDIATE r.stmt;
  END LOOP;
END;
/
"""
open(OUT + '/sql/99_sb_drop.sql', 'w', encoding='utf-8').write(drop)

install = HDR.format(title='00 – Komplettinstallation (als DEMO in SQL Developer mit F5 oder SQL*Plus)') + """
-- sqlplus DEMO@//localhost:1521/PDBORCL @00_sb_install.sql
WHENEVER SQLERROR EXIT FAILURE ROLLBACK
@@99_sb_drop.sql
@@01_sb_schema.sql
@@02_sb_data.sql
WHENEVER SQLERROR CONTINUE
@@03_sb_check.sql
"""
open(OUT + '/sql/00_sb_install.sql', 'w', encoding='utf-8').write(install)

# ---------------------------------------------------------------- Java: BuiltinCatalog
def js(s):
    if s is None:
        return 'null'
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"') + '"'

J = ['package com.dan.sternbild.data;', '',
     '/**',
     ' * Eingebaute Kopie der Stammdaten, identisch mit sql/02_sb_data.sql.',
     ' * Wird genutzt, wenn Oracle nicht erreichbar ist, und fuer Headless-Tests.',
     ' * GENERIERT aus gen/data.py – nicht von Hand aendern.',
     ' */',
     'final class BuiltinCatalog {', '',
     '    private BuiltinCatalog() { }', '',
     '    /** code, nameDe, nameLa, kind, zodiacOrder (0 = keiner), glyph, beschreibung */',
     '    static final Object[][] CONSTELLATIONS = {']
for c in CONS:
    glyph = '"\\u%04X"' % c['glyph'] if c['glyph'] else 'null'
    J.append('        {%s, %s, %s, \'%s\', %d, %s,\n         %s},' % (js(c['code']), js(c['de']), js(c['la']), c['kind'], c['zodiac'] or 0, glyph, js(c['desc'])))
J.append('    };')
J.append('')
J.append('    /** starId, code, no, name, bayer, raHours, decDeg, mag, colorHex, spectral, distLy (0 = unbekannt) */')
J.append('    static final Object[][] STARS = {')
star_id = {}
nid = 1
for c in CONS:
    for i, s in enumerate(c['stars']):
        star_id[(c['code'], i)] = nid
        name, bayer, ra, dec, mag, col, spec, dist = s
        J.append('        {%dL, %s, %d, %s, %s, %sd, %sd, %sd, %s, %s, %d},' % (nid, js(c['code']), i + 1, js(name), js(bayer), repr(ra), repr(dec), repr(mag), js(col), js(spec), dist or 0))
        nid += 1
J.append('    };')
J.append('')
J.append('    /** code, fromStarId, toStarId, drawOrder */')
J.append('    static final Object[][] LINES = {')
for c in CONS:
    for o, (a, b) in enumerate(c['lines']):
        def rid(r):
            if isinstance(r, str):
                code, k = r.split('.'); return star_id[(code, int(k))]
            return star_id[(c['code'], r)]
        J.append('        {%s, %dL, %dL, %d},' % (js(c['code']), rid(a), rid(b), o + 1))
J.append('    };')
J.append('')
J.append('    /** code, name, width, height, horizonY, moonX, moonY */')
J.append('    static final Object[] SCENE = {%s, %s, %sd, %sd, %sd, %sd, %sd};' % (js(SCENE['code']), js(SCENE['name']), SCENE['w'], SCENE['h'], SCENE['horizon'], SCENE['moon_x'], SCENE['moon_y']))
J.append('')
J.append('    /** code, x, y, scale, rotationDeg */')
J.append('    static final Object[][] SCENE_ITEMS = {')
for c in CONS:
    x, y, s, r = ITEMS[c['code']]
    J.append('        {%s, %sd, %sd, %sd, %sd},' % (js(c['code']), x, y, s, r))
J.append('    };')
J.append('')
J.append('    /** code, system, startMmdd, endMmdd */')
J.append('    static final Object[][] PERIODS = {')
for code, (real, astro) in PERIODS.items():
    J.append('        {%s, "REAL", %d, %d},' % (js(code), real[0], real[1]))
    if astro:
        J.append('        {%s, "ASTRO", %d, %d},' % (js(code), astro[0], astro[1]))
J.append('    };')
J.append('')
J.append('    /** code, strokeNo, points */')
J.append('    static final Object[][] STROKES = {')
for c in CONS:
    for no, stroke in enumerate(FIGURES.get(c['code'], [])):
        J.append('        {%s, %d, "%s"},' % (js(c['code']), no + 1, pts(stroke)))
J.append('    };')
J.append('')
J.append('    /** code, name, refCode, raHours, decDeg, start, peak, end, zhr, speedKms, colorHex, beschreibung */')
J.append('    static final Object[][] SHOWERS = {')
for m in METEORS:
    code, name, ref, ra, dec, st_, pk, en, zhr, v, col, desc = m
    J.append('        {%s, %s, %s, %sd, %sd, %d, %d, %d, %d, %d, %s,\n         %s},' % (js(code), js(name), js(ref), ra, dec, st_, pk, en, zhr, v, js(col), js(desc)))
J.append('    };')
J.append('}')
open(OUT + '/src/com/dan/sternbild/data/BuiltinCatalog.java', 'w', encoding='utf-8').write('\n'.join(J) + '\n')
print('Sternbilder', n_const, 'Sterne', n_star, 'Linien', n_line, 'Perioden', n_per, 'Strichzuege', n_str, 'Meteorstroeme', n_met)
