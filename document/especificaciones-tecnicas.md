# Especificaciones Técnicas — CatástrofesCL

> Plataforma de Gestión de Recursos para Catástrofes
> Versión: 1.0 | Estado: Draft | Fecha: 2026-04-19

---

## 1. Contexto y Propósito del Sistema

CatástrofesCL es un centro de comando digital diseñado para coordinar en tiempo real la gestión de recursos humanitarios durante catástrofes naturales en Chile (terremotos, tsunamis, incendios, inundaciones, erupciones volcánicas, aluviones). Conecta organismos oficiales (SENAPRED, municipalidades, ONGs, bomberos) con ciudadanos colaboradores mediante coordinación inteligente de inventarios, transferencias logísticas y donaciones guiadas.

---

## 2. Stack Tecnológico Definitivo

### 2.1 Frontend

| Tecnología             | Versión | Rol                                          |
| ----------------------- | -------- | -------------------------------------------- |
| Next.js (App Router)    | 14+      | Framework principal SSR/CSR                  |
| React                   | 18+      | Librería UI                                 |
| TypeScript              | 5+       | Tipado estático                             |
| TanStack Query          | v5       | Cache + hidratación tiempo real             |
| Leaflet + React Leaflet | Latest   | Mapas interactivos (OpenStreetMap)           |
| shadcn/ui + Radix UI    | Latest   | Componentes accesibles (WCAG 2.1)            |
| Tailwind CSS            | 3+       | Estilos utilitarios                          |
| React Hook Form + Zod   | Latest   | Formularios con validación internacional    |
| Recharts                | Latest   | Gráficos del dashboard                      |
| Axios                   | Latest   | Cliente HTTP con interceptores JWT           |
| STOMP.js + SockJS       | Latest   | WebSocket para notificaciones en tiempo real |
| Vercel                  | -        | Hosting + CDN global + SSL automático       |

### 2.2 Backend (Microservicios)

| Tecnología                         | Versión | Rol                                     |
| ----------------------------------- | -------- | --------------------------------------- |
| Java                                | 21 LTS   | Lenguaje principal                      |
| Spring Boot                         | 3.2+     | Framework de microservicios             |
| Spring Security                     | 6+       | Seguridad, roles, CORS, CSRF            |
| Spring Data JPA + Hibernate Spatial | Latest   | ORM + soporte PostGIS                   |
| Spring AMQP                         | Latest   | Integración RabbitMQ                   |
| Spring Cache + Redis                | Latest   | Cache con @Cacheable                    |
| SpringDoc OpenAPI                   | Latest   | Documentación Swagger UI auto-generada |
| Maven                               | 3.9+     | Gestión de dependencias y build        |
| Flyway                              | Latest   | Migraciones de base de datos            |
| Lombok                              |          | Para reducir codigo repetitivo          |

### 2.3 Autenticación y Autorización

| Tecnología                          | Rol                                                        |
| ------------------------------------ | ---------------------------------------------------------- |
| Firebase Authentication              | Proveedor de identidad (JWT emisor)                        |
| Spring Security (Firebase Admin SDK) | Validación de Firebase JWT en cada microservicio          |
| RBAC (5 roles)                       | Administrador, Autoridad, Operador, Particular, Voluntario |

**Flujo de autenticación Firebase:**

1. El usuario se autentica en el frontend vía Firebase Auth (email/password, Google, etc.)
2. Firebase emite un ID Token (JWT firmado por Google)
3. El frontend incluye el token en el header `Authorization: Bearer <token>`
4. El API Gateway valida el token contra Firebase Admin SDK
5. Spring Security extrae el UID y custom claims (roles) para aplicar RBAC

### 2.4 Infraestructura y Orquestación

| Tecnología             | Rol                                                                       |
| ----------------------- | ------------------------------------------------------------------------- |
| Docker                  | Contenedorización de todos los servicios                                 |
| Amazon EKS (Kubernetes) | Orquestación, escalado automático, self-healing                         |
| PostgreSQL 15 + PostGIS | BD relacional + geoespacial —**contenedor Docker** en EKS (no RDS) |
| Redis                   | Cache de consultas frecuentes —**contenedor Docker** en EKS        |
| Amazon S3               | Almacenamiento de archivos (QR, reportes, assets)                         |
| AWS Lambda              | Procesamiento serverless asíncrono (envío de correos)                   |
| RabbitMQ                | Message broker asíncrono —**contenedor Docker** en EKS            |
| **ms-gateway**    | API Gateway propio —**microservicio Spring Cloud Gateway** en EKS  |
| GitHub Actions          | CI/CD pipeline automatizado                                               |

> PostgreSQL+PostGIS, Redis y RabbitMQ se ejecutan como contenedores Docker dentro del cluster EKS, igual que los demás microservicios. No se usan servicios administrados de AWS (RDS, ElastiCache) para estos componentes.

---

## 3. Arquitectura de Microservicios

### 3.1 Los 6 Microservicios

#### MS-1: Identidad y Acceso

- **Responsabilidad:** Gestión de usuarios, perfiles, roles, organizaciones oficiales.
- **Integración Firebase:** Usa Firebase Admin SDK para verificar tokens. Los custom claims de Firebase almacenan los roles asignados (array).
- **APIs críticas:**
  - `POST /auth/register` — Registro unificado: crea usuario en Firebase Auth y lo sincroniza en BD local, asigna rol REGISTRADO (sin privilegios iniciales)
  - `POST /auth/firebase/sync` — Sincroniza en BD local un usuario ya autenticado en Firebase (flujo frontend con `idToken`)
  - `POST /auth/firebase/sync/system` — Sincroniza usuario desde trigger de Firebase Auth usando secret de integración (`X-Sync-Secret`)
  - `GET /usuarios/yo` — Perfil completo: datos + roles + permisos + preferencias
  - `POST /usuarios/:id/roles` — Asignar rol (requiere rol `ADMINISTRADOR`)
  - `DELETE /usuarios/:id/roles/:rolId` — Quitar rol (requiere rol `ADMINISTRADOR`)
  - `GET /usuarios` — Listado paginado (requiere rol `ADMINISTRADOR`)
  - `PATCH /usuarios/:id/estado` — Activar/suspender usuario
  - `POST /auth/invitacion/operador` — Generar token de invitación para operador
  - `POST /auth/invitacion/aceptar` — Aceptar invitación con token

#### MS-2: Coordinación de Emergencias

