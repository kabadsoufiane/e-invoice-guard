package com.einvoiceguard.service;

import com.einvoiceguard.dto.ValidationResult;
import com.einvoiceguard.support.InvoiceTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FacturXValidationServiceTest {

    private final FacturXValidationService service = new FacturXValidationService();

    @Test
    void valideUnEchantillonFacturXReelEtDetecteLeProfil(@TempDir Path tempDir) throws Exception {
        Path pdf = copierEchantillonVers(tempDir, "exemple_facturx_valide.pdf");

        ValidationResult result = service.validatePdf(pdf);

        assertThat(result.valid()).isTrue();
        assertThat(result.detectedProfile()).isEqualTo("EN16931");
        assertThat(result.detectedFormat()).isEqualTo("CII");
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void valideUnPdfGenereParLeBuilderDuMemeService(@TempDir Path tempDir) throws Exception {
        FacturXBuilderService builder = new FacturXBuilderService(new InvoicePdfLayoutService());
        byte[] pdf = builder.buildFacturX(InvoiceTestFixtures.uneFactureValide());

        Path fichier = tempDir.resolve("genere.pdf");
        Files.write(fichier, pdf);

        ValidationResult result = service.validatePdf(fichier);

        assertThat(result.valid()).isTrue();
        assertThat(result.detectedProfile()).isEqualTo("EN16931");
    }

    @Test
    void renvoieUnVerdictInvalidePourUnFichierQuiNestPasUnPdf(@TempDir Path tempDir) throws Exception {
        Path fauxPdf = tempDir.resolve("pas-un-pdf.pdf");
        Files.writeString(fauxPdf, "ceci n'est pas un PDF");

        ValidationResult result = service.validatePdf(fauxPdf);

        assertThat(result.valid()).isFalse();
    }

    @Test
    void exposeUneVersionDeRulesetNonVide() {
        String version = service.getActiveRulesetVersion();

        assertThat(version).isNotBlank();
    }

    private Path copierEchantillonVers(Path tempDir, String classpathFile) throws Exception {
        Path destination = tempDir.resolve(classpathFile);
        try (var in = new ClassPathResource("samples/" + classpathFile).getInputStream()) {
            Files.copy(in, destination);
        }
        return destination;
    }
}
