package com.einvoiceguard.dto;

import com.einvoiceguard.validation.ValidSiret;
import com.einvoiceguard.validation.ValidVatNumber;

/**
 * JSON metier pour la pre-validation France (voir POST /v1/prevalidate/fr).
 * Les deux champs sont optionnels individuellement (un appelant peut ne
 * vouloir verifier que le SIRET, ou que la TVA), mais au moins un des deux
 * doit etre fourni - controle applicatif dans FrPrevalidationService.
 */
public record FrPrevalidationRequest(
        @ValidSiret(optional = true, message = "SIRET invalide : 14 chiffres attendus avec cle de controle Luhn valide")
        String siret,

        @ValidVatNumber(optional = true, message = "Numero de TVA invalide (format attendu : FRxx999999999)")
        String vatNumber
) {
}
