package com.einvoiceguard.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class VatNumberValidatorTest {

    private final ConstraintValidatorContext context = Mockito.mock(ConstraintValidatorContext.class);

    private VatNumberValidator requiredValidator;
    private VatNumberValidator optionalValidator;

    @BeforeEach
    void setUp() {
        requiredValidator = new VatNumberValidator();
        requiredValidator.initialize(annotation(false));

        optionalValidator = new VatNumberValidator();
        optionalValidator.initialize(annotation(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "FR12345678901",
            "FR 12 345678901", // espaces internes tolerés
            "fr12345678901",   // casse tolérée
            "DE123456789",
            "BE0123456789"
    })
    void accepteLesNumerosDeTvaValides(String vat) {
        assertThat(requiredValidator.isValid(vat, context)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "INVALID123",     // pas de code pays reconnu
            "FR1234",         // SIREN trop court pour un TVA francais
            "FR123456789012", // SIREN trop long
            "XX123456789",    // code pays hors UE
            "1234567890",     // pas de lettres
            "F"               // trop court
    })
    void rejetteLesNumerosDeTvaInvalides(String vat) {
        assertThat(requiredValidator.isValid(vat, context)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejetteNullEtVideQuandObligatoire(String vat) {
        assertThat(requiredValidator.isValid(vat, context)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void accepteNullEtVideQuandOptionnel(String vat) {
        assertThat(optionalValidator.isValid(vat, context)).isTrue();
    }

    @Test
    void rejetteUnTvaOptionnelMaisMalFormate() {
        // Optionnel = champ facultatif, mais SI une valeur est fournie, elle doit rester valide.
        assertThat(optionalValidator.isValid("INVALIDE", context)).isFalse();
    }

    private ValidVatNumber annotation(boolean optional) {
        return new ValidVatNumber() {
            @Override
            public String message() {
                return "";
            }

            @Override
            public Class<?>[] groups() {
                return new Class<?>[0];
            }

            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() {
                return new Class[0];
            }

            @Override
            public boolean optional() {
                return optional;
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return ValidVatNumber.class;
            }
        };
    }
}
