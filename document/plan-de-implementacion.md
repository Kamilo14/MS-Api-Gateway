# Plan de Implementación — CatástrofesCL

> Plataforma de Gestión de Recursos para Catástrofes
> Versión: 1.2 | Metodología: Sprints semanales | GitHub Projects (Kanban)

## Contexto operativo actual (enfatizado)

**Durante esta etapa, la aplicación web se operará en local (localhost).**

- La prioridad de implementación y validación es entorno local end-to-end.
- Las decisiones de despliegue cloud/EKS/RDS se mantienen como ruta futura, no como foco de ejecución actual.

### Estrategia de sincronización Firebase Auth → BD por entorno

**Entorno local (activo y prioritario)**
- Flujo principal: `Frontend` registra/autentica en Firebase Auth y luego llama `POST /auth/firebase/sync` con `idToken`.
- Resultado esperado: usuario sincronizado en PostgreSQL local (`ms-identity`) inmediatamente después del registro/login.

**Entorno producción (ruta objetivo)**
- Flujo principal: trigger `auth.user().onCreate` (Cloud Function) invoca `POST /auth/firebase/sync/system`.
- Seguridad: integración protegida por `X-Sync-Secret` (`FIREBASE_SYNC_SECRET`).
- Objetivo: alta automática en BD sin depender de una llamada explícita del frontend.

---

## Decisiones de Arquitectura Fijas

> Estas decisiones están cerradas. No modificar sin registrar en `arreglos-y-cambios.md`.

### Estructura de Repositorios — Un repo por microservicio

Cada microservicio tiene su **propio repositorio GitHub independiente**. Los frontends también tienen repositorios separados.

```
Organización GitHub: catastrofescl
├── ms-identity/           → repositorio independiente
├── ms-emergencies/        → repositorio independiente
├── ms-resources/          → repositorio independiente
├── ms-citizen/            → repositorio independiente
├── ms-logistics/          → repositorio independiente
├── ms-notifications/      → repositorio independiente
├── frontend-info/         → repositorio independiente
├── frontend-dashboard/    → repositorio independiente
└── catastrofescl-infra/   → repositorio de infra (Docker Compose, Helm charts, CI/CD base)
```

**Ventajas:** deploy independiente por microservicio, pipelines CI/CD aislados, historial de cambios limpio por dominio, cada integrante puede ser owner de su repositorio.

**Regla de branches en cada repo:**

```
main      ← producción (protegida, solo merge desde develop)
develop   ← integración (protegida, requiere PR + 1 aprobación)
feature/* ← nuevas funcionalidades
fix/*     ← correcciones
```

### Base de Datos — Compartida entre todos los microservicios

Todos los microservicios apuntan a **una sola instancia PostgreSQL 15 + PostGIS** desplegada como **contenedor Docker en EKS** (con PersistentVolumeClaim para persistir datos). En local se levanta con Docker Compose. Cada microservicio accede **únicamente a las tablas de su dominio**.

> **Upgrade path (decisión pendiente de equipo):** En el futuro se puede migrar a AWS RDS multi-AZ para alta disponibilidad administrada. Por ahora usamos contenedor Docker por costo y simplicidad.

```
BD: catastrofescl_db (única instancia — contenedor Docker)
├── Dominio ms-identity      → usuarios, roles, usuarios_roles, permisos, roles_permisos,
│                               organizaciones, usuarios_organizaciones,
│                               solicitudes_rol, invitaciones_operador
├── Dominio ms-emergencies   → emergencias, anuncios
├── Dominio ms-resources     → centros, operadores_centro, catalogo_items,
│                               inventario, movimientos_inventario, registro_auditoria
├── Dominio ms-citizen       → necesidades, donaciones, items_donacion
├── Dominio ms-logistics     → transferencias, items_transferencia,
│                               misiones, rutas_voluntario, voluntarios_mision
└── Dominio ms-notifications → notificaciones, preferencias_notificacion, eventos_procesados
```

**Regla:** Un microservicio nunca hace JOIN con tablas de otro dominio. Si necesita datos de otro dominio, los obtiene vía API REST o evento RabbitMQ.

### Modelo de Roles — N:M con permisos en BD

- `usuarios` → `usuarios_roles` → `roles` → `roles_permisos` → `permisos`
- Un usuario puede tener **cualquier combinación de roles** simultáneamente
- Los permisos se gestionan desde la **UI de administración** sin tocar código
- En este MS (etapa actual), los `@PreAuthorize` se aplican por **rol** (`hasRole/hasAnyRole`).
- Firebase custom claims almacena array de roles: `{ "roles": ["AUTORIDAD", "VOLUNTARIO"] }`
- Los permisos se cargan desde BD al autenticar y se cachean en **Redis TTL 5 min**

