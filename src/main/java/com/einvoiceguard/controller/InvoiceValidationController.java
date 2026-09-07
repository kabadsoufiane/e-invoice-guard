package com.einvoiceguard.controller;

import com.einvoiceguard.dto.InvoiceBuildRequest;
import com.einvoiceguard.dto.ValidationResult;
import com.einvoiceguard.service.FacturXBuilderService;
import com.einvoiceguard.service.FacturXValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Couche API REST exposee derriere RapidAPI.
 * RapidAPI gere l'authentification par cle et les quotas ; ce controleur
 * fait confiance au header X-RapidAPI-Proxy-Secret (verifie par le filtre
 * RapidApiProxyFilter) pour s'assurer que l'appel transite bien par le hub.
 *
 * Documentation interactive : /swagger-ui.html (voir OpenApiConfig).
 */
@RestController
@RequestMapping("/v1")
@Tag(name = "Factur-X / ZUGFeRD", description = "Validation et generation de factures electroniques conformes")
public class InvoiceValidationController {

    private final FacturXValidationService validationService;
    private final FacturXBuilderService builderService;

    public InvoiceValidationController(FacturXValidationService validationService,
                                        FacturXBuilderService builderService) {
        this.validationService = validationService;
        this.builderService = builderService;
    }

    // NOTE : la validation XML brute (CII/UBL) sans PDF n'est pas geree par
    // org.mustangproject.validator.PDFValidator (qui valide toujours un PDF
    // et en extrait le XML embarque). Un futur endpoint /v1/validate/xml
    // pourra s'appuyer sur ph-schematron directement pour ce cas ; a faire.

    /**
     * Valide un PDF Factur-X/ZUGFeRD envoye en multipart.
     */
    @Operation(
            summary = "Valider un PDF Factur-X/ZUGFeRD",
            description = "Extrait le XML embarque dans le PDF et verifie sa conformite "
                    + "EN 16931 / Factur-X / ZUGFeRD / XRechnung, ainsi que la conformite PDF/A-3 (via veraPDF)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verdict de validation (valide ou non, avec erreurs detaillees)",
                    content = @Content(schema = @Schema(implementation = ValidationResult.class))),
            @ApiResponse(responseCode = "422", description = "PDF illisible ou non reconnu comme Factur-X/ZUGFeRD")
    })
    @PostMapping(value = "/validate/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ValidationResult> validatePdf(@RequestParam("file") MultipartFile file) throws IOException {
        Path tempFile = writeTempFile(file.getBytes(), "invoice-", ".pdf");
        try {
            return ResponseEntity.ok(validationService.validatePdf(tempFile));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Genere un PDF/A-3 hybride Factur-X a partir de JSON metier.
     */
    @Operation(
            summary = "Generer une facture Factur-X",
            description = "Construit un PDF/A-3 hybride embarquant le XML structure (CII) "
                    + "conforme au profil demande (MINIMUM, BASIC, EN16931 ou EXTENDED)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF/A-3 Factur-X genere",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
            @ApiResponse(responseCode = "400", description = "Champs metier invalides ou manquants"),
            @ApiResponse(responseCode = "422", description = "Impossible de generer le PDF (donnees incoherentes)")
    })
    @PostMapping(value = "/build/facturx", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> buildFacturX(@Valid @RequestBody InvoiceBuildRequest request) {
        byte[] pdf = builderService.buildFacturX(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + request.invoiceNumber() + ".pdf\"")
                .body(pdf);
    }

    /**
     * Renvoie les versions de rulesets actuellement actives (tracabilite d'audit).
     */
    @Operation(summary = "Versions des rulesets actifs",
            description = "Utile pour la tracabilite d'audit : quelle version des regles EN 16931 / Peppol a valide la facture.")
    @GetMapping("/rulesets")
    public ResponseEntity<String> getActiveRulesets() {
        return ResponseEntity.ok(validationService.getActiveRulesetVersion());
    }

    private Path writeTempFile(byte[] content, String prefix, String suffix) throws IOException {
        Path tempFile = Files.createTempFile(prefix, suffix);
        Files.write(tempFile, content);
        return tempFile;
    }
}