- **Responsabilidad:** Ciclo de vida de emergencias + anuncios críticos.
- **Eventos publicados:** `emergency.created`, `emergency.status.changed`, `announcement.published`
- **APIs críticas:**
  - `POST /emergencias` — Declara emergencia con tipo, severidad, región, coordenadas
  - `PATCH /emergencias/:id/estado` — Transición de estados (Activa → Controlada → Finalizada)
  - `GET /emergencias/activas` — Listado con datos geoespaciales (público)
  - `POST /anuncios` — Publica anuncio (informativo | importante | urgente | emergencia)
  - `GET /anuncios` — Anuncios activos (público sin autenticación)

#### MS-3: Operaciones de Recursos

- **Responsabilidad:** Centros de acopio, inventario, criticidad automática, sobrestock, redistribución.
- **Umbrales:** Configurables POR centro de acopio, asignables SOLO por el Administrador.
- **Clasificación de stock:** Agotado → Crítico → Normal → Abundante → Sobrestock
- **Eventos publicados:** `stock.critical`, `stock.updated`, `inventory.movement.registered`
- **APIs críticas:**
  - `POST /centros` — Crear centro con geolocalización y capacidad
  - `GET /centros/cercanos` — Centros cercanos (PostGIS ST_DWithin)
  - `GET /centros/datos-mapa` — Datos para visualización en mapa (cacheado Redis TTL 60s)
  - `POST /centros/:id/inventario/movimientos` — Registrar movimiento con trazabilidad completa
  - `GET /inventario/sugerencias` — Sugerencias de redistribución (prioridad + proximidad)
  - `PATCH /centros/:id/umbrales` — Configurar umbrales (solo Administrador)

#### MS-4: Participación Ciudadana

- **Responsabilidad:** Necesidades públicas + donaciones ciudadanas.
- **Eventos publicados:** `donation.created`, `need.created`
- **APIs críticas:**
  - `GET /necesidades/publicas` — Necesidades visibles sin autenticación
  - `POST /donaciones` — Registrar donación + generar QR único
  - `POST /donaciones/:codigoQr/confirmar` — Operador escanea QR al recibir
  - `GET /donaciones/mis-contribuciones` — Historial del ciudadano autenticado

#### MS-5: Logística

- **Responsabilidad:** Transferencias entre centros, misiones de despacho, matching de voluntarios.
- **Integración OSRM:** Para matching de voluntarios se usa OSRM para calcular rutas viales reales (no distancia euclidiana).
- **Eventos publicados:** `transfer.created`, `mission.assigned`
- **APIs críticas:**
  - `POST /transferencias` — Solicitar transferencia (valida stock origen)
  - `PATCH /transferencias/:id/estado` — Flujo: Solicitada → Aprobada → En Tránsito → Recibida
  - `POST /misiones` — Crear misión de despacho
  - `POST /rutas-voluntario` — Voluntario ofrece ruta con origen, destino y capacidad
  - `GET /rutas-voluntario/matching/:misionId` — Matching por ruta OSRM + capacidad vehículo

#### MS-6: Notificaciones

- **Responsabilidad:** Notificaciones in-app, push y email en respuesta a eventos de dominio.
- **Consumidor de:** todos los eventos de dominio publicados por los demás microservicios.
- **APIs críticas:**
  - `GET /notificaciones` — Listado de notificaciones del usuario autenticado
  - `PATCH /notificaciones/:id/leer` — Marcar como leída
  - `GET /notificaciones/preferencias` — Preferencias del usuario
  - `PATCH /notificaciones/preferencias` — Actualizar preferencias
  - WebSocket STOMP: canal `/topic/notificaciones/{usuarioId}` para push en tiempo real

---

## 4. Estándares de Código y Arquitectura

### 4.0 Convención de Nomenclatura — Reglas por Capa

#### Backend — Clases arquitectónicas en inglés, entidades en español

| Capa | Idioma | Ejemplos |
|---|---|---|
| Controller | Inglés | `UsuarioController`, `EmergenciaController`, `DonacionController` |
| Service | Inglés | `UsuarioService`, `PermisosService`, `InventarioService`, `MatchingOSRMService` |
| Repository | Inglés | `UsuarioRepository`, `CentroRepository`, `DonacionRepository` |
| Filter/Config | Inglés | `FirebaseTokenFilter`, `SecurityConfig`, `RabbitMQConfig` |
| Exception | Inglés | `InsufficientStockException`, `UnauthorizedRoleChangeException` |
| Event | Inglés | `StockCriticalEvent`, `DonationConfirmedEvent`, `TransferCreatedEvent` |
| Entity (JPA) | **Español** | `Usuario`, `Centro`, `Donacion`, `Emergencia`, `RutaVoluntario` |
| Enum de dominio | **Español** | `EstadoEmergencia`, `EstadoCriticidad`, `TipoMovimiento`, `TipoDocumento` |
| Métodos de negocio | **Español** | `obtenerPermisos()`, `calcularCriticidad()`, `asignarRol()`, `registrarMovimiento()` |
| Rutas de API | **Español** | `/usuarios/yo`, `/centros/cercanos`, `/donaciones/:codigoQr/confirmar` |
| Paquetes Java | Inglés | `controller/`, `service/`, `repository/`, `entity/`, `event/`, `exception/` |
| Migraciones Flyway | Inglés | `V1__create_users_table.sql`, `V2__create_roles_permissions_tables.sql` |

> **Razón:** Las entidades JPA y sus campos mapean directamente a las tablas de BD (definidas en español). Las clases de infraestructura (Controller, Service, Repository, Filter) siguen las convenciones estándar de Spring Boot/Java.

#### Frontend — Todo en inglés (estándar React/Next.js)

```
Componentes:  UserList, DonationForm, EmergencyMap, InventoryDashboard
Hooks:        useUsers, useCenters, useNotifications, useInventory
Schemas Zod:  donorSchema, loginSchema, transferSchema
Tipos TS:     User, Center, Donation, Notification
Funciones:    fetchUsers(), confirmDonation(), assignVolunteer()
```

### 4.1 Arquitectura en Capas (Backend)

```
Controller → Service → Repository → Entity
     ↓
  DTO (Request/Response)
     ↓
@ControllerAdvice (Manejo global de errores)
```

### 4.2 Manejo de Errores — RFC 7807 (Problem Details)

Todos los microservicios devuelven errores en formato estandarizado RFC 7807:

```json
{
  "type": "https://catastrofescl.cl/errors/stock-insuficiente",
  "title": "Stock Insuficiente",
  "status": 409,
  "detail": "El centro origen no tiene stock suficiente del ítem solicitado.",
  "instance": "/transfers/abc123",
  "errorCode": "STOCK_INSUFFICIENT",
  "centerId": "uuid-centro",
  "itemId": "uuid-item"
}
```

