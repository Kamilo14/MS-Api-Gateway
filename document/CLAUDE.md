# CLAUDE.md — Instrucciones Maestras del Proyecto CatástrofesCL

> **LEER ESTE ARCHIVO COMPLETO ANTES DE RESPONDER CUALQUIER PROMPT.**
> Este archivo define cómo Claude debe comportarse en cada interacción dentro del proyecto.

---

## ⚠️ PROTOCOLO OBLIGATORIO — Antes de cada respuesta

Cada vez que recibas un prompt relacionado con este proyecto, debes ejecutar este protocolo en orden:

```
1. LEER  → especificaciones-tecnicas.md   (stack, arquitectura, estándares)
2. LEER  → plan-de-implementacion.md      (fase actual, tareas pendientes)
3. LEER  → errores.md                     (errores abiertos que pueden afectar la tarea)
4. LEER  → arreglos-y-cambios.md          (decisiones ya tomadas, no revertir)
5. ACTUAR → ejecutar la tarea solicitada con el contexto completo
6. ACTUALIZAR → escribir en los archivos correspondientes según lo que ocurrió
```

**No omitas ningún paso.** Si el contexto ya está en la conversación activa, puedes resumir la lectura, pero nunca asumas sin verificar.

---

## 🧪 Contexto operativo actual (obligatorio)

**Por ahora, la aplicación web se usará únicamente en entorno local.**

- Priorizar siempre configuración, pruebas y validaciones en localhost.
- Evitar decisiones orientadas a despliegue cloud/productivo si no son explícitamente solicitadas.
- Si una tarea tiene opción local vs cloud, elegir local por defecto y documentar ese supuesto.

---

## 🧠 Identidad del Proyecto

**Nombre:** CatástrofesCL
**Tipo:** Plataforma de gestión de recursos humanitarios para catástrofes naturales en Chile
**Arquitectura:** 7 Microservicios (6 de negocio + 1 gateway) + 2 Frontends + Mensajería asíncrona
**Equipo:** Camilo Calderón, Alfonso González, Gabriela Huenchullán

### Los 6 Microservicios de Negocio (nombres internos)

| Código              | Nombre                       | Puerto local |
| -------------------- | ---------------------------- | ------------ |
| `ms-identity`      | Identidad y Acceso           | 8081         |
| `ms-emergencies`   | Coordinación de Emergencias | 8082         |
| `ms-resources`     | Operaciones de Recursos      | 8083         |
| `ms-citizen`       | Participación Ciudadana     | 8084         |
| `ms-logistics`     | Logística                   | 8085         |
| `ms-notifications` | Notificaciones               | 8086         |

### Microservicio de Infraestructura

| Código        | Nombre                             | Puerto local |
| -------------- | ---------------------------------- | ------------ |
| `ms-gateway` | API Gateway (Spring Cloud Gateway) | 8080         |

> `ms-gateway` es un microservicio propio con su repositorio independiente. Actúa como punto de entrada único: recibe todas las peticiones del frontend, valida el token Firebase y las enruta al microservicio correspondiente. **No se usa API Gateway de AWS.**

### Servicios de Soporte (contenedores Docker, no servicios AWS administrados)

| Servicio                | Puerto local      | Imagen Docker                |
| ----------------------- | ----------------- | ---------------------------- |
| PostgreSQL 15 + PostGIS | 5432              | `postgis/postgis:15-3.4`   |
| Redis                   | 6379              | `redis:7-alpine`           |
| RabbitMQ                | 5672 / 15672 (UI) | `rabbitmq:3.13-management` |
| Adminer                 | 8090              | `adminer`                  |

### Los 2 Frontends

| Nombre                 | Tipo                      | Usuarios                       |
| ---------------------- | ------------------------- | ------------------------------ |
| `frontend-info`      | Next.js SSR               | Ciudadanos, público general   |
| `frontend-dashboard` | Next.js Client Components | Autoridades, Operadores, Admin |

---

## 📁 Archivos de Control del Proyecto

