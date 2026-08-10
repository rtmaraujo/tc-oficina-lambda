package br.com.fiap.lambda;

public class CpfValidator {

    private CpfValidator() {}

    public static boolean isValidCpf(String cpf) {
        if (cpf == null) {
            return false;
        }

        String digits = cpf.replaceAll("\\D", "");
        if (digits.length() != 11) {
            return false;
        }

        if (digits.chars().distinct().count() == 1) {
            return false;
        }

        return validatesChecksum(digits);
    }

    private static boolean validatesChecksum(String cpf) {
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += Character.getNumericValue(cpf.charAt(i)) * (10 - i);
        }
        int firstDigit = remainderToDigit(sum);

        if (firstDigit != Character.getNumericValue(cpf.charAt(9))) {
            return false;
        }

        sum = 0;
        for (int i = 0; i < 10; i++) {
            sum += Character.getNumericValue(cpf.charAt(i)) * (11 - i);
        }
        int secondDigit = remainderToDigit(sum);

        return secondDigit == Character.getNumericValue(cpf.charAt(10));
    }

    private static int remainderToDigit(int sum) {
        int result = 11 - (sum % 11);
        return result > 9 ? 0 : result;
    }
}
