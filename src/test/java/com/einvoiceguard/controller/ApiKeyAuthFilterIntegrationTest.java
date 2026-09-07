package com.einvoiceguard.controller;

import com.einvoiceguard.support.InvoiceTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifie ApiKeyAuthFilter avec des cles API reellement configurees
 * (contexte Spring dedie, distinct du contexte "permissif" par defaut
 * utilise par InvoiceValidationControllerTest).
 */
@SpringBootTest(properties = "EINVOICEGUARD_API_KEYS=cle-valide-1,cle-valide-2")
@AutoConfigureMockMvc
class ApiKeyAuthFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejetteLaRequeteSansCleApiQuandDesClesSontConfigurees() throws Exception {
        mockMvc.perform(post("/v1/build/facturx")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InvoiceTestFixtures.uneFactureValideJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void rejetteLaRequeteAvecUneCleApiIncorrecte() throws Exception {
        mockMvc.perform(post("/v1/build/facturx")
                        .header("X-API-Key", "cle-invalide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InvoiceTestFixtures.uneFactureValideJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accepteLaRequeteAvecUneCleApiValide() throws Exception {
        mockMvc.perform(post("/v1/build/facturx")
                        .header("X-API-Key", "cle-valide-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InvoiceTestFixtures.uneFactureValideJson()))
                .andExpect(status().isOk());
    }

    @Test
    void accepteLaSecondeCleApiValideConfiguree() throws Exception {
        mockMvc.perform(post("/v1/build/facturx")
                        .header("X-API-Key", "cle-valide-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(InvoiceTestFixtures.uneFactureValideJson()))
                .andExpect(status().isOk());
    }

    @Test
    void laisseSwaggerUiAccessibleSansCleApi() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void neBloquePasLaSondeDeSanteAvecUn401() throws Exception {
        // /actuator/health est exempte par ApiKeyAuthFilter (chemin public), meme si
        // aucune dependance actuator n'est presente ici pour repondre 200 : le point
        // essentiel est l'absence de 401 (pas de blocage par le filtre de cle API).
        mockMvc.perform(get("/actuator/health"))
                .andExpect(result -> org.assertj.core.api.Assertions
                        .assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
    }
}
