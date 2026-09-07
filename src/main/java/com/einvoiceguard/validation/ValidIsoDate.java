package com.einvoiceguard.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valide qu'une chaine est une date ISO-8601 reelle (format yyyy-MM-dd,
 * ex. 2026-02-30 est rejete car fevrier n'a pas 30 jours), et qu'elle
 * reste dans une plage raisonnable (ni trop ancienne, ni trop future) pour
 * detecter les erreurs de saisie evidentes (ex. annee a 4 chiffres inversee).
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IsoDateValidator.class)
public @interface ValidIsoDate {
    String message() default "Date invalide (format attendu : yyyy-MM-dd, ex. 2026-09-06)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