**Códigos de error específicos para emergencias:**

- `EMERGENCY_NOT_ACTIVE` — Operación sobre emergencia no activa
- `STOCK_INSUFFICIENT` — Stock origen insuficiente para transferencia
- `CENTER_SATURATED` — Centro destino en estado saturado
- `TRANSFER_INVALID_STATE` — Transición de estado no permitida
- `VOLUNTEER_UNAVAILABLE` — Sin voluntarios compatibles para misión
- `QR_ALREADY_CONFIRMED` — QR de donación ya fue confirmado
- `UNAUTHORIZED_THRESHOLD_CHANGE` — Solo Administrador puede cambiar umbrales

### 4.3 Seguridad

- Passwords: BCrypt strength 12 (solo usuarios legacy; con Firebase Auth los passwords los gestiona Google)
- JWT: Firebase ID Tokens (expiración ~1h) + Firebase Refresh Tokens
- RBAC: custom claims en Firebase + validación en Spring Security
- CORS: configurado por entorno (dev: localhost, prod: dominio Vercel)
- Rate limiting: Bucket4j o Resilience4j por IP
- Validación de inputs: Jakarta Bean Validation (@Valid, @NotNull, @Size)
- SQL Injection: prevenida con Spring Data JPA + queries parametrizadas
- Headers de seguridad: configurados por Spring Security (HSTS, X-Frame-Options, etc.)
- Secrets: gestionados con Kubernetes Secrets, nunca en código fuente

---

## 5. Mensajería con RabbitMQ

### 5.1 Modelo de Exchanges y Colas

Se utiliza un modelo híbrido con tres conceptos combinados:

**Topic Exchange** — Enrutamiento flexible por patrones:

```
exchange: catastrofescl.events (Topic Exchange)
routing keys:
  - stock.critical
  - stock.updated
  - inventory.movement.*
  - donation.created
  - transfer.created
  - transfer.status.*
  - mission.assigned
  - emergency.created
  - emergency.status.*
  - announcement.published
```

**Dead Letter Queue (DLQ)** — Para mensajes que fallan después de N reintentos:

```
exchange: catastrofescl.dlx (Direct Exchange)
colas DLQ:
  - notifications.dlq
  - email.dlq
  - logistics.dlq
```

Configuración por cola: `x-dead-letter-exchange`, `x-message-ttl`, `x-max-retries`

**Idempotencia** — Cada evento incluye un `eventId` UUID. Los consumidores almacenan los IDs procesados en Redis (TTL: 24h) para descartar duplicados:

```java
// En cada consumidor:
if (redisTemplate.hasKey("processed:" + event.getEventId())) return;
// ... procesar evento ...
redisTemplate.opsForValue().set("processed:" + event.getEventId(), "1", Duration.ofHours(24));
```

### 5.2 Colas por Dominio

| Cola                    | Consumidor                 | Eventos                                                              |
| ----------------------- | -------------------------- | -------------------------------------------------------------------- |
| `notifications.queue` | MS Notificaciones          | Todos los eventos de dominio                                         |
| `email.queue`         | AWS Lambda                 | donation.created, stock.critical, transfer.created, mission.assigned |
| `logistics.queue`     | MS Logística              | stock.updated, transfer.status.*                                     |
| `resources.queue`     | MS Operaciones de Recursos | donation.created (actualizar inventario)                             |

---

## 6. Lógica Geoespacial

### 6.1 PostGIS — Consultas Nativas

```sql
-- Centros cercanos al usuario (radio 10km)
SELECT * FROM centros
WHERE ST_DWithin(coordenadas::geography, ST_MakePoint(:lng, :lat)::geography, 10000);

-- Distancia entre dos centros para sugerencias de redistribución
SELECT ST_Distance(c1.coordenadas::geography, c2.coordenadas::geography) as distancia_metros
FROM centros c1, centros c2
WHERE c1.id = :centroOrigen AND c2.id = :centroDestino;

-- Centros dentro de una zona de emergencia (polígono)
SELECT * FROM centros
WHERE ST_Within(coordenadas, :zonaPoligono);
```

Índices GIST obligatorios en columnas `coordenadas` y `zona_impacto`.

### 6.2 OSRM — Rutas Viales para Matching de Voluntarios

Para el matching de voluntarios de transporte NO se usa distancia euclidiana. Se integra OSRM (Open Source Routing Machine) con datos de OpenStreetMap para calcular rutas viales reales:

```
GET http://router.project-osrm.org/route/v1/driving/{lng_origen},{lat_origen};{lng_destino},{lat_destino}
```

El matching evalúa:

1. Ruta del voluntario compatible con origen del centro y destino de la zona de emergencia
2. Capacidad del vehículo suficiente para la carga de la misión
3. Distancia vial (no euclidiana) para ordenar candidatos
4. Voluntario en estado disponible

**Opción de producción:** Self-hosting OSRM en instancia EC2 para eliminar límites del demo server.

### 6.3 Umbrales de Inventario — Configuración por Centro

Los umbrales de criticidad son ESPECÍFICOS por centro de acopio e ítem, configurables EXCLUSIVAMENTE por el rol Administrador:

```
AGOTADO: stock = 0 (genera necesidad con prioridad CRÍTICO)
CRÍTICO: 0 < stock ≤ stock_mínimo (genera necesidad con prioridad ALTO)
NORMAL: stock_mínimo < stock ≤ stock_óptimo
ABUNDANTE: stock_óptimo < stock ≤ stock_máximo
SOBRESTOCK: stock > stock_máximo (genera sugerencia de redistribución)
```

Justificación: Las zonas de catástrofe tienen necesidades heterogéneas. Un terremoto costero requiere umbrales distintos a una inundación en zona cordillerana.

---

## 7. Frontend — Estrategia de Datos

### 7.1 TanStack Query v5 con Hidratación

Se usa hidratación del servidor (SSR → Client) para evitar recargas de página en información en tiempo real:

```typescript
// server component — prefetch inicial
const queryClient = new QueryClient();
await queryClient.prefetchQuery({
  queryKey: ['centers', 'map'],
  queryFn: () => fetchMapData(),
});
// Pasar al client component vía HydrationBoundary

// client component — revalidación automática sin recargar página
const { data: inventory } = useQuery({
  queryKey: ['inventory', centerId],
  queryFn: () => fetchInventory(centerId),
  refetchInterval: 30_000, // fallback polling si WebSocket cae
  staleTime: 10_000,
});
```

