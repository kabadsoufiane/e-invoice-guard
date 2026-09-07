package com.einvoiceguard.dto;

/**
 * Un resultat de validation associe a son fichier source, pour le
 * traitement par lot (voir POST /v1/validate/batch). Permet a l'appelant
 * de retrouver quel fichier a echoue sans avoir a deviner l'ordre des
 * reponses.
 */
public record BatchValidationResult(
        String filename,
        ValidationResult result
) {
}
