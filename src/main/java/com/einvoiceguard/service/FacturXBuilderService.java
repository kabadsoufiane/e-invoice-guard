package com.einvoiceguard.service;

import com.einvoiceguard.dto.InvoiceBuildRequest;
import com.einvoiceguard.exception.InvoiceProcessingException;
import org.mustangproject.Invoice;
import org.mustangproject.Item;
import org.mustangproject.Product;
import org.mustangproject.TradeParty;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA1;
import org.mustangproject.ZUGFeRD.ZUGFeRDImporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Genere un PDF/A-3 hybride Factur-X a partir de JSON metier, via mustangproject.
 * Le client n'a jamais besoin de comprendre le format XML CII sous-jacent :
 * il envoie des champs metier simples, on renvoie un PDF pret a transmettre.
 *
 * mustangproject part toujours d'un PDF/A "blanc" existant (mise en forme
 * visuelle deja faite ailleurs, ex: par le moteur de templating du client)
 * et y embarque le XML structure pour produire le PDF/A-3 hybride final.
 *
 * Reference : org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA1 (module
 * "library" du depot ZUGFeRD/mustangproject sur GitHub).
 */
@Service
public class FacturXBuilderService {

    private static final Logger log = LoggerFactory.getLogger(FacturXBuilderService.class);

    private final InvoicePdfLayoutService layoutService;

    public FacturXBuilderService(InvoicePdfLayoutService layoutService) {
        this.layoutService = layoutService;
    }

    /**
     * Genere la facture Factur-X complete : dessine d'abord la mise en page
     * visuelle du PDF (via InvoicePdfLayoutService/PDFBox), puis y embarque
     * le XML structure CII (via mustangproject) pour produire le PDF/A-3
     * hybride final, pret a etre transmis au client final.
     */
    public byte[] buildFacturX(InvoiceBuildRequest request) {
        byte[] basePdf = layoutService.renderInvoicePdf(request);
        return buildFacturX(request, basePdf);
    }

    /**
     * @param request les donnees metier de la facture (JSON recu par l'API)
     * @param basePdf un PDF/A "blanc" deja mis en forme (logo, mentions legales, mise en page) ;
     *                c'est dans ce PDF que le XML structure sera embarque
     */
    public byte[] buildFacturX(InvoiceBuildRequest request, byte[] basePdf) {
        try {
            Invoice invoice = new Invoice();
            invoice.setNumber(request.invoiceNumber());
            invoice.setIssueDate(parseDate(request.issueDate()));
            invoice.setCurrency(request.currency());

            TradeParty seller = new TradeParty(request.sellerName(), "", "", "", "")
                    .addVATID(request.sellerVatNumber());
            invoice.setSender(seller);

            TradeParty buyer = new TradeParty(request.buyerName(), "", "", "", "");
            if (request.buyerVatNumber() != null && !request.buyerVatNumber().isBlank()) {
                buyer.addVATID(request.buyerVatNumber());
            }
            invoice.setRecipient(buyer);

            for (InvoiceBuildRequest.InvoiceLine line : request.lines()) {
                Product product = new Product(line.description(), "", "C62", line.vatRate());
                Item item = new Item(product, line.unitPrice(), line.quantity());
                invoice.addItem(item);
            }

            return export(invoice, request.profile(), basePdf);
        } catch (InvoiceProcessingException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Echec de generation de la facture Factur-X", ex);
            throw new InvoiceProcessingException(
                    "Impossible de generer la facture : verifiez les champs obligatoires et le PDF de base fourni.", ex);
        }
    }

    /**
     * Convertit une facture Factur-X/ZUGFeRD existante vers un autre profil
     * (voir POST /v1/convert). Le PDF source sert lui-meme de gabarit visuel :
     * on en extrait l'objet metier Invoice via mustangproject (ZUGFeRDImporter
     * sait deja reconstruire un Invoice a partir du XML CII embarque), puis on
     * reexporte ce meme PDF avec le profil cible demande.
     *
     * Contrairement a buildFacturX(request, basePdf), aucune donnee metier
     * n'est fournie par l'appelant ici : tout provient du document source.
     */
    public byte[] convertProfile(byte[] sourcePdf, String targetProfile) {
        try {
            ZUGFeRDImporter importer = new ZUGFeRDImporter(new ByteArrayInputStream(sourcePdf));
            Invoice invoice = new Invoice(importer);

            byte[] converted = export(invoice, targetProfile, sourcePdf);
            log.info("Facture convertie vers le profil {}", targetProfile);
            return converted;
        } catch (InvoiceProcessingException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Echec de conversion de profil Factur-X", ex);
            throw new InvoiceProcessingException(
                    "Impossible de convertir la facture : PDF source illisible ou non reconnu comme Factur-X/ZUGFeRD.", ex);
        }
    }

    /**
     * Chainage volontairement eclate : setProducer/setCreator/ignorePDFAErrors
     * sont heritees de ZUGFeRDExporterFromA3 et renvoient ce type parent,
     * pas ZUGFeRDExporterFromA1 - les chainer ensemble casserait la compilation.
     */
    private byte[] export(Invoice invoice, String profile, byte[] basePdf) throws Exception {
        ZUGFeRDExporterFromA1 exporter = new ZUGFeRDExporterFromA1();
        exporter.setProducer("E-Invoice Guard");
        exporter.setCreator("E-Invoice Guard API");
        exporter.setProfile(profile); // "MINIMUM" | "BASIC" | "EN16931" | "EXTENDED"
        exporter.ignorePDFAErrors(); // le PDF de base fourni par le client n'est pas toujours un PDF/A strict

        exporter.load(basePdf);
        exporter.setTransaction(invoice);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        exporter.export(output);
        exporter.close();
        return output.toByteArray();
    }

    private Date parseDate(String isoDate) {
        LocalDate localDate = LocalDate.parse(isoDate, DateTimeFormatter.ISO_LOCAL_DATE);
        return Date.from(localDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
    }
}