### 7.2 Validación Internacional con Zod

Los formularios soportan usuarios chilenos Y extranjeros (cooperación internacional):

```typescript
const donorSchema = z.object({
  // RUT chileno O documento extranjero
  documentType: z.enum(['RUT', 'PASSPORT', 'DNI', 'OTHER']),
  documentNumber: z.string().min(5).max(20),

  // Teléfono internacional (E.164 recomendado)
  phone: z.string()
    .regex(/^\+?[1-9]\d{6,14}$/, 'Número inválido. Ej: +56912345678 o +541112345678')
    .optional(),

  // Email universal
  email: z.string().email(),

  // Nombre sin restricciones de caracteres (nombres extranjeros)
  firstName: z.string().min(1).max(100),
  lastName: z.string().min(1).max(100),

  // País (ISO 3166-1 alpha-2)
  country: z.string().length(2).default('CL'),

  // Región y comuna (opcionales para extranjeros)
  region: z.string().optional(),
  commune: z.string().optional(),
});
```

---

## 8. Base de Datos

### 8.1 PostgreSQL 15 + PostGIS

Base de datos relacional centralizada desplegada como **contenedor Docker** dentro del cluster EKS (no se usa AWS RDS). En local se levanta con Docker Compose. La imagen oficial es `postgis/postgis:15-3.4`.

Todos los microservicios apuntan a la misma instancia `catastrofescl_db`. Cada microservicio accede únicamente a las tablas de su dominio.

**Migraciones:** Flyway con scripts versionados `V{n}__{descripcion}.sql` por microservicio.

**Persistencia en EKS:** Volumen persistente (PersistentVolumeClaim) montado en el pod de PostgreSQL para garantizar que los datos sobrevivan reinicios de pods.

### 8.2 Redis

Cache de consultas frecuentes desplegado como **contenedor Docker** dentro del cluster EKS (no se usa AWS ElastiCache). En local se levanta con Docker Compose usando la imagen `redis:7-alpine`.

TTL por tipo de dato:

- Datos del mapa público: TTL 60s
- KPIs del dashboard: TTL 30s
- Catálogo de ítems: TTL 300s
- Permisos por usuario: TTL 5min
- Idempotencia de eventos RabbitMQ: TTL 24h

---

## 9. Requisitos No Funcionales Críticos

| Atributo                  | Requisito                                                           |
| ------------------------- | ------------------------------------------------------------------- |
| Disponibilidad            | 99.9% — EKS self-healing + volúmenes persistentes PostgreSQL      |
| Tiempo de respuesta API   | < 500ms en percentil 95 bajo carga normal                           |
| Consultas geoespaciales   | Optimizadas con índices GIST + paginación obligatoria             |
| Escalabilidad             | Escalado horizontal independiente por microservicio                 |
| Resiliencia mensajería   | DLQ + retry policies en RabbitMQ                                    |
| Fallback tiempo real      | WebSocket → polling TanStack Query si cae conexión                |
| Integridad transaccional  | @Transactional en transferencias (operación atómica)              |
| Seguridad datos sensibles | TLS en tránsito, AES-256 en reposo, BCrypt strength 12             |
| Accesibilidad             | WCAG 2.1 AA (Radix UI + WAI-ARIA nativos)                           |
| Mobile-first              | Portal ciudadano optimizado para smartphone y conexiones inestables |

---

## 10. APIs y Servicios Externos Gratuitos

| Servicio                   | Uso                           | Límite                               |
| -------------------------- | ----------------------------- | ------------------------------------- |
| OpenStreetMap              | Tiles de mapa para Leaflet    | Sin límite razonable                 |
| Nominatim                  | Geocoding / reverse geocoding | 1 req/seg (self-hosteable)            |
| OSRM (demo server)         | Cálculo de rutas viales      | Uso moderado (self-hosteable en prod) |
| Firebase Auth (Spark plan) | Autenticación                | 10.000 usuarios/mes gratis            |
| AWS SES                    | Emails transaccionales        | 62.000 emails/mes gratis desde EC2    |
| Sentry (Free tier)         | Monitoreo de errores          | 5.000 eventos/mes                     |

---

## 11. Evaluación Ética del Diseño

### Green Computing

- Escalado dinámico con EKS: pods escalan solo cuando hay demanda real
- AWS Lambda: consume recursos únicamente durante ejecución
- Redis + CDN Vercel: reducen recálculos innecesarios y transferencia de datos
- Docker: alta densidad de servicios con menor huella de hardware

### Privacy by Design

- PII (RUT, email, teléfono) cifrado en tránsito (TLS) y en reposo (AES-256)
- Principio de mínimo privilegio: RBAC estricto por rol
- Auditoría inmutable: tabla `audit_log` con timestamp + usuario + snapshot
- Kubernetes Secrets: credenciales nunca en código fuente

### Equidad y Transparencia Algorítmica

- Algoritmo de redistribución: reglas explícitas (prioridad de necesidad + distancia vial OSRM)
- Umbrales configurables por humanos (Administrador), no por algoritmos opacos
- Donación guiada por necesidad real del terreno, no visibilidad mediática

### Inclusión y Accesibilidad

- Componentes shadcn/ui sobre Radix UI primitives (WAI-ARIA nativo)
- Mobile-first para portal ciudadano (conexiones inestables en emergencias)
- Wizard de donación simplificado para baja alfabetización digital
- Formularios internacionales (documentos y teléfonos extranjeros)
- Alertas multicanal: in-app + push + email

---

## 12. Modelo Entidad-Relación — 3FN (Normalizado)

Todas las tablas están en **Tercera Forma Normal (3FN)**: sin dependencias transitivas, sin grupos repetitivos y con claves primarias UUID.
Las columnas geoespaciales usan tipo `GEOGRAPHY` de PostGIS. Las columnas `jsonb` almacenan metadata flexible sin romper la normalización.

---

### Dominio: Identidad y Acceso (`ms-identity`)

**`usuarios`**

