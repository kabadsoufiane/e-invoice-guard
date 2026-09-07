package com.einvoiceguard.dto;

import com.einvoiceguard.validation.ValidIsoDate;
import com.einvoiceguard.validation.ValidVatNumber;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * JSON metier pour generer une facture Factur-X conforme.
 * Chaque contrainte de validation produit, en cas d'echec, un message
 * d'erreur cible sur le champ concerne (voir GlobalExceptionHandler),
 * ce qui evite a l'integrateur de deviner quel champ poser probleme.
 */
public record InvoiceBuildRequest(
        @NotBlank(message = "Le numero de facture est obligatoire")
        @Size(max = 100, message = "Le numero de facture ne doit pas depasser 100 caracteres")
        String invoiceNumber,

        @NotBlank(message = "La date d'emission est obligatoire")
        @ValidIsoDate
        String issueDate,        // format ISO: yyyy-MM-dd

        @NotBlank(message = "Le nom du vendeur est obligatoire")
        @Size(max = 200, message = "Le nom du vendeur ne doit pas depasser 200 caracteres")
        String sellerName,

        @NotBlank(message = "Le numero de TVA du vendeur est obligatoire")
        @ValidVatNumber(message = "Numero de TVA du vendeur invalide (format attendu : ex. FR12345678901)")
        String sellerVatNumber,

        @NotBlank(message = "Le nom de l'acheteur est obligatoire")
        @Size(max = 200, message = "Le nom de l'acheteur ne doit pas depasser 200 caracteres")
        String buyerName,

        @ValidVatNumber(optional = true, message = "Numero de TVA de l'acheteur invalide (format attendu : ex. FR98765432109)")
        String buyerVatNumber,

        @NotBlank(message = "La devise est obligatoire")
        @Pattern(regexp = "^[A-Z]{3}$", message = "La devise doit etre un code ISO 4217 de 3 lettres majuscules (ex. EUR, USD, CHF)")
        String currency,         // ex: "EUR"

        @NotNull(message = "Le profil Factur-X est obligatoire")
        @Pattern(regexp = "^(MINIMUM|BASIC|EN16931|EXTENDED)$",
                message = "Le profil doit etre l'une des valeurs : MINIMUM, BASIC, EN16931, EXTENDED")
        String profile,           // "MINIMUM" | "BASIC" | "EN16931" | "EXTENDED"

        @NotEmpty(message = "La facture doit contenir au moins une ligne")
        @Size(max = 500, message = "La facture ne peut pas depasser 500 lignes")
        List<@jakarta.validation.Valid InvoiceLine> lines
) {
    public record InvoiceLine(
            @NotBlank(message = "La description de la ligne est obligatoire")
            @Size(max = 500, message = "La description ne doit pas depasser 500 caracteres")
            String description,

            @NotNull(message = "La quantite est obligatoire")
            @DecimalMin(value = "0.0001", message = "La quantite doit etre strictement positive")
            BigDecimal quantity,

            @NotNull(message = "Le prix unitaire est obligatoire")
            @DecimalMin(value = "0.0", message = "Le prix unitaire ne peut pas etre negatif")
            BigDecimal unitPrice,

            @NotNull(message = "Le taux de TVA est obligatoire")
            @DecimalMin(value = "0.0", message = "Le taux de TVA ne peut pas etre negatif")
            @jakarta.validation.constraints.DecimalMax(value = "100.0", message = "Le taux de TVA ne peut pas depasser 100%")
            BigDecimal vatRate   // ex: 20.00 pour 20%
    ) {
    }
}
