package com.dan.sternbild.app;

import com.dan.fbutton.FButton;
import com.dan.fbutton.FButtonVariant;
import com.dan.fcombobox.FComboBox;
import com.dan.fdatepicker.FDatePicker;
import com.dan.fsegmentedcontrol.FSegmentedControl;
import com.dan.fstyle.FFontRole;
import com.dan.fstyle.FTheme;
import com.dan.fstyle.FThemePreset;
import com.dan.ftogglebutton.FToggleButton;
import com.dan.sternbild.model.MeteorShower;
import com.dan.sternbild.model.SkyData;
import com.dan.sternbild.render.SkyCanvas;
import com.dan.sternbild.render.SkyMood;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Bedienleiste unter dem Himmel – F-Komponenten in FTheme-Farben, live umschaltbar.
 */
public class ControlBar extends JPanel {

    private final SkyCanvas canvas;
    private final FSegmentedControl moodControl = new FSegmentedControl(SkyMood.DAEMMERUNG.getLabel(), SkyMood.NACHT.getLabel());
    private final FToggleButton linesToggle = new FToggleButton(true);
    private final FToggleButton namesToggle = new FToggleButton(true);
    private final FToggleButton milkyToggle = new FToggleButton(true);
    private final FToggleButton tourToggle = new FToggleButton(true);
    private final FButton birthdayButton = new FButton("Geburtstag …");
    private final FDatePicker datePicker = new FDatePicker();
    private final JPopupMenu datePopup = new JPopupMenu();
    private final FComboBox themeBox = new FComboBox(FThemePreset.values());
    private final FComboBox meteorBox = new FComboBox();
    private final List<JLabel> captions = new ArrayList<>();
    private final FTheme.ThemeListener themeListener = t -> applyTheme();

    public ControlBar(SkyCanvas canvas) {
        super(new BorderLayout(12, 0));
        this.canvas = canvas;
        setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        setOpaque(false);

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
        left.setOpaque(false);
        moodControl.setFixedSegmentWidth(96);
        moodControl.setSelectedIndex(canvas.getMood() == SkyMood.NACHT ? 1 : 0);
        canvas.addPropertyChangeListener("mood", e -> {
            int idx = canvas.getMood() == SkyMood.NACHT ? 1 : 0;
            if (moodControl.getSelectedIndex() != idx) {
                moodControl.setSelectedIndex(idx);
            }
        });
        moodControl.getModel().addPropertyChangeListener(e ->
                canvas.setMood(moodControl.getSelectedIndex() == 1 ? SkyMood.NACHT : SkyMood.DAEMMERUNG));
        moodControl.setToolTipText("Stimmung: Dämmerung wie Sternbild.jpg, Nacht mit Milchstraße wie Sternbild1.jpg");
        left.add(moodControl);
        left.add(Box.createHorizontalStrut(12));
        left.add(toggle("Linien", linesToggle, canvas::setShowLines));
        left.add(Box.createHorizontalStrut(6));
        left.add(toggle("Namen", namesToggle, canvas::setShowNames));
        left.add(Box.createHorizontalStrut(6));
        left.add(toggle("Milchstraße", milkyToggle, canvas::setShowMilkyWay));
        left.add(Box.createHorizontalStrut(6));
        left.add(toggle("Tour", tourToggle, canvas::setTour));
        left.add(Box.createHorizontalStrut(12));

        birthdayButton.setVariant(FButtonVariant.GHOST);
        birthdayButton.setToolTipText("Wo stand die Sonne an deinem Geburtstag?");
        datePopup.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        datePopup.add(datePicker);
        datePicker.setSelectedDate(LocalDate.now());
        datePicker.getModel().addPropertyChangeListener(e -> {
            if ("selectedDate".equals(e.getPropertyName()) && e.getNewValue() instanceof LocalDate d) {
                canvas.setBirthday(d);
                birthdayButton.setText(d.format(DateTimeFormatter.ofPattern("d. MMM", Locale.GERMANY)) + " …");
                datePopup.setVisible(false);
            }
        });
        birthdayButton.addActionListener(e -> datePopup.show(birthdayButton, 0, -datePopup.getPreferredSize().height - 6));
        left.add(birthdayButton);
        left.add(Box.createHorizontalGlue());
        for (Component c : left.getComponents()) {
            if (c instanceof JComponent jc) {
                jc.setAlignmentY(Component.CENTER_ALIGNMENT);
                jc.setMaximumSize(jc.getPreferredSize());
            }
        }
        add(left, BorderLayout.CENTER);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
        meteorBox.setToolTipText("Meteorströme: automatisch nach heutigem Datum, aus, oder ein Strom im Maximum");
        meteorBox.setPreferredSize(new Dimension(226, meteorBox.getPreferredSize().height));
        meteorBox.addItem(ShowerChoice.AUTO);
        meteorBox.addItem(ShowerChoice.OFF);
        meteorBox.addActionListener(e -> applyShower());
        right.add(meteorBox);
        right.add(Box.createHorizontalStrut(16));
        themeBox.setPreferredSize(new Dimension(212, themeBox.getPreferredSize().height));
        themeBox.setSelectedItem(FTheme.getInstance().getCurrentPreset());
        themeBox.setToolTipText("Theme der Bedienelemente");
        themeBox.addActionListener(e -> {
            Object p = themeBox.getSelectedItem();
            if (p instanceof FThemePreset preset && preset != FTheme.getInstance().getCurrentPreset()) {
                FTheme.getInstance().applyPreset(preset);
            }
        });
        right.add(themeBox);
        add(right, BorderLayout.EAST);

        applyTheme();
    }

