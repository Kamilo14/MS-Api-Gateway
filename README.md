# MS Api Gateway — CatástrofesCL

> **API Gateway — Punto de entrada único para la plataforma de gestión de recursos humanitarios durante catástrofes**

[![Java](https://img.shields.io/badge/Java-21%20LTS-orange)](https://www.oracle.com/java/technologies/javase/jdk21-archive.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.14-green)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-Gateway-blue)](https://spring.io/projects/spring-cloud-gateway)
[![License](https://img.shields.io/badge/License-MIT-purple)](LICENSE)

## Descripción

`ms-gateway` es el microservicio de infraestructura que actúa como **punto de entrada único** para todas las peticiones del frontend. Responsabilidades:

- 🔐 **Validación de tokens Firebase** — autorización centralizada
- 🚦 **Rate limiting por IP** — control de tráfico (100 req/min por defecto)
- 🛣️ **Enrutamiento dinámico** — distribuye peticiones a los 6 microservicios de negocio
- 🛡️ **Headers de seguridad** — HSTS, X-Frame-Options, X-Content-Type-Options
- 🌐 **CORS global** — configurado para Vercel + localhost

## Requisitos

- **Java 21 LTS** (OpenJDK o Eclipse Temurin)
- **Maven 3.9+**
- **PostgreSQL 15 + PostGIS** (en máquina host, `localhost:5432`) ⭐ **OBLIGATORIO**
  - Esta es una **BD local única y compartida** por todos los microservicios del sistema.
- **Redis 7** (opcional, en máquina host `localhost:6379`)
- **Docker** (opcional, para ejecutar el gateway en contenedor)
- **Firebase Admin SDK** credenciales (`serviceAccountKey.json`)

## Quick Start (Desarrollo Local)

### 0. Pre-requisito: PostgreSQL local

**Asegúrate de que PostgreSQL 15 está corriendo en `localhost:5432`** antes de cualquier paso.

```bash
# Windows (PowerShell)
# Si usas pgAdmin o servicios instalados, asegúrate de que están activos

# macOS (si usas Homebrew)
brew services start postgresql

# Linux
sudo service postgresql start
# o
sudo systemctl start postgresql

# Verificar que está corriendo
psql -U catastrofescl -d catastrofescl_db -c "SELECT version();"
```

Si PostgreSQL no está instalado, descárgalo desde [postgresql.org](https://www.postgresql.org/download/).

### 1. Clonar el repositorio

```bash
git clone https://github.com/catastrofescl/ms-gateway.git
cd ms-gateway
```

### 2. Configurar variables de entorno

```bash
# Copiar .env.example a .env
cp .env.example .env

# Editar .env con tus valores
export FIREBASE_ENABLED=true
export FIREBASE_PROJECT_ID=tu-proyecto-firebase
export FIREBASE_CREDENTIALS_PATH=/ruta/a/serviceAccountKey.json
export SPRING_PROFILES_ACTIVE=local
```

### 3. Ejecutar en máquina host

```bash
# Compilar
./mvnw clean package

# Ejecutar
./mvnw spring-boot:run
```

El gateway estará disponible en **`http://localhost:8080`**.

### 4. Ejecutar en Docker (opción A: sin orquestación)

```bash
# Build imagen
docker build -t catastrofescl-gateway:latest .

# Run con host network (acceso directo a localhost)
docker run \
  --network host \
  -e FIREBASE_ENABLED=true \
  -e FIREBASE_PROJECT_ID=tu-proyecto-firebase \
  -v /ruta/a/serviceAccountKey.json:/etc/firebase/serviceAccountKey.json:ro \
  catastrofescl-gateway:latest
```

Consulta [DOCKER.md](DOCKER.md) para detalles completos sobre contenerización (host network, bridge, EKS).

## Estructura del Proyecto

```
ms-gateway/
├── src/
│   ├── main/
│   │   ├── java/cl/catastrofescl/gateway/
│   │   │   ├── GatewayApplication.java          ← punto de entrada
│   │   │   ├── config/
│   │   │   │   ├── FirebaseConfig.java
│   │   │   │   └── GatewaySecurityProperties.java
│   │   │   ├── filter/
│   │   │   │   ├── FirebaseAuthenticationFilter.java
│   │   │   │   ├── RateLimitingFilter.java
│   │   │   │   └── SecurityHeadersFilter.java
│   │   │   ├── exception/
│   │   │   │   └── GatewayExceptionHandler.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/                                     ← pruebas (futuro)
├── document/                                     ← documentación del proyecto
│   ├── CLAUDE.md                                 ← protocolo obligatorio
│   ├── plan-de-implementacion.md                 ← roadmap
│   ├── especificaciones-tecnicas.md              ← stack técnico
│   ├── errores.md                                ← registro de bugs
│   ├── arreglos-y-cambios.md                     ← decisiones técnicas
│   └── avances.md                                ← bitácora de progreso
├── Dockerfile                                    ← multi-stage para prod
├── .dockerignore
├── DOCKER.md                                     ← guía de contenerización
├── pom.xml                                       ← dependencias Maven
├── .env.example                                  ← variables de entorno
└── README.md                                     ← este archivo
```

## Configuración

### application.yml

```yaml
server:
  port: 8080  # Puerto de escucha

spring:
  cloud:
    gateway:
      routes:
        - id: ms-identity
          uri: http://localhost:8081  # ← ajustar según ambiente
          predicates:
            - Path=/auth/**, /usuarios/**
        # ... demás rutas

gateway:
  security:
    public-paths:
      - /auth/register
      - /auth/login
      - /emergencias/activas
      - /actuator/**
  rate-limit:
    requests-per-minute: 100

firebase:
  enabled: ${FIREBASE_ENABLED:true}
  credentials-path: ${FIREBASE_CREDENTIALS_PATH:}
  project-id: ${FIREBASE_PROJECT_ID:}

logging:
  level:
    cl.catastrofescl: DEBUG
```

### Variables de Entorno

| Variable | Descripción | Ejemplo | Obligatorio |
|---|---|---|---|
| `FIREBASE_ENABLED` | Activar validación Firebase | `true` | ✅ |
| `FIREBASE_PROJECT_ID` | Project ID en Firebase Console | `mi-proyecto-123` | ✅ |
| `FIREBASE_CREDENTIALS_PATH` | Ruta a serviceAccountKey.json | `/etc/firebase/key.json` | ✅ |
| `SPRING_PROFILES_ACTIVE` | Perfil de Spring Boot | `local`, `dev`, `prod` | ✅ |
| `JAVA_OPTS` | Opciones de JVM | `-Xmx512m -Xms256m` | ❌ |

## Endpoints

### Rutas públicas (sin autenticación Firebase)

```bash
# Registro de usuario
POST /auth/register
Content-Type: application/json
{
  "correo": "usuario@example.com",
  "password": "SecurePass123!",
  "nombre": "Juan",
  "apellido": "Pérez"
}

# Login
POST /auth/login
Content-Type: application/json
{
  "correo": "usuario@example.com",
  "password": "SecurePass123!"
}

# Emergencias activas (mapa público)
GET /emergencias/activas

# Health check
GET /actuator/health
```

### Rutas protegidas (requieren Bearer token Firebase)

```bash
# Listar usuarios (rol ADMINISTRADOR)
GET /usuarios
Authorization: Bearer <id_token>

# Crear centro de acopio
POST /centros
Authorization: Bearer <id_token>
Content-Type: application/json
{
  "nombre": "Centro Principal",
  "latitud": -33.8688,
  "longitud": -51.2093
}

# Crear necesidad ciudadana
POST /necesidades
Authorization: Bearer <id_token>
Content-Type: application/json
{
  "titulo": "Se necesita agua potable",
  "cantidad": 100,
  "unidad": "litros"
}
```

## Health Check

El gateway expone endpoints de salud en `/actuator/`:

```bash
# Liveness probe (¿está vivo?)
GET /actuator/health/liveness
Response: {"status":"UP"}

# Readiness probe (¿listo para recibir tráfico?)
GET /actuator/health/readiness
Response: {"status":"UP"}

# Métricas Prometheus
GET /actuator/prometheus
```

## Flujo de Autenticación

```
1. Frontend autentica en Firebase Auth (email, Google, etc.)
   ↓
2. Firebase emite ID Token (JWT) con custom claims (roles)
   ↓
3. Frontend incluye token en header: Authorization: Bearer <token>
   ↓
4. Gateway valida token con Firebase Admin SDK
   ↓
5. Si válido:
   - inyecta headers X-Firebase-Uid y X-Firebase-Email
   - enruta hacia microservicio correspondiente
   ↓
6. Si inválido:
   - responde 401 Unauthorized (RFC 7807)
```

## Estandarización de Errores

Todos los errores se responden en formato **RFC 7807 (Problem Details)**:

```json
{
  "type": "https://catastrofescl.cl/errors/token-invalido",
  "title": "Token de Autenticación Inválido",
  "status": 401,
  "detail": "El token Firebase proporcionado es inválido o ha expirado.",
  "instance": "/ruta/del/recurso",
  "errorCode": "TOKEN_INVALIDO",
  "timestamp": "2026-05-05T12:30:00Z"
}
```

## Testing

```bash
# Ejecutar pruebas unitarias
./mvnw test

# Ejecutar con cobertura
./mvnw test jacoco:report

# Ver reporte: target/site/jacoco/index.html
```

## Docker

> ⚠️ **IMPORTANTE:** PostgreSQL 15 debe estar corriendo en **`localhost:5432`** (máquina host), no en Docker.

### Build imagen (multi-stage)

```bash
docker build -t catastrofescl-gateway:latest .
```

### Opción A: Run con host network (más simple)

```bash
docker run \
  --network host \
  -e FIREBASE_ENABLED=true \
  -e FIREBASE_PROJECT_ID=tu-proyecto-firebase \
  -v /ruta/a/serviceAccountKey.json:/etc/firebase/serviceAccountKey.json:ro \
  catastrofescl-gateway:latest
```

El gateway accede a `localhost:5432` (PostgreSQL) y `localhost:808X` (otros MS) directamente.

### Opción B: Run con docker-compose

```bash
# Asegúrate de que PostgreSQL está corriendo localmente
docker-compose up -d

# Ver estado
docker-compose ps

# Logs del gateway
docker-compose logs -f gateway

# Detener
docker-compose down
```

Consulta [DOCKER.md](DOCKER.md) para detalles avanzados (bridge network, host.docker.internal, EKS).

## Debugging

### Ver logs en vivo

```bash
# Con mvnw
./mvnw spring-boot:run --debug

# Con docker-compose
docker-compose logs -f gateway

# Con Docker directo
docker logs -f <container-id>
```

### Endpoints de debug

```bash
# Verificar configuración cargada
curl -H "Authorization: Bearer test" http://localhost:8080/actuator/env | jq

# Verificar rutas configuradas
curl http://localhost:8080/actuator/gateway/routes | jq
```

## CI/CD

Este repositorio incluye GitHub Actions para:

- ✅ Build con Maven en cada push
- ✅ Verificación de tests
- ✅ Análisis de código con Sonar
- ✅ Build de imagen Docker
- ✅ Push a Amazon ECR (en main)
- ✅ Deploy a EKS (manual, Fase 9)

## Troubleshooting

### Gateway no se conecta a PostgreSQL

**Síntoma:** `org.postgresql.util.PSQLException: Connection refused`

**Solución:**
1. ✅ Verificar que **PostgreSQL está corriendo localmente**:
   ```bash
   # Windows: iniciar desde pgAdmin o servicios
   # macOS: brew services list | grep postgres
   # Linux: sudo systemctl status postgresql
   psql -U catastrofescl -d catastrofescl_db -c "SELECT 1"
   ```
2. Verificar que la BD `catastrofescl_db` existe
3. Verificar que el usuario `catastrofescl` tiene acceso
4. Si usas Docker, usar `host.docker.internal:5432` (Windows/Mac) o `localhost:5432` (Linux)

### Gateway no se conecta a otros microservicios

**Síntoma:** `500 Connection refused` al llamar a `/auth/**`, `/centros/**`, etc.

**Solución:**
1. Verificar que los microservicios estén corriendo en los puertos correctos (8081-8086)
2. Si usas `docker run --network host`, el gateway accede a `localhost:808X`
3. Si usas bridge network, usar `host.docker.internal:808X` (Windows/Mac) o `localhost:808X` (Linux)

### Firebase token inválido

**Síntoma:** `401 TOKEN_INVALIDO`

**Solución:**
1. Verificar que `FIREBASE_ENABLED=true`
2. Verificar que `serviceAccountKey.json` existe
3. Validar que el `FIREBASE_PROJECT_ID` coincida con el del archivo
4. Si usas Postman, asegúrate de que el Bearer token es un `idToken` válido (no refresh token)

### Health check falla en Docker

**Síntoma:** `docker ps` muestra `(unhealthy)`

**Solución:**
```bash
# Esperar más tiempo antes de fallar
docker update --health-start-period=20s <container-id>

# O verificar manualmente
docker exec <container-id> \
  wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health/liveness
```

## Referencias

- 📚 [Spring Cloud Gateway](https://spring.io/projects/spring-cloud-gateway)
- 🔐 [Firebase Admin SDK](https://firebase.google.com/docs/admin/setup)
- 🐳 [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)
- 📖 [RFC 7807 — Problem Details](https://tools.ietf.org/html/rfc7807)
- 🗺️ [CatástrofesCL — Especificaciones Técnicas](document/especificaciones-tecnicas.md)

## Contribuir

Ver [CLAUDE.md](document/CLAUDE.md) para protocolo de desarrollo y estándares de código.

## Licencia

MIT — Ver [LICENSE](LICENSE) para detalles.

---

**Proyecto:** CatástrofesCL  
**Componente:** API Gateway  
**Versión:** 0.0.1-SNAPSHOT  
**Mantenedor:** Equipo de Desarrollo (@catastrofescl)
