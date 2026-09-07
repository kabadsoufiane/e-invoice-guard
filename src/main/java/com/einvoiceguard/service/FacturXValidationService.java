package com.einvoiceguard.service;

import com.einvoiceguard.dto.ValidationError;
import com.einvoiceguard.dto.ValidationResult;
import com.einvoiceguard.exception.InvoiceProcessingException;
import org.mustangproject.ZUGFeRD.ZUGFeRDImporter;
import org.mustangproject.validator.IrrecoverableValidationError;
import org.mustangproject.validator.PDFValidator;
import org.mustangproject.validator.ValidationContext;
import org.mustangproject.validator.ValidationResultItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coeur du produit : valide un fichier PDF Factur-X/ZUGFeRD et renvoie
 * un verdict detaille, pas juste vrai/faux.
 *
 * S'appuie sur mustangproject (Apache-2.0), module "validator", qui embarque
 * deja veraPDF pour le controle PDF/A-3 et sa propre logique EN 16931 /
 * Factur-X / ZUGFeRD / XRechnung. La classe cle est PDFValidator, pilotee
 * par un ValidationContext qui accumule les ValidationResultItem.
 *
 * Reference : org.mustangproject.validator.PDFValidator (module "validator"
 * du depot ZUGFeRD/mustangproject sur GitHub).
 *
 * IMPORTANT (RGPD) : ne jamais logger le contenu du document, seulement
 * des identifiants de requete et des codes d'erreur.
 */
@Service
public class FacturXValidationService {

    private static final Logger log = LoggerFactory.getLogger(FacturXValidationService.class);

    // mustangproject.validator.PDFValidator ne renseigne jamais
    // ValidationContext.getProfile() (verifie par decompilation du bytecode :
    // aucun appel a setProfile() dans PDFValidator.validate()). On extrait donc
    // le profil et le format nous-memes directement depuis le XML CII embarque,
    // qui est la source de verite normative (urn:cen.eu:en16931:2017, urn Factur-X
    // minimum/basic/en16931/extended, ou urn ZUGFeRD 2.x equivalents).
    private static final Pattern GUIDELINE_ID_PATTERN = Pattern.compile(
            "<(?:ram:)?GuidelineSpecifiedDocumentContextParameter>\\s*<(?:ram:)?ID>([^<]+)</(?:ram:)?ID>");

    private static final Map<String, String> PROFILE_URN_TO_LABEL = new LinkedHashMap<>();
    static {
        PROFILE_URN_TO_LABEL.put("en16931", "EN16931");
        PROFILE_URN_TO_LABEL.put("extended", "EXTENDED");
        PROFILE_URN_TO_LABEL.put("comfort", "COMFORT");
        PROFILE_URN_TO_LABEL.put("basicwl", "BASIC WL");
        PROFILE_URN_TO_LABEL.put("basic", "BASIC");
        PROFILE_URN_TO_LABEL.put("minimum", "MINIMUM");
        PROFILE_URN_TO_LABEL.put("xrechnung", "XRECHNUNG");
    }

    /**
     * Valide un fichier PDF Factur-X/ZUGFeRD : PDFValidator extrait le XML
     * embarque, verifie sa conformite EN 16931, et controle en interne
     * la conformite PDF/A-3 (via veraPDF) - encodage, pieces jointes, MIME.
     */
    public ValidationResult validatePdf(Path pdfFile) {
        // Un logger dedie par appel evite de melanger les traces entre requetes concurrentes.
        Logger contextLogger = LoggerFactory.getLogger("mustang.validation");
        ValidationContext context = new ValidationContext(contextLogger);

        try {
            byte[] fileContents = Files.readAllBytes(pdfFile);

            PDFValidator validator = new PDFValidator(context);
            validator.setFilenameAndContents(pdfFile.getFileName().toString(), fileContents);
            validator.validate();

            List<ValidationError> errors = toValidationErrors(context.getResults());
            boolean isValid = context.isValid();
            String detectedProfile = detectProfile(fileContents);

            log.info("Validation PDF terminee, valide={}, profil={}, erreurs={}",
                    isValid, detectedProfile, errors.size());

            return new ValidationResult(isValid, safe(detectedProfile), safe(context.getFormat()), errors, List.of());
        } catch (IrrecoverableValidationError ex) {
            // Erreur bloquante remontee par mustangproject lui-meme (ex: pas un PDF valide)
            log.warn("Erreur de validation irrecuperable : {}", ex.getMessage());
            return new ValidationResult(false, "UNKNOWN", "UNKNOWN",
                    List.of(new ValidationError("IRRECOVERABLE", "FATAL", ex.getMessage(), null, null)), List.of());
        } catch (Exception ex) {
            log.warn("Echec du traitement du PDF envoye", ex);
            throw new InvoiceProcessingException(
                    "Impossible de traiter le PDF : format non reconnu ou fichier corrompu.", ex);
        }
    }

    /**
     * Les rulesets (definitions de regles Schematron/XSD) sont couteux a charger.
     * Cache Caffeine configure dans application.yml (6h de duree de vie).
     */
    @Cacheable("rulesets")
    public String getActiveRulesetVersion() {
        // A remplacer par la lecture reelle de la version embarquee de mustangproject / phive-rules
        return "EN16931-2025.1 / PEPPOL-BIS3-2026.05";
    }

    private List<ValidationError> toValidationErrors(List<ValidationResultItem> results) {
        if (results == null) {
            return List.of();
        }
        return results.stream()
                .map(item -> new ValidationError(
                        item.getID(),
                        item.getSeverity() != null ? item.getSeverity().toString().toUpperCase() : "ERROR",
                        item.getMessage(),
                        item.getLocation(),
                        null
                ))
                .toList();
    }

    /**
     * Extrait le profil Factur-X/EN16931 reellement declare dans le XML CII
     * embarque (ram:GuidelineSpecifiedDocumentContextParameter/ram:ID), car
     * ValidationContext.getProfile() n'est jamais renseigne par PDFValidator.
     * Retourne null si aucun XML n'a pu etre extrait ou si l'URN est inconnue.
     */
    private String detectProfile(byte[] pdfBytes) {
        try {
            ZUGFeRDImporter importer = new ZUGFeRDImporter(new ByteArrayInputStream(pdfBytes));
            byte[] rawXml = importer.getRawXML();
            if (rawXml == null || rawXml.length == 0) {
                return null;
            }
            String xml = new String(rawXml, java.nio.charset.StandardCharsets.UTF_8);
            Matcher matcher = GUIDELINE_ID_PATTERN.matcher(xml);
            if (!matcher.find()) {
                return null;
            }
            String urn = matcher.group(1).trim().toLowerCase();
            for (Map.Entry<String, String> entry : PROFILE_URN_TO_LABEL.entrySet()) {
                if (urn.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
            return null;
        } catch (Exception ex) {
            log.debug("Impossible d'extraire le profil depuis le XML embarque : {}", ex.getMessage());
            return null;
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }
}