---

## Resumen de Fases

| Fase | Nombre                        | Duración estimada | Objetivo                                         |
| ---- | ----------------------------- | ------------------ | ------------------------------------------------ |
| 0    | Fundamentos e Infraestructura | 1 semana           | Repositorios, Docker local, Firebase, CI/CD base |
| 1    | Núcleo de Identidad          | 1–2 semanas       | MS Identidad y Acceso + Firebase Auth + RBAC     |
| 2    | Operaciones de Recursos       | 2 semanas          | Centros de acopio + inventario + criticidad      |
| 3    | Participación Ciudadana      | 1–2 semanas       | Necesidades + donaciones + QR + mapa público    |
| 4    | Logística                    | 2 semanas          | Transferencias + misiones + matching OSRM        |
| 5    | Emergencias y Anuncios        | 1 semana           | MS Coordinación de Emergencias                  |
| 6    | Notificaciones                | 1–2 semanas       | MS Notificaciones + WebSocket + Lambda email     |
| 7    | Integración y Mensajería    | 1 semana           | RabbitMQ Topic Exchange + DLQ + idempotencia     |
| 8    | Dashboard y Frontend Completo | 2 semanas          | Todos los frontends + TanStack Query + Zod       |
| 9    | QA, Hardening y Despliegue    | 1–2 semanas       | Testing, seguridad, EKS producción              |

---

## Fase 0 — Fundamentos e Infraestructura

**Duración:** 1 semana
**Objetivo:** Sentar las bases técnicas antes de escribir lógica de negocio.

### Tareas — Repositorios

- [ ] Crear organización GitHub `catastrofescl`
- [ ] Crear **10 repositorios independientes**:
  - `ms-gateway` ← Spring Cloud Gateway (punto de entrada único)
  - `ms-identity`, `ms-emergencies`, `ms-resources`, `ms-citizen`
  - `ms-logistics`, `ms-notifications`
  - `frontend-info`, `frontend-dashboard`
  - `catastrofescl-infra` (Docker Compose, Helm charts, scripts)
- [ ] Configurar branch protection en cada repo: `main` y `develop` protegidas, PR obligatorio + 1 aprobación
- [ ] Crear GitHub Projects centralizado con tablero Kanban + Issues + milestones (vinculado a todos los repos)
- [ ] Crear archivo `README.md` en cada repositorio con descripción del microservicio, puerto local y dependencias

### Tareas — Infraestructura Local (`catastrofescl-infra`)

- [ ] Configurar `docker-compose.yml` con **todos los servicios** (todos como contenedores Docker, sin servicios AWS administrados):
  ```yaml
  # BD compartida — contenedor Docker, no RDS
  postgres:
    image: postgis/postgis:15-3.4
    ports: ["5432:5432"]
    volumes:
      - postgres_data:/var/lib/postgresql/data
    environment:
      POSTGRES_DB: catastrofescl_db
      POSTGRES_USER: catastrofescl
      POSTGRES_PASSWORD: ${DB_PASSWORD}

  # Cache — contenedor Docker, no ElastiCache
  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]

  # Message broker — contenedor Docker
  rabbitmq:
    image: rabbitmq:3.13-management
    ports: ["5672:5672", "15672:15672"]

  # Inspector de BD
  adminer:
    image: adminer
    ports: ["8090:8090"]

  volumes:
    postgres_data:
  ```
- [ ] Crear `.env.example` con todas las variables requeridas por los 7 microservicios
- [ ] Documentar cómo levantar el entorno local en el `README.md` del repo `catastrofescl-infra`

### Tareas — Base de Datos Compartida

- [ ] Crear schema inicial con Flyway en `ms-identity` (primer MS en ejecutar):
  - `V1__create_users_table.sql` — usuarios, roles, usuarios_roles
  - `V2__create_permissions_tables.sql` — permisos, roles_permisos
  - `V3__seed_roles.sql` — insertar los 5 roles
  - `V4__seed_permissions.sql` — insertar todos los permisos del sistema
  - `V5__seed_roles_permissions.sql` — asignar permisos iniciales a cada rol
- [ ] Verificar que cada MS tenga configurado Flyway apuntando a `catastrofescl_db` con sus propias migraciones
- [ ] Crear índices GIST iniciales para columnas geográficas

### Tareas — Firebase y CI/CD

- [ ] Crear proyecto Firebase:
  - Habilitar Email/Password + Google provider
  - Configurar custom claims para array de roles via Firebase Admin SDK
  - Obtener credenciales `serviceAccountKey.json` para cada microservicio
