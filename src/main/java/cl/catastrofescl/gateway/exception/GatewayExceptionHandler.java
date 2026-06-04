package cl.catastrofescl.gateway.exception;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Manejador global de excepciones para el API Gateway.
 * Intercepta errores (como 404 de rutas no encontradas o 503 de servicio no disponible)
 * y los formatea según el estándar RFC 7807 (Problem Details).
 */
@Component
@Order(-2) // Alta prioridad para capturar errores antes que el DefaultErrorWebExceptionHandler
@Slf4j
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String errorCode = "GATEWAY_ERROR_INTERNO";
        String detail = "Ha ocurrido un error interno en el API Gateway.";
        String title = "Error Interno del Gateway";

        if (isUpstreamUnreachable(ex)) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            errorCode = "SERVICIO_NO_DISPONIBLE";
            title = "Microservicio no responde";
            detail = "No se pudo conectar al microservicio de destino (¿está levantado y "
                    + "expone el puerto esperado?). Rutas /auth/** y /usuarios/** → ms-identity (8081); "
                    + "/emergencias/** → ms-emergencies (8082); /centros/** → ms-resources (8083).";
        } else if (isCircuitOpen(ex)) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            errorCode = "CIRCUIT_ABIERTO";
            title = "Servicio temporalmente no disponible";
            detail = "El circuit breaker está abierto: el microservicio falló repetidamente. "
                    + "Revise que el servicio upstream esté en marcha y vuelva a intentar.";
        } else if (ex instanceof ResponseStatusException responseStatusException) {
            status = HttpStatus.valueOf(responseStatusException.getStatusCode().value());
            if (status == HttpStatus.NOT_FOUND) {
                errorCode = "RUTA_NO_ENCONTRADA";
                title = "Ruta No Encontrada";
                detail = "El recurso solicitado no existe o no hay un microservicio que pueda atenderlo.";
            } else if (status == HttpStatus.SERVICE_UNAVAILABLE) {
                errorCode = "SERVICIO_NO_DISPONIBLE";
                title = "Servicio No Disponible";
                detail = "El microservicio de destino no está disponible en este momento.";
            } else {
                errorCode = "ERROR_HTTP_" + status.value();
                title = "Error HTTP " + status.value();
                detail = responseStatusException.getReason() != null ? responseStatusException.getReason() : ex.getMessage();
            }
        }

        log.error("Gateway error [{}]: {} - {}", errorCode, exchange.getRequest().getPath().value(), ex.getMessage());

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

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
                title,
                status.value(),
                detail,
                exchange.getRequest().getPath().value(),
                errorCode,
                Instant.now().toString());

        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));

        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * Conexión rechazada, host desconocido, timeouts de red típicos del Netty client del Gateway.
     */
    private static boolean isUpstreamUnreachable(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ConnectException
                    || t instanceof UnknownHostException
                    || t instanceof UnresolvedAddressException) {
                return true;
            }
            if (t instanceof java.net.NoRouteToHostException) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null) {
                String m = msg.toLowerCase();
                if (m.contains("connection refused")
                        || m.contains("connection reset")
                        || m.contains("actively refused")
                        || m.contains("no route to host")
                        || m.contains("failed to resolve")) {
                    return true;
                }
            }
            String cn = t.getClass().getName();
            if (cn.contains("ConnectTimeout")
                    || cn.contains("ReadTimeout")
                    || cn.contains("PrematureClose")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCircuitOpen(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof CallNotPermittedException) {
                return true;
            }
        }
        return false;
    }
}
