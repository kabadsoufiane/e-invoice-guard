package com.einvoiceguard.support;

import com.einvoiceguard.dto.InvoiceBuildRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Constructeurs de requetes de facture reutilisables entre tests, pour
 * eviter de dupliquer un JSON metier complet dans chaque cas de test.
 */
public final class InvoiceTestFixtures {

    private InvoiceTestFixtures() {
    }

    public static InvoiceBuildRequest uneFactureValide() {
        return new InvoiceBuildRequest(
                "FA-2026-0042",
                LocalDate.now().toString(),
                "Kabad Digital Services",
                "FR12345678901",
                "Client Test SARL",
                "FR98765432109",
                "EUR",
                "EN16931",
                List.of(
                        new InvoiceBuildRequest.InvoiceLine(
                                "Prestation de developpement backend",
                                BigDecimal.valueOf(5),
                                BigDecimal.valueOf(450.00),
                                BigDecimal.valueOf(20)
                        ),
                        new InvoiceBuildRequest.InvoiceLine(
                                "Licence API E-Invoice Guard - abonnement mensuel",
                                BigDecimal.ONE,
                                BigDecimal.valueOf(29.00),
                                BigDecimal.valueOf(20)
                        )
                )
        );
    }

    public static String uneFactureValideJson() {
        return """
                {
                  "invoiceNumber": "FA-2026-0042",
                  "issueDate": "%s",
                  "sellerName": "Kabad Digital Services",
                  "sellerVatNumber": "FR12345678901",
                  "buyerName": "Client Test SARL",
                  "buyerVatNumber": "FR98765432109",
                  "currency": "EUR",
                  "profile": "EN16931",
                  "lines": [
                    {"description": "Prestation de developpement backend", "quantity": 5, "unitPrice": 450.00, "vatRate": 20},
                    {"description": "Licence API E-Invoice Guard - abonnement mensuel", "quantity": 1, "unitPrice": 29.00, "vatRate": 20}
                  ]
                }
                """.formatted(LocalDate.now());
    }
}
