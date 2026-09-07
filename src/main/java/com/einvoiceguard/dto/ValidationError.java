package com.einvoiceguard.dto;

/**
 * Une erreur de validation localisee et actionnable.
 * C'est le differenciateur produit reclame par les utilisateurs (cf. retours communaute) :
 * ne pas juste dire "invalide", mais dire QUOI, OU, et POURQUOI.
 */
public record ValidationError(
        String ruleId,       // ex: "BR-FX-EN-04"
        String severity,     // "FATAL" | "ERROR" | "WARNING"
        String message,      // explication humaine
        String xpath,        // localisation dans le XML, si applicable
        Integer line         // ligne dans le document source, si applicable
) {
}
