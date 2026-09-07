package com.einvoiceguard.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Verifie qu'une date est bien au format ISO-8601 (yyyy-MM-dd) et
 * calendairement valide (rejette par ex. 2026-02-30). Bornes larges
 * (20 ans dans le passe, 5 ans dans le futur) pour attraper les erreurs de
 * saisie grossieres sans etre trop restrictif sur les factures rectificatives
 * anciennes ou les factures d'abonnement emises a l'avance.
 */
public class IsoDateValidator implements ConstraintValidator<ValidIsoDate, String> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            // @NotBlank s'occupe deja du cas vide ; ne pas dupliquer le message d'erreur ici.
            return true;
        }
        try {
            LocalDate date = LocalDate.parse(value, FORMATTER);
            LocalDate today = LocalDate.now();
            return !date.isBefore(today.minusYears(20)) && !date.isAfter(today.plusYears(5));
        } catch (DateTimeParseException ex) {
            return false;
        }
    }
}