Estos 4 archivos son la memoria viva del proyecto. Claude los lee al inicio y los actualiza al final de cada sesión:

### `plan-de-implementacion.md`

- Contiene las 10 fases (0–9) con checklists de tareas
- **Claude debe:** marcar tareas como completadas (`- [x]`) cuando se terminen
- **Claude debe:** actualizar el estado de la fase en curso

### `errores.md`

- Registro de bugs, errores de integración y problemas técnicos
- **Claude debe:** agregar un nuevo registro `[ERR-XXX]` cuando encuentre un error
- **Claude debe:** actualizar el estado a `Resuelto` con referencia al arreglo

### `arreglos-y-cambios.md`

- Registro de decisiones técnicas, bugfixes y cambios de arquitectura
- **Claude debe:** agregar un registro `[ARR-XXX]` o `[DEC-XXX]` cuando resuelva un error o tome una decisión
- **Claude nunca debe:** revertir decisiones ya documentadas aquí sin justificación explícita

### `avances.md`

- Bitácora de progreso con tablas de estado por microservicio, frontend e infraestructura
- **Claude debe:** actualizar las tablas de estado (⬜ → 🔄 → ✅) al terminar cada sesión
- **Claude debe:** agregar una entrada con fecha, integrante y resumen de lo completado

---

## 🏗️ Estándares de Código — SIEMPRE aplicar

### Backend (Spring Boot — Java 21)

**Estructura de paquetes obligatoria por microservicio:**

```
ms-{nombre}/
├── src/main/java/cl/catastrofescl/{nombre}/
│   ├── controller/      ← @RestController, manejo de HTTP
│   ├── service/         ← lógica de negocio, @Service
│   ├── repository/      ← @Repository, Spring Data JPA
│   ├── entity/          ← @Entity, mapeo JPA
│   ├── dto/
│   │   ├── request/     ← DTOs de entrada (@Valid)
│   │   └── response/    ← DTOs de salida
│   ├── event/           ← clases de eventos de dominio (RabbitMQ)
│   ├── config/          ← Spring Security, RabbitMQ, Redis, Firebase
│   └── exception/       ← @ControllerAdvice + clases de excepción
├── src/main/resources/
│   ├── application.properties
│   └── db/migration/    ← scripts Flyway V{n}__{descripcion}.sql
└── src/test/
    ├── unit/            ← JUnit 5 + Mockito
    └── integration/     ← Testcontainers
```

**Reglas de código obligatorias:**

- Siempre arquitectura en capas: `Controller → Service → Repository → Entity`
- Nunca lógica de negocio en Controllers
- Nunca queries SQL nativas sin justificación (usar Spring Data JPA)
- Todos los endpoints deben tener validación con `@Valid` en el Request DTO
- Todos los errores deben lanzarse como excepciones específicas (nunca `RuntimeException` genérica)
- El `@ControllerAdvice` global maneja TODAS las excepciones y devuelve formato RFC 7807

**Formato de error RFC 7807 — SIEMPRE:**

```json
{
  "type": "https://catastrofescl.cl/errors/{codigo-kebab-case}",
  "title": "Título Legible",
  "status": 4xx | 5xx,
  "detail": "Descripción detallada del problema.",
  "instance": "/ruta/del/recurso",
  "errorCode": "CODIGO_SNAKE_UPPER",
  "timestamp": "2026-04-19T12:00:00Z"
}
```

**Seguridad — NUNCA omitir:**

- Todos los endpoints privados deben tener `@PreAuthorize` con el rol requerido
- Los tokens Firebase se validan en el `FirebaseTokenFilter`, nunca manualmente en servicios
- Las variables sensibles van en `application.properties` referenciando variables de entorno: `${VARIABLE}`
- Nunca hardcodear credenciales, URLs de servicios externos ni API keys en el código, en formatos .env de forma local

### Frontend (Next.js 14+ — TypeScript)

**Estructura de carpetas obligatoria:**

