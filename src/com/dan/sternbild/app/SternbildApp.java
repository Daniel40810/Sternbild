package com.dan.sternbild.app;

import com.dan.fframe.FFrame;
import com.dan.ficons.FIconType;
import com.dan.fstyle.FTheme;
import com.dan.fstyle.FThemePreset;
import com.dan.sternbild.data.BuiltinSkyRepository;
import com.dan.sternbild.data.DbConfig;
import com.dan.sternbild.data.OracleSkyRepository;
import com.dan.sternbild.model.SkyData;
import com.dan.sternbild.render.SkyCanvas;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Arrays;

/**
 * Sternbild – Sternbilder und Tierkreis über der Ostsee.
 * <p>
 * Start: {@code java -jar Sternbild.jar} (liest Oracle, fällt bei Bedarf auf eingebaute Daten zurück)
 * oder {@code java -jar Sternbild.jar --offline}.
 */
public final class SternbildApp {

    private final FFrame frame = new FFrame("Sternbild · Ostsee bei Nacht");
    private final SkyCanvas canvas = new SkyCanvas();
    private final ControlBar bar = new ControlBar(canvas);
    private final FTheme.ThemeListener themeListener = t -> applyFrameTheme();

    private SternbildApp() { }

    public static void main(String[] args) {
        boolean offline = Arrays.asList(args).contains("--offline");
        SwingUtilities.invokeLater(() -> {
            FTheme.getInstance().applyPreset(FThemePreset.MIDNIGHT_BLUE);
            new SternbildApp().start(offline);
        });
    }

    private void start(boolean offline) {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLogoType(FIconType.HARBOR);
        frame.getComponentPane().add(canvas, BorderLayout.CENTER);
        frame.getComponentPane().add(bar, BorderLayout.SOUTH);
        frame.setPreferredFrameSize(new Dimension(1440, 900));
        frame.setMinimumSize(new Dimension(1280, 760));
        applyFrameTheme();
        FTheme.getInstance().addThemeListener(themeListener);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        load(offline);
    }

    private void applyFrameTheme() {
        FTheme th = FTheme.getInstance();
        frame.setTaskbarColors(th.getPrimary(), th.getAccent());
        frame.setIconGlowColor(th.getGlow());
        frame.setComponentPaneColor(th.getBackground());
    }

    private void load(boolean offline) {
        DbConfig cfg = DbConfig.load();
        boolean useDb = cfg.enabled() && !offline;
        canvas.setLoadingText(useDb ? "Verbinde mit Oracle · " + cfg.describe() + " …" : "Himmel wird vorbereitet …");
        bar.setStatus(useDb ? "Verbinde mit " + cfg.describe() + " …" : "Offline-Modus");
        new SwingWorker<SkyData, Void>() {
            private String note = "";

            @Override
            protected SkyData doInBackground() {
                if (useDb) {
                    try {
                        return new OracleSkyRepository(cfg).load(cfg.scene());
                    } catch (Exception ex) {
                        note = "Oracle nicht erreichbar (" + shortReason(ex) + ")";
                    }
                }
                return new BuiltinSkyRepository().load(cfg.scene());
            }

            @Override
            protected void done() {
                try {
                    SkyData data = get();
                    canvas.setData(data);
                    bar.setData(data);
                    String src = data.getSource() + " · " + data.getConstellations().size() + " Sternbilder";
                    bar.setStatus(note.isEmpty() ? src : src + " · " + note);
                } catch (Exception ex) {
                    bar.setStatus("Fehler beim Laden: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private static String shortReason(Exception ex) {
        String m = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        m = m.replaceAll("\\s+", " ");
        return m.length() > 60 ? m.substring(0, 57) + "…" : m;
    }
}
