package cl.catastrofescl.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Filtro global que agrega headers de seguridad a todas las respuestas del Gateway.
 *
 * Headers inyectados:
 * - Strict-Transport-Security (HSTS) — fuerza HTTPS en producción
 * - X-Content-Type-Options — previene MIME-sniffing
 * - X-Frame-Options — previene clickjacking
 * - X-XSS-Protection — protección XSS del navegador
 * - Referrer-Policy — controla qué info de referrer se envía
 * - Content-Security-Policy — restricción básica de fuentes de contenido
 * - Permissions-Policy — restringe APIs del navegador
 *
 * Orden: 3 (se ejecuta después de autenticación y rate limiting).
 */
@Component
@Slf4j
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            ServerHttpResponse response = exchange.getResponse();
            HttpHeaders headers = response.getHeaders();

            // HSTS — 1 año, incluir subdominios
            agregarSiAusente(headers,
                    "Strict-Transport-Security", "max-age=31536000; includeSubDomains");

            // Prevenir MIME type sniffing
            agregarSiAusente(headers,
                    "X-Content-Type-Options", "nosniff");

            // Prevenir clickjacking — solo permitir mismo origen
            agregarSiAusente(headers,
                    "X-Frame-Options", "DENY");

            // Protección XSS del navegador (legacy, pero sigue siendo útil)
            agregarSiAusente(headers,
                    "X-XSS-Protection", "1; mode=block");

            // Controlar información de referrer
            agregarSiAusente(headers,
                    "Referrer-Policy", "strict-origin-when-cross-origin");

            // Content Security Policy básica
            agregarSiAusente(headers,
                    "Content-Security-Policy", "default-src 'self'");

            // Permissions Policy — restringir APIs del navegador
            agregarSiAusente(headers,
                    "Permissions-Policy",
                    "camera=(), microphone=(), geolocation=(self), payment=()");
        }));
    }

    /**
     * Agrega un header solo si no existe previamente en la respuesta.
     * Evita sobreescribir headers que el microservicio downstream ya haya definido.
     */
    private void agregarSiAusente(HttpHeaders headers, String name, String value) {
        if (!headers.containsKey(name)) {
            headers.add(name, value);
        }
    }

    @Override
    public int getOrder() {
        return 3; // Después de auth y rate limiting
    }
}
