package com.dan.sternbild.model;

/**
 * Koordinatenraum einer Himmelsszene (SB_SCENE). Eine Einheit entspricht einem Grad bei Skalierung 1.
 * moonX/moonY sind NaN, wenn die Szene keinen Mond zeigt.
 */
public record SceneLayout(String code, String name, double width, double height, double horizonY,
                          double moonX, double moonY) {

    public boolean hasMoon() {
        return !Double.isNaN(moonX) && !Double.isNaN(moonY);
    }
}
