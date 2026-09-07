package com.einvoiceguard.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

/**
 * Gestion centralisee des erreurs.
 * Important : chaque handler force explicitement produces = APPLICATION_JSON.
 * Sans cela, sur un endpoint dont le mapping de succes declare
 * produces = APPLICATION_PDF (ex. /v1/build/facturx), Spring tente de
 * respecter ce type de contenu meme pour la reponse d'erreur, echoue a
 * convertir un Map en PDF, et renvoie un 500 vide au lieu du JSON attendu.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvoiceProcessingException.class)
    public ResponseEntity<Map<String, Object>> handleProcessingException(InvoiceProcessingException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "error", "PROCESSING_FAILED",
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        // Chaque erreur precise le champ concerne (avec son index de ligne pour les
        // listes, ex. "lines[0].quantity") et la valeur rejetee, pour que l'integrateur
        // n'ait pas a deviner quel champ corriger.
        List<Map<String, Object>> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> {
                    Map<String, Object> detail = new java.util.LinkedHashMap<>();
                    detail.put("field", fieldError.getField());
                    detail.put("message", fieldError.getDefaultMessage());
                    Object rejected = fieldError.getRejectedValue();
                    detail.put("rejectedValue", rejected != null ? rejected.toString() : null);
                    return detail;
                })
                .toList();
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "error", "INVALID_REQUEST",
                        "message", "La requete contient " + details.size() + " champ(s) invalide(s). Voir 'details'.",
                        "details", details
                ));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Map<String, Object>> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        // Se produit quand le client impose un header Accept incompatible avec
        // le "produces" declare sur l'endpoint (ex: Accept: application/json
        // sur /v1/build/facturx, qui ne produit que application/pdf en succes).
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "error", "NOT_ACCEPTABLE",
                        "message", "Cet endpoint ne peut pas repondre avec le type demande dans le header Accept. "
                                + "Utilisez Accept: application/pdf (succes) ou omettez le header (les erreurs restent en JSON)."
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        // Toujours logger la cause reelle cote serveur, meme si on ne l'expose pas au client.
        log.error("Erreur inattendue non geree : {}", ex.getMessage(), ex);
        // Ne jamais renvoyer la stacktrace brute au client (fuite d'info + non professionnel)
        return ResponseEntity.internalServerError()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "error", "INTERNAL_ERROR",
                        "message", "Une erreur inattendue est survenue."
                ));
    }
}
