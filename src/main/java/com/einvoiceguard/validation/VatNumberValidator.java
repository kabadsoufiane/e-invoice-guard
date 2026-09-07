package com.einvoiceguard.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Verifie le format d'un numero de TVA intracommunautaire.
 * Format general EU : 2 lettres (code pays ISO 3166-1 alpha-2 parmi les
 * Etats membres) + 2 a 12 caracteres alphanumeriques.
 * Format specifique France, verifie plus precisement : FR + 2 caracteres
 * de controle (chiffres ou lettres) + 9 chiffres (numero SIREN).
 */
public class VatNumberValidator implements ConstraintValidator<ValidVatNumber, String> {

    private static final Set<String> EU_COUNTRY_CODES = Set.of(
            "AT", "BE", "BG", "CY", "CZ", "DE", "DK", "EE", "EL", "GR", "ES", "FI", "FR",
            "HR", "HU", "IE", "IT", "LT", "LU", "LV", "MT", "NL", "PL", "PT", "RO", "SE",
            "SI", "SK", "XI"
    );

    private static final Pattern GENERIC_VAT_PATTERN = Pattern.compile("^[A-Z]{2}[0-9A-Z]{2,12}$");
    private static final Pattern FRENCH_VAT_PATTERN = Pattern.compile("^FR[0-9A-Z]{2}[0-9]{9}$");

    private boolean optional;

    @Override
    public void initialize(ValidVatNumber annotation) {
        this.optional = annotation.optional();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return optional;
        }
        String normalized = value.replace(" ", "").toUpperCase();
        if (normalized.length() < 4) {
            return false;
        }
        String countryCode = normalized.substring(0, 2);
        if (!EU_COUNTRY_CODES.contains(countryCode)) {
            return false;
        }
        if (countryCode.equals("FR")) {
            return FRENCH_VAT_PATTERN.matcher(normalized).matches();
        }
        return GENERIC_VAT_PATTERN.matcher(normalized).matches();
    }
}