- [ ] Crear pipeline GitHub Actions **base** (replicar en los 6 repos de microservicios):
  ```
  Build Maven → Tests → Docker build → Push ECR → (Deploy EKS en Fase 9)
  ```
- [ ] Crear pipeline GitHub Actions para frontends (auto-deploy a Vercel en push a `main`)
- [ ] Configurar Amazon ECR: crear un repositorio de imágenes por cada microservicio (6 total)

### Entregables

- 10 repositorios creados con estructura de branches y README
- Docker Compose local levantado con PostgreSQL+PostGIS, Redis, RabbitMQ y Adminer (todos como contenedores)
- BD `catastrofescl_db` creada con schema base y seeds de roles y permisos
- Firebase configurado con custom claims de roles (array)
- Pipelines CI/CD base operativos en los 7 repos de microservicios (incluyendo ms-gateway)

---

## Fase 0.5 — MS Gateway (Spring Cloud Gateway)

**Duración:** 3–4 días
**Dependencias:** Fase 0 completa
**Repositorio:** `ms-gateway`
**Puerto local:** 8080
**Este microservicio debe estar listo antes que cualquier otro MS de negocio**

### Responsabilidad

Punto de entrada único para todas las peticiones del frontend. Valida el token Firebase, aplica rate limiting por IP y enruta cada petición al microservicio correspondiente según el path.

### Arquitectura — Patrón GlobalFilter (NO es Spring Security tradicional)

**El Gateway NO tiene:**
- ❌ `SecurityConfig.java` — Spring Cloud Gateway no usa `@EnableWebSecurity`
- ❌ Controllers de negocio — es un proxy puro, solo enruta
- ❌ `@RestController` — no expone lógica, solo traduce requests

**El Gateway SÍ tiene:**
- ✅ `GlobalFilter` — cadena de filtros reactivos (`FirebaseAuthenticationFilter`, `RateLimitingFilter`, `SecurityHeadersFilter`)
- ✅ `CorsConfig.java` — configuración centralizada de CORS (simplifica `application.yml`)
- ✅ `application.yml` — rutas y propiedades de configuración

**Flujo de un request:**
```
Cliente → Gateway:8080
    ↓
OPTIONS (preflight)? → CORS permitido → responde 200 + headers
    ↓
Authentication Filter → valida Bearer token Firebase
    ↓
Rate Limiting Filter → verifica límite por IP
    ↓
Security Headers Filter → inyecta HSTS, X-Frame-Options, etc.
    ↓
Route matching → busca ruta por Path predicate
    ↓
Load Balance → suma identidad (X-Firebase-Uid header) → microservicio downstream
```

### Tareas

- [x] Crear proyecto Spring Boot con dependencias: `spring-cloud-starter-gateway`, Firebase Admin SDK, `bucket4j` (rate limiting)
- [x] Configurar rutas en `application.yml`:
  ```yaml
  spring:
    cloud:
      gateway:
        routes:
          - id: ms-identity
            uri: http://ms-identity:8081
            predicates:
              - Path=/auth/**, /usuarios/**
          - id: ms-emergencies
            uri: http://ms-emergencies:8082
            predicates:
              - Path=/emergencias/**, /anuncios/**
          - id: ms-resources
            uri: http://ms-resources:8083
            predicates:
              - Path=/centros/**, /inventario/**
          - id: ms-citizen
            uri: http://ms-citizen:8084
            predicates:
              - Path=/necesidades/**, /donaciones/**
          - id: ms-logistics
            uri: http://ms-logistics:8085
            predicates:
              - Path=/transferencias/**, /misiones/**, /rutas-voluntario/**
          - id: ms-notifications
            uri: http://ms-notifications:8086
            predicates:
              - Path=/notificaciones/**
  ```
- [x] Implementar `FirebaseAuthenticationFilter` (GlobalFilter) — valida el token Firebase en cada request antes de enrutar
- [x] Implementar `RateLimitingFilter` (GlobalFilter) — máximo 100 requests/minuto por IP con Bucket4j
- [x] Implementar `SecurityHeadersFilter` (GlobalFilter) — inyecta HSTS, X-Frame-Options, X-Content-Type-Options
- [x] Crear `CorsConfig.java` — configuración centralizada de CORS (complementa `application.yml`)
- [x] Configurar headers de seguridad globales y CORS para orígenes permitidos (local + Vercel)
- [ ] Agregar al `docker-compose.yml` del repo `catastrofescl-infra`

### Entregables

- `ms-gateway` corriendo en puerto 8080
- Todas las rutas configuradas y enrutando correctamente a cada MS
- Validación de token Firebase funcionando en el gateway
- Rate limiting activo
- CORS centralizado en `CorsConfig.java`
- Headers de seguridad inyectados automáticamente

