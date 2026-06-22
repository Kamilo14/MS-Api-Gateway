package cl.catastrofescl.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Configuración centralizada de CORS (Cross-Origin Resource Sharing) para el Gateway.
 *
 * Define qué orígenes, métodos y headers pueden acceder a los endpoints del gateway.
 * Se aplica globalmente a todas las rutas (/**).
 *
 * Ambiente:
 * - LOCAL: localhost:3000, localhost:3001, localhost:5173, localhost:5174
 * - STAGING/PROD: *.vercel.app
 *
 * Activación: Controlada por `gateway.cors.enabled` (default: true)
 */
@Configuration
@Slf4j
@ConditionalOnProperty(name = "gateway.cors.enabled", havingValue = "true", matchIfMissing = true)
public class CorsConfig {

    /**
     * Fuente de configuración CORS reactiva para Spring Cloud Gateway.
     *
     * Nota: Spring Cloud Gateway ya tiene soporte nativo en application.yml
     * (spring.cloud.gateway.globalcors), pero este bean permite:
     * - Validaciones programáticas más complejas
     * - Reutilización en tests
     * - Mayor documentación del flujo CORS
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfig = new CorsConfiguration();

        // Orígenes permitidos (patrones para dev local: localhost, 127.0.0.1, IP de red)
        List<String> allowedOriginPatterns = Arrays.asList(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "http://[::1]:*",
                "http://172.*.*.*:*",
                "http://192.168.*.*:*",
                "http://10.*.*.*:*",
                "https://*.vercel.app"
        );
        corsConfig.setAllowedOriginPatterns(allowedOriginPatterns);
        log.info("CORS: Patrones de origen permitidos: {}", allowedOriginPatterns);

        // Métodos HTTP permitidos
        corsConfig.setAllowedMethods(Arrays.asList(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        // Headers que el cliente puede enviar
        corsConfig.setAllowedHeaders(Arrays.asList(
                "Authorization",         // Para tokens Firebase (Bearer)
                "Content-Type",
                "X-Requested-With",      // Para AJAX
                "Accept",
                "X-Sync-Secret"          // Para sincronización técnica de usuarios
        ));

        // Headers que exponemos desde la respuesta hacia el cliente
        corsConfig.setExposedHeaders(Arrays.asList(
                "X-Rate-Limit-Remaining",  // Límite de rate limiting restante
                "X-Rate-Limit-Reset",
                "X-Response-Time"
        ));

        // Permitir credenciales (cookies, autorización)
        corsConfig.setAllowCredentials(true);

        // Duración máxima en segundos que el navegador cachea la preflight response
        corsConfig.setMaxAge(3600L); // 1 hora

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        log.debug("CORS configurado: métodos={}, headers permitidos={}, credenciales=true, maxAge=3600s",
                corsConfig.getAllowedMethods(),
                corsConfig.getAllowedHeaders());

        return source;
    }
}