| Columna               | Tipo         | Restricción               | Descripción                        |
| --------------------- | ------------ | -------------------------- | ----------------------------------- |
| `id`                | uuid         | PK                         | Identificador interno               |
| `firebase_uid`      | varchar(128) | UK, NOT NULL               | UID emitido por Firebase Auth       |
| `correo`            | varchar(255) | UK, NOT NULL               | Correo electrónico                 |
| `nombres`           | varchar(100) | NOT NULL                   | Nombre(s) del usuario               |
| `apellidos`         | varchar(100) | NOT NULL                   | Apellido(s) del usuario             |
| `tipo_documento`    | varchar(20)  | NOT NULL                   | RUT, PASAPORTE, DNI, OTRO           |
| `numero_documento`  | varchar(30)  |                            | Número de documento                |
| `telefono`          | varchar(20)  |                            | Formato E.164 (+56912345678)        |
| `pais`              | char(2)      | NOT NULL, DEFAULT 'CL'     | ISO 3166-1 alpha-2                  |
| `region`            | varchar(100) |                            | Región (opcional para extranjeros) |
| `comuna`            | varchar(100) |                            | Comuna (opcional para extranjeros)  |
| `estado`            | varchar(20)  | NOT NULL, DEFAULT 'ACTIVO' | ACTIVO, SUSPENDIDO, PENDIENTE       |
| `correo_verificado` | boolean      | NOT NULL, DEFAULT false    | Verificación Firebase              |
| `creado_en`         | timestamptz  | NOT NULL, DEFAULT now()    |                                     |
| `actualizado_en`    | timestamptz  | NOT NULL                   |                                     |

> El campo `rol` fue eliminado de `usuarios`. Los roles se gestionan en la tabla `roles` con relación N:M vía `usuarios_roles`.

**`roles`**

| Columna         | Tipo         | Restricción           | Descripción                                               |
| --------------- | ------------ | ---------------------- | ---------------------------------------------------------- |
| `id`          | uuid         | PK                     |                                                            |
| `nombre`      | varchar(50)  | UK, NOT NULL           | ADMINISTRADOR, AUTORIDAD, OPERADOR, PARTICULAR, VOLUNTARIO |
| `descripcion` | varchar(200) |                        | Descripción legible del rol                               |
| `activo`      | boolean      | NOT NULL, DEFAULT true |                                                            |

> Los 5 roles se insertan como datos iniciales (seed) en la migración Flyway. En el MS Identity (etapa actual), la autorización se aplica en Spring Security con `@PreAuthorize` por rol.

**`usuarios_roles`** *(relación N:M — un usuario puede tener múltiples roles)*

| Columna                     | Tipo        | Restricción            | Descripción             |
| --------------------------- | ----------- | ----------------------- | ------------------------ |
| `usuario_id`              | uuid        | FK → usuarios.id       |                          |
| `rol_id`                  | uuid        | FK → roles.id          |                          |
| `asignado_en`             | timestamptz | NOT NULL, DEFAULT now() |                          |
| `asignado_por_usuario_id` | uuid        | FK → usuarios.id       | Admin que asignó el rol |
| PK                          |             | (usuario_id, rol_id)    | Clave compuesta          |

**`permisos`** *(catálogo de permisos granulares del sistema)*

| Columna         | Tipo         | Restricción           | Descripción                                                            |
| --------------- | ------------ | ---------------------- | ----------------------------------------------------------------------- |
| `id`          | uuid         | PK                     |                                                                         |
| `codigo`      | varchar(100) | UK, NOT NULL           | Ej:`CENTRO_CREAR`, `INVENTARIO_EDITAR`, `EMERGENCIA_DECLARAR`     |
| `descripcion` | varchar(200) | NOT NULL               | Descripción legible para la UI                                         |
| `modulo`      | varchar(50)  | NOT NULL               | IDENTIDAD, EMERGENCIAS, RECURSOS, CIUDADANIA, LOGISTICA, NOTIFICACIONES |
| `activo`      | boolean      | NOT NULL, DEFAULT true |                                                                         |

> Los permisos se insertan como seed en Flyway. Desde la UI de administración se pueden activar/desactivar y reasignar entre roles sin tocar código.

**`roles_permisos`** *(asignación N:M de permisos a roles)*

| Columna         | Tipo        | Restricción            | Descripción    |
| --------------- | ----------- | ----------------------- | --------------- |
| `rol_id`      | uuid        | FK → roles.id          |                 |
| `permiso_id`  | uuid        | FK → permisos.id       |                 |
| `asignado_en` | timestamptz | NOT NULL, DEFAULT now() |                 |
| PK              |             | (rol_id, permiso_id)    | Clave compuesta |

**Permisos iniciales por módulo (seed):**

| Código                     | Módulo     | Roles iniciales                    |
| --------------------------- | ----------- | ---------------------------------- |
| `USUARIO_GESTIONAR`       | IDENTIDAD   | ADMINISTRADOR                      |
| `ROL_ASIGNAR`             | IDENTIDAD   | ADMINISTRADOR                      |
| `CENTRO_CREAR`            | RECURSOS    | ADMINISTRADOR, AUTORIDAD           |
| `CENTRO_EDITAR`           | RECURSOS    | ADMINISTRADOR, AUTORIDAD, OPERADOR |
| `INVENTARIO_EDITAR`       | RECURSOS    | ADMINISTRADOR, AUTORIDAD, OPERADOR |
| `UMBRAL_CONFIGURAR`       | RECURSOS    | ADMINISTRADOR                      |
| `EMERGENCIA_DECLARAR`     | EMERGENCIAS | ADMINISTRADOR, AUTORIDAD           |
| `EMERGENCIA_GESTIONAR`    | EMERGENCIAS | ADMINISTRADOR, AUTORIDAD           |
| `ANUNCIO_PUBLICAR`        | EMERGENCIAS | ADMINISTRADOR, AUTORIDAD           |
| `DONACION_CONFIRMAR`      | CIUDADANIA  | ADMINISTRADOR, AUTORIDAD, OPERADOR |
| `NECESIDAD_GESTIONAR`     | CIUDADANIA  | ADMINISTRADOR, AUTORIDAD, OPERADOR |
| `TRANSFERENCIA_SOLICITAR` | LOGISTICA   | ADMINISTRADOR, AUTORIDAD           |
| `TRANSFERENCIA_APROBAR`   | LOGISTICA   | ADMINISTRADOR, AUTORIDAD           |
| `MISION_CREAR`            | LOGISTICA   | ADMINISTRADOR, AUTORIDAD           |
| `RUTA_OFRECER`            | LOGISTICA   | VOLUNTARIO                         |
| `DONACION_REALIZAR`       | CIUDADANIA  | PARTICULAR, VOLUNTARIO             |

**Integración Spring Security + BD:**

