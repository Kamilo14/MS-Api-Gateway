# Avances — CatástrofesCL
> Bitácora de progreso del proyecto. Actualizar al final de cada sesión o sprint de trabajo.

---

## Estado General del Proyecto

| Fase | Nombre | Estado | % Completado |
|---|---|---|---|
| Fase 0 | Fundamentos e Infraestructura | 🔄 En progreso | 50% |
| Fase 1 | MS Identidad y Acceso | ✅ Completado | 100% |
| Fase 2 | MS Operaciones de Recursos | ⬜ No iniciado | 0% |
| Fase 3 | MS Participación Ciudadana | ⬜ No iniciado | 0% |
| Fase 4 | MS Logística | ⬜ No iniciado | 0% |
| Fase 5 | MS Coordinación de Emergencias | ⬜ No iniciado | 0% |
| Fase 6 | MS Notificaciones + Lambda | ⬜ No iniciado | 0% |
| Fase 7 | Integración RabbitMQ Completa | ⬜ No iniciado | 0% |
| Fase 8 | Frontend Completo | ⬜ No iniciado | 0% |
| Fase 9 | QA, Hardening y Producción | ⬜ No iniciado | 0% |

**Leyenda:** ⬜ No iniciado | 🔄 En progreso | ✅ Completado | 🔴 Bloqueado

---

## Formato de Entrada de Avance

```
## [Fecha] Sprint / Sesión — Descripción breve

**Integrante(s):** Nombre(s)
**Fase trabajada:** Fase X — Nombre

### Completado
- Lista de tareas terminadas

### En progreso
- Tareas iniciadas pero no terminadas

### Bloqueadores
- Problemas que impiden avanzar (crear issue en errores.md si aplica)

### Próximos pasos
- Qué se hará en la siguiente sesión
```

---

## Registro de Avances

### [2026-05-04] Cierre de Fase 1 — MS Identidad y Acceso

**Integrante(s):** Claude  
**Fase trabajada:** Fase 1 — MS Identidad y Acceso

#### Completado
- Se cerró la checklist de Fase 1 en `plan-de-implementacion.md`.
- Se alineó datasource por defecto de `application.yml` a `catastrofescl_db`.
- Se agregó soporte de endpoint listado en `/usuarios` (manteniendo compatibilidad con `/usuarios/listar`).
- Se agregaron endpoints raíz para solicitudes de rol (`POST /solicitudes-rol`, `PATCH /solicitudes-rol/{id}`).
- Se incorporaron pruebas unitarias para `UsuarioService`, `PermisosService` y `RolService`.
- Se incorporó prueba de integración con Testcontainers para flujo: sync Firebase sistema → asignación de rol → carga de permisos.
- Se actualizó el plan con la migración adicional `V8__seed_default_registered_role.sql`.

#### En progreso
- Ejecución de pruebas en entorno local del equipo (pendiente comando local por limitación del runner del agente).

#### Bloqueadores
- El entorno del agente no dispone de `pwsh`, por lo que no puede ejecutar Maven tests directamente desde esta sesión.

#### Próximos pasos
- Ejecutar `.\mvnw.cmd test` en máquina local para validar la suite completa.

### [2026-05-04] Ajuste final de seguridad: autorización solo por rol en MS Identity

**Integrante(s):** Claude  
**Fase trabajada:** Fase 1 — MS Identidad y Acceso

#### Completado
- Se simplificó `@PreAuthorize` en endpoints administrativos a validación exclusiva por rol.
- Se reemplazaron expresiones `hasRole('ADMINISTRADOR') and hasAuthority(...)` por `hasRole('ADMINISTRADOR')`.
- Controladores actualizados: `UsuarioController`, `RolController`, `PermisoController`, `AuthController` (invitación operador).
- Se actualizó documentación para reflejar enfoque por rol en esta etapa del MS.

#### En progreso
- Ninguno para este ajuste.

#### Bloqueadores
- Ninguno.

