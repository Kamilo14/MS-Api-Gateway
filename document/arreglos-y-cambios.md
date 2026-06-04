# Arreglos y Cambios — CatástrofesCL
> Este archivo documenta todos los cambios, correcciones y decisiones técnicas aplicadas durante el desarrollo.

---

## Formato de Registro

```
### [ARR-XXX] Título del Arreglo o Cambio
- **Fecha:** YYYY-MM-DD
- **Autor:** Nombre
- **Tipo:** Bugfix | Refactor | Feature | Config | Decisión técnica
- **Error relacionado:** ERR-XXX (si aplica)
- **Descripción del cambio:** Qué se modificó y por qué.
- **Archivos afectados:** Lista de archivos o clases modificadas.
- **Tests actualizados:** Sí / No / N/A
```

---

## Cambios Aplicados

### [ARR-011] Implementación de API Gateway (Fase 0.5)
- **Fecha:** 2026-05-05
- **Autor:** Claude
- **Tipo:** Feature
- **Error relacionado:** N/A
- **Descripción del cambio:** Se implementó `ms-gateway` usando Spring Cloud Gateway (reactivo). Se eliminaron las dependencias legacy WebMVC e integró validación Firebase Admin SDK (bloqueante manejado con Mono.fromCallable), rate limiting con Bucket4j en memoria y configuración de Headers de Seguridad. Además se implementó un `GatewayExceptionHandler` para devolver siempre formato RFC 7807 (Problem Details).
- **Archivos afectados:** `pom.xml`, `application.yml`, `GatewayApplication.java`, `FirebaseAuthenticationFilter.java`, `RateLimitingFilter.java`, `SecurityHeadersFilter.java`, `GatewayExceptionHandler.java`, `.env`
- **Tests actualizados:** N/A

### [ARR-010] Cierre técnico de Fase 1 con endpoints faltantes y suite de pruebas inicial
- **Fecha:** 2026-05-04
- **Autor:** Claude
- **Tipo:** Feature
- **Error relacionado:** N/A
- **Descripción del cambio:** Se completaron brechas para cierre de Fase 1: datasource por defecto alineado a `catastrofescl_db`, endpoint de listado habilitado en `/usuarios` (manteniendo `/usuarios/listar`), endpoints raíz de solicitudes de rol (`/solicitudes-rol`), incorporación de pruebas unitarias (`UsuarioService`, `PermisosService`, `RolService`) y prueba de integración con Testcontainers para flujo de registro/sync y carga de permisos. Se actualizó el plan de implementación incluyendo migración `V8__seed_default_registered_role.sql`.
- **Archivos afectados:** `application.yml`, `UsuarioController.java`, `SolicitudRolController.java`, `src/test/java/...`, `plan-de-implementacion.md`, `avances.md`
- **Tests actualizados:** Sí

### [ARR-009] Simplificación de autorización a solo roles en MS Identity
- **Fecha:** 2026-05-04
- **Autor:** Claude
- **Tipo:** Security
- **Error relacionado:** N/A
- **Descripción del cambio:** Se eliminó la validación por permisos en `@PreAuthorize` de endpoints administrativos y se dejó autorización únicamente por rol (`hasRole('ADMINISTRADOR')`) en `AuthController`, `UsuarioController`, `RolController` y `PermisoController`. Se actualizó documentación para reflejar el enfoque por rol en esta etapa del MS.
- **Archivos afectados:** `AuthController.java`, `UsuarioController.java`, `RolController.java`, `PermisoController.java`, `plan-de-implementacion.md`, `CLAUDE.md`, `especificaciones-tecnicas.md`, `avances.md`
- **Tests actualizados:** N/A

### [ARR-008] Refuerzo de autorización híbrida por rol y permiso en endpoints administrativos
- **Fecha:** 2026-05-04
- **Autor:** Claude
- **Tipo:** Security
- **Error relacionado:** N/A
- **Descripción del cambio:** Se reforzaron endpoints administrativos para exigir rol `ADMINISTRADOR` además del permiso `USUARIO_GESTIONAR` en controladores de roles, permisos e invitación de operador. También se actualizó la documentación para reflejar estrategia híbrida de autorización (`hasAuthority` + `hasRole/hasAnyRole`) en endpoints críticos.
- **Archivos afectados:** `AuthController.java`, `RolController.java`, `PermisoController.java`, `plan-de-implementacion.md`, `CLAUDE.md`, `especificaciones-tecnicas.md`, `avances.md`
- **Tests actualizados:** N/A

