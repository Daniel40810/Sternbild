package com.dan.sternbild;

import com.dan.sternbild.app.SternbildApp;
import com.dan.sternbild.model.Constellation;
import com.dan.sternbild.model.MeteorShower;
import com.dan.sternbild.render.SkyCanvas;
import com.dan.sternbild.render.SkyMood;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;

/** Startet die echte App unter Xvfb und speichert Screenshots. Nicht Teil des JARs. */
public final class ScreenshotHarness {

    private static Robot robot;
    private static Frame frame;
    private static SkyCanvas canvas;

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "shots";
        new File(out).mkdirs();
        SternbildApp.main(new String[] { "--offline" });
        Thread.sleep(2500);
        frame = Frame.getFrames()[0];
        SwingUtilities.invokeAndWait(() -> canvas = find(frame, SkyCanvas.class));
        robot = new Robot();
        Thread.sleep(6500);
        shot(out + "/1_daemmerung_mond_tauriden.png");

        SwingUtilities.invokeAndWait(() -> canvas.setMood(SkyMood.NACHT));
        hover("Cyg");
        Thread.sleep(2200);
        shot(out + "/2_nacht_schwan_figur.png");

        SwingUtilities.invokeAndWait(() -> {
            MeteorShower per = canvas.getData().getShowers().stream().filter(m -> m.code().equals("PER")).findFirst().get();
            canvas.setMeteorShower(per, 1.0);
        });
        Thread.sleep(300);
        Point r = canvas.radiantOnScreenForTest();
        Point on = canvas.getLocationOnScreen();
        robot.mouseMove(on.x + r.x, on.y + r.y);
        Thread.sleep(3500);
        shot(out + "/3_perseiden_maximum.png");

        Point m = canvas.moonOnScreenForTest();
        robot.mouseMove(on.x + m.x, on.y + m.y);
        Thread.sleep(1800);
        shot(out + "/4_mond_karte.png");

        SwingUtilities.invokeAndWait(() -> canvas.setMood(SkyMood.DAEMMERUNG));
        hover("Leo");
        Thread.sleep(2200);
        shot(out + "/5_loewe_figur.png");
        System.exit(0);
    }

    private static void hover(String code) throws Exception {
        Constellation[] c = new Constellation[1];
        SwingUtilities.invokeAndWait(() -> c[0] = canvas.getData().find(code).orElseThrow());
        Point p = canvas.centerOnScreenForTest(c[0]);
        Point on = canvas.getLocationOnScreen();
        // auf einen Stern zielen: Mittelpunkt kann zwischen den Linien liegen
        robot.mouseMove(on.x + p.x, on.y + p.y);
        Thread.sleep(200);
        if (canvas.getFocused() != c[0]) {
            SwingUtilities.invokeAndWait(() -> canvas.setPinned(c[0]));
        }
    }

    private static void shot(String file) throws Exception {
        Rectangle b = frame.getBounds();
        BufferedImage img = robot.createScreenCapture(b);
        ImageIO.write(img, "png", new File(file));
        System.out.println("gespeichert: " + file);
    }

    @SuppressWarnings("unchecked")
    private static <T> T find(Container root, Class<T> type) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) return (T) c;
            if (c instanceof Container k) {
                T t = find(k, type);
                if (t != null) return t;
            }
        }
        return null;
    }
}