---

## Fase 1 — MS Identidad y Acceso

**Duración:** 1–2 semanas
**Dependencias:** Fase 0 completa
**Repositorio:** `ms-identity`
**Puerto local:** 8081
**Estado:** ✅ Completada
**Tablas propias:** `usuarios`, `roles`, `usuarios_roles`, `permisos`, `roles_permisos`, `organizaciones`, `usuarios_organizaciones`, `solicitudes_rol`, `invitaciones_operador`

### Tareas Backend

- [X] Crear proyecto Spring Boot con dependencias: Web, Security, Data JPA, Lombok, Firebase Admin SDK, Validation, OpenAPI, Spring Cache
- [X] Configurar `application.yml` con conexión a `catastrofescl_db` (misma BD compartida)
- [X] Implementar `FiltroTokenFirebase` — extrae UID y array de roles del custom claim Firebase
- [X] Implementar `ServicioPermisos` — carga permisos del usuario desde BD, los cachea en Redis TTL 5min
- [X] Configurar Spring Security: `@PreAuthorize` basado en roles para endpoints del MS
- [X] Implementar `@ControllerAdvice` global con formato RFC 7807
- [X] Crear entidades JPA en español:
  - `Usuario`, `Rol`, `UsuarioRol`, `Permiso`, `RolPermiso`
  - `Organizacion`, `UsuarioOrganizacion`
  - `SolicitudRol`, `InvitacionOperador`
- [X] Migraciones Flyway (en orden, sin modificar una vez aplicadas):
  - `V1__create_users_table.sql`
  - `V2__create_roles_permissions_tables.sql`
  - `V3__seed_roles.sql` — 5 roles iniciales
  - `V4__seed_permissions.sql` — todos los permisos del sistema con su módulo
  - `V5__seed_roles_permissions.sql` — asignación inicial de permisos por rol
  - `V6__create_organizations_tables.sql`
  - `V7__create_requests_invitations_table.sql`
  - `V8__seed_default_registered_role.sql` — agrega rol `REGISTRADO` por defecto sin privilegios
- [X] Implementar endpoints:
  - [X] `POST /auth/register` — registro unificado (Firebase Auth + sincronización BD local), asigna rol REGISTRADO por defecto
  - [X] `POST /auth/firebase/sync` — sincroniza usuario Firebase ya autenticado con BD local
  - [X] `GET /usuarios/yo` — perfil completo: datos + roles + permisos + preferencias
  - [X] `POST /usuarios/:id/roles` — asignar rol a usuario (requiere rol `ADMINISTRADOR`) + actualizar custom claim Firebase
  - [X] `DELETE /usuarios/:id/roles/:rolId` — quitar rol (requiere rol `ADMINISTRADOR`)
  - [X] `GET /usuarios` — listado paginado (requiere rol `ADMINISTRADOR`)
  - [X] `PATCH /usuarios/:id/estado` — activar/suspender usuario (requiere rol `ADMINISTRADOR`)
  - [X] `POST /auth/invitacion/operador` — generar token de invitación (requiere rol `ADMINISTRADOR`)
  - [X] `POST /auth/invitacion/aceptar` — aceptar invitación con token
  - [X] `POST /solicitudes-rol` — voluntario solicita extensión de rol
  - [X] `PATCH /solicitudes-rol/:id` — autoridad aprueba/rechaza solicitud
  - [X] `GET /permisos` — listar permisos del sistema (requiere rol `ADMINISTRADOR`)
  - [X] `POST /roles/:id/permisos` — asignar permiso a rol desde UI (requiere rol `ADMINISTRADOR`)
  - [X] `DELETE /roles/:id/permisos/:permisoId` — quitar permiso de rol (requiere rol `ADMINISTRADOR`)
- [X] Tests unitarios `ServicioUsuario`, `ServicioPermisos`, `ServicioRol` (cobertura objetivo ≥ 80%)
- [X] Tests de integración con Testcontainers: flujo completo registro → asignación de rol → carga de permisos

### Entregables

- `ms-identity` corriendo en Docker en puerto 8081
- Autenticación Firebase funcional end-to-end con array de roles
- Permisos cargados desde BD y cacheados en Redis
- `@PreAuthorize` por roles funcionando en todos los endpoints
- Swagger UI disponible en `localhost:8081/swagger-ui.html`

---

## Fase 2 — MS Operaciones de Recursos