### [ARR-007] Endpoint de registro unificado Firebase Auth + BD local
- **Fecha:** 2026-05-04
- **Autor:** Claude
- **Tipo:** Feature
- **Error relacionado:** N/A
- **Descripción del cambio:** Se implementó `POST /auth/register` para crear cuentas en Firebase Auth desde backend y sincronizarlas inmediatamente en PostgreSQL con rol `REGISTRADO` por defecto. Se agregó DTO de registro con validaciones, se habilitó el endpoint como público en seguridad y se incorporó manejo de errores de Firebase Auth en `ProblemDetail`.
- **Archivos afectados:** `AuthController.java`, `AuthService.java`, `SecurityConfig.java`, `GlobalExceptionHandler.java`, `dto/request/RegistroFirebaseRequest.java`, `especificaciones-tecnicas.md`, `plan-de-implementacion.md`, `avances.md`
- **Tests actualizados:** N/A

### [ARR-006] Fallback de caché local para evitar fallo por Redis en desarrollo
- **Fecha:** 2026-05-04
- **Autor:** Claude
- **Tipo:** Config
- **Error relacionado:** ERR-006
- **Descripción del cambio:** Se hizo configurable `spring.cache.type` mediante variable de entorno y se definió `SPRING_CACHE_TYPE=simple` en `.env` para entorno local. Con esto, `POST /auth/firebase/sync` deja de depender de Redis para pruebas locales y sincroniza correctamente hacia PostgreSQL.
- **Archivos afectados:** `src/main/resources/application.yml`, `.env`, `errores.md`, `avances.md`
- **Tests actualizados:** N/A

### [ARR-005] Implementación del trigger Firebase Auth onCreate para sync automático
- **Fecha:** 2026-05-04
- **Autor:** Claude
- **Tipo:** Feature
- **Error relacionado:** N/A
- **Descripción del cambio:** Se implementó una Cloud Function `auth.user().onCreate` (`syncIdentityOnUserCreate`) para invocar automáticamente `POST /auth/firebase/sync/system` al crear usuarios en Firebase Auth. El endpoint y secreto de integración se consumen por variables de entorno (`IDENTITY_SYNC_BASE_URL`, `FIREBASE_SYNC_SECRET`) sin hardcodear credenciales.
- **Archivos afectados:** `firebase-functions/index.js`, `firebase-functions/package.json`
- **Tests actualizados:** N/A

### [ARR-004] Sync Firebase tolerante a usuarios existentes por correo
- **Fecha:** 2026-05-04
- **Autor:** Claude
- **Tipo:** Bugfix
- **Error relacionado:** ERR-005
- **Descripción del cambio:** Se ajustó `AuthService` para que la sincronización de usuarios (`/auth/firebase/sync` y `/auth/firebase/sync/system`) busque primero por `firebaseUid` y, si no existe, por `correo`. Cuando encuentra un usuario por correo, lo actualiza y vincula el `firebaseUid`, evitando errores 500 por duplicidad de correo.
- **Archivos afectados:** `AuthService.java`, `errores.md`
- **Tests actualizados:** N/A

### [ARR-003] Habilitación explícita de Firebase Auth en entorno local
- **Fecha:** 2026-05-04
- **Autor:** Claude
- **Tipo:** Config
- **Error relacionado:** ERR-004
- **Descripción del cambio:** Se habilitó `FIREBASE_ENABLED=true` en `.env` para registrar `FirebaseTokenFilter` y permitir autenticación real con `idToken` en endpoints protegidos (`/auth/firebase/sync`).
- **Archivos afectados:** `.env`, `errores.md`, `avances.md`
- **Tests actualizados:** N/A

### [ARR-002] Rol base sin privilegios para usuarios nuevos y aclaración de endpoints de sync
- **Fecha:** 2026-05-04
- **Autor:** Claude
- **Tipo:** Feature
- **Error relacionado:** ERR-003
- **Descripción del cambio:** Se agregó el rol `REGISTRADO` como rol por defecto para cuentas nuevas sincronizadas desde Firebase, sin permisos iniciales. Además, se documentó directamente en `AuthController` cuál endpoint es para sincronización manual desde cliente autenticado y cuál endpoint es técnico para integración automática vía `X-Sync-Secret`.
- **Archivos afectados:** `AuthService.java`, `AuthController.java`, `V8__seed_default_registered_role.sql`, `errores.md`, `especificaciones-tecnicas.md`, `avances.md`
- **Tests actualizados:** N/A

