# Registro de Errores — CatástrofesCL
> Este archivo documenta errores encontrados durante el desarrollo. Actualizar en cada sesión de trabajo.

---

## Formato de Registro

```
### [ERR-XXX] Título del Error
- **Fecha:** YYYY-MM-DD
- **Microservicio/Módulo:** ms-identity | ms-resources | ms-logistics | ms-citizen | ms-emergencies | ms-notifications | frontend-info | frontend-dashboard | infra
- **Severidad:** 🔴 Crítico | 🟡 Medio | 🟢 Menor
- **Estado:** Abierto | En revisión | Resuelto
- **Descripción:** Qué ocurre y cuándo ocurre.
- **Causa raíz:** Por qué ocurre (si se identificó).
- **Solución aplicada:** Ver arreglos-y-cambios.md #ARR-XXX
```

---

## Errores Activos

*(Sin errores registrados — proyecto en inicio)*

---

## Errores Resueltos

### [ERR-006] Error 500 en `/auth/firebase/sync` por dependencia de Redis en entorno local
- **Fecha:** 2026-05-04
- **Microservicio/Módulo:** ms-identity
- **Severidad:** 🟡 Medio
- **Estado:** Resuelto
- **Descripción:** Las llamadas desde Postman a `POST /auth/firebase/sync` devolvían `500 Internal Server Error` con `debugMessage: "Unable to connect to Redis"`, impidiendo sincronizar usuarios de Firebase hacia PostgreSQL local.
- **Causa raíz:** Configuración fija de caché en Redis (`spring.cache.type=redis`) sin instancia Redis disponible en entorno local.
- **Solución aplicada:** Se parametrizó el tipo de caché y se configuró entorno local con `SPRING_CACHE_TYPE=simple`, eliminando la dependencia de Redis para pruebas locales. Ver `arreglos-y-cambios.md` #ARR-006.

### [ERR-005] Error 500 al sincronizar usuario Firebase ya existente por correo
- **Fecha:** 2026-05-04
- **Microservicio/Módulo:** ms-identity
- **Severidad:** 🟡 Medio
- **Estado:** Resuelto
- **Descripción:** `POST /auth/firebase/sync` devolvía `500` cuando el usuario no existía por `firebaseUid` pero sí por `correo`, generando conflicto de unicidad al intentar crear registro duplicado.
- **Causa raíz:** La búsqueda previa a creación solo consideraba `firebaseUid`.
- **Solución aplicada:** Se ajustó el flujo de sync para buscar usuario por `firebaseUid` o por `correo`; si existe, se actualiza y se vincula el `firebaseUid`. Ver `arreglos-y-cambios.md` #ARR-004.

### [ERR-004] `403 Access Denied` en `/auth/firebase/sync` por Firebase deshabilitado en local
- **Fecha:** 2026-05-04
- **Microservicio/Módulo:** ms-identity
- **Severidad:** 🟡 Medio
- **Estado:** Resuelto
- **Descripción:** Las peticiones autenticadas con Bearer token a `/auth/firebase/sync` devolvían 403 pese a usar un `idToken` válido.
- **Causa raíz:** `firebase.enabled` quedaba en `false` por defecto (sin `FIREBASE_ENABLED=true` en entorno), por lo que `FirebaseTokenFilter` no se registraba y no se establecía autenticación en el contexto de seguridad.
- **Solución aplicada:** Se habilitó Firebase en entorno local con `FIREBASE_ENABLED=true` en `.env`, permitiendo validación de token y autenticación para endpoints protegidos.

### [ERR-003] Usuarios de Firebase sin rol efectivo para endpoints administrativos
- **Fecha:** 2026-05-04
- **Microservicio/Módulo:** ms-identity
- **Severidad:** 🟡 Medio
- **Estado:** Resuelto
- **Descripción:** Usuarios autenticados en Firebase obtenían `403 Access Denied` al consumir endpoints administrativos porque el flujo de sincronización inicial asignaba un rol de negocio y no quedaba explícito el endpoint técnico de sincronización automática.
- **Causa raíz:** Faltaba un rol base explícito para cuentas nuevas sin privilegios y faltaba señalización en código de los endpoints de sincronización (`/auth/firebase/sync` y `/auth/firebase/sync/system`).
- **Solución aplicada:** Se agregó el rol por defecto `REGISTRADO` (sin permisos), se actualizó la asignación por defecto en `AuthService` y se documentaron en `AuthController` los endpoints de sincronización manual y automática. Ver `arreglos-y-cambios.md` #ARR-002.

### [ERR-002] Sincronización no automática de usuarios Firebase a BD local
- **Fecha:** 2026-04-24
- **Microservicio/Módulo:** ms-identity
- **Severidad:** 🟡 Medio
- **Estado:** Resuelto
- **Descripción:** Los usuarios creados en Firebase Authentication no aparecían automáticamente en la tabla `usuarios`, y los rechazos de seguridad se reportaban como error 500 genérico.
- **Causa raíz:** No existía un endpoint técnico para sincronización automática vía trigger; además, `AccessDeniedException` y errores de validación caían en el handler global genérico.
- **Solución aplicada:** Se creó `POST /auth/firebase/sync/system` con `X-Sync-Secret` y se agregaron handlers específicos para `AccessDeniedException` (403) y `MethodArgumentNotValidException` (400). Ver `arreglos-y-cambios.md` #ARR-001.

### [ERR-001] Falla de arranque por validación de esquema en `usuarios.pais`
- **Fecha:** 2026-04-24
- **Microservicio/Módulo:** ms-identity
- **Severidad:** 🔴 Crítico
- **Estado:** Resuelto
- **Descripción:** La API no iniciaba al activar Firebase. Spring Boot caía durante la creación del `EntityManagerFactory`.
- **Causa raíz:** Incompatibilidad entre el tipo de columna en PostgreSQL y el mapeo esperado por Hibernate en `usuarios.pais` (`bpchar/CHAR(2)` en BD vs `VARCHAR(2)` esperado en validación).
- **Solución aplicada:** Ajuste del tipo de columna en BD para alinear esquema y entidad (`ALTER TABLE usuarios ALTER COLUMN pais TYPE VARCHAR(2) USING TRIM(pais);` + default `CL`).

---

## Plantilla Rápida

```markdown
### [ERR-001] 
- **Fecha:** 
- **Microservicio/Módulo:** 
- **Severidad:** 
- **Estado:** Abierto
- **Descripción:** 
- **Causa raíz:** 
- **Solución aplicada:** Pendiente
```
