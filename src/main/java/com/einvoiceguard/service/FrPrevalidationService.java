package com.einvoiceguard.service;

import com.einvoiceguard.dto.FrPrevalidationRequest;
import com.einvoiceguard.dto.FrPrevalidationResult;
import com.einvoiceguard.exception.InvoiceProcessingException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Pre-validation specifique France : SIRET/SIREN (cle de controle Luhn,
 * norme INSEE) et coherence syntaxique d'un numero de TVA intracommunautaire
 * francais.
 *
 * La logique de controle est volontairement dupliquee sous forme de methodes
 * statiques simples (plutot que de reinstancier les ConstraintValidator de
 * Bean Validation hors de leur cycle de vie habituel) : cela reste facilement
 * testable unitairement et evite toute dependance a l'API interne de
 * jakarta.validation.ConstraintValidator, qui n'est pas concue pour un usage
 * programmatique direct.
 *
 * Verification syntaxique uniquement : aucun appel reseau externe (ni a
 * l'API INSEE Sirene, ni au service VIES de la Commission europeenne).
 */
@Service
public class FrPrevalidationService {

    private static final Pattern VAT_FORMAT = Pattern.compile("^[A-Z]{2}[A-Z0-9]{2,13}$");

    public FrPrevalidationResult prevalidate(FrPrevalidationRequest request) {
        boolean hasSiret = request.siret() != null && !request.siret().isBlank();
        boolean hasVat = request.vatNumber() != null && !request.vatNumber().isBlank();

        if (!hasSiret && !hasVat) {
            throw new InvoiceProcessingException(
                    "Fournissez au moins un SIRET ou un numero de TVA a pre-valider.", null);
        }

        FrPrevalidationResult.FieldCheck siretCheck = hasSiret ? checkSiret(request.siret()) : null;
        FrPrevalidationResult.FieldCheck vatCheck = hasVat ? checkVatNumber(request.vatNumber()) : null;

        List<String> warnings = new ArrayList<>();
        if (hasVat && !request.vatNumber().trim().toUpperCase().startsWith("FR")) {
            warnings.add("Le numero de TVA fourni ne commence pas par FR : "
                    + "la coherence avec un SIRET francais n'a pas ete verifiee.");
        }

        boolean overallValid = (siretCheck == null || siretCheck.valid()) && (vatCheck == null || vatCheck.valid());

        return new FrPrevalidationResult(overallValid, siretCheck, vatCheck, warnings);
    }

    /**
     * Meme regle que SiretValidator (com.einvoiceguard.validation) : 14 chiffres
     * et cle de controle de Luhn, comme l'exige l'INSEE.
     */
    private FrPrevalidationResult.FieldCheck checkSiret(String rawSiret) {
        String digits = rawSiret.replace(" ", "");
        boolean formatOk = digits.matches("\\d{14}");
        boolean valid = formatOk && isLuhnValid(digits);
        String message = valid
                ? "SIRET valide (14 chiffres, cle de controle Luhn correcte)."
                : "SIRET invalide : attendu 14 chiffres avec cle de controle Luhn valide.";
        return new FrPrevalidationResult.FieldCheck(rawSiret, valid, message);
    }

    /**
     * Meme regle que VatNumberValidator (com.einvoiceguard.validation) :
     * 2 lettres de code pays ISO 3166-1 alpha-2 suivies de 2 a 13 caracteres
     * alphanumeriques.
     */
    private FrPrevalidationResult.FieldCheck checkVatNumber(String rawVat) {
        String normalized = rawVat.replace(" ", "").toUpperCase();
        boolean valid = VAT_FORMAT.matcher(normalized).matches();
        String message = valid
                ? "Numero de TVA syntaxiquement valide (format europeen standard)."
                : "Numero de TVA invalide (format attendu : 2 lettres pays + caracteres alphanumeriques, ex. FR12345678901).";
        return new FrPrevalidationResult.FieldCheck(rawVat, valid, message);
    }

    private boolean isLuhnValid(String digits) {
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            int digit = Character.getNumericValue(digits.charAt(digits.length() - 1 - i));
            if (i % 2 == 1) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
        }
        return sum % 10 == 0;
    }
}
