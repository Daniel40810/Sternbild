package com.dan.sternbild.data;

import com.dan.sternbild.model.SkyData;

/** Liefert die Himmelsdaten einer Szene. */
public interface SkyRepository {

    /**
     * @param sceneCode z. B. "OSTSEE"
     * @throws Exception wenn die Quelle nicht lesbar ist
     */
    SkyData load(String sceneCode) throws Exception;
}
