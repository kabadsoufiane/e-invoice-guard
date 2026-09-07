package com.einvoiceguard.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class SiretValidatorTest {

    private final ConstraintValidatorContext context = Mockito.mock(ConstraintValidatorContext.class);
    private SiretValidator requiredValidator;
    private SiretValidator optionalValidator;

    @BeforeEach
    void setUp() {
        requiredValidator = new SiretValidator();
        requiredValidator.initialize(annotation(false));

        optionalValidator = new SiretValidator();
        optionalValidator.initialize(annotation(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "73282932000074", // SIRET reel connu (INSEE), cle de Luhn valide
            "55208131766522"  // autre SIRET reel connu, cle de Luhn valide
    })
    void accepteLesSiretAvecCleDeLuhnValide(String siret) {
        assertThat(requiredValidator.isValid(siret, context)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "73282932000075", // meme numero que le cas valide, dernier chiffre altere -> Luhn invalide
            "1234567890123",  // 13 chiffres seulement
            "123456789012345",// 15 chiffres
            "ABCDEFGHIJKLMN"  // pas des chiffres
    })
    void rejetteLesSiretInvalides(String siret) {
        assertThat(requiredValidator.isValid(siret, context)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejetteNullEtVideQuandObligatoire(String siret) {
        assertThat(requiredValidator.isValid(siret, context)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void accepteNullEtVideQuandOptionnel(String siret) {
        assertThat(optionalValidator.isValid(siret, context)).isTrue();
    }

    @Test
    void toleresLesEspacesDansLaSaisie() {
        assertThat(requiredValidator.isValid("732 829 320 000 74", context)).isTrue();
    }

    private ValidSiret annotation(boolean optional) {
        return new ValidSiret() {
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
                return ValidSiret.class;
            }
        };
    }
}
