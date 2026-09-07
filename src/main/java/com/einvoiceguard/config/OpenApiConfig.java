package com.einvoiceguard.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadonnees de la documentation OpenAPI / Swagger UI.
 * Interface accessible sur /swagger-ui.html une fois l'application demarree,
 * JSON brut sur /v3/api-docs.
 *
 * Le schema de securite documente le header attendu par RapidAPI
 * (voir RapidApiProxyFilter) - il n'active pas d'authentification
 * cote Spring, il ne fait que la decrire dans Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    private static final String RAPIDAPI_PROXY_HEADER = "X-RapidAPI-Proxy-Secret";

    @Bean
    public OpenAPI eInvoiceGuardOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("E-Invoice Guard API")
                        .description("Validation et generation de factures electroniques conformes : "
                                + "Factur-X, ZUGFeRD, XRechnung, Peppol BIS 3 (EN 16931), PDF/A-3 hybride. "
                                + "Destinee a une publication sur RapidAPI.")
                        .version("v0.1.0")
                        .contact(new Contact()
                                .name("E-Invoice Guard")
                                .url("https://rapidapi.com"))
                        .license(new License()
                                .name("Usage prive - non publie sous licence open source")))
                .components(new Components()
                        .addSecuritySchemes(RAPIDAPI_PROXY_HEADER, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(RAPIDAPI_PROXY_HEADER)
                                .description("Secret partage verifie par RapidApiProxyFilter "
                                        + "(configure via la variable d'environnement EINVOICEGUARD_PROXY_SECRET)")))
                .addSecurityItem(new SecurityRequirement().addList(RAPIDAPI_PROXY_HEADER));
    }
}
