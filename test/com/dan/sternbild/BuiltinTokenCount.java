package com.dan.sternbild;

import java.lang.reflect.Field;

/** Zählt die Punkte aller Strichzüge im eingebauten Katalog (per Reflection, der Katalog ist paketintern). */
final class BuiltinTokenCount {

    private BuiltinTokenCount() { }

    static int count() throws Exception {
        Class<?> k = Class.forName("com.dan.sternbild.data.BuiltinCatalog");
        Field f = k.getDeclaredField("STROKES");
        f.setAccessible(true);
        int n = 0;
        for (Object[] r : (Object[][]) f.get(null)) {
            n += ((String) r[2]).split(";").length;
        }
        return n;
    }
}
