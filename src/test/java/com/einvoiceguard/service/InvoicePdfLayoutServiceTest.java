package com.einvoiceguard.service;

import com.einvoiceguard.support.InvoiceTestFixtures;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvoicePdfLayoutServiceTest {

    private final InvoicePdfLayoutService service = new InvoicePdfLayoutService();

    @Test
    void genereUnPdfNonVideEtLisibleParPdfbox() throws Exception {
        byte[] pdf = service.renderInvoicePdf(InvoiceTestFixtures.uneFactureValide());

        assertThat(pdf).isNotEmpty();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
        }
    }

    @Test
    void embarqueUnOutputIntentPourLaConformitePdfA() throws Exception {
        byte[] pdf = service.renderInvoicePdf(InvoiceTestFixtures.uneFactureValide());

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getDocumentCatalog().getOutputIntents()).isNotEmpty();
        }
    }

    @Test
    void embarqueDesMetadonneesXmp() throws Exception {
        byte[] pdf = service.renderInvoicePdf(InvoiceTestFixtures.uneFactureValide());

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getDocumentCatalog().getMetadata()).isNotNull();
        }
    }

    @Test
    void produitUnPdfDifferentPourDesFacturesDifferentes() {
        byte[] pdf1 = service.renderInvoicePdf(InvoiceTestFixtures.uneFactureValide());

        var autreFacture = new com.einvoiceguard.dto.InvoiceBuildRequest(
                "FA-2026-9999",
                java.time.LocalDate.now().toString(),
                "Autre Vendeur SAS",
                "FR11122233344",
                "Autre Client",
                null,
                "USD",
                "BASIC",
                java.util.List.of(new com.einvoiceguard.dto.InvoiceBuildRequest.InvoiceLine(
                        "Autre prestation", java.math.BigDecimal.TEN, java.math.BigDecimal.valueOf(100), java.math.BigDecimal.valueOf(10)))
        );
        byte[] pdf2 = service.renderInvoicePdf(autreFacture);

        assertThat(pdf1).isNotEqualTo(pdf2);
    }
}