### [ARR-001] Sincronización automática de usuarios Firebase hacia MS Identity
- **Fecha:** 2026-04-24
- **Autor:** Claude
- **Tipo:** Feature
- **Error relacionado:** ERR-002
- **Descripción del cambio:** Se implementó un endpoint técnico `POST /auth/firebase/sync/system` para sincronizar usuarios creados en Firebase Auth hacia la BD local mediante trigger externo (Cloud Function o integrador), protegido por `X-Sync-Secret` (`FIREBASE_SYNC_SECRET`). Además, se estandarizó el manejo de errores de seguridad/validación para responder `403` y `400` en lugar de `500`.
- **Archivos afectados:** `AuthController.java`, `AuthService.java`, `SyncFirebaseSystemRequest.java`, `SecurityConfig.java`, `GlobalExceptionHandler.java`, `application.yml`, `collect.json`, `especificaciones-tecnicas.md`
- **Tests actualizados:** N/A

### [DEC-013] Convención de nomenclatura por capa arquitectónica
- **Fecha:** 2026-04-24
- **Autor:** Equipo
- **Tipo:** Decisión técnica — estándares de código
- **Descripción:** Se establece que las **clases de capa arquitectónica** siguen convención en **inglés** (estándar Spring Boot/Java/React): `UserController`, `UserService`, `UserRepository`, `FirebaseTokenFilter`, `InsufficientStockException`, `StockCriticalEvent`. Las **entidades JPA** se nombran en **español** para coincidir 1:1 con las tablas de BD (`Usuario`, `Centro`, `Donacion`). Los **métodos de negocio** y **rutas de API** van en español. El **frontend** (componentes, hooks, schemas, tipos TypeScript) sigue convención estándar en inglés (`UserList`, `useUsers`, `donorSchema`).
- **Archivos afectados:** `CLAUDE.md`, `especificaciones-tecnicas.md`, `plan-de-implementacion.md`
- **Tests actualizados:** N/A

### [DEC-010] API Gateway como microservicio propio (Spring Cloud Gateway) — sin AWS API Gateway
- **Fecha:** 2026-04-19
- **Autor:** Equipo
- **Tipo:** Decisión técnica — arquitectura de entrada
- **Descripción:** El API Gateway no es un servicio de AWS sino un microservicio propio `ms-gateway` implementado con Spring Cloud Gateway. Tiene su propio repositorio GitHub, su propio pipeline CI/CD y corre como pod en EKS en el puerto 8080. Responsabilidades: validación del token Firebase, enrutamiento a los 6 MS de negocio, rate limiting con Bucket4j, CORS global y headers de seguridad.
- **Archivos afectados:** `especificaciones-tecnicas.md`, `CLAUDE.md`, `plan-de-implementacion.md`
- **Tests actualizados:** Pendiente — Fase 0.5

### [DEC-011] PostgreSQL y Redis como contenedores Docker — sin RDS ni ElastiCache AWS
- **Fecha:** 2026-04-19
- **Autor:** Equipo
- **Tipo:** Decisión técnica — persistencia e infraestructura
- **Descripción:** PostgreSQL 15 + PostGIS y Redis se despliegan como contenedores Docker dentro del cluster EKS, no como servicios administrados de AWS (no RDS, no ElastiCache). En local se levantan con Docker Compose. En EKS usan PersistentVolumeClaim para persistir datos entre reinicios de pods. Esto reduce costos y simplifica la infraestructura. **Upgrade path:** migrar a RDS multi-AZ y ElastiCache es una decisión pendiente del equipo para fases futuras.
- **Archivos afectados:** `especificaciones-tecnicas.md` secciones 2.4 y 8, `CLAUDE.md`, `plan-de-implementacion.md`
- **Tests actualizados:** N/A

### [DEC-012] Estructura de repositorios — un repositorio por microservicio
- **Fecha:** 2026-04-19
- **Autor:** Equipo
- **Tipo:** Decisión técnica — estructura de repositorios
- **Descripción:** Cada microservicio tiene su propio repositorio GitHub independiente (ms-identity, ms-emergencies, ms-resources, ms-citizen, ms-logistics, ms-notifications). Los frontends también son repositorios separados (frontend-info, frontend-dashboard). La infraestructura compartida vive en `catastrofescl-infra`. Cada repo tiene su propio pipeline CI/CD, historial de cambios y puede hacer deploy independiente.
- **Archivos afectados:** `plan-de-implementacion.md` — Fase 0 y notas transversales
- **Tests actualizados:** N/A

