package com.dan.sternbild.model;

import java.time.LocalDate;
import java.time.MonthDay;

/**
 * Zeitraum im Jahr als MMDD-Paar; darf über den Jahreswechsel gehen (z. B. 1217–0119).
 */
public record ZodiacPeriod(ZodiacSystem system, int startMmdd, int endMmdd) {

    public boolean contains(LocalDate date) {
        int md = date.getMonthValue() * 100 + date.getDayOfMonth();
        if (startMmdd <= endMmdd) {
            return md >= startMmdd && md <= endMmdd;
        }
        return md >= startMmdd || md <= endMmdd;
    }

    public MonthDay start() { return MonthDay.of(startMmdd / 100, startMmdd % 100); }

    public MonthDay end() { return MonthDay.of(endMmdd / 100, endMmdd % 100); }

    /** z. B. "16.09. – 29.10." */
    public String format() {
        return String.format("%02d.%02d. – %02d.%02d.", startMmdd % 100, startMmdd / 100, endMmdd % 100, endMmdd / 100);
    }
}
