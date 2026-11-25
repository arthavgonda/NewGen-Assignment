package com.newgen.los.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public final class DateUtil {

    public static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    public static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    public static final DateTimeFormatter FILE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private static final List<DateTimeFormatter> INPUT_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"));

    private DateUtil() {
    }

    public static LocalDate parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (DateTimeFormatter format : INPUT_FORMATS) {
            try {
                return LocalDate.parse(text.trim(), format);
            } catch (DateTimeParseException ignored) {
                // try the next pattern
            }
        }
        throw new IllegalArgumentException("Invalid date '" + text + "', use dd-MM-yyyy or yyyy-MM-dd");
    }

    public static String format(LocalDate date) {
        return date == null ? "" : date.format(DISPLAY_DATE);
    }

    public static String format(LocalDateTime dateTime) {
        return dateTime == null ? "" : dateTime.format(DISPLAY_DATE_TIME);
    }
}
