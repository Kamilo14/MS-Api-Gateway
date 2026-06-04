package cl.catastrofescl.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Cuando Firebase está desactivado (desarrollo local / compose sin credenciales),
 * no intentar validar Bearer; deja pasar el tráfico (rutas públicas y protegidas)
 * sin inyectar X-Firebase-Uid. El microservicio downstream debe aplicar su propia
 * política (p. ej. modo dev con X-Dev-* o sin auth).
 */
@Component
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "false")
public class FirebaseDisabledPassthroughFilter implements GlobalFilter, Ordered {

    private static final String HEADER_FIREBASE_UID = "X-Firebase-Uid";

    /**
     * Opcional: sin Firebase, inyecta UID para ms-emergencies (modo dev + trust gateway).
     * Nunca usar en producción.
     */
    @Value("${gateway.dev.impersonate-firebase-uid:}")
    private String impersonateFirebaseUid;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange ex = exchange;
        if (StringUtils.hasText(impersonateFirebaseUid)) {
            String existing = exchange.getRequest().getHeaders().getFirst(HEADER_FIREBASE_UID);
            if (!StringUtils.hasText(existing)) {
                ex = exchange.mutate()
                        .request(exchange.getRequest().mutate()
                                .header(HEADER_FIREBASE_UID, impersonateFirebaseUid)
                                .build())
                        .build();
            }
        }
        return chain.filter(ex);
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