**Duración:** 2 semanas
**Dependencias:** Fase 1
**Repositorio:** `ms-resources`
**Puerto local:** 8083
**Tablas propias:** `centros`, `operadores_centro`, `catalogo_items`, `inventario`, `movimientos_inventario`, `registro_auditoria`
**Este es el núcleo operativo del sistema**

### Tareas Backend

- [ ] Crear entidades JPA en español: `Centro`, `OperadorCentro`, `ItemCatalogo`, `Inventario`, `MovimientoInventario`, `RegistroAuditoria`
- [ ] Configurar `application.yml` apuntando a `catastrofescl_db`
- [ ] Habilitar PostGIS: tipo `GEOGRAPHY(POINT)` y `GEOGRAPHY(POLYGON)` con Hibernate Spatial
- [ ] Migraciones Flyway:
  - `V1__create_centers_table.sql` + índice GIST en `coordenadas`
  - `V2__create_items_catalog_table.sql`
  - `V3__create_inventory_table.sql` + constraint UNIQUE (centro_id, item_id)
  - `V4__create_inventory_movements_table.sql`
  - `V5__create_audit_log_table.sql`
- [ ] Implementar `POST /centros` — crear centro (requiere `CENTRO_CREAR`)
- [ ] Implementar `GET /centros/cercanos` — ST_DWithin con radio configurable
- [ ] Implementar `GET /centros/datos-mapa` — datos optimizados para Leaflet (cacheado Redis TTL 60s)
- [ ] Implementar `POST /centros/:id/operadores` — asignar operadores (requiere `CENTRO_EDITAR`)
- [ ] Implementar `PATCH /centros/:id/umbrales` — configurar umbrales (requiere `UMBRAL_CONFIGURAR`)
- [ ] Implementar motor de criticidad `MotorCriticidad`:
  - `AGOTADO` → publica evento + crea necesidad CRÍTICO
  - `CRITICO` → publica evento + crea necesidad ALTO
  - `SOBRESTOCK` → activa `MotorRedistribucion`
- [ ] Implementar `POST /centros/:id/inventario/movimientos` — registrar movimiento (requiere `INVENTARIO_EDITAR`)
- [ ] Implementar `GET /inventario/sugerencias` — redistribución: prioridad + distancia PostGIS ST_Distance
- [ ] Publicar eventos a RabbitMQ: `stock.critical`, `stock.updated`, `inventory.movement.registered`
- [ ] Cachear con Redis: `datos-mapa` TTL 60s, `kpis` TTL 30s, `catalogo-items` TTL 300s
- [ ] Tests unitarios de `MotorCriticidad` y `MotorRedistribucion` (cobertura ≥ 80%)

### Entregables

- Centros con inventario y umbrales por centro funcionando
- Consultas geoespaciales PostGIS operativas
- Eventos RabbitMQ publicándose correctamente

---

## Fase 3 — MS Participación Ciudadana

**Duración:** 1–2 semanas
**Dependencias:** Fase 2
**Repositorio:** `ms-citizen`
**Puerto local:** 8084
**Tablas propias:** `necesidades`, `donaciones`, `items_donacion`

### Tareas Backend

- [ ] Crear entidades JPA en español: `Necesidad`, `Donacion`, `ItemDonacion`
- [ ] Migraciones Flyway:
  - `V1__create_needs_table.sql`
  - `V2__create_donations_table.sql`
  - `V3__create_donation_items_table.sql`
- [ ] Implementar `GET /necesidades/publicas` — sin autenticación, paginadas
- [ ] Implementar `POST /necesidades` — manuales (requiere `NECESIDAD_GESTIONAR`) y automáticas (desde eventos RabbitMQ)
- [ ] Implementar `POST /donaciones` — registrar donación (requiere `DONACION_REALIZAR`):
  - Validación Zod internacional (RUT + documentos extranjeros + teléfonos E.164)
  - Generar `codigo_qr` UUID único → guardar imagen QR en S3
- [ ] Implementar `POST /donaciones/:codigoQr/confirmar` — operador escanea QR (requiere `DONACION_CONFIRMAR`):
  - Cierra ciclo de trazabilidad
  - Publica evento `donation.confirmed` → actualiza inventario vía RabbitMQ
- [ ] Implementar `GET /donaciones/mis-contribuciones` — historial del ciudadano autenticado
- [ ] Publicar eventos: `donation.created`, `need.created`
- [ ] Tests de integración del flujo completo de donación con QR

### Entregables

- Flujo completo de donación guiada con QR funcional
- Necesidades públicas visibles sin autenticación

---

## Fase 4 — MS Logística

**Duración:** 2 semanas
**Dependencias:** Fase 2 y 3
**Repositorio:** `ms-logistics`
**Puerto local:** 8085
**Tablas propias:** `transferencias`, `items_transferencia`, `misiones`, `rutas_voluntario`, `voluntarios_mision`

