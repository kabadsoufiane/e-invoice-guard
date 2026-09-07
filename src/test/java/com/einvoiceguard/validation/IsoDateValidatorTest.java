package com.einvoiceguard.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class IsoDateValidatorTest {

    private final ConstraintValidatorContext context = Mockito.mock(ConstraintValidatorContext.class);
    private final IsoDateValidator validator = new IsoDateValidator();

    @Test
    void accepteLaDateDuJour() {
        assertThat(validator.isValid(LocalDate.now().toString(), context)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-30", "2025-13-01", "2025-04-31", "2025-00-10"})
    void rejetteLesDatesCalendairementImpossibles(String date) {
        assertThat(validator.isValid(date, context)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"06/09/2026", "2026/09/06", "6-9-2026", "not-a-date", "2026-9-6"})
    void rejetteLesFormatsNonIso(String date) {
        assertThat(validator.isValid(date, context)).isFalse();
    }

    @Test
    void rejetteUneDateTropAncienne() {
        String tropAncienne = LocalDate.now().minusYears(25).toString();
        assertThat(validator.isValid(tropAncienne, context)).isFalse();
    }

    @Test
    void rejetteUneDateTropLointaineDansLeFutur() {
        String tropFuture = LocalDate.now().plusYears(10).toString();
        assertThat(validator.isValid(tropFuture, context)).isFalse();
    }

    @Test
    void accepteNullEtVideCarNotBlankSOccupeDejaDeCeCas() {
        assertThat(validator.isValid(null, context)).isTrue();
        assertThat(validator.isValid("", context)).isTrue();
    }
}
