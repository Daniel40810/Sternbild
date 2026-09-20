package com.dan.sternbild.render;

import com.dan.sternbild.model.Constellation;
import com.dan.sternbild.model.MeteorShower;
import com.dan.sternbild.model.MoonPhase;
import com.dan.sternbild.model.SceneLayout;
import com.dan.sternbild.model.SkyData;
import com.dan.sternbild.model.Star;
import com.dan.sternbild.model.ZodiacPeriod;
import com.dan.sternbild.model.ZodiacSystem;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Der animierte Nachthimmel: frei komponierte Sternbilder über der Ostsee.
 * <p>
 * Ebenen (von hinten nach vorn): Himmel + Milchstraße + schwache Sterne + Meer (gecacht) ·
 * funkelnde Hintergrundsterne · Sternbildlinien · Figurensterne · Beschriftung · Sternschnuppen ·
 * Glitzern und Spiegelungen auf dem Wasser · Küste, Düne, Strandkorb (gecacht) · Leuchtfeuer ·
 * Info-Karte und Geburtstags-Banner.
 * <p>
 * Der Himmel folgt bewusst nicht dem FTheme – er hat seinen eigenen Nachthimmel-Look.
 */
public class SkyCanvas extends JComponent {

    private static final SceneLayout DEFAULT_SCENE = new SceneLayout("OSTSEE", "Ostsee", 192, 108, 90, Double.NaN, Double.NaN);
    private static final long TICK_MS = 16;
    private static final double INTRO_STAGGER = 0.16;
    private static final double INTRO_DURATION = 1.5;
    private static final double TOUR_SECONDS = 6.5;
    private static final Color LINE_CORE = new Color(0x95B8FF);
    private static final Color LINE_FOCUS = new Color(0xD6E4FF);
    private static final Color LINE_GLOW = new Color(0x7FA6FF);
    private static final Color GOLD = new Color(0xF2D6B2);
    private static final Color CARD_BG = new Color(10, 19, 48, 196);
    private static final Color CARD_RIM = new Color(143, 180, 255, 80);
    private static final Color TEXT = new Color(0xDDE4F7);
    private static final Color MUTED = new Color(0x8F9CC6);
    private static final Color ACCENT = new Color(0x8FB4FF);

    // ------------------------------------------------------------------ Zustand
    private SkyData data;
    private SkyMood mood = SkyMood.DAEMMERUNG;
    private boolean showLines = true;
    private boolean showNames = true;
    private boolean showMilkyWay = true;
    private boolean tour = true;
    private String loadingText = "Himmel wird vorbereitet …";

    private Constellation hover;
    private Constellation pinned;
    private Constellation tourFocus;
    private Object cardShown;
    private float cardAlpha;
    private final Map<Constellation, Float> focus = new IdentityHashMap<>();
    private final Map<Constellation, Integer> introIndex = new IdentityHashMap<>();
    private List<Constellation> ordered = List.of();
    private int tourIdx = -1;
    private double tourNext;

    private MeteorShower shower;
    private double showerIntensity;
    private double nextShowerMeteor;
    private String statusText = "";
    private MoonPhase moon = MoonPhase.at(java.time.Instant.now(), java.time.ZoneId.systemDefault());
    private double moonUpdated;
    private BufferedImage moonImage;
    private double moonImageAge = -1;
    private int moonImageSize;
    private boolean hoverMoon;
    private boolean hoverRadiant;
    private Object cardTarget;

    private LocalDate birthday;
    private Constellation birthdayReal;
    private Constellation birthdayAstro;

    private final long t0 = System.nanoTime();
    private double introStart = -1;
    private final Timer timer = new Timer((int) TICK_MS, e -> tick());
    private final Random rnd = new Random(42);
    private final List<Meteor> meteors = new ArrayList<>();
    private double nextMeteor = 2.5;
    private final Twinkler[] twinklers = new Twinkler[440];
    private final Glint[] glints = new Glint[150];
    private final Blade[] blades = new Blade[46];
    private double parX, parY, parTX, parTY;

    private SceneTransform xf;
    private BufferedImage skyImage;
    private BufferedImage foreImage;
    private SkyMood skyImageMood;
    private boolean skyImageMilky;

    private final Font labelFont;
    private final Font labelFontFocus;
    private final Font latinFont;
    private final Font glyphFont;
    private final Font uiFont;
    private final Font uiFontBold;
    private final Font monoFont;
    private final boolean glyphs;

    private final List<Consumer<Constellation>> focusListeners = new CopyOnWriteArrayList<>();