```
frontend-{nombre}/
├── app/                 ← App Router (Next.js 14)
│   ├── layout.tsx       ← layout raíz
│   ├── page.tsx         ← páginas (Server Components por defecto)
│   └── (rutas)/
├── components/
│   ├── ui/              ← componentes shadcn/ui
│   └── {feature}/       ← componentes de dominio
├── lib/
│   ├── api/             ← funciones de fetch (axios)
│   ├── queries/         ← hooks TanStack Query
│   ├── schemas/         ← schemas Zod
│   └── utils/
├── hooks/               ← hooks personalizados
├── types/               ← tipos TypeScript
└── public/
```

**Reglas de código obligatorias:**

- Hidratación SSR → Client con `HydrationBoundary` para datos que requieren tiempo real
- Fallback de polling (`refetchInterval: 30_000`) siempre que se use WebSocket
- Validación Zod internacional: documentos (RUT/Pasaporte/DNI/Otro) + teléfonos E.164
- Componentes de UI siempre desde `shadcn/ui` (nunca HTML crudo sin accesibilidad)
- Interceptor Axios centralizado para auto-refresh del Firebase Token

---

## 🔁 Flujo de Mensajería RabbitMQ — SIEMPRE respetar

### Topic Exchange principal

```
Exchange: catastrofescl.events (topic)
Dead Letter Exchange: catastrofescl.dlx (direct)
```

### Routing Keys definidas (no crear nuevas sin actualizar arreglos-y-cambios.md)

```
stock.critical
stock.updated
inventory.movement.registered
donation.created
donation.confirmed
need.created
transfer.created
transfer.status.changed
mission.assigned
emergency.created
emergency.status.changed
announcement.published
```

### Reglas de mensajería obligatorias

1. **Idempotencia:** Cada consumidor DEBE verificar `processed:{eventId}` en Redis antes de procesar. TTL: 24h.
2. **DLQ:** Toda cola principal debe tener su dead letter queue configurada (`x-dead-letter-exchange`, `x-max-retries: 3`)
3. **Eventos inmutables:** Una vez publicado un evento, no se modifica. Se publica un nuevo evento de corrección.
4. **Productores no esperan consumidores:** El microservicio publica y responde al cliente de inmediato.

---

## 🗄️ Base de Datos — Reglas PostGIS y Flyway

### Flyway — Migraciones

- Formato obligatorio: `V{número}__{descripcion_con_guiones_bajos}.sql`
- Ejemplo: `V1__create_users_table.sql`, `V2__add_gist_index_centers.sql`
- **Nunca modificar** una migración ya aplicada. Crear una nueva migración de corrección.

### PostGIS — Funciones permitidas

```sql
ST_DWithin()       -- centros cercanos (usar siempre con ::geography para precisión en metros)
ST_Distance()      -- distancia entre puntos
ST_Within()        -- punto dentro de polígono (zonas de emergencia)
ST_MakePoint()     -- construir punto desde lng/lat
ST_GeomFromText()  -- construir geometría desde WKT
```

- Índices GIST **obligatorios** en todas las columnas de tipo geográfico
- Paginación **obligatoria** en todos los endpoints que devuelvan listas geoespaciales

### Umbrales de Criticidad

```
AGOTADO    → stock = 0           → necesidad prioridad CRÍTICO
CRÍTICO    → 0 < stock ≤ mínimo → necesidad prioridad ALTO
NORMAL     → mínimo < stock ≤ óptimo
ABUNDANTE  → óptimo < stock ≤ máximo
SOBRESTOCK → stock > máximo     → sugerencia de redistribución
```

- Umbrales configurables **solo por rol ADMINISTRADOR**
- Umbrales son **por centro de acopio y por ítem** (no globales)

---

## 🔐 Firebase Auth — Reglas de Integración

```
Flujo: Usuario → Firebase Auth → ID Token → API Gateway → FirebaseTokenFilter → Spring Security → @PreAuthorize
```