### [DEC-009] Base de datos PostgreSQL compartida entre todos los microservicios
- **Fecha:** 2026-04-19
- **Autor:** Equipo
- **Tipo:** Decisión técnica — persistencia
- **Descripción:** Todos los microservicios apuntan a una sola instancia `catastrofescl_db` desplegada como **contenedor Docker en EKS** (con PersistentVolumeClaim). Cada MS accede únicamente a las tablas de su dominio. Ningún MS hace JOIN con tablas de otro dominio — si necesita datos externos los obtiene vía API REST o evento RabbitMQ. Esta decisión es pragmática para la etapa actual; en el futuro se puede evolucionar a BD por microservicio o migrar a AWS RDS (ver DEC-011 para upgrade path).
- **Archivos afectados:** `plan-de-implementacion.md`, `especificaciones-tecnicas.md` sección 8
- **Tests actualizados:** N/A

### [DEC-008] Modelo de roles N:M con permisos granulares en BD
- **Fecha:** 2026-04-19
- **Autor:** Equipo
- **Tipo:** Decisión técnica — extensión del modelo de roles
- **Error relacionado:** N/A
- **Descripción del cambio:** Se eliminó la tabla `combinaciones_roles_prohibidas` (cualquier combinación de roles es válida). Se agregaron dos tablas nuevas: `permisos` (catálogo de permisos granulares por módulo, gestionables desde UI) y `roles_permisos` (asignación N:M de permisos a roles). Los permisos se cargan desde BD al autenticar, se cachean en Redis (TTL 5min) y se evalúan con `@PreAuthorize("hasAuthority('CODIGO_PERMISO')")` en lugar de `hasRole()`. Los custom claims de Firebase almacenan el array de roles: `{ "roles": ["AUTORIDAD", "VOLUNTARIO"] }`; los permisos se resuelven en runtime desde BD.
- **Archivos afectados:**
  - `especificaciones-tecnicas.md` — sección 12, dominio Identidad
  - `CLAUDE.md` — tabla de entidades + cheatsheet
  - Migraciones Flyway: `V3__create_permissions_tables.sql` + `V4__seed_permissions.sql`
- **Impacto en código:**
  - Nuevo `ServicioPermisos.java` — carga y cachea permisos por usuario desde BD
  - `FirebaseTokenFilter.java` — después de extraer roles, carga permisos desde `ServicioPermisos`
  - Todos los `@PreAuthorize` usan `hasAuthority('CODIGO')` en lugar de `hasRole('ROL')`
  - Eliminar `ServicioUsuarioRol.validarCombinacion()` — ya no es necesario
- **Tests actualizados:** Pendiente — actualizar en Fase 1
- **Fecha:** 2026-04-19
- **Autor:** Equipo
- **Tipo:** Decisión técnica — cambio de modelo de datos
- **Error relacionado:** N/A
- **Descripción del cambio:** Se eliminó el campo `rol varchar` de la tabla `usuarios` y se creó una estructura N:M compuesta por tres tablas nuevas: `roles` (catálogo de los 5 roles del sistema), `usuarios_roles` (asignación múltiple de roles a un usuario) y `combinaciones_roles_prohibidas` (restricciones de incompatibilidad entre roles). La motivación es permitir que un usuario tenga múltiples roles simultáneos (ej: AUTORIDAD + VOLUNTARIO). Los permisos siguen gestionándose en Spring Security, no en BD. Firebase custom claims pasa ahora un array `"roles": ["AUTORIDAD","VOLUNTARIO"]` en lugar de un string simple.
- **Archivos afectados:**
  - `especificaciones-tecnicas.md` — sección 12, dominio Identidad
  - `CLAUDE.md` — tabla de referencia de entidades
  - Migración Flyway: reemplazar `V1__create_users_table.sql` por versión sin columna `rol` + nueva `V2__create_roles_tables.sql`
### [DEC-007] Modelo N:M para roles de usuario (reemplaza campo `rol` simple)
- **Fecha:** 2026-04-19
- **Autor:** Equipo
- **Tipo:** Decisión técnica — cambio de modelo de datos
- **Error relacionado:** N/A
- **Descripción del cambio:** Se eliminó el campo `rol varchar` de la tabla `usuarios` y se creó una estructura N:M compuesta por: `roles` (catálogo de los 5 roles del sistema), `usuarios_roles` (asignación múltiple de roles a un usuario). La motivación es permitir que un usuario tenga múltiples roles simultáneos (ej: AUTORIDAD + VOLUNTARIO). Firebase custom claims pasa ahora un array `"roles": ["AUTORIDAD","VOLUNTARIO"]` en lugar de un string simple.
- **Archivos afectados:**
  - `especificaciones-tecnicas.md` — sección 12, dominio Identidad
  - `CLAUDE.md` — tabla de referencia de entidades
  - Migración Flyway: reemplazar `V1__create_users_table.sql` por versión sin columna `rol` + nueva `V2__create_roles_permissions_tables.sql`