    public SkyCanvas() {
        setOpaque(true);
        setPreferredSize(new Dimension(1400, 780));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        String serif = firstFont("Palatino Linotype", "Book Antiqua", "Georgia", "Cambria", "DejaVu Serif", "Serif");
        labelFont = new Font(serif, Font.PLAIN, 14);
        labelFontFocus = new Font(serif, Font.PLAIN, 21);
        latinFont = new Font(serif, Font.ITALIC, 13);
        String sym = firstFontDisplaying('♈', "Segoe UI Symbol", "DejaVu Sans", "Noto Sans Symbols", "Symbola", "Dialog");
        glyphs = sym != null;
        glyphFont = new Font(sym != null ? sym : "Dialog", Font.PLAIN, 16);
        String sans = firstFont("Segoe UI", "Figtree", "DejaVu Sans", "SansSerif");
        uiFont = new Font(sans, Font.PLAIN, 12);
        uiFontBold = new Font(sans, Font.BOLD, 12);
        monoFont = new Font(firstFont("Cascadia Mono", "Consolas", "DejaVu Sans Mono", "Monospaced"), Font.PLAIN, 11);

        for (int i = 0; i < twinklers.length; i++) {
            twinklers[i] = new Twinkler(rnd);
        }
        for (int i = 0; i < glints.length; i++) {
            glints[i] = new Glint(rnd);
        }
        for (int i = 0; i < blades.length; i++) {
            blades[i] = new Blade(rnd);
        }

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                handleMove(e.getX(), e.getY());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setHover(null);
                parTX = 0;
                parTY = 0;
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                handleClick(e.getX(), e.getY());
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    // ------------------------------------------------------------------ öffentliche API

    /** Setzt den Datenstand und startet die Einzeichen-Animation neu. */
    public void setData(SkyData data) {
        this.data = data;
        focus.clear();
        introIndex.clear();
        hover = null;
        pinned = null;
        tourFocus = null;
        cardShown = null;
        cardAlpha = 0;
        shower = null;
        hoverMoon = false;
        hoverRadiant = false;
        if (data != null) {
            List<Constellation> list = new ArrayList<>(data.getConstellations());
            list.sort(Comparator.comparingDouble(Constellation::getPosX));
            ordered = list;
            for (int i = 0; i < list.size(); i++) {
                introIndex.put(list.get(i), i);
            }
            introStart = now();
            tourNext = introStart + INTRO_STAGGER * list.size() + INTRO_DURATION + 1.0;
            tourIdx = -1;
            if (birthday != null) {
                setBirthday(birthday);
            }
        } else {
            ordered = List.of();
        }
        skyImage = null;
        repaint();
    }

    public SkyData getData() { return data; }

    public void setMood(SkyMood mood) {
        SkyMood old = this.mood;
        this.mood = mood == null ? SkyMood.DAEMMERUNG : mood;
        skyImage = null;
        firePropertyChange("mood", old, this.mood);
        repaint();
    }

    public SkyMood getMood() { return mood; }

    public void setShowLines(boolean b) { showLines = b; repaint(); }
    public boolean isShowLines() { return showLines; }
    public void setShowNames(boolean b) { showNames = b; repaint(); }
    public boolean isShowNames() { return showNames; }
    public void setShowMilkyWay(boolean b) { showMilkyWay = b; skyImage = null; repaint(); }
    public boolean isShowMilkyWay() { return showMilkyWay; }

    public void setTour(boolean b) {
        tour = b;
        if (b) {
            tourNext = now();
        } else {
            tourFocus = null;
        }
    }

    public boolean isTour() { return tour; }

    /**
     * Aktiver Meteorstrom (null = nur einzelne sporadische Sternschnuppen).
     *
     * @param intensity 0..1, z. B. {@link MeteorShower#activity(LocalDate)} oder 1 für die Simulation des Maximums
     */
    public void setMeteorShower(MeteorShower shower, double intensity) {
        this.shower = shower;
        this.showerIntensity = Math.max(0, Math.min(1, intensity));
        this.nextShowerMeteor = now() + 0.5;
        repaint();
    }

    public MeteorShower getMeteorShower() { return shower; }
    public double getShowerIntensity() { return showerIntensity; }

    /** Aktuelle Mondphase (wird minütlich neu berechnet). */
    public MoonPhase getMoonPhase() { return moon; }

    /** Setzt die Mondphase fest (Tests, Vorschau anderer Tage). */
    public void setMoonPhase(MoonPhase phase) {
        moon = phase;
        moonUpdated = Double.MAX_VALUE;
        moonImage = null;
        repaint();
    }

    /** Kleine Statuszeile unten links im Bild (Datenquelle). */
    public void setStatusText(String text) {
        statusText = text == null ? "" : text;
        repaint();
    }

    /** Liegt die Maus über dem Mond bzw. dem Radianten? */
    public boolean isHoverMoon() { return hoverMoon; }
    public boolean isHoverRadiant() { return hoverRadiant; }

    /** Hinweistext solange keine Daten gesetzt sind. */
    public void setLoadingText(String text) { loadingText = text; repaint(); }

    /**
     * Geburtstag: hebt das Sternbild hervor, vor dem die Sonne an diesem Tag wirklich stand,
     * und markiert das astrologische Zeichen zusätzlich in Gold. null löscht die Auswahl.
     */
    public void setBirthday(LocalDate date) {
        birthday = date;
        birthdayReal = null;
        birthdayAstro = null;
        if (date != null && data != null) {
            birthdayReal = data.zodiacFor(date, ZodiacSystem.REAL).orElse(null);
            birthdayAstro = data.zodiacFor(date, ZodiacSystem.ASTRO).orElse(null);
            pinned = birthdayReal;
        }
        repaint();
    }

    public LocalDate getBirthday() { return birthday; }
    public Constellation getBirthdayReal() { return birthdayReal; }
    public Constellation getBirthdayAstro() { return birthdayAstro; }

    /** Hält ein Sternbild fest (null löst). */
    public void setPinned(Constellation c) {
        pinned = c;
        fireFocus();
        repaint();
    }

    public Constellation getPinned() { return pinned; }

    /** Aktuell hervorgehobenes Sternbild: festgehalten, unter der Maus oder aus der Tour. */
    public Constellation getFocused() {
        if (pinned != null) {
            return pinned;
        }
        if (hover != null) {
            return hover;
        }
        return tour ? tourFocus : null;
    }

    public void addFocusListener(Consumer<Constellation> l) { focusListeners.add(l); }
    public void removeFocusListener(Consumer<Constellation> l) { focusListeners.remove(l); }

    /** Treffer-Test in Bildschirmkoordinaten; wirkt auf den zuletzt gezeichneten Zustand. */
    public Constellation pick(int x, int y) {
        if (data == null || xf == null) {
            return null;
        }
        Constellation best = null;
        double bd = 26;
        for (Constellation c : data.getConstellations()) {
            int n = c.getStars().size();
            double[] sx = new double[n], sy = new double[n];
            for (int i = 0; i < n; i++) {
                sx[i] = starX(c, i);
                sy[i] = starY(c, i);
                double d = Math.hypot(x - sx[i], y - sy[i]);
                if (d < bd) {
                    bd = d;
                    best = c;
                }
            }
            for (int[] l : c.getLines()) {
                double d = Line2D.ptSegDist(sx[l[0]], sy[l[0]], sx[l[1]], sy[l[1]], x, y) / 0.7;
                if (d < bd) {
                    bd = d;
                    best = c;
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ Test-Hooks (nutzen die echten Pfade)

    /** Setzt die Animationszeit so, dass das Intro abgeschlossen ist. */
    public void finishIntroForTest() {
        introStart = now() - 100;
    }

    /** Simuliert eine Mausbewegung. */
    public void simulateMoveForTest(int x, int y) { handleMove(x, y); }

    /** Simuliert einen Klick. */
    public void simulateClickForTest(int x, int y) { handleClick(x, y); }

    /** Führt n Animationsschritte ohne Timer aus. */
    public void advanceForTest(int n) {
        for (int i = 0; i < n; i++) {
            tick();
        }
    }

    /** Bildschirmposition des Mondes (für Tests), null ohne Mond. */
    public java.awt.Point moonOnScreenForTest() {
        ensureTransform();
        if (data == null || !data.getScene().hasMoon()) {
            return null;
        }
        double[] m = moonScreen();
        return new java.awt.Point((int) Math.round(m[0]), (int) Math.round(m[1]));
    }

    /** Bildschirmposition des Radianten (für Tests), null ohne Strom. */
    public java.awt.Point radiantOnScreenForTest() {
        ensureTransform();
        if (shower == null) {
            return null;
        }
        double[] r = radiantScreen();
        return new java.awt.Point((int) Math.round(r[0]), (int) Math.round(r[1]));
    }

    /** Bildschirmposition des Mittelpunkts eines Sternbilds (für Tests). */
    public java.awt.Point centerOnScreenForTest(Constellation c) {
        ensureTransform();
        double x = 0, y = 0;
        for (int i = 0; i < c.getStars().size(); i++) {
            x += starX(c, i);
            y += starY(c, i);
        }
        return new java.awt.Point((int) Math.round(x / c.getStars().size()), (int) Math.round(y / c.getStars().size()));
    }

    // ------------------------------------------------------------------ Lebenszyklus

    @Override
    public void addNotify() {
        super.addNotify();
        timer.start();
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    private void tick() {
        double t = now();
        // Fokus weich überblenden
        Constellation f = getFocused();
        if (data != null) {
            for (Constellation c : data.getConstellations()) {
                float cur = focus.getOrDefault(c, 0f);
                float target = c == f ? 1f : 0f;
                float next = cur + (target - cur) * 0.12f;
                if (Math.abs(next - target) < 0.002f) {
                    next = target;
                }
                focus.put(c, next);
            }
        }
        // Karte: erst ausblenden, dann wechseln
        Object target = hoverMoon ? moon : hoverRadiant ? shower : f;
        if (cardShown != target) {
            cardAlpha = Math.max(0, cardAlpha - 0.12f);
            if (cardAlpha == 0) {
                cardShown = target;
            }
        } else if (target != null) {
            cardAlpha = Math.min(1, cardAlpha + 0.08f);
        }
        // Mondphase minütlich
        if (t - moonUpdated > 60 && moonUpdated != Double.MAX_VALUE) {
            moon = MoonPhase.at(java.time.Instant.now(), java.time.ZoneId.systemDefault());
            moonUpdated = t;
        }
        // Meteorstrom: Rate aus ZHR und Aktivität, gedeckelt für die Darstellung
        if (shower != null && xf != null && data != null && t >= nextShowerMeteor) {
            double rate = Math.min(4.0, 0.15 + shower.zhr() / 40.0 * showerIntensity);
            meteors.add(Meteor.fromRadiant(rnd, xf, t, radiantScreen(), shower));
            nextShowerMeteor = t + (-Math.log(1 - rnd.nextDouble() * 0.95)) / rate;
        }
        // Tour
        if (tour && data != null && pinned == null && hover == null && t >= tourNext && !ordered.isEmpty()) {
            tourIdx = (tourIdx + 1) % ordered.size();
            tourFocus = ordered.get(tourIdx);
            tourNext = t + TOUR_SECONDS;
            fireFocus();
        }
        // Parallaxe
        parX += (parTX - parX) * 0.06;
        parY += (parTY - parY) * 0.06;
        // Sternschnuppen
        if (t >= nextMeteor && xf != null) {
            meteors.add(new Meteor(rnd, xf, t));
            nextMeteor = t + 3.5 + rnd.nextDouble() * 6.5;
        }
        meteors.removeIf(m -> t - m.born > m.life);
        repaint();
    }

    // ------------------------------------------------------------------ Eingabe

    private void handleMove(int x, int y) {
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        parTX = (x / (double) w - 0.5) * 2;
        parTY = (y / (double) h - 0.5) * 2;
        hoverMoon = isOverMoon(x, y);
        hoverRadiant = !hoverMoon && isOverRadiant(x, y);
        setHover(hoverMoon || hoverRadiant ? null : pick(x, y));
        repaint();
    }

    private boolean isOverMoon(int x, int y) {
        if (data == null || xf == null || !data.getScene().hasMoon()) {
            return false;
        }
        double[] m = moonScreen();
        return Math.hypot(x - m[0], y - m[1]) < moonRadius() * 1.3;
    }

    private boolean isOverRadiant(int x, int y) {
        if (shower == null || xf == null) {
            return false;
        }
        double[] r = radiantScreen();
        return Math.hypot(x - r[0], y - r[1]) < 16;
    }

    private void handleClick(int x, int y) {
        if (isOverMoon(x, y) || isOverRadiant(x, y)) {
            return;
        }
        Constellation c = pick(x, y);
        if (c == null) {
            pinned = null;
            if (birthday != null) {
                birthday = null;
                birthdayReal = null;
                birthdayAstro = null;
            }
        } else {
            pinned = c == pinned ? null : c;
        }
        fireFocus();
        repaint();
    }

    private void setHover(Constellation c) {
        if (c != hover) {
            hover = c;
            fireFocus();
            repaint();
        }
    }

    private void fireFocus() {
        Constellation f = getFocused();
        for (Consumer<Constellation> l : focusListeners) {
            l.accept(f);
        }
    }

    // ------------------------------------------------------------------ Zeichnen

    private double now() {
        return (System.nanoTime() - t0) / 1e9;
    }

    private void ensureTransform() {
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        SceneLayout scene = data != null ? data.getScene() : DEFAULT_SCENE;
        if (xf == null || xf.width != w || xf.height != h || xf.sceneWidth != scene.width()
                || xf.horizonScene != scene.horizonY()) {
            xf = new SceneTransform(scene, w, h);
            skyImage = null;
            foreImage = null;
        }
    }

    private double figParX() { return parX * 7 * xf.sizeFactor(); }
    private double figParY() { return parY * 4 * xf.sizeFactor(); }

    private double moonRadius() {
        return 2.9 * xf.scale;
    }

    private double[] moonScreen() {
        SceneLayout sc = data.getScene();
        return new double[] { xf.x(sc.moonX()) + parX * 3, xf.y(sc.moonY()) + parY * 2 };
    }

    private double[] radiantScreen() {
        double[] r = shower.radiantScene();
        return new double[] { xf.x(r[0]) + figParX(), xf.y(r[1]) + figParY() };
    }

    private double starX(Constellation c, int i) {
        return xf.x(c.getPosX() + c.getLocalX(i)) + figParX();
    }

    private double starY(Constellation c, int i) {
        return xf.y(c.getPosY() - c.getLocalY(i)) + figParY();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        ensureTransform();
        if (skyImage == null || skyImageMood != mood || skyImageMilky != showMilkyWay) {
            skyImage = toCompatible(Backdrop.renderSky(xf, mood, showMilkyWay, 7L), java.awt.Transparency.OPAQUE);
            skyImageMood = mood;
            skyImageMilky = showMilkyWay;
        }
        if (foreImage == null) {
            foreImage = toCompatible(Backdrop.renderForeground(xf), java.awt.Transparency.TRANSLUCENT);
        }
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            double t = now();
            g.drawImage(skyImage, 0, 0, null);
            paintTwinklers(g, t);
            if (data != null) {
                paintMoon(g);
                paintFigures(g, t);
                paintRadiant(g, t);
                paintLabels(g);
            }
            paintMeteors(g, t);
            paintWater(g, t);
            g.drawImage(foreImage, 0, 0, null);
            paintLighthouse(g, t);
            paintGrass(g, t);
            if (data == null) {
                paintLoading(g);
            } else {
                paintCard(g);
                paintBirthday(g);
                paintStatus(g);
            }
        } finally {
            g.dispose();
        }
    }

    private void paintTwinklers(Graphics2D g, double t) {
        Composite old = g.getComposite();
        double px = parX * 2.5, py = parY * 1.5;
        double hz = xf.horizonScreen;
        for (Twinkler s : twinklers) {
            double x = s.fx * xf.width + px, y = s.fy * hz * 0.97 + py;
            double low = Math.max(0, (y / hz - 0.55) / 0.45);
            double amp = 0.18 + 0.55 * low * low;
            double tw = 1 - amp * (0.5 + 0.5 * Math.sin(t * s.freq + s.phase));
            float dimming = mood == SkyMood.DAEMMERUNG ? (float) Math.max(0.25, 1 - low * 0.7) : 1f;
            float a = (float) Math.max(0, Math.min(1, s.alpha * tw * dimming));
            double r = s.radius * xf.sizeFactor();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a * 0.8f));
            double gs = r * 6;
            g.drawImage(Sprites.glow(s.color), (int) (x - gs / 2), (int) (y - gs / 2), (int) gs, (int) gs, null);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            g.setColor(s.color);
            g.fill(new Ellipse2D.Double(x - r * 0.55, y - r * 0.55, r * 1.1, r * 1.1));
        }
        g.setComposite(old);
    }

    private double introProgress(Constellation c, double t) {
        if (introStart < 0) {
            return 0;
        }
        int idx = introIndex.getOrDefault(c, 0);
        return clamp((t - introStart - 0.4 - idx * INTRO_STAGGER) / INTRO_DURATION);
    }

    private void paintFigures(Graphics2D g, double t) {
        Composite old = g.getComposite();
        double sf = xf.sizeFactor();
        for (Constellation c : data.getConstellations()) {
            double p = introProgress(c, t);
            if (p <= 0) {
                continue;
            }
            float fa = focus.getOrDefault(c, 0f);
            boolean gold = c == birthdayAstro && birthdayAstro != birthdayReal;
            int n = c.getStars().size();
            double[] sx = new double[n], sy = new double[n];
            for (int i = 0; i < n; i++) {
                sx[i] = starX(c, i);
                sy[i] = starY(c, i);
            }
            // Plejaden: zarter Nebelschleier
            if (c.isCluster()) {
                double cx = 0, cy = 0;
                for (int i = 0; i < n; i++) {
                    cx += sx[i];
                    cy += sy[i];
                }
                cx /= n;
                cy /= n;
                double rr = 34 * sf * (1 + fa * 0.35);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) ((0.22 + 0.25 * fa) * p)));
                g.drawImage(Sprites.NEBULA[0], (int) (cx - rr), (int) (cy - rr), (int) (rr * 2), (int) (rr * 2), null);
            }
            // Sagenfigur hinter den Linien, zeichnet sich mit dem Fokus ein
            if (fa > 0.01f && !c.getFigureStrokes().isEmpty()) {
                paintFigure(g, c, fa, sf);
            }
            // Linien
            List<int[]> lines = c.getLines();
            int nl = lines.size();
            boolean drawLines = showLines || fa > 0.01f || gold;
            for (int i = 0; i < nl && drawLines; i++) {
                double fr = clamp(p * nl - i);
                if (fr <= 0) {
                    continue;
                }
                int[] l = lines.get(i);
                double ax = sx[l[0]], ay = sy[l[0]], bx = sx[l[1]], by = sy[l[1]];
                double dx = bx - ax, dy = by - ay, len = Math.hypot(dx, dy);
                if (len < 10) {
                    continue;
                }
                double gap = Math.min(5.5 * sf, len * 0.2), ux = dx / len, uy = dy / len;
                double x1 = ax + ux * gap, y1 = ay + uy * gap;
                double segLen = (len - 2 * gap) * fr;
                double x2 = x1 + ux * segLen, y2 = y1 + uy * segLen;
                float base = showLines ? 1f : fa;
                Color glowC = gold ? GOLD : LINE_GLOW;
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clampF((0.10f + 0.28f * fa) * base)));
                g.setColor(glowC);
                g.setStroke(new BasicStroke((float) ((4.2 + 3.5 * fa) * sf), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(x1, y1, x2, y2));
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clampF((0.55f + 0.45f * fa) * base)));
                g.setColor(gold ? GOLD : blend(LINE_CORE, LINE_FOCUS, fa));
                g.setStroke(new BasicStroke((float) ((1.15 + 0.6 * fa) * sf), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(x1, y1, x2, y2));
                // Lichtpuls über die Linien des fokussierten Sternbilds
                if (fa > 0.2f && fr >= 1) {
                    double ph = (t * 0.55 + i * 0.23) % 1.0;
                    double qx = x1 + (len - 2 * gap) * ux * ph, qy = y1 + (len - 2 * gap) * uy * ph;
                    double ps = 16 * sf;
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clampF((float) (fa * 0.95 * Math.sin(Math.PI * ph)))));
                    g.drawImage(Sprites.glow(LINE_FOCUS), (int) (qx - ps / 2), (int) (qy - ps / 2), (int) ps, (int) ps, null);
                }
            }
            // Sterne
            double starIn = clamp(p * 1.6);
            for (int i = 0; i < n; i++) {
                Star s = c.getStars().get(i);
                double tw = 1 - 0.14 * (0.5 + 0.5 * Math.sin(t * (1.7 + (s.id() % 7) * 0.37) + s.id()));
                float a = (float) (Math.max(0.35, Math.min(1, (6.2 - s.mag()) / 4.6)) * tw * starIn);
                a = clampF(a * (0.85f + 0.15f * fa));
                double r = (0.95 + Math.max(0, 5.2 - s.mag()) * 0.6) * sf * (1 + 0.12 * fa);
                double gs = r * 7.5;
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a * 0.9f));
                g.drawImage(Sprites.glow(s.color()), (int) (sx[i] - gs / 2), (int) (sy[i] - gs / 2), (int) gs, (int) gs, null);
                if (s.mag() < 2.4) {
                    double ss = r * (10 + Math.max(0, 1.6 - s.mag()) * 3);
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                            clampF((float) (a * 0.7 * (0.8 + 0.2 * Math.sin(t * 1.3 + i))))));
                    g.drawImage(Sprites.SPIKE, (int) (sx[i] - ss / 2), (int) (sy[i] - ss / 2), (int) ss, (int) ss, null);
                }
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
                g.setColor(s.color());
                g.fill(new Ellipse2D.Double(sx[i] - r * 0.6, sy[i] - r * 0.6, r * 1.2, r * 1.2));
            }
        }
        g.setComposite(old);
        g.setStroke(new BasicStroke(1f));
    }

    private void paintLabels(Graphics2D g) {
        Composite old = g.getComposite();
        double t = now();
        for (Constellation c : data.getConstellations()) {
            float fa = focus.getOrDefault(c, 0f);
            double p = introProgress(c, t);
            if (p < 0.6 || (!showNames && fa < 0.02f)) {
                continue;
            }
            float appear = (float) clamp((p - 0.6) / 0.4);
            double cx = 0, maxY = -1e9;
            int n = c.getStars().size();
            for (int i = 0; i < n; i++) {
                cx += starX(c, i);
                maxY = Math.max(maxY, starY(c, i));
            }
            cx /= n;
            double y = Math.min(maxY + 22 + 8 * fa, xf.horizonScreen - 8);
            String name = c.getNameDe();
            String glyph = canShow(c.getGlyph()) ? c.getGlyph() : null;
            float normal = showNames ? 0.55f : 0f;
            float a = clampF((normal + (1 - normal) * fa) * appear);
            if (a <= 0.01f) {
                continue;
            }
            Font nameFont = fa > 0.5f ? labelFontFocus : labelFont;
            FontMetrics fm = g.getFontMetrics(nameFont);
            int glyphW = 0;
            Font gf = glyphFont.deriveFont(fa > 0.5f ? 19f : 14f);
            if (glyph != null) {
                glyphW = g.getFontMetrics(gf).stringWidth(glyph) + 6;
            }
            int tw = fm.stringWidth(name) + glyphW;
            double x = cx - tw / 2.0;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            if (fa > 0.3f) {
                // weicher Schein hinter dem Namen
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a * 0.35f * fa));
                double gw = tw + 60, gh = 46;
                g.drawImage(Sprites.NEBULA[0], (int) (cx - gw / 2), (int) (y - gh * 0.72), (int) gw, (int) gh, null);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            }
            if (glyph != null) {
                g.setFont(gf);
                g.setColor(c == birthdayAstro || c == birthdayReal ? GOLD : blend(new Color(0xB9C9F2), GOLD, fa));
                g.drawString(glyph, (float) x, (float) y);
            }
            g.setFont(nameFont);
            g.setColor(blend(new Color(0xB9C9F2), Color.WHITE, fa));
            g.drawString(name, (float) (x + glyphW), (float) y);
            if (fa > 0.05f) {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a * fa * 0.85f));
                g.setFont(latinFont);
                g.setColor(new Color(0xC8D6F5));
                FontMetrics lm = g.getFontMetrics();
                g.drawString(c.getNameLa(), (float) (cx - lm.stringWidth(c.getNameLa()) / 2.0), (float) (y + 17));
            }
        }
        g.setComposite(old);
    }

    private void paintMeteors(Graphics2D g, double t) {
        Composite old = g.getComposite();
        for (Meteor m : meteors) {
            double k = (t - m.born) / m.life;
            if (k < 0 || k > 1) {
                continue;
            }
            double fade = Math.sin(Math.PI * k);
            double dist = m.speed * (t - m.born);
            double hx = m.x + m.vx * dist, hy = m.y + m.vy * dist;
            if (hy > xf.horizonScreen - 4) {
                continue;
            }
            double tx = hx - m.vx * m.length * fade, ty = hy - m.vy * m.length * fade;
            if (m.train && k > 0.3) {
                // Nachleuchten: die bereits geflogene Bahn glimmt kurz nach
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clampF((float) (0.22 * (1 - k)))));
                g.setColor(m.color);
                g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(m.x, m.y, hx, hy));
            }
            g.setComposite(AlphaComposite.SrcOver);
            Color mc = m.color;
            g.setPaint(new GradientPaint((float) tx, (float) ty, new Color(mc.getRed(), mc.getGreen(), mc.getBlue(), 0),
                    (float) hx, (float) hy, new Color(mc.getRed(), mc.getGreen(), mc.getBlue(), (int) (230 * fade))));
            g.setComposite(AlphaComposite.SrcOver);
            g.setStroke(new BasicStroke(m.train ? 2.2f : 1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(tx, ty, hx, hy));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) fade));
            int hs = m.train ? 20 : 14;
            g.drawImage(Sprites.glow(mc), (int) hx - hs / 2, (int) hy - hs / 2, hs, hs, null);
        }
        g.setComposite(old);
        g.setStroke(new BasicStroke(1f));
    }

    private void paintWater(Graphics2D g, double t) {
        Composite old = g.getComposite();
        int w = xf.width, h = xf.height;
        double hz = xf.horizonScreen;
        // Glitzern
        for (Glint gl : glints) {
            double x = gl.fx * w, y = hz + 3 + Math.pow(gl.fy, 1.7) * (h - hz);
            if (!Landscape.isWater(x, y, w, hz, h)) {
                continue;
            }
            float a = (float) (0.06 + 0.12 * Math.max(0, Math.sin(t * 1.6 + gl.phase)));
            double len = gl.len * (0.4 + gl.fy);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            g.setColor(new Color(175, 198, 255));
            g.fill(new java.awt.geom.Rectangle2D.Double(x - len / 2, y, len, 1.1));
        }
        // Mondglitzern: ein flirrender Lichtpfad vom Horizont bis zum Betrachter
        if (data != null && data.getScene().hasMoon() && moon.illumination() > 0.03) {
            double mx = moonScreen()[0];
            double illum = moon.illumination();
            int rows = 90;
            for (int k = 0; k < rows; k++) {
                double f = k / (double) rows;
                double y = hz + 2 + Math.pow(f, 1.6) * (h - hz);
                double spread = (5 + (y - hz) * 0.42) * (0.55 + 0.6 * illum);
                for (int j = 0; j < 3; j++) {
                    double seed = Math.sin(k * 12.9898 + j * 78.233) * 43758.5453;
                    double rndv = seed - Math.floor(seed);
                    double x = mx + (rndv - 0.5) * 2 * spread + Math.sin(t * 1.3 + k * 0.7 + j) * 2.5;
                    if (!Landscape.isWater(x, y, w, hz, h)) {
                        continue;
                    }
                    double sparkle = 0.35 + 0.65 * Math.max(0, Math.sin(t * (2.1 + rndv * 2.4) + k * 1.7 + j * 2.1));
                    float a = clampF((float) (illum * 0.55 * sparkle * (1 - f * 0.45)));
                    double len = (3 + 16 * rndv) * (0.5 + f);
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
                    g.setColor(new Color(255, 240, 214));
                    g.fill(new java.awt.geom.Rectangle2D.Double(x - len / 2, y, len, 1.2 + f));
                }
            }
        }
        // Spiegelungen heller Figurensterne nahe dem Horizont
        if (data != null) {
            double tt = now();
            for (Constellation c : data.getConstellations()) {
                double p = introProgress(c, tt);
                if (p <= 0) {
                    continue;
                }
                for (int i = 0; i < c.getStars().size(); i++) {
                    Star s = c.getStars().get(i);
                    if (s.mag() > 2.3) {
                        continue;
                    }
                    double sx = starX(c, i), sy = starY(c, i);
                    double d = hz - sy;
                    if (d <= 0 || d > (hz) * 0.62) {
                        continue;
                    }
                    double strength = (1 - d / (hz * 0.62)) * p * Math.min(1, (2.6 - s.mag()) / 2.0);
                    for (int k = 0; k < 10; k++) {
                        double y = hz + 5 + k * (d * 0.035 + 3.4);
                        double wob = Math.sin(t * 2.4 + k * 1.3 + s.id()) * (1.2 + k * 0.45);
                        double x = sx - parX * 2 + wob;
                        if (y > h || !Landscape.isWater(x, y, w, hz, h)) {
                            break;
                        }
                        float a = clampF((float) ((0.34 - k * 0.03) * strength));
                        double ww = Math.max(1.5, (7 - k * 0.5) * xf.sizeFactor());
                        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
                        g.setColor(s.color());
                        g.fill(new java.awt.geom.Rectangle2D.Double(x - ww / 2, y, ww, 1.4));
                    }
                }
            }
        }
        g.setComposite(old);
    }

    private void paintLighthouse(Graphics2D g, double t) {
        int w = xf.width, h = xf.height;
        double hz = xf.horizonScreen;
        double lx = w * Landscape.LIGHTHOUSE_X;
        double ly0 = Landscape.coastTop(lx, w, hz, h) + 1;
        double th = h * 0.085, tw = th * 0.15;
        double ly = ly0 - th - tw * 0.9;
        double phase = t * 0.85;
        double c = Math.cos(phase), s = Math.sin(phase);
        Composite old = g.getComposite();
        // Lichtkegel
        double len = w * 0.42;
        double ex = lx + c * len, ey = ly - len * 0.012;
        double spread = len * (0.035 + 0.06 * Math.abs(s)) * Math.abs(c);
        Path2D beam = new Path2D.Double();
        beam.moveTo(lx, ly);
        beam.lineTo(ex, ey - spread);
        beam.lineTo(ex, ey + spread * 0.55);
        beam.closePath();
        int alpha = (int) (60 + 90 * Math.max(0, s));
        g.setPaint(new GradientPaint((float) lx, (float) ly, new Color(255, 236, 190, alpha),
                (float) ex, (float) ey, new Color(255, 236, 190, 0)));
        g.fill(beam);
        // Lampe, blitzt auf, wenn der Strahl zum Betrachter zeigt
        double flash = Math.pow(Math.max(0, s), 8);
        double gs = tw * 3 + tw * 18 * flash;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clampF((float) (0.55 + 0.45 * flash))));
        g.drawImage(Sprites.glow(new Color(255, 236, 200)), (int) (lx - gs / 2), (int) (ly - gs / 2), (int) gs, (int) gs, null);
        g.setComposite(old);
        g.setColor(new Color(255, 241, 207));
        g.fill(new java.awt.geom.Rectangle2D.Double(lx - tw * 0.42, ly - tw * 0.3, tw * 0.84, tw * 0.6));
    }

    private void paintGrass(Graphics2D g, double t) {
        int w = xf.width, h = xf.height;
        double hz = xf.horizonScreen;
        g.setColor(new Color(0x020309));
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Blade b : blades) {
            double x = w * (Landscape.DUNE_START + 0.05 + b.fx * (0.95 - Landscape.DUNE_START));
            double y = Landscape.duneTop(x, w, hz, h) + 2;
            if (Double.isNaN(y) || y > h) {
                continue;
            }
            double len = b.len * h * 0.045;
            double sway = Math.sin(t * 1.1 + b.phase) * 0.14;
            Path2D p = new Path2D.Double();
            p.moveTo(x, y);
            p.quadTo(x + (b.lean + sway) * len * 0.3, y - len * 0.6, x + (b.lean + sway) * len, y - len);
            g.draw(p);
        }
        g.setStroke(new BasicStroke(1f));
    }

    private void paintLoading(Graphics2D g) {
        g.setFont(labelFontFocus);
        g.setColor(new Color(220, 228, 250, 200));
        FontMetrics fm = g.getFontMetrics();
        String s = loadingText == null ? "" : loadingText;
        g.drawString(s, (xf.width - fm.stringWidth(s)) / 2f, (float) (xf.horizonScreen * 0.45));
    }

    // ------------------------------------------------------------------ Info-Karte

    /** Inhalt der Info-Karte – für Sternbild, Mond oder Meteorstrom. */
    private record Card(String kicker, String glyph, String title, String subtitle, List<String[]> rows, String text) { }

    private Card cardFor(Object target) {
        if (target instanceof Constellation c) {
            List<String[]> rows = new ArrayList<>();
            Star b = c.getBrightestStar();
            rows.add(new String[] { "Hellster Stern", b.name() + " · " + fmt(b.mag()) + " mag" });
            if (b.distLy() > 0 || b.spectral() != null) {
                String d = b.distLy() > 0 ? String.format(Locale.GERMANY, "%,d Lj", b.distLy()) : "";
                String sp = b.spectral() != null ? b.spectral() : "";
                rows.add(new String[] { "Entfernung · Typ", (d + (d.isEmpty() || sp.isEmpty() ? "" : " · ") + sp) });
            }
            rows.add(new String[] { "Sterne · Linien", c.getStars().size() + " · " + c.getLines().size() });
            if (c.isZodiac()) {
                ZodiacPeriod real = c.getRealPeriod(), astro = c.getAstroPeriod();
                // Zuerst das bekannte Sternzeichen-Datum, danach der astronomische Sonnenstand
                rows.add(new String[] { "Sternzeichen", astro != null ? astro.format() : "kein Sternzeichen" });
                rows.add(new String[] { "Sonne im Sternbild", real != null ? real.format() : "–" });
            }
            String kicker = c.isCluster() ? "STERNHAUFEN" : c.isZodiac() ? "TIERKREIS · " + c.getZodiacOrder() + " / 13" : "STERNBILD";
            return new Card(kicker, canShow(c.getGlyph()) ? c.getGlyph() : null, c.getNameDe(), c.getNameLa(), rows, c.getDescription());
        }
        if (target instanceof MoonPhase m) {
            DateTimeFormatter df = DateTimeFormatter.ofPattern("EE, d. MMMM", Locale.GERMANY);
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[] { "Beleuchtet", Math.round(m.illumination() * 100) + " %" });
            rows.add(new String[] { "Mondalter", String.format(Locale.GERMANY, "%.1f Tage", m.ageDays()) });
            rows.add(new String[] { "Nächster Vollmond", m.nextFullMoon().format(df) });
            rows.add(new String[] { "Nächster Neumond", m.nextNewMoon().format(df) });
            return new Card("MOND · HEUTE", null, m.name(), m.waxing() ? "zunehmend" : "abnehmend", rows,
                    "Berechnet aus der mittleren Länge des Mondmonats (29,53 Tage); die Termine stimmen auf etwa einen Tag.");
        }
        if (target instanceof MeteorShower m) {
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[] { "Aktiv", m.formatPeriod() });
            rows.add(new String[] { "Maximum", m.formatPeak() + " · ZHR " + m.zhr() });
            rows.add(new String[] { "Geschwindigkeit", m.speedKms() + " km/s" });
            rows.add(new String[] { "Radiant bei", m.reference().getNameDe() });
            rows.add(new String[] { "Heute", showerIntensity > 0 ? Math.round(showerIntensity * 100) + " % der Maximalrate" : "nicht aktiv" });
            return new Card("METEORSTROM", null, m.name(), "Radiant – hier scheinen die Spuren herzukommen", rows, m.description());
        }
        return null;
    }

    private void paintCard(Graphics2D g) {
        Card card = cardFor(cardShown);
        if (card == null || cardAlpha <= 0.01f) {
            return;
        }
        float a = cardAlpha;
        int cw = 340, pad = 18;
        int x = xf.width - cw - 24;
        int y = 22 + (int) ((1 - a) * -10);
        List<String> desc = wrap(card.text(), g.getFontMetrics(uiFont), cw - 2 * pad);
        List<String> sub = wrap(card.subtitle(), g.getFontMetrics(latinFont.deriveFont(14f)), cw - 2 * pad);
        int h = pad + 14 + 32 + sub.size() * 18 + 2 + 12 + card.rows().size() * 19 + 10 + desc.size() * 17 + pad;

        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        RoundRectangle2D shape = new RoundRectangle2D.Double(x, y, cw, h, 18, 18);
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new RoundRectangle2D.Double(x + 2, y + 6, cw, h, 18, 18));
        g.setColor(CARD_BG);
        g.fill(shape);
        g.setPaint(new GradientPaint(x, y, new Color(255, 255, 255, 22), x, y + 60, new Color(255, 255, 255, 0)));
        g.fill(shape);
        g.setColor(CARD_RIM);
        g.setStroke(new BasicStroke(1f));
        g.draw(shape);

        int cy = y + pad + 10;
        g.setFont(monoFont.deriveFont(java.util.Map.of(java.awt.font.TextAttribute.TRACKING, 0.16f)));
        g.setColor(ACCENT);
        g.drawString(card.kicker(), x + pad, cy);
        cy += 32;
        int nx = x + pad;
        if (card.glyph() != null) {
            g.setFont(glyphFont.deriveFont(24f));
            g.setColor(GOLD);
            g.drawString(card.glyph(), nx, cy);
            nx += g.getFontMetrics().stringWidth(card.glyph()) + 8;
        }
        g.setFont(labelFontFocus.deriveFont(26f));
        g.setColor(Color.WHITE);
        g.drawString(card.title(), nx, cy);
        g.setFont(latinFont.deriveFont(14f));
        g.setColor(MUTED);
        for (String line : sub) {
            cy += 18;
            g.drawString(line, x + pad, cy);
        }
        cy += 14;
        g.setColor(new Color(143, 180, 255, 45));
        g.drawLine(x + pad, cy, x + cw - pad, cy);
        cy += 19;
        for (String[] r : card.rows()) {
            g.setFont(uiFont);
            g.setColor(MUTED);
            g.drawString(r[0], x + pad, cy);
            g.setFont(uiFontBold);
            g.setColor(TEXT);
            g.drawString(r[1], x + pad + 132, cy);
            cy += 19;
        }
        cy += 6;
        g.setFont(uiFont);
        g.setColor(new Color(0xC3CDEA));
        for (String line : desc) {
            g.drawString(line, x + pad, cy);
            cy += 17;
        }
        g.setComposite(old);
    }

    private void paintStatus(Graphics2D g) {
        g.setFont(uiFont);
        int x = 18, y = xf.height - 14;
        if (shower != null) {
            String s = "Meteorstrom: " + shower.name() + " · aktiv " + shower.formatPeriod() + " · Maximum "
                    + shower.formatPeak() + (showerIntensity >= 0.999 ? " (Simulation des Maximums)" : "");
            g.setColor(new Color(242, 214, 178, 190));
            g.drawString(s, x, y - 17);
        }
        g.setColor(new Color(160, 175, 215, 150));
        g.drawString(statusText, x, y);
    }

    // ------------------------------------------------------------------ Sagenfiguren, Mond, Radiant

    private void paintFigure(Graphics2D g, Constellation c, float fa, double sf) {
        float reveal = clampF((fa - 0.05f) / 0.75f);
        if (reveal <= 0) {
            return;
        }
        Composite old = g.getComposite();
        double ox = xf.x(c.getPosX()) + figParX(), oy = xf.y(c.getPosY()) + figParY();
        for (double[][] stroke : c.getFigureStrokes()) {
            Path2D path = spline(stroke, ox, oy, reveal);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clampF(0.11f * fa)));
            g.setColor(new Color(170, 195, 255));
            g.setStroke(new BasicStroke((float) (6.5 * sf), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(path);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clampF(0.55f * fa)));
            g.setColor(new Color(214, 226, 255));
            g.setStroke(new BasicStroke((float) (1.1 * sf), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(path);
        }
        g.setComposite(old);
    }

    /** Catmull-Rom-Spline durch die Punkte, bis zum Anteil {@code reveal} der Gesamtlänge. */
    private Path2D spline(double[][] pts, double ox, double oy, double reveal) {
        List<double[]> poly = new ArrayList<>();
        int n = pts.length;
        for (int i = 0; i < n - 1; i++) {
            double[] p0 = pts[Math.max(0, i - 1)], p1 = pts[i], p2 = pts[i + 1], p3 = pts[Math.min(n - 1, i + 2)];
            for (int k = 0; k < 10; k++) {
                double t = k / 10.0, t2 = t * t, t3 = t2 * t;
                double x = 0.5 * (2 * p1[0] + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2
                        + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3);
                double y = 0.5 * (2 * p1[1] + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2
                        + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3);
                poly.add(new double[] { ox + x * xf.scale, oy + y * xf.scale });
            }
        }
        poly.add(new double[] { ox + pts[n - 1][0] * xf.scale, oy + pts[n - 1][1] * xf.scale });
        double total = 0;
        for (int i = 1; i < poly.size(); i++) {
            total += Math.hypot(poly.get(i)[0] - poly.get(i - 1)[0], poly.get(i)[1] - poly.get(i - 1)[1]);
        }
        double limit = total * reveal, run = 0;
        Path2D path = new Path2D.Double();
        path.moveTo(poly.get(0)[0], poly.get(0)[1]);
        for (int i = 1; i < poly.size(); i++) {
            double[] a = poly.get(i - 1), b = poly.get(i);
            double seg = Math.hypot(b[0] - a[0], b[1] - a[1]);
            if (run + seg >= limit) {
                double f = seg == 0 ? 0 : (limit - run) / seg;
                path.lineTo(a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f);
                break;
            }
            path.lineTo(b[0], b[1]);
            run += seg;
        }
        return path;
    }

    private void paintMoon(Graphics2D g) {
        if (!data.getScene().hasMoon()) {
            return;
        }
        double r = moonRadius();
        double[] m = moonScreen();
        int size = (int) Math.ceil(r * 2) + 2;
        if (moonImage == null || moonImageSize != size || Math.abs(moonImageAge - moon.ageDays()) > 0.05) {
            moonImage = toCompatible(MoonPainter.render(size, moon), java.awt.Transparency.TRANSLUCENT);
            moonImageSize = size;
            moonImageAge = moon.ageDays();
        }
        Composite old = g.getComposite();
        float light = (float) (0.08 + 0.35 * moon.illumination());
        double halo = r * (7 + 5 * moon.illumination()) * (hoverMoon ? 1.15 : 1);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, light));
        g.drawImage(Sprites.NEBULA[2], (int) (m[0] - halo / 2), (int) (m[1] - halo / 2), (int) halo, (int) halo, null);
        double inner = r * 3.4;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clampF(light * 1.6f)));
        g.drawImage(Sprites.glow(new Color(255, 246, 225)), (int) (m[0] - inner / 2), (int) (m[1] - inner / 2), (int) inner, (int) inner, null);
        g.setComposite(old);
        g.drawImage(moonImage, (int) Math.round(m[0] - size / 2.0), (int) Math.round(m[1] - size / 2.0), null);
    }

    private void paintRadiant(Graphics2D g, double t) {
        if (shower == null) {
            return;
        }
        double[] r = radiantScreen();
        if (r[1] > xf.horizonScreen) {
            return;
        }
        Composite old = g.getComposite();
        Color c = shower.color();
        float base = hoverRadiant ? 0.95f : 0.6f;
        double pulse = 0.5 + 0.5 * Math.sin(t * 2.2);
        g.setColor(c);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] { 2f, 3f }, 0f));
        for (int k = 0; k < 2; k++) {
            double rr = 7 + k * 7 + pulse * 3;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clampF(base * (0.7f - k * 0.3f))));
            g.draw(new Ellipse2D.Double(r[0] - rr, r[1] - rr, rr * 2, rr * 2));
        }
        g.setStroke(new BasicStroke(1f));
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clampF(base * 0.5f)));
        for (int k = 0; k < 8; k++) {
            double ang = k * Math.PI / 4 + t * 0.05;
            double r1 = 20 + pulse * 3, r2 = 30 + pulse * 5;
            g.draw(new Line2D.Double(r[0] + Math.cos(ang) * r1, r[1] + Math.sin(ang) * r1,
                    r[0] + Math.cos(ang) * r2, r[1] + Math.sin(ang) * r2));
        }
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, base));
        g.setFont(uiFont);
        g.setColor(new Color(0xF2D6B2));
        String label = "Radiant · " + shower.name();
        g.drawString(label, (float) (r[0] + 16), (float) (r[1] - 14));
        g.setComposite(old);
    }

    private void paintBirthday(Graphics2D g) {
        if (birthday == null) {
            return;
        }
        String date = birthday.format(DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMANY));
        String real = birthdayReal != null ? label(birthdayReal) : "–";
        String astro = birthdayAstro != null ? label(birthdayAstro) : "–";
        boolean same = birthdayReal == birthdayAstro;
        String[] parts = same
                ? new String[] { date, "Sonne und Astrologie einig: " + real }
                : new String[] { date, "Sonne stand in " + real, "astrologisch " + astro };
        g.setFont(uiFontBold);
        FontMetrics fm = g.getFontMetrics();
        int gap = 22, pad = 16, x = 24, y = 22, hgt = 36;
        int w = pad * 2;
        for (String p : parts) {
            w += fm.stringWidth(p) + gap;
        }
        w -= gap;
        RoundRectangle2D pill = new RoundRectangle2D.Double(x, y, w, hgt, hgt, hgt);
        g.setColor(CARD_BG);
        g.fill(pill);
        g.setColor(new Color(242, 214, 178, 110));
        g.draw(pill);
        int cx = x + pad;
        for (int i = 0; i < parts.length; i++) {
            g.setColor(i == 0 ? GOLD : i == 2 ? new Color(0xE9D7B8) : Color.WHITE);
            g.drawString(parts[i], cx, y + 23);
            cx += fm.stringWidth(parts[i]) + gap;
            if (i < parts.length - 1) {
                g.setColor(new Color(143, 180, 255, 90));
                g.fillOval(cx - gap / 2 - 2, y + hgt / 2 - 2, 4, 4);
            }
        }
    }

    private String label(Constellation c) {
        return canShow(c.getGlyph()) ? c.getGlyph() + " " + c.getNameDe() : c.getNameDe();
    }

    private boolean canShow(String glyph) {
        return glyphs && glyph != null && glyphFont.canDisplayUpTo(glyph) == -1;
    }

    // ------------------------------------------------------------------ Hilfen

    /**
     * Kopiert ein gecachtes Ebenenbild in das Format der Grafikkonfiguration des Bildschirms.
     * So blittet Java2D ohne Formatumwandlung – schneller und robust auf allen Pipelines (D3D, GDI, XRender).
     */
    private BufferedImage toCompatible(BufferedImage src, int transparency) {
        java.awt.GraphicsConfiguration gc = getGraphicsConfiguration();
        if (gc == null) {
            return src;
        }
        BufferedImage dst = gc.createCompatibleImage(src.getWidth(), src.getHeight(), transparency);
        Graphics2D g = dst.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return dst;
    }

    private static List<String> wrap(String text, FontMetrics fm, int width) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String probe = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(probe) > width && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(probe);
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }

    private static String fmt(double v) {
        return String.format(Locale.GERMANY, "%.2f", v);
    }

    private static double clamp(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static float clampF(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static Color blend(Color a, Color b, float t) {
        t = clampF(t);
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    private static String firstFont(String... names) {
        List<String> have = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String n : names) {
            if (have.contains(n)) {
                return n;
            }
        }
        return names[names.length - 1];
    }

    private static String firstFontDisplaying(char ch, String... names) {
        List<String> have = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String n : names) {
            if ((have.contains(n) || "Dialog".equals(n)) && new Font(n, Font.PLAIN, 14).canDisplay(ch)) {
                return n;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ Partikel

    private static final class Twinkler {
        final double fx, fy, phase, freq, radius;
        final float alpha;
        final Color color;

        Twinkler(Random r) {
            fx = r.nextDouble();
            fy = Math.pow(r.nextDouble(), 0.9);
            phase = r.nextDouble() * Math.PI * 2;
            freq = 1.2 + r.nextDouble() * 3.2;
            double mag = 3.6 + 2.2 * r.nextDouble();
            radius = 0.6 + (5.8 - mag) * 0.45;
            alpha = (float) Math.min(1, (6.4 - mag) / 3.4);
            double pick = r.nextDouble();
            color = pick < 0.14 ? new Color(0xFFDCB4) : pick < 0.34 ? new Color(0xD6E2FF) : new Color(0xF1F4FF);
        }
    }

    private static final class Glint {
        final double fx, fy, phase, len;

        Glint(Random r) {
            fx = r.nextDouble();
            fy = r.nextDouble();
            phase = r.nextDouble() * Math.PI * 2;
            len = 4 + r.nextDouble() * 14;
        }
    }

    private static final class Blade {
        final double fx, len, lean, phase;

        Blade(Random r) {
            fx = r.nextDouble();
            len = 0.5 + r.nextDouble() * 1.2;
            lean = (r.nextDouble() - 0.5) * 0.9;
            phase = r.nextDouble() * Math.PI * 2;
        }
    }

    private static final class Meteor {
        final double x, y, vx, vy, speed, length, born, life;
        final Color color;
        /** Strom-Meteore: kräftiger, mit kurzem Nachleuchten der Spur. */
        boolean train;

        Meteor(double x, double y, double vx, double vy, double speed, double length, double born, double life, Color color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.speed = speed;
            this.length = length;
            this.born = born;
            this.life = life;
            this.color = color;
        }

        /** Meteor eines Stroms: startet in zufälliger Richtung vom Radianten weg; schneller Strom = schnellere Spur. */
        static Meteor fromRadiant(Random r, SceneTransform xf, double now, double[] radiant, MeteorShower s) {
            double ang = r.nextDouble() * Math.PI * 2;
            double vx = Math.cos(ang), vy = Math.sin(ang);
            if (vy < 0 && r.nextDouble() < 0.6) {
                vy = -vy;      // mehr Spuren laufen in den sichtbaren Himmel statt aus dem Bild
            }
            double d0 = (3 + r.nextDouble() * 34) * xf.scale;
            double sf = xf.sizeFactor();
            double length = (50 + d0 * 0.7 + r.nextDouble() * 50) * sf;
            double speed = (110 + s.speedKms() * 4.2) * sf;   // schnelle Ströme (Leoniden 71 km/s) sichtbar schneller
            Meteor m = new Meteor(radiant[0] + vx * d0, radiant[1] + vy * d0, vx, vy, speed, length, now,
                    0.7 + r.nextDouble() * 0.6, s.color());
            m.train = true;
            return m;
        }

        Meteor(Random r, SceneTransform xf, double now) {
            double dir = r.nextBoolean() ? 1 : -1;
            double ang = 0.35 + r.nextDouble() * 0.35;
            x = xf.width * (0.12 + r.nextDouble() * 0.76);
            y = xf.horizonScreen * (0.05 + r.nextDouble() * 0.35);
            vx = Math.cos(ang) * dir;
            vy = Math.sin(ang);
            speed = (380 + r.nextDouble() * 300) * xf.sizeFactor();
            length = (110 + r.nextDouble() * 120) * xf.sizeFactor();
            born = now;
            life = 0.7 + r.nextDouble() * 0.5;
            color = Color.WHITE;
        }
    }
}