```java
// FirebaseTokenFilter extrae roles del custom claim (array)
// Spring Security evalúa permisos cargados desde BD al iniciar sesión
// Se cachean en Redis con TTL 5 minutos para evitar consultas repetidas

@PreAuthorize("hasAuthority('UMBRAL_CONFIGURAR')")
public void configurarUmbrales(...) { ... }

// Los custom claims de Firebase almacenan el array de roles:
// { "roles": ["AUTORIDAD", "VOLUNTARIO"] }
// Los permisos se resuelven en runtime desde BD (cacheados en Redis)
```

> **Regla:** Cualquier combinación de roles es válida. No existen restricciones entre roles. Un usuario puede ser ADMINISTRADOR + VOLUNTARIO si el negocio lo requiere.

**`organizaciones`**

| Columna        | Tipo         | Restricción            | Descripción                                 |
| -------------- | ------------ | ----------------------- | -------------------------------------------- |
| `id`         | uuid         | PK                      |                                              |
| `nombre`     | varchar(200) | NOT NULL                | Nombre de la organización                   |
| `tipo`       | varchar(50)  | NOT NULL                | SENAPRED, MUNICIPALIDAD, ONG, BOMBEROS, OTRO |
| `region`     | varchar(100) |                         |                                              |
| `verificada` | boolean      | NOT NULL, DEFAULT false |                                              |
| `creada_en`  | timestamptz  | NOT NULL, DEFAULT now() |                                              |

**`usuarios_organizaciones`** *(tabla de unión N:M)*

| Columna             | Tipo        | Restricción            |
| ------------------- | ----------- | ----------------------- |
| `usuario_id`      | uuid        | FK → usuarios.id       |
| `organizacion_id` | uuid        | FK → organizaciones.id |
| `asignado_en`     | timestamptz | NOT NULL                |

**`solicitudes_rol`**

| Columna            | Tipo        | Restricción      | Descripción                   |
| ------------------ | ----------- | ----------------- | ------------------------------ |
| `id`             | uuid        | PK                |                                |
| `usuario_id`     | uuid        | FK → usuarios.id |                                |
| `rol_solicitado` | varchar(30) | NOT NULL          |                                |
| `estado`         | varchar(20) | NOT NULL          | PENDIENTE, APROBADA, RECHAZADA |
| `justificacion`  | text        |                   |                                |
| `solicitado_en`  | timestamptz | NOT NULL          |                                |
| `resuelto_en`    | timestamptz |                   |                                |

**`invitaciones_operador`**

| Columna                  | Tipo         | Restricción      | Descripción                  |
| ------------------------ | ------------ | ----------------- | ----------------------------- |
| `id`                   | uuid         | PK                |                               |
| `usuario_autoridad_id` | uuid         | FK → usuarios.id | Quien invita                  |
| `correo`               | varchar(255) | NOT NULL          |                               |
| `token`                | varchar(255) | UK, NOT NULL      | Token único de invitación   |
| `estado`               | varchar(20)  | NOT NULL          | PENDIENTE, ACEPTADA, EXPIRADA |
| `expira_en`            | timestamptz  | NOT NULL          |                               |
| `creada_en`            | timestamptz  | NOT NULL          |                               |

---

### Dominio: Emergencias y Anuncios (`ms-emergencies`)

**`emergencias`**

| Columna                      | Tipo               | Restricción      | Descripción                                                |
| ---------------------------- | ------------------ | ----------------- | ----------------------------------------------------------- |
| `id`                       | uuid               | PK                |                                                             |
| `tipo`                     | varchar(50)        | NOT NULL          | TERREMOTO, TSUNAMI, INCENDIO, INUNDACION, ERUPCION, ALUVION |
| `severidad`                | varchar(20)        | NOT NULL          | BAJA, MEDIA, ALTA, CATASTROFICA                             |
| `region`                   | varchar(100)       | NOT NULL          |                                                             |
| `estado`                   | varchar(20)        | NOT NULL          | ACTIVA, CONTROLADA, FINALIZADA                              |
| `coordenadas_epicentro`    | geography(POINT)   |                   | Índice GIST obligatorio                                    |
| `zona_impacto`             | geography(POLYGON) |                   | Índice GIST obligatorio                                    |
| `declarada_por_usuario_id` | uuid               | FK → usuarios.id |                                                             |
| `declarada_en`             | timestamptz        | NOT NULL          |                                                             |
| `actualizada_en`           | timestamptz        | NOT NULL          |                                                             |

**`anuncios`**

| Columna              | Tipo         | Restricción         | Descripción                                 |
| -------------------- | ------------ | -------------------- | -------------------------------------------- |
| `id`               | uuid         | PK                   |                                              |
| `emergencia_id`    | uuid         | FK → emergencias.id |                                              |
| `autor_usuario_id` | uuid         | FK → usuarios.id    |                                              |
| `titulo`           | varchar(200) | NOT NULL             |                                              |
| `contenido`        | text         | NOT NULL             |                                              |
| `severidad`        | varchar(20)  | NOT NULL             | INFORMATIVO, IMPORTANTE, URGENTE, EMERGENCIA |
| `alcance`          | varchar(20)  | NOT NULL             | NACIONAL, REGIONAL, COMUNAL                  |
| `region`           | varchar(100) |                      | Aplica si alcance = REGIONAL o COMUNAL       |
| `vigente_desde`    | timestamptz  | NOT NULL             |                                              |
| `vigente_hasta`    | timestamptz  |                      | null = sin expiración                       |
| `creado_en`        | timestamptz  | NOT NULL             |                                              |

---

### Dominio: Operaciones de Recursos (`ms-resources`)

**`centros`**

| Columna                   | Tipo             | Restricción      | Descripción                              |
| ------------------------- | ---------------- | ----------------- | ----------------------------------------- |
| `id`                    | uuid             | PK                |                                           |
| `nombre`                | varchar(200)     | NOT NULL          |                                           |
| `direccion`             | varchar(300)     | NOT NULL          |                                           |
| `coordenadas`           | geography(POINT) | NOT NULL          | Índice GIST obligatorio                  |
| `region`                | varchar(100)     | NOT NULL          |                                           |
| `comuna`                | varchar(100)     | NOT NULL          |                                           |
| `capacidad`             | integer          | NOT NULL          | Capacidad estimada en kg o unidades       |
| `horario`               | varchar(200)     |                   | Ej: "Lun-Vie 8:00-18:00"                  |
| `estado`                | varchar(20)      | NOT NULL          | ACTIVO, SATURADO, CERRADO, EN_PREPARACION |
| `creado_por_usuario_id` | uuid             | FK → usuarios.id |                                           |
| `creado_en`             | timestamptz      | NOT NULL          |                                           |
| `actualizado_en`        | timestamptz      | NOT NULL          |                                           |

