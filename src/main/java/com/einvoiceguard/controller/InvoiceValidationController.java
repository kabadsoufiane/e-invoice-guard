package com.einvoiceguard.controller;

import com.einvoiceguard.dto.BatchValidationResult;
import com.einvoiceguard.dto.ConvertRequest;
import com.einvoiceguard.dto.FrPrevalidationRequest;
import com.einvoiceguard.dto.FrPrevalidationResult;
import com.einvoiceguard.dto.InvoiceBuildRequest;
import com.einvoiceguard.dto.ValidationResult;
import com.einvoiceguard.service.FacturXBuilderService;
import com.einvoiceguard.service.FacturXValidationService;
import com.einvoiceguard.service.FrPrevalidationService;
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
import java.util.List;

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
    private final FrPrevalidationService frPrevalidationService;

    public InvoiceValidationController(FacturXValidationService validationService,
                                        FacturXBuilderService builderService,
                                        FrPrevalidationService frPrevalidationService) {
        this.validationService = validationService;
        this.builderService = builderService;
        this.frPrevalidationService = frPrevalidationService;
    }

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
     * Valide directement un JSON metier de facture, sans generer de PDF.
     * Utile pour un controle rapide en amont (formulaire, import CSV, etc.)
     * avant de consommer le quota de generation PDF.
     * La validation Bean Validation (@Valid) est deja executee par Spring
     * avant l'entree dans la methode ; si on arrive ici, la requete est
     * syntaxiquement et semantiquement valide au sens des contraintes JSR-380.
     */
    @Operation(
            summary = "Valider un JSON metier de facture (sans PDF)",
            description = "Applique les memes regles de validation metier que /v1/build/facturx "
                    + "(TVA, dates, devise, profil, lignes) sans generer de PDF. "
                    + "Utile pour un controle rapide cote client avant l'appel couteux de generation."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "JSON metier valide",
                    content = @Content(schema = @Schema(implementation = ValidationResult.class))),
            @ApiResponse(responseCode = "400", description = "Champs metier invalides ou manquants")
    })
    @PostMapping(value = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ValidationResult> validateJson(@Valid @RequestBody InvoiceBuildRequest request) {
        // Si on atteint ce point, @Valid n'a leve aucune MethodArgumentNotValidException :
        // toutes les contraintes Bean Validation de InvoiceBuildRequest sont satisfaites.
        return ResponseEntity.ok(ValidationResult.ok(request.profile(), "CII"));
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
     * Convertit une facture Factur-X/ZUGFeRD existante vers un autre profil
     * (ex. BASIC -> EN16931). Le PDF source sert de gabarit visuel : on en
     * extrait les donnees metier existantes via le XML CII embarque, puis
     * on regenere un nouveau PDF/A-3 hybride avec le profil cible demande.
     */
    @Operation(
            summary = "Convertir une facture vers un autre profil Factur-X",
            description = "Extrait les donnees d'un PDF Factur-X/ZUGFeRD existant et regenere "
                    + "un nouveau PDF/A-3 hybride conforme au profil cible demande "
                    + "(MINIMUM, BASIC, EN16931 ou EXTENDED)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF/A-3 regenere dans le profil cible",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
            @ApiResponse(responseCode = "400", description = "Profil cible invalide"),
            @ApiResponse(responseCode = "422", description = "PDF source illisible ou non convertible")
    })
    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> convert(@RequestParam("file") MultipartFile file,
                                           @Valid @RequestBody ConvertRequest targetProfile) {
        Path tempFile;
        try {
            tempFile = writeTempFile(file.getBytes(), "convert-", ".pdf");
        } catch (IOException ex) {
            throw new com.einvoiceguard.exception.InvoiceProcessingException(
                    "Impossible de lire le fichier source envoye.", ex);
        }
        try {
            byte[] sourcePdf = Files.readAllBytes(tempFile);
            byte[] converted = builderService.convertProfile(sourcePdf, targetProfile.targetProfile());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"facture-convertie.pdf\"")
                    .body(converted);
        } catch (IOException ex) {
            throw new com.einvoiceguard.exception.InvoiceProcessingException(
                    "Impossible de lire le fichier source envoye.", ex);
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
                // best effort de nettoyage du fichier temporaire
            }
        }
    }

    /**
     * Pre-validation specifique France : verifie le format et la cle de
     * controle d'un SIRET/SIREN et la coherence d'un numero de TVA
     * intracommunautaire francais, avant transmission de la facture.
     * Verification syntaxique uniquement (pas d'appel a l'API INSEE Sirene
     * ni au service VIES europeen, qui necessiteraient un appel reseau
     * externe a chaque requete).
     */
    @Operation(
            summary = "Pre-valider un SIRET/SIREN et un numero de TVA francais",
            description = "Verifie le format et la cle de controle (algorithme de Luhn) d'un SIRET/SIREN, "
                    + "ainsi que la coherence d'un numero de TVA intracommunautaire francais, avant transmission."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verdict de pre-validation France",
                    content = @Content(schema = @Schema(implementation = FrPrevalidationResult.class))),
            @ApiResponse(responseCode = "400", description = "Requete malformee")
    })
    @PostMapping(value = "/prevalidate/fr", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FrPrevalidationResult> prevalidateFr(@Valid @RequestBody FrPrevalidationRequest request) {
        return ResponseEntity.ok(frPrevalidationService.prevalidate(request));
    }

    /**
     * Valide plusieurs PDF Factur-X/ZUGFeRD en une seule requete (traitement
     * en masse). Chaque fichier est valide independamment ; un echec sur un
     * fichier n'interrompt pas le traitement des autres.
     */
    @Operation(
            summary = "Valider plusieurs PDF en une seule requete (traitement par lot)",
            description = "Applique la meme validation que /v1/validate/pdf a chaque fichier envoye. "
                    + "Un fichier illisible ou non conforme n'empeche pas le traitement des autres : "
                    + "chaque resultat est associe au nom du fichier source."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des verdicts, un par fichier envoye",
                    content = @Content(schema = @Schema(implementation = BatchValidationResult.class))),
            @ApiResponse(responseCode = "400", description = "Aucun fichier envoye ou lot trop volumineux")
    })
    @PostMapping(value = "/validate/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<BatchValidationResult>> validateBatch(@RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new com.einvoiceguard.exception.InvoiceProcessingException(
                    "Aucun fichier envoye. Utilisez le champ 'files' avec un ou plusieurs PDF.", null);
        }
        if (files.size() > 50) {
            throw new com.einvoiceguard.exception.InvoiceProcessingException(
                    "Le lot ne peut pas depasser 50 fichiers par requete.", null);
        }

        List<BatchValidationResult> results = files.stream()
                .map(this::validateSingleFileInBatch)
                .toList();
        return ResponseEntity.ok(results);
    }

    private BatchValidationResult validateSingleFileInBatch(MultipartFile file) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "fichier-sans-nom.pdf";
        try {
            Path tempFile = writeTempFile(file.getBytes(), "batch-", ".pdf");
            try {
                ValidationResult result = validationService.validatePdf(tempFile);
                return new BatchValidationResult(filename, result);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException ex) {
            ValidationResult failed = new ValidationResult(false, "UNKNOWN", "UNKNOWN",
                    List.of(new com.einvoiceguard.dto.ValidationError(
                            "IO_ERROR", "FATAL", "Impossible de lire le fichier envoye : " + ex.getMessage(), null, null)),
                    List.of());
            return new BatchValidationResult(filename, failed);
        }
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
