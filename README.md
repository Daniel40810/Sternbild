# Sternbild – Sternbilder und Tierkreis über der Ostsee

Java-21-Swing-App (NetBeans/Ant) mit einem animierten, frei komponierten Nachthimmel:
13 Tierkreis-Sternbilder (inkl. Schlangenträger), 10 bekannte Sternbilder und die Plejaden.
Die Daten kommen aus Oracle 21c (Service `PDBORCL`, Schema `DEMO`). Ist die Datenbank nicht
erreichbar, fällt die App auf identische eingebaute Daten zurück.

## Start

1. **Datenbank** (einmalig, als `DEMO`): in SQL Developer `sql/00_sb_install.sql` öffnen und mit **F5** als Skript ausführen,
   oder `sqlplus DEMO@//localhost:1521/PDBORCL @sql/00_sb_install.sql`.
   Am Ende zeigt `03_sb_check.sql` die erwarteten Zeilenzahlen (24 / 169 / 156 / 24 / 25 / 122 / 10).
2. **App**: In NetBeans *Run Project* (Hauptklasse `com.dan.sternbild.app.SternbildApp`).
   Ohne Datenbank: Programmargument `--offline`.

Die Verbindung steht in `src/com/dan/sternbild/sternbild.properties`. Wer sie ändern will, ohne neu zu bauen,
legt eine `sternbild.properties` ins Arbeitsverzeichnis. Diese Datei hat Vorrang.

## Bedienung

| Aktion | Wirkung |
|---|---|
| Maus über ein Sternbild | Linien leuchten auf, Lichtpuls läuft über die Figur, Info-Karte erscheint |
| Klick auf ein Sternbild | festhalten; Klick in den leeren Himmel löst |
| Dämmerung / Nacht | Stimmung wie `Sternbild.jpg` bzw. Milchstraßennacht wie `Sternbild1.jpg` |
| Linien · Namen · Milchstraße | Ebenen ein/aus |
| Tour | hebt alle 24 Figuren nacheinander hervor (6,5 s) |
| Geburtstag … | zeigt, vor welchem Sternbild die Sonne an dem Tag wirklich stand, das astrologische Zeichen wird golden markiert |
| Meteore heute | Meteorstrom, der heute aktiv ist, mit Rate nach Datum und ZHR; der Radiant ist markiert |
| Meteore: ein Strom | simuliert diesen Strom im Maximum (z. B. Perseiden, Geminiden) |
| Nur einzelne | nur gelegentliche sporadische Sternschnuppen |
| Maus über Mond oder Radiant | Karte mit Mondphase und nächstem Voll-/Neumond bzw. Daten des Meteorstroms |
| Theme-Auswahl | FTheme-Preset für die Bedienelemente, der Himmel behält seinen eigenen Look |

Effekte: Sagenfiguren zeichnen sich beim Hervorheben als leuchtende Linien hinter dem Sternbild ein,
Mond in der heutigen Phase mit Erdschein, Halo und Mondglitzern auf der Ostsee,
Meteorströme aus ihrem Radianten mit kurzem Nachleuchten, Linien zeichnen sich beim Start nacheinander ein, Sterne funkeln (am Horizont stärker),
Vier-Strahl-Sterne, Sternschnuppen, Milchstraße mit Dunkelwolken, Spiegelungen heller Sterne und Glitzern
auf dem Wasser, Leuchtturm mit rotierendem Lichtkegel und Aufblitzen, wiegendes Dünengras, leichte Parallaxe
bei Mausbewegung.

## Aufbau

```
com.dan.sternbild
├─ app      SternbildApp (FFrame-Hülle, lädt im Hintergrund), ControlBar (F-Komponenten, FTheme-gebunden)
├─ model    Star, Constellation, SkyData, SceneLayout, ZodiacPeriod, ZodiacSystem, SkyMath (Projektion),
│           MeteorShower (Aktivität nach Datum), MoonPhase (Phase, nächster Voll-/Neumond)
├─ data     SkyRepository → OracleSkyRepository (JDBC) | BuiltinSkyRepository (BuiltinCatalog)
│           SkyDataAssembler baut aus beiden Quellen dasselbe Modell; DbConfig liest sternbild.properties
└─ render   SkyCanvas (16-ms-Timer, alle Ebenen), Backdrop (gecachter Himmel + Silhouetten),
            Landscape (Küste, Düne), MoonPainter, Sprites, SkyMood, SceneTransform
```