### Tareas Backend

- [ ] Crear entidades JPA en español: `Transferencia`, `ItemTransferencia`, `Mision`, `RutaVoluntario`, `VoluntarioMision`
- [ ] Migraciones Flyway:
  - `V1__create_transfers_table.sql`
  - `V2__create_transfer_items_table.sql`
  - `V3__create_missions_table.sql`
  - `V4__create_volunteer_routes_table.sql` + índices GIST en coordenadas
  - `V5__create_mission_volunteers_table.sql` + constraint UNIQUE (mision_id, ruta_voluntario_id)
- [ ] Implementar `POST /transferencias` — (requiere `TRANSFERENCIA_SOLICITAR`):
  - Validar stock suficiente en centro origen consultando tabla `inventario`
  - Estado inicial: `SOLICITADA`
- [ ] Implementar `PATCH /transferencias/:id/estado` — flujo de estados (requiere `TRANSFERENCIA_APROBAR`):
  - `SOLICITADA → APROBADA → EN_TRANSITO → RECIBIDA` (o `RECHAZADA`)
  - En `RECIBIDA`: actualizar inventarios origen y destino `@Transactional`
- [ ] Implementar `POST /misiones` — (requiere `MISION_CREAR`)
- [ ] Implementar `POST /rutas-voluntario` — voluntario ofrece ruta (requiere `RUTA_OFRECER`)
- [ ] Implementar `ServicioMatchingOSRM` — cliente HTTP a OSRM:
  - `GET /rutas-voluntario/matching/:misionId`
  - Filtros: compatibilidad de ruta, capacidad de vehículo, voluntario disponible
  - Ordenamiento: distancia vial OSRM + prioridad de necesidad
- [ ] Publicar eventos: `transfer.created`, `transfer.status.changed`, `mission.assigned`
- [ ] Tests de `ServicioMatchingOSRM` con mocks del servidor OSRM
- [ ] Tests de integridad transaccional en recepción de transferencia

### Entregables

- Flujo completo de transferencias con `@Transactional` funcionando
- Matching de voluntarios con OSRM operativo

---

## Fase 5 — MS Coordinación de Emergencias

**Duración:** 1 semana
**Dependencias:** Fase 1
**Repositorio:** `ms-emergencies`
**Puerto local:** 8082
**Tablas propias:** `emergencias`, `anuncios`

### Tareas Backend

- [ ] Crear entidades JPA en español: `Emergencia`, `Anuncio`
- [ ] Migraciones Flyway:
  - `V1__create_emergencies_table.sql` + índices GIST en `coordenadas_epicentro` y `zona_impacto`
  - `V2__create_announcements_table.sql`
- [ ] Implementar `POST /emergencias` — (requiere `EMERGENCIA_DECLARAR`)
- [ ] Implementar `PATCH /emergencias/:id/estado` — transición de estados (requiere `EMERGENCIA_GESTIONAR`)
- [ ] Implementar `GET /emergencias/activas` — con datos geoespaciales, público
- [ ] Implementar `POST /anuncios` — (requiere `ANUNCIO_PUBLICAR`)
- [ ] Implementar `GET /anuncios` — activos, público sin auth
- [ ] Publicar eventos: `emergency.created`, `emergency.status.changed`, `announcement.published`

### Entregables

- Ciclo de vida completo de emergencias con zonas PostGIS
- Sistema de anuncios críticos funcional

---

## Fase 6 — MS Notificaciones + Lambda Email

**Duración:** 1–2 semanas
**Dependencias:** RabbitMQ configurado con Topic Exchange
**Repositorio:** `ms-notifications`
**Puerto local:** 8086
**Tablas propias:** `notificaciones`, `preferencias_notificacion`, `eventos_procesados`

### Tareas Backend (MS Notificaciones)

- [ ] Crear entidades JPA en español: `Notificacion`, `PreferenciaNotificacion`, `EventoProcesado`
- [ ] Migraciones Flyway:
  - `V1__create_notifications_table.sql`
  - `V2__create_notification_preferences_table.sql` + UNIQUE (usuario_id, tipo_evento)
  - `V3__create_processed_events_table.sql`
- [ ] Configurar consumidores `@RabbitListener` para todas las colas de eventos
- [ ] Implementar idempotencia: verificar `EventoProcesado` en BD + Redis `processed:{eventId}` TTL 24h
- [ ] Implementar `GET /notificaciones` — listado paginado del usuario autenticado
- [ ] Implementar `PATCH /notificaciones/:id/leer` — marcar como leída
- [ ] Implementar `GET /notificaciones/preferencias` — preferencias del usuario
- [ ] Implementar `PATCH /notificaciones/preferencias` — actualizar preferencias
- [ ] Configurar servidor WebSocket STOMP:
  - Canal: `/topic/notificaciones/{usuarioId}`
  - Fallback: el frontend usa polling TanStack Query si WebSocket cae

