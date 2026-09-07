package com.einvoiceguard.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Verifie que la requete provient bien du proxy RapidAPI, pas d'un appel direct
 * qui contournerait la facturation et les quotas. RapidAPI transmet un secret
 * partage dans le header configure ci-dessous (voir doc "Adding APIs" de RapidAPI).
 *
 * A configurer dans le tableau de bord provider RapidAPI :
 * Security > "Deploy your API" > "Add a secret header".
 */
@Component
public class RapidApiProxyFilter extends HttpFilter {

    @Value("${einvoiceguard.rapidapi.proxy-secret-header}")
    private String proxySecretHeaderName;

    @Value("${EINVOICEGUARD_PROXY_SECRET:}")
    private String expectedSecret;

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        // En local/dev, si aucun secret n'est configure, on laisse passer (pratique pour les tests).
        if (expectedSecret == null || expectedSecret.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        String providedSecret = request.getHeader(proxySecretHeaderName);
        if (!expectedSecret.equals(providedSecret)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"FORBIDDEN\",\"message\":\"Appel direct non autorise, passez par RapidAPI.\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