**Datenbank (Präfix `SB_`)**

| Tabelle | Inhalt |
|---|---|
| `SB_CONSTELLATION` | 24 Figuren: IAU-Kürzel, deutscher und lateinischer Name, Art (Sternbild/Haufen), Tierkreis-Nr., Symbol, Text |
| `SB_STAR` | 169 Figurensterne: RA/Dek (J2000, gerundet), mag, Farbe, Spektralklasse, Entfernung |
| `SB_CONST_LINE` | 156 Linien mit Zeichenreihenfolge; Sterne dürfen aus fremden Figuren stammen (Alpheratz) |
| `SB_SCENE`, `SB_SCENE_ITEM` | frei komponierter Himmel „OSTSEE“: Position, Skalierung und Drehung je Figur |
| `SB_ZODIAC_PERIOD` | Sonnendurchgang astronomisch (`REAL`) und astrologisch (`ASTRO`) |
| `SB_FIGURE_STROKE` | 122 Strichzüge der Sagenfiguren; Punkte hängen an Figurensternen (`s3:1.2:-0.5`) oder sind frei (`p4:-2`) |
| `SB_METEOR_SHOWER` | 10 Meteorströme (IMO-Kalender): Radiant (RA/Dek), Aktivität, Maximum, ZHR, Geschwindigkeit, Farbe |

Die Mondposition steht in `SB_SCENE.MOON_X/MOON_Y`; die Mondphase berechnet die App selbst.
Fehlen die beiden neuen Tabellen (ältere Installation), startet die App ohne Figuren und Ströme.

Schlüssel über Sequence + Trigger, kein IDENTITY. Nicht-ASCII-Texte stehen als `UNISTR`, damit die Skripte
unabhängig von der Kodierung des Editors laufen.

**Eine Datenquelle.** `gen/data.py` (Sterne, Linien, Texte, Anordnung, Meteorströme) und `gen/figures.py`
(Sagenfiguren) sind die einzigen Stellen, an denen Daten gepflegt werden. `python gen/generate.py` erzeugt daraus `sql/*.sql` **und** `BuiltinCatalog.java`.
`python gen/layout.py` rendert zusätzlich eine Vorschau der Anordnung (`gen/layout_preview.png`).
Zum Verschieben einzelner Figuren im Himmel gibt es zwei Wege: in `layout.py` unter `OVERRIDES` oder direkt in
`SB_SCENE_ITEM` (die App liest beim Start).

## Tests

`test/com/dan/sternbild/SternbildSmokeTest` (Klasse mit `main`, im Projektordner starten), 45 Checks:
Daten (24/13/169/156, geliehener Alpheratz, alle Sterne im Bild), Tierkreis-Logik (jeder Tag des
Schaltjahrs genau einem Zeichen je System), SQL-Skripte ⇄ eingebaute Daten, Oracle-Fallback,
Renderer (beide Stimmungen), Hover, Klick, Geburtstag und Tour über die echten Eingabepfade,
Sagenfiguren (alle Punkte gültig, im Bild), Meteorströme je Datum (Perseiden 12.08., Geminiden 14.12. …),
alle Radianten im sichtbaren Himmel, Mondphase gegen bekannte Termine, Hover über Mond und Radiant.
`ScreenshotHarness` startet die echte App und speichert Screenshots (siehe `docs/`).

## Grenzen

- Die SQL-Skripte sind generiert und gegen die eingebauten Daten geprüft. Der Grundstand lief erfolgreich auf
  PDBORCL; die Erweiterung (Sagenfiguren, Meteorströme, Mondposition) wurde dort noch nicht installiert.
- Der Himmel ist bewusst komponiert, nicht astronomisch für Ort und Uhrzeit berechnet. Die Figuren selbst sind
  aber aus echten Sternpositionen projiziert.
- Hintergrundsterne und Milchstraße werden prozedural erzeugt und sind nicht in der Datenbank.
- Die Mondphase stammt aus der mittleren Monatslänge (±1 Tag). Der Mond steht an einer festen Stelle der Szene.
- Die Meteorraten sind für die Darstellung gedeckelt (höchstens 4 pro Sekunde) und verlaufen linear zum Maximum.