### Tareas AWS Lambda (Email)

- [ ] Crear función Lambda (Node.js) para consumir `email.queue`
- [ ] Integrar con AWS SES para envío de correos
- [ ] Templates de email para: confirmación de donación, stock crítico, transferencia creada/recibida, misión asignada, anuncio urgente

### Entregables

- Notificaciones in-app en tiempo real via WebSocket
- Emails automáticos via Lambda + SES
- DLQ configuradas y monitoreadas

---

## Fase 7 — Integración RabbitMQ Completa

**Duración:** 1 semana
**Dependencias:** Todas las fases anteriores

### Tareas

- [ ] Configurar Topic Exchange: `catastrofescl.events`
- [ ] Configurar Dead Letter Exchange: `catastrofescl.dlx`
- [ ] Definir todas las colas con binding keys por routing pattern
- [ ] Configurar DLQ para cada cola principal (`notifications.dlq`, `email.dlq`, `logistics.dlq`)
- [ ] Configurar retry policies: `x-max-retries: 3`, `x-message-ttl: 30000`
- [ ] Implementar idempotencia en TODOS los consumidores (Redis `processed:{eventId}`)
- [ ] Pruebas de resiliencia: bajar un microservicio consumidor y verificar que mensajes se retienen
- [ ] Monitorear DLQ: alertas cuando mensajes llegan a dead letter

### Entregables

- Arquitectura de mensajería completa con Topic Exchange + DLQ + idempotencia
- Pruebas de tolerancia a fallos documentadas

---

## Fase 8 — Frontend Completo

**Duración:** 2 semanas
**Dependencias:** Todas las APIs disponibles

### Frontend de Información (Portal Ciudadano — Next.js SSR)

- [ ] Layout base + sistema de rutas App Router
- [ ] Mapa interactivo público (Leaflet + OpenStreetMap):
  - Marcadores coloreados por estado de centro
  - Popups con necesidades críticas
  - Filtros por región/comuna/tipo de necesidad
  - "Centros cerca de mí" (geolocalización del navegador)
- [ ] Wizard de donación guiada (4 pasos: centro → necesidades → ítems → confirmar/QR)
- [ ] Feed de anuncios críticos con badges de severidad
- [ ] Formulario de registro ciudadano con validación Zod internacional
- [ ] Panel de impacto personal del ciudadano (historial de donaciones)
- [ ] Formulario de oferta de ruta voluntaria

### Frontend Dashboard (Autoridades/Operadores — Next.js Client Components)

- [ ] Dashboard KPIs en tiempo real (TanStack Query + WebSocket STOMP)
- [ ] Gestión de centros de acopio e inventarios
- [ ] Flujos de transferencias (tabla con estados + acciones)
- [ ] Gestión de misiones de despacho
- [ ] Mapa operativo táctico con capas de datos enriquecidas
- [ ] Publicación de anuncios críticos (formulario con preview)
- [ ] Administración de emergencias activas
- [ ] Gestión de umbrales de inventario por centro (Admin only)
- [ ] Gráficos con Recharts: evolución de stock, distribución de donaciones

### Integración y Validación Frontend

- [ ] Interceptores Axios con auto-refresh Firebase token
- [ ] Hidratación TanStack Query (SSR → Client) para mapa y datos públicos
- [ ] Fallback: polling automático si WebSocket cae (refetchInterval: 30s)
- [ ] Validación internacional completa (Zod schema para documentos y teléfonos)
- [ ] Tests con React Testing Library (flujos de usuario críticos)

### Entregables

- Ambos frontends desplegados en Vercel
- Preview deployments por Pull Request activos
- Accesibilidad WCAG 2.1 AA verificada

---

## Fase 9 — QA, Hardening y Despliegue a Producción

**Duración:** 1–2 semanas

### Tareas de Calidad

- [ ] Revisar cobertura de tests (meta: ≥ 80% en servicios críticos)
- [ ] Tests de carga con k6 o JMeter (meta: < 500ms en P95 bajo 100 usuarios concurrentes)
- [ ] Tests de integración end-to-end con Testcontainers para flujos completos
- [ ] Revisión de seguridad:
  - Escaneo de dependencias (OWASP Dependency Check)
  - Verificar headers de seguridad Spring Security
  - Revisar configuración CORS por entorno
  - Verificar que Kubernetes Secrets no estén expuestos

