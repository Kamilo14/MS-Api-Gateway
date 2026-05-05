package cl.catastrofescl.gateway.exception;

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

        if (ex instanceof ResponseStatusException responseStatusException) {
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
}