- **Impacto en código:**
  - `Usuario.java` → eliminar campo `rol`, agregar relación `@ManyToMany` con `Rol`
  - `FirebaseTokenFilter.java` → leer array `roles` del custom claim en lugar de string
  - `ServicioUsuarioRol.java` → nuevo servicio para asignación de roles
  - `@PreAuthorize` existentes → usar `hasAuthority('CODIGO_PERMISO')` (ver DEC-008)
- **Tests actualizados:** Pendiente — actualizar en Fase 1

---

## Decisiones Técnicas Documentadas

### [DEC-001] Firebase Auth como proveedor de identidad
- **Fecha:** 2026-04-19
- **Decisión:** Usar Firebase Authentication en lugar de implementar JWT propio con Spring Security.
- **Razón:** Firebase Auth provee gestión robusta de tokens, refresh automático, verificación de email, y soporta múltiples providers (email/password, Google) out-of-the-box. Los custom claims de Firebase permiten almacenar los roles del usuario directamente en el token (array: `{ "roles": ["AUTORIDAD", "VOLUNTARIO"] }`).
- **Impacto:** MS Identidad valida tokens Firebase con Firebase Admin SDK en lugar de generar JWT propios.

### [DEC-002] Base de datos compartida en fase inicial
- **Fecha:** 2026-04-19
- **Decisión:** Una sola instancia PostgreSQL+PostGIS como **contenedor Docker en EKS** (con PersistentVolumeClaim) para todos los microservicios. Ver DEC-011 para upgrade path a RDS.
- **Razón:** Simplifica las relaciones entre dominios y mantiene consistencia transaccional sin incrementar la complejidad operativa en esta etapa del proyecto.
- **Impacto:** Cada microservicio accede solo a sus tablas de dominio. En futuras iteraciones se puede evolucionar a BD independiente por microservicio o migrar a AWS RDS multi-AZ.

### [DEC-003] OSRM para matching de voluntarios
- **Fecha:** 2026-04-19
- **Decisión:** Usar OSRM en lugar de distancia euclidiana para el matching de voluntarios de transporte.
- **Razón:** La distancia "en línea recta" es inadecuada para Chile, donde la geografía montañosa, ríos y accidentes geográficos hacen que la distancia real vial sea muy diferente a la euclidiana. OSRM usa datos reales de OpenStreetMap.
- **Impacto:** MS Logística hace llamadas HTTP al OSRM API. En producción se recomienda self-hosting en EC2.

### [DEC-004] Umbrales de criticidad configurables solo por Administrador
- **Fecha:** 2026-04-19
- **Decisión:** Solo el rol Administrador puede modificar los umbrales de stock por centro de acopio.
- **Razón:** Los umbrales determinan cuándo se generan alertas críticas y sugerencias de redistribución. Una configuración incorrecta podría generar falsas alarmas o ignorar escasez real. El rol Autoridad puede ver pero no modificar.
- **Impacto:** Endpoint `PATCH /centers/:id/thresholds` protegido con `@PreAuthorize("hasRole('ADMIN')")`.

### [DEC-005] TanStack Query con hidratación SSR para datos en tiempo real
- **Fecha:** 2026-04-19
- **Decisión:** Usar hidratación de servidor (SSR → Client) con TanStack Query v5 en lugar de recargar la página para actualizar datos en tiempo real.
- **Razón:** Durante emergencias, el panel de autoridades y el mapa público deben actualizarse sin interrumpir la experiencia del usuario. La hidratación permite tener datos frescos del servidor en el primer render y luego actualizar silenciosamente en background.
- **Impacto:** Uso de `HydrationBoundary` en server components + `useQuery` con `refetchInterval` en client components.

---

## Plantilla Rápida

```markdown
### [ARR-001] 
- **Fecha:** 
- **Autor:** 
- **Tipo:** 
- **Error relacionado:** 
- **Descripción del cambio:** 
- **Archivos afectados:** 
- **Tests actualizados:** 
```
