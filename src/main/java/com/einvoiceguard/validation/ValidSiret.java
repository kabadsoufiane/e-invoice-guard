package com.einvoiceguard.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valide un numero SIRET francais : 14 chiffres respectant la cle de
 * controle de l'algorithme de Luhn (norme INSEE).
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SiretValidator.class)
public @interface ValidSiret {
    String message() default "Numero SIRET invalide (14 chiffres attendus, cle de controle Luhn incorrecte)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Si true, une valeur null ou vide est acceptee (champ optionnel). */
    boolean optional() default false;
}
