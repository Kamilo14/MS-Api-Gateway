package cl.catastrofescl.gateway.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Configuración de Firebase Admin SDK para el Gateway.
 * Inicializa la conexión con Firebase para validar tokens de autenticación.
 *
 * Se activa solo cuando firebase.enabled=true (por defecto: true).
 * En entorno local, se espera un archivo serviceAccountKey.json
 * referenciado por FIREBASE_CREDENTIALS_PATH.
 */
@Configuration
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class FirebaseConfig {

    @Value("${firebase.credentials-path:}")
    private String credentialsPath;

    @Value("${firebase.project-id:}")
    private String projectId;
//Inicializar Firebase 
    @PostConstruct
    public void inicializarFirebase() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseOptions.Builder builder = FirebaseOptions.builder();

            if (credentialsPath != null && !credentialsPath.isBlank()) {
                builder.setCredentials(GoogleCredentials.fromStream(
                        new FileInputStream(credentialsPath)));
                log.info("Firebase Admin SDK inicializado con credenciales desde: {}", credentialsPath);
            } else {
                builder.setCredentials(GoogleCredentials.getApplicationDefault());
                log.info("Firebase Admin SDK inicializado con Application Default Credentials (ADC)");
            }

            if (projectId != null && !projectId.isBlank()) {
                builder.setProjectId(projectId);
            }

            FirebaseApp.initializeApp(builder.build());
            log.info("Firebase Admin SDK listo para validar tokens en el Gateway");
        }
    }

    @Bean
    public FirebaseAuth firebaseAuth() {
        return FirebaseAuth.getInstance();
    }
}
