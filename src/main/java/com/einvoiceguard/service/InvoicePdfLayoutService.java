package com.einvoiceguard.service;

import com.einvoiceguard.dto.InvoiceBuildRequest;
import com.einvoiceguard.exception.InvoiceProcessingException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.apache.pdfbox.util.Matrix;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.AdobePDFSchema;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.schema.PDFAIdentificationSchema;
import org.apache.xmpbox.schema.XMPBasicSchema;
import org.apache.xmpbox.xml.XmpSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Dessine la mise en page visuelle de la facture (en-tete, coordonnees,
 * tableau des lignes, totaux, mentions legales) et produit un PDF/A-1b
 * de base pret a etre transmis a mustangproject (FacturXBuilderService),
 * qui y embarquera ensuite le XML structure CII pour produire le PDF/A-3
 * hybride final.
 *
 * Conformite PDF/A-1b assuree par :
 * - Police standard PDType1Font (Helvetica) avec encodage WinAnsi
 * - Profil couleur ICC sRGB embarque via un OutputIntent (obligatoire PDF/A)
 * - Metadonnees XMP (Dublin Core + PDF/A Identification) obligatoires
 *
 * Reference PDFBox : org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent,
 * org.apache.xmpbox.XMPMetadata (module xmpbox, inclus dans pdfbox-3.x).
 */
@Service
public class InvoicePdfLayoutService {

    private static final Logger log = LoggerFactory.getLogger(InvoicePdfLayoutService.class);

    private static final float MARGIN = 50f;
    private static final float PAGE_WIDTH = 595.28f;  // A4 portrait, en points (72 dpi)
    private static final float PAGE_HEIGHT = 841.89f;

