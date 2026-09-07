package com.einvoiceguard.exception;

/**
 * Exception metier levee quand un document ne peut pas etre traite
 * (format non reconnu, XML corrompu, PDF sans piece jointe, etc.)
 * Distincte d'une simple "non-validite" au sens Schematron, qui elle
 * renvoie un ValidationResult avec valid=false plutot qu'une exception.
 */
public class InvoiceProcessingException extends RuntimeException {

    public InvoiceProcessingException(String message) {
        super(message);
    }

    public InvoiceProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
