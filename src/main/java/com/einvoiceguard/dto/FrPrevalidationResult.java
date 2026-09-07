package com.einvoiceguard.dto;

import java.util.List;

/**
 * Verdict de la pre-validation France (SIRET/SIREN + TVA intracommunautaire).
 * Reponse volontairement detaillee champ par champ, dans le meme esprit que
 * ValidationResult : dire QUOI est invalide, pas juste vrai/faux.
 */
public record FrPrevalidationResult(
        boolean valid,
        FieldCheck siret,
        FieldCheck vatNumber,
        List<String> warnings
) {
    public record FieldCheck(
            String value,
            boolean valid,
            String message
    ) {
    }
}
