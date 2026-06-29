package cl.catastrofescl.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Propiedades personalizadas del Gateway.
 * Configuración de rutas públicas (sin autenticación Firebase)
 * y parámetros de rate limiting.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "gateway")
public class GatewaySecurityProperties {

    private Security security = new Security();
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class Security {
        /**
         * Rutas que no requieren token  (públicas).
         * Soporta patrones Ant: /auth/register, /emergencias/activas, /actuator/**
         */
        private List<String> publicPaths = new ArrayList<>();
    }

    @Data
    public static class RateLimit {
        /**
         * Máximo de requests por minuto por IP.
         */
        private int requestsPerMinute = 100;
    }
}