- **Custom claims** en el token Firebase almacenan el array de roles: `{ "roles": ["ADMINISTRADOR", "AUTORIDAD", "OPERADOR", "PARTICULAR", "VOLUNTARIO"] }` (puede ser uno o varios)
- El `FirebaseTokenFilter` extrae el UID y el array de custom claims y los inyecta en el `SecurityContext`
- El endpoint `POST /usuarios/:id/roles` asigna roles y actualiza el custom claim en Firebase **y** en la BD local
- **Nunca** generar ni validar JWT propios. Solo Firebase ID Tokens.
- Para tests de integración: usar Firebase Emulator Suite (no el proyecto real de producción)

---

## 🧪 Testing — Estándares Mínimos

### Backend

| Tipo         | Framework                         | Cobertura mínima                  |
| ------------ | --------------------------------- | ---------------------------------- |
| Unitario     | JUnit 5 + Mockito                 | 80% en clases Service              |
| Integración | Testcontainers + Spring Boot Test | Flujos completos por microservicio |
| API          | SpringDoc OpenAPI                 | 100% endpoints documentados        |

**Tests obligatorios por microservicio antes de marcar la fase como completada:**

- Happy path de cada endpoint crítico
- Error path con respuesta RFC 7807 correcta
- Validación de permisos RBAC (acceso denegado con rol incorrecto)
- Idempotencia del consumidor RabbitMQ

### Frontend

| Tipo           | Framework             | Qué testear                    |
| -------------- | --------------------- | ------------------------------- |
| Componentes    | React Testing Library | Flujos de usuario críticos     |
| Formularios    | Testing Library + Zod | Validación internacional       |
| E2E (opcional) | Playwright            | Flujos de donación y dashboard |

---

## 📋 Flujo de Trabajo por Tarea

Cuando el usuario pida implementar algo, Claude debe seguir este flujo exacto:

### Paso 1 — Verificar contexto

```
¿En qué fase estoy según plan-de-implementacion.md?
¿Hay errores abiertos en errores.md que afecten esta tarea?
¿Hay decisiones en arreglos-y-cambios.md que deba respetar?
```

### Paso 2 — Planificar antes de codificar

Antes de escribir código, mostrar:

```
📍 Fase: X — Nombre de la fase
📦 Microservicio / Frontend: nombre
🎯 Tarea: descripción de lo que se va a implementar
📐 Archivos a crear/modificar: lista
⚠️ Dependencias: qué debe estar funcionando antes
```

### Paso 3 — Implementar

- Respetar estructura de paquetes definida en este archivo
- Aplicar todos los estándares de código
- Incluir tests para lo implementado
- Documentar con Swagger (`@Operation`, `@ApiResponse`) en cada endpoint nuevo

### Paso 4 — Actualizar archivos de control

Al terminar, Claude DEBE actualizar automáticamente:

**En `plan-de-implementacion.md`:**

```markdown
- [x] Tarea completada  ← marcar con [x]
```

**En `avances.md`:**

```markdown
## [YYYY-MM-DD] Descripción breve
**Integrante(s):** quien trabajó
**Fase trabajada:** Fase X — Nombre
### Completado
- lista de lo terminado
### Próximos pasos
- siguiente tarea
```

**En `errores.md`** (si se encontró un error):

```markdown
### [ERR-XXX] Título
- Fecha, microservicio, severidad, descripción, causa raíz
```

**En `arreglos-y-cambios.md`** (si se aplicó un arreglo o se tomó una decisión):

```markdown
### [ARR-XXX] o [DEC-XXX] Título
- Fecha, autor, tipo, descripción, archivos afectados
```

---

## 🚦 Reglas de Comportamiento de Claude

### SIEMPRE hacer:

- ✅ Leer los 4 archivos de control al inicio de cada sesión
- ✅ Respetar las decisiones técnicas documentadas en `arreglos-y-cambios.md`
- ✅ Generar código completo y funcional (no pseudocódigo ni fragmentos incompletos)
- ✅ Incluir imports, anotaciones y configuración necesaria en cada archivo generado
- ✅ Aplicar validaciones y manejo de errores RFC 7807 en todo endpoint nuevo
- ✅ Actualizar los 4 archivos de control al finalizar la tarea
- ✅ Preguntar si hay ambigüedad en los requisitos ANTES de implementar
- ✅ Indicar explícitamente qué archivos se crean, modifican o eliminan
- ✅ Nombrar **paquetes Java en inglés**: `controller/`, `service/`, `repository/`, `entity/`, `event/`, `exception/`
- ✅ Nombrar **clases de capa arquitectónica en inglés**: `UserController`, `UserService`, `UserRepository`, `FirebaseTokenFilter`, `InsufficientStockException`, `StockUpdatedEvent`
- ✅ Nombrar **entidades JPA en español** (mapean a tablas en español): `Usuario`, `Centro`, `Donacion`, `Emergencia`
- ✅ Nombrar **archivos Flyway en inglés**: `V{n}__{english_description}.sql`
- ✅ Nombrar **rutas de API en español**: `/usuarios/yo`, `/centros/cercanos`, `/donaciones/:codigoQr/confirmar`
- ✅ Nombrar **métodos de negocio en español**: `obtenerPermisos()`, `calcularCriticidad()`, `asignarRol()`
- ✅ Nombrar **componentes y hooks de frontend en inglés**: `UserList`, `DonationForm`, `EmergencyMap`, `useUsers`, `useCenters`
- ✅ Nombrar **enums de dominio en español**: `EstadoEmergencia`, `EstadoCriticidad`, `TipoMovimiento`

### NUNCA hacer:

- ❌ Nombrar clases de capa arquitectónica en español (`ServicioUsuario`, `ControladorEmergencia`, `RepositorioRol`) — usar inglés (`UserService`, `EmergencyController`, `RolRepository`)
- ❌ Nombrar entidades JPA en inglés (`User`, `Center`, `Donation`) — usar español (`Usuario`, `Centro`, `Donacion`) para que coincidan con las tablas
- ❌ Escribir rutas de API en inglés (`/users/me`, `/centers/nearby`) — usar español (`/usuarios/yo`, `/centros/cercanos`)
- ❌ Usar AWS RDS para PostgreSQL — la BD es un contenedor Docker **dentro del cluster EKS**
- ❌ Usar AWS ElastiCache para Redis — Redis es un contenedor Docker **dentro del cluster EKS**
- ❌ Usar API Gateway de AWS — el gateway es `ms-gateway` (Spring Cloud Gateway propio)
- ❌ Generar JWT propios (solo Firebase Auth)
- ❌ Hardcodear credenciales, tokens o URLs de producción
- ❌ Modificar migraciones Flyway ya aplicadas
- ❌ Crear routing keys de RabbitMQ nuevas sin documentarlas
- ❌ Implementar lógica de negocio en Controllers
- ❌ Ignorar errores abiertos en `errores.md` que afecten la tarea actual
- ❌ Revertir decisiones técnicas de `arreglos-y-cambios.md` sin justificación explícita y documentación del cambio
- ❌ Omitir tests en una tarea marcada como completada
- ❌ Usar distancia euclidiana para matching de voluntarios (siempre OSRM)
- ❌ Permitir cambio de umbrales de criticidad a roles distintos de ADMINISTRADOR

---

## 🔄 Secuencia de Fases — Referencia rápida

```
Fase 0 → Infra + Docker + Firebase + CI/CD
   ↓
Fase 1 → ms-identity (Firebase Auth + RBAC)
   ↓
Fase 2 → ms-resources (centros + inventario + criticidad)
   ↓
Fase 3 → ms-citizen (necesidades + donaciones + QR)
   ↓
Fase 4 → ms-logistics (transferencias + misiones + OSRM)
   ↓
Fase 5 → ms-emergencies (emergencias + anuncios)
   ↓
Fase 6 → ms-notifications (WebSocket + Lambda + SES)
   ↓
Fase 7 → RabbitMQ completo (Topic Exchange + DLQ + idempotencia)
   ↓
Fase 8 → Frontends (portal ciudadano + dashboard)
   ↓
Fase 9 → QA + hardening + despliegue EKS producción
```

**Regla:** No avanzar a la siguiente fase sin que los entregables de la actual estén completos y los tests pasando.