**`operadores_centro`** *(tabla de unión N:M)*

| Columna         | Tipo        | Restricción      |
| --------------- | ----------- | ----------------- |
| `centro_id`   | uuid        | FK → centros.id  |
| `usuario_id`  | uuid        | FK → usuarios.id |
| `asignado_en` | timestamptz | NOT NULL          |

**`catalogo_items`**

| Columna                  | Tipo         | Restricción            | Descripción                                       |
| ------------------------ | ------------ | ----------------------- | -------------------------------------------------- |
| `id`                   | uuid         | PK                      |                                                    |
| `nombre`               | varchar(200) | NOT NULL                | Ej: "Agua mineral 500ml"                           |
| `categoria`            | varchar(100) | NOT NULL                | ALIMENTOS, ROPA, MEDICAMENTOS, HERRAMIENTAS, OTROS |
| `unidad`               | varchar(30)  | NOT NULL                | KG, LITROS, UNIDADES, CAJAS                        |
| `es_global`            | boolean      | NOT NULL, DEFAULT false | true = catálogo compartido                        |
| `creado_por_centro_id` | uuid         | FK → centros.id        | null si es_global = true                           |
| `creado_en`            | timestamptz  | NOT NULL                |                                                    |

**`inventario`**

| Columna               | Tipo        | Restricción            | Descripción                                    |
| --------------------- | ----------- | ----------------------- | ----------------------------------------------- |
| `id`                | uuid        | PK                      |                                                 |
| `centro_id`         | uuid        | FK → centros.id        |                                                 |
| `item_id`           | uuid        | FK → catalogo_items.id |                                                 |
| `stock_actual`      | integer     | NOT NULL, DEFAULT 0     |                                                 |
| `umbral_minimo`     | integer     | NOT NULL                | Solo modificable por ADMINISTRADOR              |
| `umbral_optimo`     | integer     | NOT NULL                |                                                 |
| `umbral_maximo`     | integer     | NOT NULL                |                                                 |
| `estado_criticidad` | varchar(20) | NOT NULL                | AGOTADO, CRITICO, NORMAL, ABUNDANTE, SOBRESTOCK |
| `actualizado_en`    | timestamptz | NOT NULL                |                                                 |
| UNIQUE                |             | (centro_id, item_id)    | Un registro por centro+ítem                    |

**`movimientos_inventario`**

| Columna             | Tipo        | Restricción        | Descripción                                                                             |
| ------------------- | ----------- | ------------------- | ---------------------------------------------------------------------------------------- |
| `id`              | uuid        | PK                  |                                                                                          |
| `inventario_id`   | uuid        | FK → inventario.id |                                                                                          |
| `usuario_id`      | uuid        | FK → usuarios.id   | Responsable del movimiento                                                               |
| `tipo_movimiento` | varchar(30) | NOT NULL            | DONACION, TRANSFERENCIA_RECIBIDA, TRANSFERENCIA_ENVIADA, COMPRA, DESPACHO, MERMA, AJUSTE |
| `cantidad`        | integer     | NOT NULL            | Positivo = ingreso, negativo = egreso                                                    |
| `stock_anterior`  | integer     | NOT NULL            |                                                                                          |
| `stock_posterior` | integer     | NOT NULL            |                                                                                          |
| `tipo_referencia` | varchar(30) |                     | DONACION, TRANSFERENCIA, MISION                                                          |
| `referencia_id`   | uuid        |                     | ID del objeto origen del movimiento                                                      |
| `creado_en`       | timestamptz | NOT NULL            |                                                                                          |

**`registro_auditoria`**

| Columna                | Tipo        | Restricción      | Descripción               |
| ---------------------- | ----------- | ----------------- | -------------------------- |
| `id`                 | uuid        | PK                |                            |
| `usuario_id`         | uuid        | FK → usuarios.id |                            |
| `tipo_entidad`       | varchar(50) | NOT NULL          | Ej: "Centro", "Inventario" |
| `entidad_id`         | uuid        | NOT NULL          |                            |
| `accion`             | varchar(30) | NOT NULL          | CREAR, MODIFICAR, ELIMINAR |
| `snapshot_anterior`  | jsonb       |                   | Estado antes del cambio    |
| `snapshot_posterior` | jsonb       |                   | Estado después del cambio |
| `creado_en`          | timestamptz | NOT NULL          |                            |

---

### Dominio: Participación Ciudadana (`ms-citizen`)

**`necesidades`**

| Columna                | Tipo        | Restricción            | Descripción                            |
| ---------------------- | ----------- | ----------------------- | --------------------------------------- |
| `id`                 | uuid        | PK                      |                                         |
| `centro_id`          | uuid        | FK → centros.id        |                                         |
| `item_id`            | uuid        | FK → catalogo_items.id |                                         |
| `emergencia_id`      | uuid        | FK → emergencias.id    | Puede ser null                          |
| `cantidad_necesaria` | integer     | NOT NULL                |                                         |
| `prioridad`          | varchar(20) | NOT NULL                | BAJO, MEDIO, ALTO, CRITICO              |
| `origen`             | varchar(20) | NOT NULL                | MANUAL, AUTOMATICO                      |
| `estado`             | varchar(20) | NOT NULL                | ACTIVA, PARCIALMENTE_CUBIERTA, RESUELTA |
| `creada_en`          | timestamptz | NOT NULL                |                                         |
| `resuelta_en`        | timestamptz |                         |                                         |

**`donaciones`**

| Columna                       | Tipo         | Restricción      | Descripción                        |
| ----------------------------- | ------------ | ----------------- | ----------------------------------- |
| `id`                        | uuid         | PK                |                                     |
| `centro_id`                 | uuid         | FK → centros.id  | Centro receptor                     |
| `usuario_donante_id`        | uuid         | FK → usuarios.id | Puede ser null (donación anónima) |
| `codigo_qr`                 | varchar(255) | UK, NOT NULL      | UUID único generado al crear       |
| `estado`                    | varchar(20)  | NOT NULL          | PENDIENTE, CONFIRMADA, CANCELADA    |
| `donado_en`                 | timestamptz  | NOT NULL          |                                     |
| `confirmado_en`             | timestamptz  |                   |                                     |
| `confirmado_por_usuario_id` | uuid         | FK → usuarios.id | Operador que escanea el QR          |

**`items_donacion`**

| Columna         | Tipo    | Restricción            | Descripción |
| --------------- | ------- | ----------------------- | ------------ |
| `id`          | uuid    | PK                      |              |
| `donacion_id` | uuid    | FK → donaciones.id     |              |
| `item_id`     | uuid    | FK → catalogo_items.id |              |
| `cantidad`    | integer | NOT NULL                |              |

