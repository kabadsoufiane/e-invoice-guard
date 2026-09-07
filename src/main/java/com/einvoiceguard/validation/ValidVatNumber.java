package com.einvoiceguard.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valide un numero de TVA intracommunautaire au format europeen standard :
 * 2 lettres de code pays ISO 3166-1 alpha-2 suivies de 2 a 13 caracteres
 * alphanumeriques (le format exact du bloc numerique varie selon le pays,
 * ex. France : FR + 2 caracteres de controle + SIREN 9 chiffres = FRxx999999999).
 *
 * Espaces internes autorises en saisie (ex. "FR 12 345678901"), normalises
 * avant controle. La verification est syntaxique (format), pas un appel au
 * service VIES de la Commission europeenne (hors scope, necessiterait un
 * appel reseau externe a chaque validation).
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = VatNumberValidator.class)
public @interface ValidVatNumber {
    String message() default "Numero de TVA intracommunautaire invalide (format attendu : 2 lettres pays + caracteres alphanumeriques, ex. FR12345678901)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Si true, une valeur null ou vide est acceptee (champ optionnel, ex. acheteur particulier). */
    boolean optional() default false;
}
