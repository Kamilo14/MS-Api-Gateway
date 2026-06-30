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
    private Cors cors = new Cors();

    @Data
    public static class Cors {
        /**
         * Origen público del frontend (p. ej. http://54.x.x.x:3000 en ECS).
         * Next.js reenvía el header Origin al gateway; si no está permitido, CORS responde 403 vacío
         * y la petición nunca llega al microservicio downstream.
         */
        private String frontendOrigin = "http://localhost:3000";
        /**
         * Patrones extra (CSV) vía {@code GATEWAY_CORS_EXTRA_ORIGIN_PATTERNS}.
         */
        private String extraOriginPatternsCsv = "";
    }

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