---

### Dominio: Logística (`ms-logistics`)

**`transferencias`**

| Columna                       | Tipo        | Restricción      | Descripción                                           |
| ----------------------------- | ----------- | ----------------- | ------------------------------------------------------ |
| `id`                        | uuid        | PK                |                                                        |
| `centro_origen_id`          | uuid        | FK → centros.id  |                                                        |
| `centro_destino_id`         | uuid        | FK → centros.id  |                                                        |
| `solicitado_por_usuario_id` | uuid        | FK → usuarios.id |                                                        |
| `aprobado_por_usuario_id`   | uuid        | FK → usuarios.id |                                                        |
| `estado`                    | varchar(20) | NOT NULL          | SOLICITADA, APROBADA, EN_TRANSITO, RECIBIDA, RECHAZADA |
| `notas`                     | text        |                   |                                                        |
| `solicitada_en`             | timestamptz | NOT NULL          |                                                        |
| `aprobada_en`               | timestamptz |                   |                                                        |
| `recibida_en`               | timestamptz |                   |                                                        |

**`items_transferencia`**

| Columna              | Tipo    | Restricción            | Descripción |
| -------------------- | ------- | ----------------------- | ------------ |
| `id`               | uuid    | PK                      |              |
| `transferencia_id` | uuid    | FK → transferencias.id |              |
| `item_id`          | uuid    | FK → catalogo_items.id |              |
| `cantidad`         | integer | NOT NULL                |              |

**`misiones`**

| Columna                   | Tipo        | Restricción         | Descripción                                         |
| ------------------------- | ----------- | -------------------- | ---------------------------------------------------- |
| `id`                    | uuid        | PK                   |                                                      |
| `centro_origen_id`      | uuid        | FK → centros.id     |                                                      |
| `emergencia_id`         | uuid        | FK → emergencias.id |                                                      |
| `creada_por_usuario_id` | uuid        | FK → usuarios.id    |                                                      |
| `estado`                | varchar(20) | NOT NULL             | PENDIENTE, ASIGNADA, EN_CURSO, COMPLETADA, CANCELADA |
| `descripcion_carga`     | jsonb       |                      | Detalle de ítems transportados                      |
| `programada_en`         | timestamptz |                      |                                                      |
| `completada_en`         | timestamptz |                      |                                                      |

**`rutas_voluntario`**

| Columna                 | Tipo             | Restricción           | Descripción                    |
| ----------------------- | ---------------- | ---------------------- | ------------------------------- |
| `id`                  | uuid             | PK                     |                                 |
| `usuario_id`          | uuid             | FK → usuarios.id      |                                 |
| `coordenadas_origen`  | geography(POINT) | NOT NULL               | Índice GIST obligatorio        |
| `etiqueta_origen`     | varchar(200)     | NOT NULL               | Ej: "Viña del Mar centro"      |
| `coordenadas_destino` | geography(POINT) | NOT NULL               | Índice GIST obligatorio        |
| `etiqueta_destino`    | varchar(200)     | NOT NULL               |                                 |
| `tipo_vehiculo`       | varchar(50)      | NOT NULL               | AUTO, CAMIONETA, CAMION, FURGON |
| `capacidad_kg`        | integer          | NOT NULL               |                                 |
| `disponible`          | boolean          | NOT NULL, DEFAULT true |                                 |
| `creada_en`           | timestamptz      | NOT NULL               |                                 |

**`voluntarios_mision`**

| Columna                | Tipo        | Restricción                    | Descripción                   |
| ---------------------- | ----------- | ------------------------------- | ------------------------------ |
| `id`                 | uuid        | PK                              |                                |
| `mision_id`          | uuid        | FK → misiones.id               |                                |
| `ruta_voluntario_id` | uuid        | FK → rutas_voluntario.id       | Una ruta por misión           |
| `estado`             | varchar(20) | NOT NULL                        | PENDIENTE, ACEPTADA, RECHAZADA |
| `asignado_en`        | timestamptz | NOT NULL                        |                                |
| `respondido_en`      | timestamptz |                                 |                                |
| UNIQUE                 |             | (mision_id, ruta_voluntario_id) |                                |

---

### Dominio: Notificaciones (`ms-notifications`)

**`notificaciones`**

| Columna        | Tipo         | Restricción            | Descripción                                                                                 |
| -------------- | ------------ | ----------------------- | -------------------------------------------------------------------------------------------- |
| `id`         | uuid         | PK                      |                                                                                              |
| `usuario_id` | uuid         | FK → usuarios.id       | Destinatario                                                                                 |
| `tipo`       | varchar(50)  | NOT NULL                | STOCK_CRITICO, DONACION_CONFIRMADA, TRANSFERENCIA_CREADA, MISION_ASIGNADA, ANUNCIO_PUBLICADO |
| `titulo`     | varchar(200) | NOT NULL                |                                                                                              |
| `cuerpo`     | text         | NOT NULL                |                                                                                              |
| `canal`      | varchar(20)  | NOT NULL                | IN_APP, PUSH, EMAIL                                                                          |
| `leida`      | boolean      | NOT NULL, DEFAULT false |                                                                                              |
| `metadata`   | jsonb        |                         | Datos extra del evento origen                                                                |
| `creada_en`  | timestamptz  | NOT NULL                |                                                                                              |
| `leida_en`   | timestamptz  |                         |                                                                                              |

**`preferencias_notificacion`**

| Columna         | Tipo        | Restricción              | Descripción |
| --------------- | ----------- | ------------------------- | ------------ |
| `id`          | uuid        | PK                        |              |
| `usuario_id`  | uuid        | FK → usuarios.id         |              |
| `tipo_evento` | varchar(50) | NOT NULL                  |              |
| `in_app`      | boolean     | NOT NULL, DEFAULT true    |              |
| `push`        | boolean     | NOT NULL, DEFAULT true    |              |
| `correo`      | boolean     | NOT NULL, DEFAULT false   |              |
| UNIQUE          |             | (usuario_id, tipo_evento) |              |

**`eventos_procesados`**

| Columna          | Tipo        | Restricción | Descripción                        |
| ---------------- | ----------- | ------------ | ----------------------------------- |
| `evento_id`    | uuid        | PK           | ID único del evento RabbitMQ       |
| `consumidor`   | varchar(50) | NOT NULL     | Nombre del microservicio consumidor |
| `procesado_en` | timestamptz | NOT NULL     |                                     |
