package cl.catastrofescl.gateway.filter;

import cl.catastrofescl.gateway.config.GatewaySecurityProperties;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Filtro global de autenticación Firebase para el Gateway.
 *
 * Flujo:
 * 1. Verifica si la ruta es pública (sin auth) → pasa directo.
 * 2. Extrae el Bearer token del header Authorization.
 * 3. Valida el token con Firebase Admin SDK (llamada bloqueante envuelta en Mono).
 * 4. Si es válido, inyecta headers X-Firebase-Uid y X-Firebase-Email al downstream.
 * 5. Si es inválido o ausente, responde 401 en formato RFC 7807.
 *
 * Orden: 1 (se ejecuta antes que rate limiting y headers de seguridad).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FirebaseAuthenticationFilter implements GlobalFilter, Ordered {

    private final FirebaseAuth firebaseAuth;
    private final GatewaySecurityProperties gatewayProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_FIREBASE_UID = "X-Firebase-Uid";
    private static final String HEADER_FIREBASE_EMAIL = "X-Firebase-Email";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String method = request.getMethod().name();

        // Permitir preflight CORS sin autenticación
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return chain.filter(exchange);
        }

        // Rutas públicas configuradas — sin validación de token
        if (esRutaPublica(path)) {
            log.debug("Ruta pública, sin autenticación requerida: {}", path);
            return chain.filter(exchange);
        }

        // Extraer Bearer token
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Token ausente o formato incorrecto para ruta protegida: {}", path);
            return responderError(exchange, HttpStatus.UNAUTHORIZED,
                    "TOKEN_AUSENTE",
                    "Se requiere un token Bearer válido de Firebase para acceder a este recurso.",
                    path);
        }

        String idToken = authHeader.substring(BEARER_PREFIX.length());

        // Validar token con Firebase (bloqueante → envuelto en Mono con scheduler elástico)
        return Mono.fromCallable(() -> firebaseAuth.verifyIdToken(idToken))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(decodedToken -> {
                    log.debug("Token válido — UID: {}, email: {}",
                            decodedToken.getUid(), decodedToken.getEmail());

                    // Inyectar identidad del usuario como headers hacia el microservicio downstream
                    ServerHttpRequest mutatedRequest = request.mutate()
                            .header(HEADER_FIREBASE_UID, decodedToken.getUid())
                            .header(HEADER_FIREBASE_EMAIL,
                                    decodedToken.getEmail() != null ? decodedToken.getEmail() : "")
                            .build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                })
                .onErrorResume(FirebaseAuthException.class, ex -> {
                    log.warn("Token Firebase inválido: {}", ex.getMessage());
                    return responderError(exchange, HttpStatus.UNAUTHORIZED,
                            "TOKEN_INVALIDO",
                            "El token Firebase proporcionado es inválido o ha expirado.",
                            path);
                })
                .onErrorResume(Exception.class, ex -> {
                    log.error("Error inesperado al validar token Firebase: {}", ex.getMessage(), ex);
                    return responderError(exchange, HttpStatus.INTERNAL_SERVER_ERROR,
                            "ERROR_AUTENTICACION",
                            "Error interno al validar las credenciales de autenticación.",
                            path);
                });
    }

    /**
     * Verifica si la ruta solicitada coincide con alguna ruta pública configurada.
     */
    private boolean esRutaPublica(String path) {
        return gatewayProperties.getSecurity().getPublicPaths().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * Genera una respuesta de error en formato RFC 7807 (Problem Details).
     */
    private Mono<Void> responderError(ServerWebExchange exchange, HttpStatus status,
                                       String errorCode, String detail, String instance) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        String body = String.format("""
                {
                  "type": "https://catastrofescl.cl/errors/%s",
                  "title": "%s",
                  "status": %d,
                  "detail": "%s",
                  "instance": "%s",
                  "errorCode": "%s",
                  "timestamp": "%s"
                }""",
                errorCode.toLowerCase().replace("_", "-"),
                obtenerTitulo(errorCode),
                status.value(),
                detail,
                instance,
                errorCode,
                Instant.now().toString());

        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private String obtenerTitulo(String errorCode) {
        return switch (errorCode) {
            case "TOKEN_AUSENTE" -> "Token de Autenticación Requerido";
            case "TOKEN_INVALIDO" -> "Token de Autenticación Inválido";
            case "ERROR_AUTENTICACION" -> "Error de Autenticación";
            default -> "Error de Gateway";
        };
    }

    @Override
    public int getOrder() {
        return 1; // Se ejecuta primero (antes de rate limiting)
    }
}