### Tareas de Despliegue EKS

- [ ] Crear cluster EKS con configuración multi-AZ
- [ ] Configurar Ingress Controller con certificado TLS
- [ ] Crear Kubernetes Secrets para todas las credenciales
- [ ] Desplegar los 6 microservicios con Helm charts
- [ ] Configurar Horizontal Pod Autoscaler (HPA) por microservicio
- [ ] Configurar health checks (liveness + readiness probes)
- [ ] Crear PersistentVolumeClaim para el pod de PostgreSQL (datos sobreviven reinicios)
- [ ] Crear PersistentVolumeClaim para el pod de Redis
- [ ] Conectar Sentry a todos los microservicios y frontends

> **💡 Mejoras futuras (decisión pendiente de equipo):** Una vez que el sistema esté estable en producción, evaluar migración a:
>
> - **AWS RDS multi-AZ** con backups automáticos gestionados (para PostgreSQL)
> - **AWS ElastiCache** en subnet privada de la VPC (para Redis)
>
> Esta migración requiere consenso del equipo y se documenta en `arreglos-y-cambios.md` cuando se decida.

### Tareas Finales

- [ ] Documentar runbooks de operación (cómo escalar, cómo revisar DLQ, cómo hacer rollback)
- [ ] Configurar alertas de monitoreo (CloudWatch + Sentry)
- [ ] Smoke tests en producción
- [ ] Revisión final de accesibilidad WCAG 2.1

### Entregables

- Sistema completo en producción sobre AWS EKS
- Documentación operativa
- Pipeline CI/CD completo de desarrollo a producción

---

## Notas Transversales

### Estructura interna de cada repositorio de microservicio

```
ms-{nombre}/
├── src/main/java/cl/catastrofescl/{nombre}/
│   ├── controller/      ← @RestController, manejo de HTTP
│   ├── service/         ← @Service, lógica de negocio
│   ├── repository/      ← @Repository, Spring Data JPA
│   ├── entity/          ← @Entity, mapeo JPA (nombres de clases en español)
│   ├── dto/
│   │   ├── request/     ← DTOs de entrada (@Valid)
│   │   └── response/    ← DTOs de salida
│   ├── event/           ← clases de eventos RabbitMQ
│   ├── config/          ← Spring Security, RabbitMQ, Redis, Firebase
│   └── exception/       ← @ControllerAdvice + excepciones propias
├── src/main/resources/
│   ├── application.yml  ← siempre apunta a catastrofescl_db
│   └── db/migration/    ← scripts Flyway propios del MS (V{n}__{english_name}.sql)
├── src/test/
│   ├── unit/
│   └── integration/     ← Testcontainers
├── Dockerfile
├── .github/workflows/
│   └── ci.yml           ← Build → Test → Docker build → Push ECR
└── README.md
```

### Variables de entorno comunes a todos los microservicios

```properties
# BD compartida — misma URL en todos los MS
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/catastrofescl_db
SPRING_DATASOURCE_USERNAME=catastrofescl
SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}

# Redis compartido
SPRING_REDIS_HOST=localhost
SPRING_REDIS_PORT=6379

# RabbitMQ compartido
SPRING_RABBITMQ_HOST=localhost
SPRING_RABBITMQ_PORT=5672

# Firebase (cada MS tiene su propia copia del serviceAccountKey)
FIREBASE_PROJECT_ID=${FIREBASE_PROJECT_ID}
FIREBASE_CREDENTIALS_PATH=/secrets/serviceAccountKey.json
```

### Code Review

Todo código requiere Pull Request con revisión de al menos 1 compañero antes de merge a `develop`. Merge a `main` solo desde `develop` con todos los tests pasando.

### Convenciones de Commits (en cada repo)

```
feat(ms-resources): implementar motor de criticidad de inventario
fix(ms-logistics): corregir matching OSRM con capacidad de vehiculo
refactor(ms-identity): extraer FiltroTokenFirebase a clase separada
test(ms-citizen): agregar tests de integracion para flujo de donacion con QR
docs(ms-emergencies): documentar endpoints en Swagger
chore(infra): actualizar Docker Compose con imagen RabbitMQ 3.13
```

### Gestión de Secrets

- Local: archivo `.env` en cada repo (en `.gitignore`)
- CI/CD: GitHub Secrets configurados por repositorio
- Producción: Kubernetes Secrets (uno por microservicio)

### Estrategia de Rollback

Si un deploy falla en producción: `kubectl rollout undo deployment/<ms-nombre>` revierte al pod anterior. Cada microservicio hace rollback independiente sin afectar a los demás.
