package com.dan.sternbild.render;

import com.dan.sternbild.model.SceneLayout;

/**
 * Abbildung Szeneneinheiten → Bildschirm. Der Horizont liegt bei 84 % der Höhe,
 * die Szene wird so skaliert, dass sie in Breite und Himmelshöhe ganz sichtbar ist.
 */
final class SceneTransform {

    final int width;
    final int height;
    final double scale;
    final double offsetX;
    final double horizonScreen;
    final double horizonScene;
    final double sceneWidth;

    SceneTransform(SceneLayout scene, int width, int height) {
        this.width = width;
        this.height = height;
        this.sceneWidth = scene.width();
        this.horizonScene = scene.horizonY();
        this.horizonScreen = Math.round(height * 0.84);
        double byWidth = width / scene.width();
        double byHeight = (horizonScreen - 10) / (scene.horizonY() - 3);
        this.scale = Math.max(0.1, Math.min(byWidth, byHeight));
        this.offsetX = (width - scene.width() * scale) / 2.0;
    }

    double x(double sceneX) { return offsetX + sceneX * scale; }

    double y(double sceneY) { return horizonScreen - (horizonScene - sceneY) * scale; }

    /** Größenfaktor für Sterne und Linien relativ zu einer Referenzskalierung von 7 px/Einheit. */
    double sizeFactor() { return Math.max(0.7, Math.min(1.6, scale / 7.0)); }
}
