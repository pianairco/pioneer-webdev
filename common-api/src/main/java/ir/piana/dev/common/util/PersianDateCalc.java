package ir.piana.dev.common.util;

import com.github.mfathi91.time.PersianChronology;
import com.github.mfathi91.time.PersianDate;
import com.github.mfathi91.time.PersianMonth;

import java.time.temporal.ChronoUnit;

public abstract class PersianDateCalc {
    public abstract PersianDate plus(PersianDate persianDate, int amount);
    public abstract PersianDate minus(PersianDate persianDate, int amount);

    private final static PersianDateCalc calcDay = new CalcDay();
    private static PersianDateCalc calcWeek = new CalcWeek();
    private static PersianDateCalc calcMonth = new CalcMonth();
    private static PersianDateCalc calcYear = new CalcYear();
    public static PersianDateCalc fromPeriodUnit(PeriodUnit periodUnit) {
        switch (periodUnit) {
            case DAY -> {
                return calcDay;
            }
            case WEEK -> {
                return calcWeek;
            }
            case MONTH -> {
                return calcMonth;
            }
            case YEAR -> {
                return calcYear;
            }
        }

        throw new RuntimeException("invalid input");
    }

    public static PersianDateCalc fromChronoUnit(ChronoUnit chronoUnit) {
        switch (chronoUnit) {
            case DAYS -> {
                return calcDay;
            }
            case WEEKS -> {
                return calcWeek;
            }
            case MONTHS -> {
                return calcMonth;
            }
            case YEARS -> {
                return calcYear;
            }
        }

        throw new RuntimeException("invalid input");
    }

    public static PersianDateCalc BasedDay() {
        return calcDay;
    }

    public static PersianDateCalc BasedWeek() {
        return calcWeek;
    }

    public static PersianDateCalc BasedMonth() {
        return calcMonth;
    }

    public static PersianDateCalc BasedYear() {
        return calcYear;
    }

    private static boolean isBetween(int val, int lowerLimit, int upperLimit) {
        return val >= lowerLimit && val <= upperLimit;
    }

    private static int intRequireRange(int val, int lowerLimit, int upperLimit, String valName) {
        if (!isBetween(val, lowerLimit, upperLimit)) {
            throw new IllegalArgumentException(valName + " " + val +
                    " is out of valid range [" + lowerLimit + ", " + upperLimit + "]");
        }
        return val;
    }

    private static PersianMonth of(int month) {
        intRequireRange(month, 1, 12, "month");
        return PersianMonth.values()[month - 1];
    }

    /**
     * Resolves the date, resolving days past the end of month.
     *
     * @param year the year to represent
     * @param month the month-of-year to represent, validated from 1 to 12
     * @param day the day-of-month to represent, validated from 1 to 31
     * @return the resolved date, not null
     */
    private static PersianDate resolvePreviousValid(int year, int month, int day) {
        boolean leapYear = PersianChronology.INSTANCE.isLeapYear(year);
        int maxDaysOfMonth = of(month).length(leapYear);

        if (day > maxDaysOfMonth) {
            day = maxDaysOfMonth;
        }
        return PersianDate.of(year, month, day);
    }

    private static class CalcDay extends PersianDateCalc {

        @Override
        public PersianDate plus(PersianDate persianDate, int amount) {
            return persianDate.plusDays(amount);
        }

        @Override
        public PersianDate minus(PersianDate persianDate, int amount) {
            if (amount == 0) {
                return persianDate;
            }
            return PersianDate.ofJulianDays(persianDate.toJulianDay() - amount);
        }
    }

    private static class CalcWeek extends PersianDateCalc {

        @Override
        public PersianDate plus(PersianDate persianDate, int amount) {
            return persianDate.plusDays(amount * 7);
        }

        @Override
        public PersianDate minus(PersianDate persianDate, int amount) {
            if (amount == 0) {
                return persianDate;
            }
            return PersianDate.ofJulianDays(persianDate.toJulianDay() - (amount * 7));
        }
    }

    private static class CalcMonth extends PersianDateCalc {

        @Override
        public PersianDate plus(PersianDate persianDate, int amount) {
            return persianDate.plusMonths(amount);
        }

        @Override
        public PersianDate minus(PersianDate persianDate, int amount) {
            if (amount == 0) {
                return persianDate;
            }
            long monthCount = persianDate.getYear() * 12L + (persianDate.getMonthValue() - 1);
            long calcMonths = monthCount + amount;
            int newYear = (int) Math.floorDiv(calcMonths, 12L);
            int newMonth = (int) Math.floorMod(calcMonths, 12L) + 1;
            return resolvePreviousValid(newYear, newMonth, persianDate.getDayOfMonth());
        }
    }

    private static class CalcYear extends PersianDateCalc {

        @Override
        public PersianDate plus(PersianDate persianDate, int amount) {
            return persianDate.plusYears(amount);
        }

        @Override
        public PersianDate minus(PersianDate persianDate, int amount) {
            if (amount == 0) {
                return persianDate;
            }
            long monthCount = persianDate.getYear() * 12L + (persianDate.getMonthValue() - 1);
            long calcMonths = monthCount - amount;
            int newYear = (int) Math.floorDiv(calcMonths, 12L);
            int newMonth = (int) Math.floorMod(calcMonths, 12L) + 1;
            return resolvePreviousValid(newYear, newMonth, persianDate.getDayOfMonth());
        }
    }
}
