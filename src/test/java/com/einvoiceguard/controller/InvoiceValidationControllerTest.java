package com.einvoiceguard.controller;

import com.einvoiceguard.support.InvoiceTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration bout-en-bout des 3 endpoints, sans cle API configuree
 * (comportement permissif par defaut : voir ApiKeyAuthFilterTest pour le
 * comportement avec cles actives).
 */
@SpringBootTest
@AutoConfigureMockMvc
class InvoiceValidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ---- /v1/build/facturx ----

    @Test
    void genereUneFactureFacturXValide() throws Exception {
        mockMvc.perform(post("/v1/build/facturx")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InvoiceTestFixtures.uneFactureValideJson()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().exists("Content-Disposition"));
    }

    @Test
    void rejetteUneFactureAvecTvaVendeurInvalide() throws Exception {
        String jsonInvalide = InvoiceTestFixtures.uneFactureValideJson()
                .replace("FR12345678901", "INVALID123");

        mockMvc.perform(post("/v1/build/facturx")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInvalide))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.details[0].field").value("sellerVatNumber"));
    }

    @Test
    void rejetteUneFactureAvecDevisenInvalide() throws Exception {
        String jsonInvalide = InvoiceTestFixtures.uneFactureValideJson()
                .replace("\"EUR\"", "\"eur\"");

        mockMvc.perform(post("/v1/build/facturx")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInvalide))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("currency"));
    }

    @Test
    void rejetteUneFactureAvecProfilInvalide() throws Exception {
        String jsonInvalide = InvoiceTestFixtures.uneFactureValideJson()
                .replace("\"EN16931\"", "\"FULL\"");

        mockMvc.perform(post("/v1/build/facturx")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInvalide))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("profile"));
    }

    @Test
    void rejetteUneFactureSansAucuneLigne() throws Exception {
        String jsonSansLignes = InvoiceTestFixtures.uneFactureValideJson()
                .replaceAll("(?s)\"lines\": \\[.*\\]", "\"lines\": []");

        mockMvc.perform(post("/v1/build/facturx")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonSansLignes))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("lines"));
    }

    @Test
    void rejetteUneFactureAvecPlusieursChampsInvalidesSimultanement() throws Exception {
        String jsonInvalide = InvoiceTestFixtures.uneFactureValideJson()
                .replace("\"EUR\"", "\"eur\"")
                .replace("\"EN16931\"", "\"FULL\"")
                .replace("FR12345678901", "XX999");

        mockMvc.perform(post("/v1/build/facturx")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonInvalide))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.length()").value(3));
    }

    // ---- /v1/validate/pdf ----

    @Test
    void valideUnPdfFacturXEchantillonEnvoyeEnMultipart() throws Exception {
        MockMultipartFile fichier = new MockMultipartFile(
                "file", "exemple_facturx_valide.pdf", MediaType.APPLICATION_PDF_VALUE,
                new ClassPathResource("samples/exemple_facturx_valide.pdf").getInputStream());

        mockMvc.perform(multipart("/v1/validate/pdf").file(fichier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.detectedProfile").value("EN16931"));
    }

    @Test
    void renvoieUnVerdictInvalidePourUnFichierQuiNestPasUnPdf() throws Exception {
        MockMultipartFile fichier = new MockMultipartFile(
                "file", "pas-un-pdf.txt", MediaType.TEXT_PLAIN_VALUE,
                "ceci n'est pas un PDF".getBytes());

        mockMvc.perform(multipart("/v1/validate/pdf").file(fichier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }

    // ---- /v1/rulesets ----

    @Test
    void exposeLaVersionDesRulesetsActifs() throws Exception {
        mockMvc.perform(get("/v1/rulesets"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyOrNullString())));
    }
}