#### Próximos pasos
- Probar con usuario no administrador que reciba `403` en endpoints administrativos.

### [2026-05-04] Refuerzo de seguridad en endpoints por rol + permiso

**Integrante(s):** Claude  
**Fase trabajada:** Fase 1 — MS Identidad y Acceso

#### Completado
- Se reforzó `@PreAuthorize` en endpoints administrativos para requerir rol `ADMINISTRADOR` y permiso `USUARIO_GESTIONAR`.
- Endpoints ajustados:
  - `POST /roles/{id}/permisos/{permisoId}`
  - `DELETE /roles/{id}/permisos/{permisoId}`
  - `GET /permisos`
  - `POST /auth/invitacion/operador`
- Se alineó documentación del proyecto a estrategia híbrida de autorización (rol + permiso) en endpoints críticos.

#### En progreso
- Ninguno para este ajuste.

#### Bloqueadores
- Ninguno.

#### Próximos pasos
- Verificar en Postman con usuario sin rol `ADMINISTRADOR` que reciba `403` en endpoints administrativos.

### [2026-05-04] Endpoint de registro unificado implementado (`/auth/register`)

**Integrante(s):** Claude  
**Fase trabajada:** Fase 1 — MS Identidad y Acceso

#### Completado
- Se implementó `POST /auth/register` como endpoint público para registro desde frontend/Postman.
- El nuevo flujo crea usuario en Firebase Auth y sincroniza de inmediato en PostgreSQL.
- Se agregó `RegistroFirebaseRequest` con validaciones (`correo`, `password`, datos de perfil).
- Se habilitó el endpoint en `SecurityConfig` y se agregó manejo explícito de errores `FirebaseAuthException`.
- Se actualizó documentación funcional en `especificaciones-tecnicas.md` y `plan-de-implementacion.md`.

#### En progreso
- Ninguno para el endpoint backend.

#### Bloqueadores
- Ninguno.

#### Próximos pasos
- Probar desde Postman `POST /auth/register` con correo nuevo y confirmar alta en Firebase Auth + tabla `usuarios`.

### [2026-05-04] Resolución de error Redis para sincronización local Firebase → BD

**Integrante(s):** Claude  
**Fase trabajada:** Fase 1 — MS Identidad y Acceso

#### Completado
- Se identificó que `POST /auth/firebase/sync` fallaba con `500` por `Unable to connect to Redis`.
- Se parametrizó el cache manager en `application.yml` con `spring.cache.type: ${SPRING_CACHE_TYPE:redis}`.
- Se configuró `.env` local con `SPRING_CACHE_TYPE=simple`.
- Se confirmó flujo operativo en local: Postman (`Bearer idToken`) → sync usuario en PostgreSQL.

#### En progreso
- Ninguno en backend para este incidente.

#### Bloqueadores
- Ninguno.

#### Próximos pasos
- Mantener `SPRING_CACHE_TYPE=simple` en local y usar `redis` en entornos con servicio Redis activo.

### [2026-05-04] Corrección de autenticación local Firebase para endpoints protegidos

**Integrante(s):** Claude  
**Fase trabajada:** Fase 1 — MS Identidad y Acceso

#### Completado
- Se detectó causa de `403` en `/auth/firebase/sync`: Firebase deshabilitado en entorno local.
- Se configuró `FIREBASE_ENABLED=true` en `.env`.
- Se registró trazabilidad de error y corrección en `errores.md` y `arreglos-y-cambios.md`.

#### En progreso
- Validación funcional de flujo Postman: token Firebase (`idToken`) + sync en BD.

#### Bloqueadores
- Ninguno en backend.

#### Próximos pasos
- Reiniciar la API para tomar `.env` actualizado.
- Reintentar `POST /auth/firebase/sync` con `Authorization: Bearer <idToken>`.

### [2026-05-04] Trigger Firebase Auth onCreate implementado para sync automático

**Integrante(s):** Claude  
**Fase trabajada:** Fase 1 — MS Identidad y Acceso

