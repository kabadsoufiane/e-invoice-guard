package com.einvoiceguard.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Cible d'une conversion de profil Factur-X (voir POST /v1/convert).
 */
public record ConvertRequest(
        @NotNull(message = "Le profil cible est obligatoire")
        @Pattern(regexp = "^(MINIMUM|BASIC|EN16931|EXTENDED)$",
                message = "Le profil cible doit etre l'une des valeurs : MINIMUM, BASIC, EN16931, EXTENDED")
        String targetProfile
) {
}
