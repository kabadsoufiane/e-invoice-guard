package com.einvoiceguard.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Verifie qu'un numero SIRET est compose de 14 chiffres et respecte la cle
 * de controle de Luhn, comme l'exige l'INSEE.
 */
public class SiretValidator implements ConstraintValidator<ValidSiret, String> {

    private boolean optional;

    @Override
    public void initialize(ValidSiret annotation) {
        this.optional = annotation.optional();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return optional;
        }
        String digits = value.replace(" ", "");
        if (!digits.matches("\\d{14}")) {
            return false;
        }
        return isLuhnValid(digits);
    }

    private boolean isLuhnValid(String digits) {
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            int digit = Character.getNumericValue(digits.charAt(digits.length() - 1 - i));
            if (i % 2 == 1) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
        }
        return sum % 10 == 0;
    }
}