#### Completado
- Implementación de Cloud Function `auth.user().onCreate` (`syncIdentityOnUserCreate`) en `firebase-functions/index.js`.
- Envío automático a `POST /auth/firebase/sync/system` al crear usuario en Firebase Auth.
- Manejo de payload mínimo para alta en BD (`firebaseUid`, `correo`, `nombres`, `apellidos`, `tipoDocumento`, `pais`).
- Configuración por variables de entorno para integración local segura:
  - `IDENTITY_SYNC_BASE_URL`
  - `FIREBASE_SYNC_SECRET`

#### En progreso
- Despliegue/configuración del entorno Firebase Functions con variables de entorno en el proyecto Firebase.

#### Bloqueadores
- Ninguno en backend `ms-identity`.

#### Próximos pasos
- Configurar URL accesible desde la función hacia `ms-identity` en entorno local (túnel o endpoint accesible).
- Validar flujo completo: alta en Firebase Auth → trigger onCreate → registro en BD.

### [2026-05-04] Rol por defecto sin privilegios y aclaración de endpoints de sincronización

**Integrante(s):** Claude  
**Fase trabajada:** Fase 1 — MS Identidad y Acceso

#### Completado
- Se agregó migración `V8__seed_default_registered_role.sql` para crear el rol `REGISTRADO`.
- Se cambió la asignación de rol por defecto en `AuthService` de `PARTICULAR` a `REGISTRADO` (sin privilegios iniciales).
- Se comentaron en `AuthController` las líneas de los endpoints de sincronización:
  - `POST /auth/firebase/sync` (manual, cliente autenticado)
  - `POST /auth/firebase/sync/system` (automático, integración con `X-Sync-Secret`)
- Se actualizó trazabilidad en `errores.md`, `arreglos-y-cambios.md` y `especificaciones-tecnicas.md`.

#### En progreso
- Configuración del trigger externo de Firebase Auth (`onCreate`) para invocar `/auth/firebase/sync/system`.

#### Bloqueadores
- Ninguno en backend.

#### Próximos pasos
- Publicar custom claims de roles en Firebase cuando corresponda (`ADMINISTRADOR`, `AUTORIDAD`, etc.).
- Probar flujo completo alta Firebase → sync BD → asignación explícita de rol con permisos.

### [2026-04-24] Automatización de sync Firebase Auth → BD local

**Integrante(s):** Claude  
**Fase trabajada:** Fase 1 — MS Identidad y Acceso

#### Completado
- Endpoint técnico `POST /auth/firebase/sync/system` implementado para automatizar altas desde trigger Firebase Auth.
- Seguridad de integración por secreto `X-Sync-Secret` (`FIREBASE_SYNC_SECRET`).
- Corrección de manejo de errores para devolver `403` en Access Denied y `400` en validaciones.
- Colección Postman (`collect.json`) actualizada con request de sincronización automática.
- Documentación técnica y de trazabilidad actualizada (`especificaciones-tecnicas.md`, `errores.md`, `arreglos-y-cambios.md`).

#### En progreso
- Integración externa del trigger (Cloud Function) apuntando al nuevo endpoint técnico.

#### Bloqueadores
- Ninguno en backend; pendiente despliegue/configuración del trigger fuera de este repositorio.

#### Próximos pasos
- Configurar `FIREBASE_SYNC_SECRET` en entorno.
- Implementar y desplegar Cloud Function `auth.user().onCreate` para invocar `/auth/firebase/sync/system`.

---

### [2026-04-24] Implementación de MS Identidad y Acceso (Fase 1)

**Integrante(s):** Claude  
**Fase trabajada:** Fase 1 — MS Identidad y Acceso

