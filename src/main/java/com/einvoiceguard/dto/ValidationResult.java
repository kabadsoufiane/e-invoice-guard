package com.einvoiceguard.dto;

import java.util.List;

/**
 * Reponse standard de tous les endpoints de validation.
 */
public record ValidationResult(
        boolean valid,
        String detectedProfile,   // ex: "EN16931", "BASIC", "MINIMUM", "EXTENDED"
        String detectedFormat,    // ex: "FACTUR-X", "ZUGFERD", "XRECHNUNG", "PEPPOL-BIS3"
        List<ValidationError> errors,
        List<ValidationError> warnings
) {
    public static ValidationResult ok(String profile, String format) {
        return new ValidationResult(true, profile, format, List.of(), List.of());
    }
}
