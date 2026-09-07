package com.einvoiceguard.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authentification par cle API, complementaire au controle de proxy RapidAPI
 * deja assure par {@link RapidApiProxyFilter}. Alors que RapidApiProxyFilter
 * verifie que la requete transite bien par la plateforme RapidAPI (secret
 * partage plateforme <-> backend), ApiKeyAuthFilter verifie l'identite du
 * client final via une cle qui lui est propre (header X-API-Key).
 *
 * Ce filtre reste utile meme hors RapidAPI (appel direct de l'API en marque
 * blanche, environnement de test, integration interne) : chaque client se
 * voit attribuer sa propre cle, revocable independamment des autres.
 *
 * Configuration : variable d'environnement EINVOICEGUARD_API_KEYS, une ou
 * plusieurs cles separees par des virgules. Si elle est vide/absente, le
 * filtre laisse passer toutes les requetes (pratique en local/dev, comme
 * pour RapidApiProxyFilter). En production, definir au moins une cle.
 */
@Component
public class ApiKeyAuthFilter extends HttpFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);

    /** Chemins exemptes d'authentification : documentation, sonde de sante, racine. */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/swagger-ui.html",
            "/v3/api-docs",
            "/actuator/health",
            "/"
    );

    @Value("${einvoiceguard.api-key.header-name}")
    private String apiKeyHeaderName;

    @Value("${EINVOICEGUARD_API_KEYS:}")
    private String rawApiKeys;

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (isPublicPath(request.getRequestURI()) || isPreflight(request)) {
            chain.doFilter(request, response);
            return;
        }

        Set<String> validKeys = parseValidKeys();

        // Aucune cle configuree : comportement permissif en local/dev, comme RapidApiProxyFilter.
        if (validKeys.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        String providedKey = request.getHeader(apiKeyHeaderName);
        if (providedKey == null || providedKey.isBlank() || !validKeys.contains(providedKey)) {
            log.warn("Requete rejetee : cle API manquante ou invalide sur {} {}",
                    request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"error\":\"UNAUTHORIZED\",\"message\":\"Cle API manquante ou invalide. "
                            + "Fournissez une cle valide dans l'en-tete " + apiKeyHeaderName + ".\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isPublicPath(String uri) {
        return PUBLIC_PATHS.contains(uri) || uri.startsWith("/swagger-ui/") || uri.startsWith("/v3/api-docs/");
    }

    private boolean isPreflight(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    private Set<String> parseValidKeys() {
        if (rawApiKeys == null || rawApiKeys.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(rawApiKeys.split(","))
                .map(String::trim)
                .filter(key -> !key.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