    /**
     * Genere un PDF/A-1b visuel complet (une page) representant la facture.
     * Le PDF produit est deliberement "base" : sans le XML structure, qui
     * sera embarque juste apres par FacturXBuilderService/mustangproject.
     */
    public byte[] renderInvoicePdf(InvoiceBuildRequest request) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new org.apache.pdfbox.pdmodel.common.PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));
            doc.addPage(page);

            // Polices TrueType reellement embarquees dans le PDF (obligatoire pour la
            // conformite PDF/A-3 : les polices standard PDF comme Helvetica ne sont
            // jamais physiquement incluses dans le fichier, ce que veraPDF rejette).
            PDFont fontRegular = loadEmbeddedFont(doc, "fonts/LiberationSans-Regular.ttf");
            PDFont fontBold = loadEmbeddedFont(doc, "fonts/LiberationSans-Bold.ttf");

            applyPdfAOutputIntent(doc);
            applyXmpMetadata(doc, request);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = PAGE_HEIGHT - MARGIN;
                y = drawHeader(cs, fontRegular, fontBold, request, y);
                y = drawParties(cs, fontRegular, fontBold, request, y);
                y = drawLinesTable(cs, fontRegular, fontBold, request, y);
                drawTotals(cs, fontRegular, fontBold, request, y);
                drawFooter(cs, fontRegular);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            log.info("Mise en page PDF/A-1b generee pour la facture {}", request.invoiceNumber());
            return out.toByteArray();
        } catch (java.time.format.DateTimeParseException ex) {
            log.warn("Date d'emission invalide fournie pour la facture {}", request.invoiceNumber());
            throw new InvoiceProcessingException(
                    "Date d'emission invalide (format attendu : yyyy-MM-dd).", ex);
        } catch (Exception ex) {
            log.warn("Echec de generation de la mise en page PDF de la facture {}", request.invoiceNumber(), ex);
            throw new InvoiceProcessingException(
                    "Impossible de generer la mise en page de la facture : verifiez les champs fournis.", ex);
        }
    }

    private PDFont loadEmbeddedFont(PDDocument doc, String classpathLocation) throws Exception {
        try (var fontStream = new ClassPathResource(classpathLocation).getInputStream()) {
            return PDType0Font.load(doc, fontStream, true);
        }
    }

    private void applyPdfAOutputIntent(PDDocument doc) throws Exception {
        try (var iccStream = new ClassPathResource("icc/sRGB.icc").getInputStream()) {
            PDOutputIntent oi = new PDOutputIntent(doc, iccStream);
            oi.setInfo("sRGB IEC61966-2.1");
            oi.setOutputCondition("sRGB IEC61966-2.1");
            oi.setOutputConditionIdentifier("sRGB IEC61966-2.1");
            oi.setRegistryName("http://www.color.org");
            doc.getDocumentCatalog().addOutputIntent(oi);
        }
    }

    private void applyXmpMetadata(PDDocument doc, InvoiceBuildRequest request) throws Exception {
        XMPMetadata xmp = XMPMetadata.createXMPMetadata();

        DublinCoreSchema dc = xmp.createAndAddDublinCoreSchema();
        dc.setTitle("Facture " + request.invoiceNumber());
        dc.addCreator(request.sellerName());
        dc.addDate(Calendar.getInstance());

        AdobePDFSchema pdf = xmp.createAndAddAdobePDFSchema();
        pdf.setProducer("E-Invoice Guard");

        XMPBasicSchema basic = xmp.createAndAddXMPBasicSchema();
        basic.setCreatorTool("E-Invoice Guard API");

        PDFAIdentificationSchema pdfaid = xmp.createAndAddPDFAIdentificationSchema();
        pdfaid.setPart(1);
        pdfaid.setConformance("B");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new XmpSerializer().serialize(xmp, baos, true);

        PDMetadata metadata = new PDMetadata(doc);
        metadata.importXMPMetadata(baos.toByteArray());
        PDDocumentCatalog catalog = doc.getDocumentCatalog();
        catalog.getCOSObject().setItem(org.apache.pdfbox.cos.COSName.METADATA, metadata);
    }

    private float drawHeader(PDPageContentStream cs, PDFont regular, PDFont bold,
                              InvoiceBuildRequest request, float y) throws Exception {
        cs.beginText();
        cs.setFont(bold, 20);
        cs.newLineAtOffset(MARGIN, y);
        cs.showText("FACTURE");
        cs.endText();

        cs.beginText();
        cs.setFont(regular, 11);
        cs.newLineAtOffset(PAGE_WIDTH - MARGIN - 160, y);
        cs.showText("N\u00b0 " + request.invoiceNumber());
        cs.endText();

        y -= 16;
        cs.beginText();
        cs.setFont(regular, 11);
        cs.newLineAtOffset(PAGE_WIDTH - MARGIN - 160, y);
        cs.showText("Date d'emission : " + formatDate(request.issueDate()));
        cs.endText();

        y -= 30;
        drawLine(cs, MARGIN, y, PAGE_WIDTH - MARGIN, y);
        return y - 20;
    }

    private float drawParties(PDPageContentStream cs, PDFont regular, PDFont bold,
                               InvoiceBuildRequest request, float y) throws Exception {
        float colWidth = (PAGE_WIDTH - 2 * MARGIN) / 2;

        cs.beginText();
        cs.setFont(bold, 11);
        cs.newLineAtOffset(MARGIN, y);
        cs.showText("EMETTEUR");
        cs.endText();

        cs.beginText();
        cs.setFont(bold, 11);
        cs.newLineAtOffset(MARGIN + colWidth, y);
        cs.showText("CLIENT");
        cs.endText();

        y -= 16;
        cs.beginText();
        cs.setFont(regular, 10);
        cs.newLineAtOffset(MARGIN, y);
        cs.showText(request.sellerName());
        cs.endText();

        cs.beginText();
        cs.setFont(regular, 10);
        cs.newLineAtOffset(MARGIN + colWidth, y);
        cs.showText(request.buyerName());
        cs.endText();

        y -= 14;
        cs.beginText();
        cs.setFont(regular, 10);
        cs.newLineAtOffset(MARGIN, y);
        cs.showText("TVA intracom. : " + request.sellerVatNumber());
        cs.endText();

        if (request.buyerVatNumber() != null && !request.buyerVatNumber().isBlank()) {
            cs.beginText();
            cs.setFont(regular, 10);
            cs.newLineAtOffset(MARGIN + colWidth, y);
            cs.showText("TVA intracom. : " + request.buyerVatNumber());
            cs.endText();
        }

        y -= 26;
        drawLine(cs, MARGIN, y, PAGE_WIDTH - MARGIN, y);
        return y - 20;
    }

    private float drawLinesTable(PDPageContentStream cs, PDFont regular, PDFont bold,
                                  InvoiceBuildRequest request, float y) throws Exception {
        float[] colX = {MARGIN, MARGIN + 220, MARGIN + 290, MARGIN + 360, MARGIN + 430};
        String[] headers = {"Description", "Quantite", "Prix unit.", "TVA %", "Total HT"};

        cs.setFont(bold, 9);
        for (int i = 0; i < headers.length; i++) {
            cs.beginText();
            cs.newLineAtOffset(colX[i], y);
            cs.showText(headers[i]);
            cs.endText();
        }
        y -= 10;
        drawLine(cs, MARGIN, y, PAGE_WIDTH - MARGIN, y);
        y -= 14;

        cs.setFont(regular, 9);
        for (InvoiceBuildRequest.InvoiceLine line : request.lines()) {
            BigDecimal lineTotal = line.quantity().multiply(line.unitPrice()).setScale(2, RoundingMode.HALF_UP);

            String description = truncate(line.description(), 38);
            String[] values = {
                    description,
                    line.quantity().stripTrailingZeros().toPlainString(),
                    formatAmount(line.unitPrice()),
                    line.vatRate().stripTrailingZeros().toPlainString() + "%",
                    formatAmount(lineTotal)
            };
            for (int i = 0; i < values.length; i++) {
                cs.beginText();
                cs.newLineAtOffset(colX[i], y);
                cs.showText(values[i]);
                cs.endText();
            }
            y -= 16;
        }

        y -= 6;
        drawLine(cs, MARGIN, y, PAGE_WIDTH - MARGIN, y);
        return y - 20;
    }

    private void drawTotals(PDPageContentStream cs, PDFont regular, PDFont bold,
                             InvoiceBuildRequest request, float y) throws Exception {
        BigDecimal totalHt = BigDecimal.ZERO;
        BigDecimal totalVat = BigDecimal.ZERO;

        for (InvoiceBuildRequest.InvoiceLine line : request.lines()) {
            BigDecimal lineHt = line.quantity().multiply(line.unitPrice()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineVat = lineHt.multiply(line.vatRate())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            totalHt = totalHt.add(lineHt);
            totalVat = totalVat.add(lineVat);
        }
        BigDecimal totalTtc = totalHt.add(totalVat);

        float labelX = PAGE_WIDTH - MARGIN - 180;
        float valueX = PAGE_WIDTH - MARGIN - 70;

        y = drawTotalRow(cs, regular, labelX, valueX, y, "Total HT", formatAmount(totalHt) + " " + request.currency());
        y = drawTotalRow(cs, regular, labelX, valueX, y, "Total TVA", formatAmount(totalVat) + " " + request.currency());
        drawTotalRow(cs, bold, labelX, valueX, y, "Total TTC", formatAmount(totalTtc) + " " + request.currency());
    }

    private float drawTotalRow(PDPageContentStream cs, PDFont font, float labelX, float valueX,
                                float y, String label, String value) throws Exception {
        cs.beginText();
        cs.setFont(font, 10);
        cs.newLineAtOffset(labelX, y);
        cs.showText(label);
        cs.endText();

        cs.beginText();
        cs.setFont(font, 10);
        cs.newLineAtOffset(valueX, y);
        cs.showText(value);
        cs.endText();

        return y - 16;
    }

    private void drawFooter(PDPageContentStream cs, PDFont regular) throws Exception {
        cs.beginText();
        cs.setFont(regular, 8);
        cs.newLineAtOffset(MARGIN, 40);
        cs.showText("Document genere electroniquement par E-Invoice Guard - facture electronique conforme Factur-X / EN 16931.");
        cs.endText();
    }

    private void drawLine(PDPageContentStream cs, float x1, float y1, float x2, float y2) throws Exception {
        cs.setLineWidth(0.5f);
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
    }

    private String formatDate(String isoDate) {
        LocalDate date = LocalDate.parse(isoDate, DateTimeFormatter.ISO_LOCAL_DATE);
        return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE));
    }

    private String formatAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1) + "\u2026";
    }
}
