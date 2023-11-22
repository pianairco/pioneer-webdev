package ir.piana.dev.common.util;

import java.util.stream.IntStream;

/**
 * A utility validator class.
 *
 * @author Alireza Pourtaghi
 */
public final class Validation {

    /**
     * Represents the national code pattern.
     */
    public static final String NATIONAL_CODE_PATTERN = "^(\\d{10})$";
    public static final String HTTP_STATUS_CODE = "^(\\d{3})$";
    /**
     * Represents the mobile number pattern.
     */
    public static final String MOBILE_NUMBER_PATTERN = "^(?:(?:\\+|00)98|0)9\\d{9}$";

    /**
     * Represents the strong password.
     * Password must contain at least one digit [0-9].
     * Password must contain at least one lowercase Latin character [a-z].
     * Password must contain at least one uppercase Latin character [A-Z].
     * Password must contain at least one special character like ! @ # & ( ).
     * Password must contain a length of at least 8 characters and a maximum of 20 characters.
     */
    public static final String PASSWORD_PATTERN = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#&()–[{}]:;',?/*~$^+=<>]).{8,20}$";

    /**
     * Represents the card number pattern.
     */
    public static final String CARD_NUMBER_PATTERN = "^(\\d{16})$";

    /**
     * Represents the merchant id pattern.
     */
    public static final String MERCHANT_ID_PATTERN = "^(\\d*)$";

    /**
     * Represents the terminal id pattern.
     */
    public static final String TERMINAL_ID_PATTERN = "^(\\d*)$";

    /**
     * Checks whether a provided national code is valid or not.
     *
     * @param nationalCode string representation of national code
     * @return true if national code is valid otherwise false
     */
    public static boolean isValidPersianNationalCode(String nationalCode) {
        if (nationalCode.matches(NATIONAL_CODE_PATTERN)) {
            int check = Integer.valueOf(nationalCode.substring(nationalCode.length() - 1), nationalCode.length());
            int sum = IntStream.range(0, 9).map(n -> Integer.parseInt(nationalCode.substring(n, n + 1)) * (10 - n)).sum() % 11;
            return sum < 2 && check == sum || sum >= 2 && check + sum == 11;
        }

        return false;
    }

    public static boolean isValidHttpStatusCode(String httpStatusCode) {
        return httpStatusCode.matches(HTTP_STATUS_CODE);
    }

    public static boolean isValidMobileNumber(String mobileNumber) {
        return mobileNumber.matches(MOBILE_NUMBER_PATTERN);
    }

    public static boolean isValidStrongPassword(String password) {
        return password.matches(PASSWORD_PATTERN);
    }
}