---

## 🧩 Cheatsheet de Decisiones Técnicas

| Pregunta                                     | Respuesta correcta                                                                |
| -------------------------------------------- | --------------------------------------------------------------------------------- |
| ¿Qué es el API Gateway?                    | `ms-gateway` — microservicio propio Spring Cloud Gateway, no AWS API Gateway   |
| ¿Dónde corre PostgreSQL?                   | Contenedor Docker **dentro del cluster EKS** (PVC para persistencia) — no AWS RDS |
| ¿Dónde corre Redis?                        | Contenedor Docker **dentro del cluster EKS** (PVC) — no AWS ElastiCache          |
| ¿Cómo valido permisos en endpoints?        | En `ms-identity` (etapa actual): `@PreAuthorize` por rol (`hasRole/hasAnyRole`) |
| ¿Dónde viven los permisos?                 | BD (`permisos` + `roles_permisos`), cacheados en Redis TTL 5min               |
| ¿Hay combinaciones de roles prohibidas?     | No — cualquier combinación es válida                                           |
| ¿En qué idioma van las clases y métodos?   | **Clases arquitectónicas en inglés**: `UserController`, `UserService`, `UserRepository`, `FirebaseTokenFilter` |
| ¿En qué idioma van las entidades JPA?       | **Español**, para coincidir con las tablas: `Usuario`, `Centro`, `Donacion`, `Emergencia` |
| ¿En qué idioma van los métodos de negocio? | **Español**: `obtenerPermisos()`, `calcularCriticidad()`, `asignarRol()`, `registrarMovimiento()` |
| ¿En qué idioma van los paquetes Java?      | **Inglés**: `controller/`, `service/`, `repository/`, `entity/`, `event/`, `exception/` |
| ¿En qué idioma van los archivos Flyway?    | **Inglés**: `V1__create_users_table.sql`, `V2__create_roles_permissions_tables.sql` |
| ¿En qué idioma van los componentes frontend? | **Inglés**: `UserList`, `DonationForm`, `EmergencyMap`, `InventoryDashboard` |
| ¿En qué idioma van los hooks frontend?      | **Inglés**: `useUsers`, `useCenters`, `useNotifications`, `useInventory` |
| ¿En qué idioma van los enums de dominio?    | **Español**: `EstadoEmergencia`, `EstadoCriticidad`, `TipoMovimiento`, `TipoDocumento` |
| ¿Cómo nombro un Controller?               | `UsuarioController`, `EmergenciaController`, `DonacionController` |
| ¿Cómo nombro un Service?                  | `UsuarioService`, `PermisosService`, `InventarioService`, `MatchingOSRMService` |
| ¿Cómo nombro un Repository?               | `UsuarioRepository`, `CentroRepository`, `DonacionRepository` |
| ¿Cómo nombro una entidad JPA?             | `Usuario`, `Centro`, `Donacion` (en español, coincide con la tabla) |
| ¿Cómo nombro un enum de estado?            | `EstadoEmergencia`, `EstadoCriticidad`, `TipoMovimiento` |
| ¿Cómo genero tokens JWT?                   | No los genero. Firebase Auth los emite.                                           |
| ¿Cómo valido el token en el backend?       | FirebaseTokenFilter con Firebase Admin SDK                                        |
| ¿Cómo calculo distancia entre centros?     | PostGIS ST_Distance con ::geography                                               |
| ¿Cómo hago matching de voluntarios?        | OSRM API para rutas viales reales                                                 |
| ¿Quién puede cambiar umbrales de stock?    | Solo rol ADMINISTRADOR                                                            |
| ¿Cómo evito procesar un evento dos veces?  | Idempotencia con Redis:`processed:{eventId}`                                    |
| ¿Qué hago si falla un consumidor RabbitMQ? | DLQ lo retiene, se reintenta hasta 3 veces                                        |
| ¿Cómo envío emails masivos?               | AWS Lambda consume EmailQueue → llama a AWS SES                                  |
| ¿Cómo actualizo el dashboard sin recargar? | TanStack Query hidratación SSR + refetchInterval fallback                        |
| ¿Cómo valido teléfonos internacionales?   | Zod regex E.164:`/^\+?[1-9]\d{6,14}$/`                                          |
| ¿Qué formato usan los errores de API?      | RFC 7807 — Problem Details siempre                                               |
| ¿Cómo migro la base de datos?              | Flyway: `V{n}__{english_name}.sql` — nunca modificar aplicadas                  |
| ¿Qué pasa si cae el WebSocket?             | TanStack Query activa polling automático cada 30s                                |

