package com.dan.sternbild.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Verbindungsdaten. Reihenfolge: ./sternbild.properties neben dem Programm, dann die
 * mitgelieferte Vorgabe im JAR (com/dan/sternbild/sternbild.properties).
 */
public record DbConfig(boolean enabled, String url, String user, String password, String scene, int timeoutSeconds) {

    public static DbConfig load() {
        Properties p = new Properties();
        try (InputStream in = DbConfig.class.getResourceAsStream("/com/dan/sternbild/sternbild.properties")) {
            if (in != null) {
                p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // Vorgaben unten greifen
        }
        Path local = Path.of("sternbild.properties");
        if (Files.isRegularFile(local)) {
            try (Reader r = Files.newBufferedReader(local, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException ignored) {
                // Vorgabe aus dem JAR bleibt
            }
        }
        return new DbConfig(
                Boolean.parseBoolean(p.getProperty("db.enabled", "true")),
                p.getProperty("db.url", "jdbc:oracle:thin:@//localhost:1521/PDBORCL"),
                p.getProperty("db.user", "DEMO"),
                p.getProperty("db.password", ""),
                p.getProperty("db.scene", "OSTSEE"),
                Integer.parseInt(p.getProperty("db.timeoutSeconds", "4").trim()));
    }

    /** z. B. "DEMO@//localhost:1521/PDBORCL" für die Statusanzeige. */
    public String describe() {
        int at = url.indexOf('@');
        return user + (at >= 0 ? url.substring(at) : "@" + url);
    }
}