    /** Statuszeile, z. B. Datenquelle – wird klein unten links im Himmel angezeigt. */
    public void setStatus(String text) {
        canvas.setStatusText(text);
    }

    /** Übernimmt die Meteorströme der geladenen Daten und wendet die Auswahl an. */
    public void setData(SkyData data) {
        Object selected = meteorBox.getSelectedItem();
        meteorBox.removeAllItems();
        meteorBox.addItem(ShowerChoice.AUTO);
        meteorBox.addItem(ShowerChoice.OFF);
        for (MeteorShower m : data.getShowers()) {
            meteorBox.addItem(new ShowerChoice(m.name(), m));
        }
        meteorBox.setSelectedItem(selected instanceof ShowerChoice c && c.shower == null ? c : ShowerChoice.AUTO);
        applyShower();
    }

    private void applyShower() {
        SkyData data = canvas.getData();
        if (!(meteorBox.getSelectedItem() instanceof ShowerChoice choice) || data == null) {
            return;
        }
        if (choice == ShowerChoice.OFF) {
            canvas.setMeteorShower(null, 0);
        } else if (choice == ShowerChoice.AUTO) {
            LocalDate today = LocalDate.now();
            MeteorShower m = data.activeShower(today).orElse(null);
            canvas.setMeteorShower(m, m == null ? 0 : m.activity(today));
        } else {
            canvas.setMeteorShower(choice.shower, 1.0);
        }
    }

    /** Eintrag der Meteorstrom-Auswahl. */
    private record ShowerChoice(String label, MeteorShower shower) {
        static final ShowerChoice AUTO = new ShowerChoice("Meteore heute", null);
        static final ShowerChoice OFF = new ShowerChoice("Nur einzelne", null);

        @Override
        public String toString() {
            return label;
        }
    }

    private JComponent toggle(String text, FToggleButton t, Consumer<Boolean> target) {
        t.getModel().addPropertyChangeListener(e -> {
            if ("selected".equals(e.getPropertyName())) {
                target.accept(t.isSelected());
            }
        });
        return group(caption(text), t);
    }

    private JLabel caption(String text) {
        JLabel l = new JLabel(text);
        captions.add(l);
        return l;
    }

    private static JComponent group(JLabel caption, JComponent control) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.setOpaque(false);
        p.add(caption);
        p.add(control);
        Dimension d = p.getPreferredSize();
        p.setMaximumSize(d);
        return p;
    }

    private void applyTheme() {
        FTheme th = FTheme.getInstance();
        for (JLabel l : captions) {
            l.setForeground(th.getText());
            l.setFont(th.getFont(FFontRole.LABEL));
        }
        for (FToggleButton t : new FToggleButton[] { linesToggle, namesToggle, milkyToggle, tourToggle }) {
            t.setOnColor(th.getPrimary());
            t.setGlowColor(th.getAccent());
        }
        moodControl.setSelectedColor(th.getPrimary());
        moodControl.setGlowColor(th.getAccent());
        datePicker.setSelectionColor(th.getPrimary());
        datePicker.setTodayGlowColor(th.getAccent());
        datePopup.setBackground(th.getSurface());
        if (themeBox.getSelectedItem() != th.getCurrentPreset()) {
            themeBox.setSelectedItem(th.getCurrentPreset());
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        FTheme th = FTheme.getInstance();
        Color s = th.getSurface(), b = th.getBackground();
        g.setPaint(new GradientPaint(0, 0, s, 0, getHeight(), b));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(new Color(th.getPrimary().getRed(), th.getPrimary().getGreen(), th.getPrimary().getBlue(), 90));
        g.fillRect(0, 0, getWidth(), 1);
        g.dispose();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        FTheme.getInstance().addThemeListener(themeListener);
        applyTheme();
    }

    @Override
    public void removeNotify() {
        FTheme.getInstance().removeThemeListener(themeListener);
        super.removeNotify();
    }
}