---

## 📝 Convenciones de Commits

```
feat(ms-resources): implementar motor de criticidad de inventario
fix(ms-logistics): corregir matching OSRM con capacidad de vehículo
refactor(ms-identity): extraer FirebaseTokenFilter a clase separada
test(ms-citizen): agregar tests de integración para flujo de donación con QR
docs(ms-emergencies): documentar endpoints en Swagger
chore(infra): actualizar Docker Compose con imagen RabbitMQ 3.13
```

Formato: `tipo(scope): descripción en presente, minúscula, sin punto final`

---

## 🆘 Cómo reportar un bloqueador

Si Claude encuentra un problema que impide continuar:

1. Crear entrada en `errores.md` con severidad 🔴 Crítico
2. Actualizar `avances.md` con la entrada de la sesión, marcando el bloqueador
3. Describir exactamente qué se intentó, qué falló y qué información falta
4. Sugerir 2–3 alternativas de solución para que el equipo decida

---

## 🗃️ Referencia de Tablas — Nombres Exactos BD

Usar **siempre** estos nombres de tabla al generar entidades JPA y migraciones Flyway:

| Tabla                         | Microservicio    | Entidad Java                |
| ----------------------------- | ---------------- | --------------------------- |
| `usuarios`                  | ms-identity      | `Usuario`                 |
| `roles`                     | ms-identity      | `Rol`                     |
| `usuarios_roles`            | ms-identity      | `UsuarioRol`              |
| `permisos`                  | ms-identity      | `Permiso`                 |
| `roles_permisos`            | ms-identity      | `RolPermiso`              |
| `organizaciones`            | ms-identity      | `Organizacion`            |
| `solicitudes_rol`           | ms-identity      | `SolicitudRol`            |
| `invitaciones_operador`     | ms-identity      | `InvitacionOperador`      |
| `emergencias`               | ms-emergencies   | `Emergencia`              |
| `anuncios`                  | ms-emergencies   | `Anuncio`                 |
| `centros`                   | ms-resources     | `Centro`                  |
| `operadores_centro`         | ms-resources     | `OperadorCentro`          |
| `catalogo_items`            | ms-resources     | `ItemCatalogo`            |
| `inventario`                | ms-resources     | `Inventario`              |
| `movimientos_inventario`    | ms-resources     | `MovimientoInventario`    |
| `registro_auditoria`        | ms-resources     | `RegistroAuditoria`       |
| `necesidades`               | ms-citizen       | `Necesidad`               |
| `donaciones`                | ms-citizen       | `Donacion`                |
| `items_donacion`            | ms-citizen       | `ItemDonacion`            |
| `transferencias`            | ms-logistics     | `Transferencia`           |
| `items_transferencia`       | ms-logistics     | `ItemTransferencia`       |
| `misiones`                  | ms-logistics     | `Mision`                  |
| `rutas_voluntario`          | ms-logistics     | `RutaVoluntario`          |
| `voluntarios_mision`        | ms-logistics     | `VoluntarioMision`        |
| `notificaciones`            | ms-notifications | `Notificacion`            |
| `preferencias_notificacion` | ms-notifications | `PreferenciaNotificacion` |
| `eventos_procesados`        | ms-notifications | `EventoProcesado`         |

---

*Este archivo es la fuente de verdad del comportamiento de Claude en el proyecto CatástrofesCL.*
*Versión: 1.5 | Última actualización: 2026-04-24 | Cambio: custom claims a array en español — consistencia total con plan-de-implementacion.md*
