package cl.catastrofescl.gateway.filter;

import cl.catastrofescl.gateway.config.GatewaySecurityProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filtro global de rate limiting por IP usando Bucket4j.
 *
 * Configuración: gateway.rate-limit.requests-per-minute (default: 100).
 * Almacena buckets en memoria (ConcurrentHashMap) por IP del cliente.
 * Limpieza periódica no implementada en esta versión (los buckets se
 * auto-regulan por refill y el mapa crece acotado en entorno local).
 *
 * Orden: 2 (se ejecuta después de la autenticación Firebase).
 */
@Component
@Slf4j
public class RateLimitingFilter implements GlobalFilter, Ordered {

    private final Map<String, Bucket> bucketCache = new ConcurrentHashMap<>();
    private final int requestsPerMinute;

    public RateLimitingFilter(GatewaySecurityProperties properties) {
        this.requestsPerMinute = properties.getRateLimit().getRequestsPerMinute();
        log.info("Rate limiting configurado: {} requests/minuto por IP", requestsPerMinute);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientIp = obtenerIpCliente(exchange);
        Bucket bucket = bucketCache.computeIfAbsent(clientIp, this::crearBucket);

        if (bucket.tryConsume(1)) {
            // Agregar headers informativos de rate limit
            exchange.getResponse().getHeaders().add("X-Rate-Limit-Remaining",
                    String.valueOf(bucket.getAvailableTokens()));
            return chain.filter(exchange);
        }

        log.warn("Rate limit excedido para IP: {}", clientIp);
        return responderRateLimitExcedido(exchange, clientIp);
    }

    /**
     * Crea un bucket con la capacidad configurada y refill gradual por minuto.
     */
    private Bucket crearBucket(String ip) {
        Bandwidth limit = Bandwidth.classic(
                requestsPerMinute,
                Refill.greedy(requestsPerMinute, Duration.ofMinutes(1))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Obtiene la IP real del cliente, considerando headers de proxy (X-Forwarded-For).
     */
    private String obtenerIpCliente(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // Tomar la primera IP (cliente original)
            return xForwardedFor.split(",")[0].trim();
        }

        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        return "unknown";
    }

    /**
     * Responde con 429 Too Many Requests en formato RFC 7807.
     */
    private Mono<Void> responderRateLimitExcedido(ServerWebExchange exchange, String clientIp) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().add("Retry-After", "60");

        String body = String.format("""
                {
                  "type": "https://catastrofescl.cl/errors/rate-limit-excedido",
                  "title": "Límite de Solicitudes Excedido",
                  "status": 429,
                  "detail": "Se ha excedido el límite de %d solicitudes por minuto. Intente nuevamente en un momento.",
                  "instance": "%s",
                  "errorCode": "RATE_LIMIT_EXCEDIDO",
                  "timestamp": "%s"
                }""",
                requestsPerMinute,
                exchange.getRequest().getPath().value(),
                Instant.now().toString());

        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return 2; // Después de autenticación Firebase
    }
}
