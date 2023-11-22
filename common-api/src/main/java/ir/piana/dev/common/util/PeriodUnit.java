package ir.piana.dev.common.util;


import ir.piana.dev.common.handler.HandlerRuntimeException;
import lombok.Getter;

import java.time.temporal.ChronoUnit;

@Getter
public enum PeriodUnit {
    DAY(1, "روز", ChronoUnit.DAYS),
    WEEK(2, "هفته", ChronoUnit.WEEKS),
    MONTH(3, "ماه", ChronoUnit.MONTHS),
    YEAR(4, "سال", ChronoUnit.YEARS);

    int code;
    String title;
    ChronoUnit chronoUnit;

    PeriodUnit(int code, String title, ChronoUnit chronoUnit) {
        this.code = code;
        this.title = title;
        this.chronoUnit = chronoUnit;
    }

    public static PeriodUnit byChronoUnit(ChronoUnit chronoUnit) throws HandlerRuntimeException {
        for (PeriodUnit value : values()) {
            if (value.chronoUnit.equals(chronoUnit))
                return value;
        }

        throw new RuntimeException("invalid input");
    }
}
