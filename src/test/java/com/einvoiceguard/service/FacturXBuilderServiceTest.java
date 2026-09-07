package com.einvoiceguard.service;

import com.einvoiceguard.dto.InvoiceBuildRequest;
import com.einvoiceguard.exception.InvoiceProcessingException;
import com.einvoiceguard.support.InvoiceTestFixtures;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.mustangproject.ZUGFeRD.ZUGFeRDImporter;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FacturXBuilderServiceTest {

    private final FacturXBuilderService service = new FacturXBuilderService(new InvoicePdfLayoutService());

    @Test
    void genereUnPdfA3AvecXmlEmbarque() throws Exception {
        byte[] pdf = service.buildFacturX(InvoiceTestFixtures.uneFactureValide());

        assertThat(pdf).isNotEmpty();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
        }

        ZUGFeRDImporter importer = new ZUGFeRDImporter(new ByteArrayInputStream(pdf));
        byte[] xml = importer.getRawXML();
        assertThat(xml).isNotNull();
        assertThat(xml.length).isGreaterThan(0);
    }

    @Test
    void leXmlEmbarqueContientLeNumeroDeFacture() throws Exception {
        byte[] pdf = service.buildFacturX(InvoiceTestFixtures.uneFactureValide());

        ZUGFeRDImporter importer = new ZUGFeRDImporter(new ByteArrayInputStream(pdf));
        String xml = new String(importer.getRawXML(), java.nio.charset.StandardCharsets.UTF_8);

        assertThat(xml).contains("FA-2026-0042");
    }

    @Test
    void leXmlEmbarqueContientLeProfilDemande() throws Exception {
        byte[] pdf = service.buildFacturX(InvoiceTestFixtures.uneFactureValide());

        ZUGFeRDImporter importer = new ZUGFeRDImporter(new ByteArrayInputStream(pdf));
        String xml = new String(importer.getRawXML(), java.nio.charset.StandardCharsets.UTF_8).toLowerCase();

        assertThat(xml).contains("en16931");
    }

    @Test
    void rejetteUneDateDemissionMalFormeeAvecUneExceptionMetier() {
        InvoiceBuildRequest requeteAvecDateInvalide = new InvoiceBuildRequest(
                "FA-2026-0001",
                "date-invalide",
                "Vendeur", "FR12345678901",
                "Acheteur", null,
                "EUR", "EN16931",
                List.of(new InvoiceBuildRequest.InvoiceLine("Ligne", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.valueOf(20)))
        );

        assertThatThrownBy(() -> service.buildFacturX(requeteAvecDateInvalide))
                .isInstanceOf(InvoiceProcessingException.class);
    }

    @Test
    void genereDesPdfDifferentsPourDesProfilsDifferents() {
        InvoiceBuildRequest base = InvoiceTestFixtures.uneFactureValide();
        InvoiceBuildRequest profilMinimum = new InvoiceBuildRequest(
                base.invoiceNumber(), base.issueDate(), base.sellerName(), base.sellerVatNumber(),
                base.buyerName(), base.buyerVatNumber(), base.currency(), "MINIMUM", base.lines()
        );

        byte[] pdfEn16931 = service.buildFacturX(base);
        byte[] pdfMinimum = service.buildFacturX(profilMinimum);

        assertThat(pdfEn16931).isNotEqualTo(pdfMinimum);
    }
}