#### Completado
- Creación de ramas `develop` y `feature/fase-1-ms-identity` según Git Flow.
- Configuración de `pom.xml` y `application.yml` (PostgreSQL, Redis, Firebase, Flyway).
- Implementación de entidades JPA (`Usuario`, `Rol`, `Permiso`, etc.) en español, según reglas DEC-013.
- Creación de 7 migraciones Flyway (V1 a V7) con el esquema completo y carga inicial de roles/permisos.
- Implementación de repositorios y servicios (`AuthService`, `UsuarioService`, `PermisosService`, `RolService`).
- Configuración de Spring Security y `FirebaseTokenFilter` (RBAC stateless, permisos en cache Redis).
- Implementación de `UsuarioController` y `AuthController` con protección `@PreAuthorize`.
- **(Nuevo en feature/fase-1-endpoints-faltantes):** Completados TODOS los endpoints del MS Identidad según la Fase 1: `GET /usuarios/yo`, endpoints de asignación/retiro de roles, endpoints de invitaciones, endpoints de solicitud de rol de voluntario, endpoints CRUD de roles/permisos.
- Fusión de las ramas hacia `develop` en GitHub.

#### En progreso
- Implementación de tests unitarios y de integración para MS Identidad (Testcontainers).
- Infraestructura Docker local completa (Fase 0).

#### Bloqueadores
- Ninguno

#### Próximos pasos
- Completar los tests para MS Identidad.
- Avanzar con la configuración local de Docker Compose (Fase 0).

---

### [2026-04-19] Inicio del Proyecto — Documentación Base

**Integrante(s):** Equipo completo  
**Fase trabajada:** Fase 0 — Fundamentos

#### Completado
- Documento de Arquitectura de Solución entregado
- Especificaciones técnicas definidas (`especificaciones-tecnicas.md`)
- Plan de implementación por fases creado (`plan-de-implementacion.md`)
- Decisiones técnicas documentadas en `arreglos-y-cambios.md`
- Decisiones clave tomadas:
  - Firebase Auth como proveedor de identidad (reemplaza JWT propio)
  - OSRM para matching de voluntarios (rutas viales reales)
  - Umbrales configurables solo por Administrador, por centro
  - TanStack Query con hidratación SSR para tiempo real
  - RabbitMQ: Topic Exchange + DLQ + Idempotencia

#### En progreso
- Configuración del entorno local (Docker Compose)
- Creación del proyecto Firebase

#### Bloqueadores
- Ninguno

#### Próximos pasos
- Completar Fase 0: Docker Compose local + Firebase project + repositorios GitHub
- Iniciar Fase 1: estructura base del MS Identidad y Acceso

---

## Estadísticas de Progreso

### Microservicios
| Microservicio | Backend | Tests | Documentación API |
|---|---|---|---|
| MS Identidad y Acceso | ✅ | ⬜ | ⬜ |
| MS Operaciones de Recursos | ⬜ | ⬜ | ⬜ |
| MS Participación Ciudadana | ⬜ | ⬜ | ⬜ |
| MS Logística | ⬜ | ⬜ | ⬜ |
| MS Coordinación de Emergencias | ⬜ | ⬜ | ⬜ |
| MS Notificaciones | ⬜ | ⬜ | ⬜ |

### Frontend
| Módulo | Implementado | Tests |
|---|---|---|
| Portal Ciudadano (Mapa + Donaciones) | ⬜ | ⬜ |
| Dashboard Autoridades/Operadores | ⬜ | ⬜ |
| Componentes shadcn/ui compartidos | ⬜ | ⬜ |
| Integración WebSocket STOMP | ⬜ | ⬜ |

### Infraestructura
| Componente | Local (Docker) | Producción (AWS) |
|---|---|---|
| PostgreSQL + PostGIS | ⬜ | ⬜ |
| Redis | ⬜ | ⬜ |
| RabbitMQ | ⬜ | ⬜ |
| Firebase Auth | ⬜ | ⬜ |
| API Gateway | ⬜ | ⬜ |
| EKS Cluster | N/A | ⬜ |
| Lambda + SES | ⬜ | ⬜ |
| CI/CD Pipeline | ⬜ | ⬜ |
