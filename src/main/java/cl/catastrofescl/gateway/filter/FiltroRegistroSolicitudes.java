package cl.catastrofescl.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Traza cada solicitud que entra al gateway (diagnóstico all-in-one ECS).
 */
@Component
@Slf4j
public class FiltroRegistroSolicitudes implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String metodo = request.getMethod().name();
        String ruta = request.getPath().value();
        String origen = request.getHeaders().getFirst("Origin");

        log.info("Solicitud gateway {} {} origen={}", metodo, ruta, origen != null ? origen : "-");

        return chain.filter(exchange).doOnSuccess(v -> {
            int status = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value()
                    : 0;
            if (status >= 400) {
                log.warn("Respuesta gateway {} {} status={}", metodo, ruta, status);
            }
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
