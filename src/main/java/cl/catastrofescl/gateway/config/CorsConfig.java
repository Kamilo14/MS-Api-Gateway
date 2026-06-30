package cl.catastrofescl.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CORS del gateway. Debe incluir el origen público del frontend en ECS
 * ({@code FRONTEND_ORIGIN}), porque Next.js reenvía el header {@code Origin} al proxy interno.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gateway.cors.enabled", havingValue = "true", matchIfMissing = true)
public class CorsConfig {

    private final GatewaySecurityProperties gatewayProperties;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfig = new CorsConfiguration();

        List<String> allowedOriginPatterns = new ArrayList<>(Arrays.asList(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "http://[::1]:*",
                "http://172.*.*.*:*",
                "http://192.168.*.*:*",
                "http://10.*.*.*:*",
                "https://*.vercel.app"
        ));

        GatewaySecurityProperties.Cors corsProps = gatewayProperties.getCors();
        if (StringUtils.hasText(corsProps.getFrontendOrigin())) {
            allowedOriginPatterns.add(corsProps.getFrontendOrigin().trim());
        }
        if (StringUtils.hasText(corsProps.getExtraOriginPatternsCsv())) {
            Arrays.stream(corsProps.getExtraOriginPatternsCsv().split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .forEach(allowedOriginPatterns::add);
        }

        corsConfig.setAllowedOriginPatterns(allowedOriginPatterns);
        log.info("CORS gateway: origenes permitidos={}", allowedOriginPatterns);

        corsConfig.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));
        corsConfig.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "X-Sync-Secret",
                "X-Dev-Roles",
                "X-Firebase-Uid",
                "X-Firebase-Email"
        ));
        corsConfig.setExposedHeaders(Arrays.asList(
                "X-Rate-Limit-Remaining",
                "X-Rate-Limit-Reset",
                "X-Response-Time"
        ));
        corsConfig.setAllowCredentials(true);
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        return source;
    }
}
