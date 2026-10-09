# Box School — Decisiones y memoria del proyecto

> **Para quien retome este proyecto (persona o IA): lee este archivo primero y actualízalo al final de cada cambio relevante.**
> Aquí está el *por qué* de las decisiones. El *qué* está en el código; el hallazgo-por-hallazgo de la auditoría inicial está en [AUDIT_REPORT.md](AUDIT_REPORT.md).
>
> Reglas de mantenimiento:
> 1. Una decisión nueva = una entrada `D-0xx` (contexto → decisión → consecuencias). No se borran; si cambian, se marca `Reemplaza a D-0xx` y se actualiza el estado de la vieja.
> 2. Lo que está pendiente de que el dueño decida va en **Decisiones abiertas** (sección 4), no se asume en silencio.
> 3. Anota al final en el **Historial** (sección 6) qué se hizo y la fecha.

## 1. El proyecto

Sistema de gestión administrativa para una **escuela de boxeo**.

- **Estudiantes**: inscritos por un admin; activan su cuenta, ven su membresía y su historial de pagos, y (objetivo) pagan su mensualidad.
- **Admins**: gestionan estudiantes (crear, editar, deshabilitar/habilitar), registran pagos, definen precios, ven quién tiene la membresía vencida o por vencer.
- **Notificación**: a cada estudiante activo se le envía un correo cuando faltan **2 días** (configurable) para que venza su mensualidad.
- Fase actual: **backend terminado y listo para ajustes/funcionalidades nuevas; el frontend (Angular, `localhost:4200`) aún no existe.**

Dueño: Santiago Torres (proyecto personal / práctica profesional). Idioma: mensajes de la API y docs en español; código e identificadores en inglés.

## 2. Mapa técnico (resumen)

| Tema | Estado |
|---|---|
| Stack | Java 21, Spring Boot 3.5, Spring Security 6, Spring Data JPA, PostgreSQL, Flyway, JJWT 0.13, springdoc, Lombok, Maven (`./mvnw`) |
| Capas | `controller` → `service` (interfaz + `Impl`) → `repository`; `mapper` a mano; DTOs en `model/dto`; excepciones en `exception`; manejo global en `config/GlobalExceptionHandler` |
| Auth | JWT stateless (HS256, 120 min por defecto), roles `ROLE_ADMIN` / `ROLE_USER` |
| Esquema | Flyway en `src/main/resources/db/migration` (V1 inicial, V2 códigos de registro + notificaciones + índices). `ddl-auto=validate` |
| Config | `application.yml` versionado **sin secretos** (solo `${VARIABLES}`); plantilla en `.env.example`; perfil `dev` en `application-dev.yml` |
| Tests | `./mvnw test` → H2 en modo PostgreSQL + las mismas migraciones; 26 tests (flujo API completo, recordatorios, JWT, códigos, bootstrap) |
| Observabilidad | `/actuator/health` (público, con probes); logs SLF4J sin PII sensible |

### Contrato de API vigente (lo que consumirá el frontend)

Errores siempre: `{ timestamp, code, message, errors? }`. Listados: `Page` de Spring (`?page=&size=&sort=`, `size` máx. 100).

| Método y ruta | Acceso | Notas |
|---|---|---|
| `POST /api/auth/login` | público (rate limit) | → `{token, username, roles}` |
| `POST /api/auth/register` | público (rate limit) | body: `username, password, email, registrationCode` → 201 + token |
| `GET /api/students/me` | autenticado | perfil + `daysUntilExpiration`, `membershipExpired` |
| `GET /api/payments/me` | autenticado | historial propio |
| `GET /api/prices/active`, `/active/{type}` | autenticado | precios vigentes (`BIWEEKLY`, `MONTHLY`) |
| `POST /api/students` | ADMIN | → 201, incluye `registrationCode` (única vez) |
| `GET /api/students?q=&status=` | ADMIN | búsqueda por nombre/apellido/email y estado |
| `GET /api/students/{id}` · `PUT /api/students/{id}` | ADMIN | |
| `PATCH /api/students/{id}/deactivate` · `/activate` | ADMIN | también deshabilita/habilita su usuario |
| `POST /api/students/{id}/registration-code` | ADMIN | regenera el código (invalida el anterior) |
| `GET /api/students/expired` | ADMIN | activos con `expirationDate < hoy` |
| `GET /api/students/expiring?days=2` | ADMIN | activos que vencen entre hoy y hoy+days |
| `GET /api/payments` · `GET /api/payments/students/{id}` | ADMIN | |
| `POST /api/payments/students/{id}` | ADMIN | body `{type}`; monto y duración salen del precio vigente |
| `POST /api/prices` | ADMIN | desactiva el precio anterior del mismo tipo |
| `POST /api/notifications/membership-reminders/run` | ADMIN | dispara el envío ya (idempotente) |

## 3. Registro de decisiones

### D-001 · Stack y estilo de capas — *Vigente*
Spring Boot + JPA + PostgreSQL con capas controller/service/repository y DTOs. Servicios con interfaz + `Impl` (convención ya existente; se mantiene por consistencia). Mappers manuales (pocos modelos; MapStruct no se justifica aún).

### D-002 · Autenticación JWT solo de acceso — *Vigente (ver O-2)*
Token HS256 con `iss=box-school`, `sub=username`, claim informativo `roles`, vida configurable (`JWT_EXPIRATION_MINUTES`, 120 por defecto). **La autorización real se recarga de la BD en cada request** (filtro JWT → `UserDetailsService`), por eso deshabilitar a alguien surte efecto de inmediato. Costo: 1 consulta por request autenticada (aceptable hoy; cachear con TTL corto si hace falta). El secreto se valida al arrancar (≥ 32 bytes, Base64) y la app no arranca si es débil. Sin refresh token todavía.

### D-003 · Autorización en dos capas y autoservicio por "me" — *Vigente*
Capa 1: matchers por URL en `SecurityConfig` (`/api/students|payments|prices|notifications/**` = ADMIN, excepto los `GET …/me` y `/api/prices/active*`). Capa 2: `@PreAuthorize("hasRole('ADMIN')")` en cada método admin. El estudiante **nunca** pasa su propio id: usa `/me` (se resuelve por el usuario autenticado) → imposible un IDOR por diseño. 401 (sin/mal token) y 403 (sin permiso) devuelven JSON uniforme. *Reemplaza* la expresión SpEL `#studentId == authentication.principal.student.id`, que no funcionaba (el principal no tiene `student`).

### D-004 · Registro por invitación con código de un solo uso — *Vigente*
**Problema:** registrar con solo el email permitía a cualquiera que supiera el email de un estudiante apropiarse de su cuenta. **Decisión:** al crear un estudiante se genera un código (10 caracteres, `SecureRandom`, alfabeto sin ambiguos) que el admin ve **una sola vez** y que además se envía al correo del estudiante. En BD solo se guarda su hash SHA-256 y expiración (72 h, `REGISTRATION_CODE_HOURS`). `register` exige email + código; todos los fallos de identidad devuelven el mismo error genérico (anti-enumeración); el código se consume. El admin puede regenerarlo. Los administradores no se registran por API (ver D-010).

### D-005 · La membresía vencida se deriva, no se guarda — *Vigente*
`Status` = solo `ACTIVE | INACTIVE` (decisión administrativa: "deshabilitado"). "Vencida" = `expirationDate < hoy`, calculado en consultas y en el DTO (`membershipExpired`, `daysUntilExpiration`). Se eliminó `EXPIRED` (nunca se asignaba y habría quedado desincronizado). La V2 migra filas `EXPIRED` → `INACTIVE`.

### D-006 · Reglas de membresía y pagos — *Vigente*
- Estudiante nuevo: 1 mes de membresía desde su inscripción.
- Pago: si la membresía sigue vigente el nuevo periodo empieza en el vencimiento actual (no se pierden días pagados); si ya venció, empieza hoy. Fin = inicio + `durationDays` del precio.
- El monto **siempre** sale del precio activo, nunca del cliente. Solo estudiantes `ACTIVE` pueden pagar.
- Concurrencia: el estudiante se lee con `PESSIMISTIC_WRITE` al pagar (dos pagos simultáneos no se pisan el vencimiento); igual al reemplazar el precio activo de un tipo.
- Un tipo de precio nuevo desactiva el anterior; el historial de precios se conserva (los pagos referencian el precio con que se pagó).

### D-007 · Deshabilitar un estudiante corta su acceso; no se borran estudiantes — *Vigente*
`deactivate/activate` también cambian `users.enabled`; el filtro JWT exige `isEnabled()`, así un token aún no expirado deja de servir. **No hay DELETE de estudiantes**: tienen pagos (historial financiero); se deshabilitan. (El DELETE anterior fallaba con 500 por la FK.) Si algún día se necesita borrar por privacidad (LOPD/GDPR), hacerlo como proceso de anonimización, no como DELETE físico.

### D-008 · Recordatorios de vencimiento — *Vigente (ver O-6)*
`MembershipReminderScheduler` (cron `app.membership-reminder.cron`, por defecto 08:00 todos los días, en `app.timezone`) llama a `MembershipReminderService`, que avisa por **correo** a estudiantes `ACTIVE` cuyo vencimiento cae en `[hoy, hoy + N]` (N = `days-before-expiry`, 2). Idempotencia: antes de enviar se inserta una fila en `notification_logs` con clave única `(student, tipo, fecha_de_vencimiento)`; si el envío falla se borra para reintentar al día siguiente/ciclo siguiente; si el servidor estuvo caído el aviso sale igual al volver. Al renovar (cambia la fecha de vencimiento) corresponde un aviso nuevo. Procesa por lotes de 100 con paginación por `id`. Sin `SPRING_MAIL_HOST` la app arranca y los avisos se omiten con warning. En el frontend, `daysUntilExpiration` de `/students/me` permite además un banner dentro de la app. Mientras haya **una** instancia no hace falta nada más; con varias, la clave única evita duplicados (ShedLock sería solo para evitar trabajo repetido).

### D-009 · Tiempo — *Vigente*
Todo "hoy" sale del bean `Clock` (`AppConfig`), con zona `app.timezone` (`APP_TIMEZONE`, vacío = zona del servidor). Prohibido `LocalDate.now()` suelto: hace imposible testear y puede desfasar el día del vencimiento si el servidor está en UTC. **Ver O-4: falta fijar la zona real del negocio.**

### D-010 · Configuración y secretos — *Vigente*
`application.yml` **sí se versiona** (solo `${VARIABLES}`); antes estaba en `.gitignore` y el repo no era reproducible. Valores reales por variables de entorno (`.env.example` las lista); `application-local.yml` queda ignorado para overrides. Perfil `dev` activa SQL logging y Swagger; Swagger está **apagado por defecto** (`SWAGGER_ENABLED`). Sin credenciales por defecto en el código: el primer admin se crea solo si existen `BOOTSTRAP_ADMIN_USERNAME`/`PASSWORD` (≥ 12 caracteres) y no hay ningún admin (`AdminBootstrap`); los admins no tienen `Student` (`users.student_id` nullable). Variables de correo ahora son las estándar de Spring (`SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD`) — antes `MAIL_HOST…`; `LICENSE_REMINDER_CRON` → `REMINDER_CRON`; `app.license-expiry-reminder.*` → `app.membership-reminder.*`.

### D-011 · Rate limiting en memoria sobre `/api/auth/**` — *Vigente (revisar al escalar)*
10 peticiones/minuto/IP (`AUTH_RATE_LIMIT_PER_MINUTE`) → 429 con `Retry-After`. Ventana fija en memoria del proceso: suficiente para una instancia. **Al pasar a varias instancias hay que moverlo a Redis (p. ej. Bucket4j)**, o cada instancia permitirá su propio cupo. Detrás de proxy: `FORWARD_HEADERS_STRATEGY=native` para ver la IP real.

### D-012 · El esquema lo gobierna Flyway — *Vigente*
`ddl-auto=validate`. **Nunca editar una migración ya aplicada**; los cambios van en `V3__…sql`, etc. SQL compatible con PostgreSQL y H2 (para que los tests usen las mismas migraciones). Para una BD existente creada por Hibernate (`ddl-auto=update`): `baseline-on-migrate` + `baseline-version: 1` la toma como V1 y aplica V2. Cada entidad nueva/cambio de columna exige su migración o el test `contextLoads` falla (esa es la red de seguridad).

### D-013 · Contrato de errores — *Vigente*
`ApiErrorResponse {timestamp, code, message, errors?}`. Mapeo: validación 400 (errores por campo), regla de negocio 400, no encontrado 404, duplicado/integridad/lock optimista 409, credenciales 401, permisos 403, rate limit 429, cualquier otro 500 con mensaje genérico (el detalle solo va al log). `GlobalExceptionHandler` extiende `ResponseEntityExceptionHandler` para que JSON malformado, tipos incorrectos, métodos no permitidos, rutas inexistentes y `sort` inválido den su 4xx correcto en vez de 500. Credenciales: usuario inexistente y clave incorrecta devuelven exactamente lo mismo.

### D-014 · Rendimiento y paginación — *Vigente*
Todo listado es paginado (default 20, máx. 100). N+1 evitado con `@EntityGraph` en pagos (precio). Índices: `students(expiration_date)`, `students(status, expiration_date)`, `payments(student_id)`, `payments(payment_date)`, `prices(type, active)`. Servicios `@Transactional(readOnly = true)` por defecto, escritura marcada explícitamente. `open-in-view=false`. Filtros de búsqueda con `Specification` (el `%`/`_` del usuario se escapa).

### D-015 · Estrategia de tests — *Vigente (ver O-7)*
H2 modo PostgreSQL + Flyway real + `ddl-auto=validate`. `ApiFlowIntegrationTest` cubre seguridad (401/403/IDOR), registro, activación/desactivación, pagos, precios, búsquedas. `MembershipReminderIntegrationTest` cubre ventana, idempotencia y reintento (sin transacción de test, como en producción). **Límite conocido:** H2 no es PostgreSQL; el bloqueo `FOR UPDATE` y el baseline de una BD existente solo se verifican contra PostgreSQL real.

### D-016 · Convenciones — *Vigente*
Código en inglés, mensajes al usuario en español. Se corrigió `StudentNotFoundExcepcion` → `StudentNotFoundException` y `desactive` → `deactivate`. Emails y usernames se normalizan a minúsculas. Sin código comentado ni muerto. Cambios de contrato de API deben anotarse en la sección 2 de este archivo.

## 4. Decisiones abiertas (necesitan al dueño)

| ID | Tema | Estado / propuesta |
|---|---|---|
| **O-1** | **Pago en línea por el estudiante.** Hoy solo el admin registra pagos (efectivo/transferencia). El objetivo del negocio incluye que el estudiante pague solo. Falta elegir **pasarela** (depende del país: Wompi/PayU/MercadoPago/Stripe…). | Propuesta: interfaz `PaymentGateway`; `Payment` gana `status` (PENDING/PAID/FAILED), `method`, `provider_reference` única; `POST /api/payments/me/checkout` crea el cobro; webhook firmado (verificar firma, idempotente por referencia) confirma y recién entonces extiende la membresía. **Nunca** confiar en el redirect del navegador. |
| O-2 | Refresh token / duración de sesión | Hoy 120 min sin refresh. Propuesta: refresh opaco rotativo en BD cuando el frontend lo pida. |
| O-3 | Facturación | La entidad `Invoice` y su tabla existen pero no se usan. Definir si hay factura por pago (¿legal/electrónica según el país?) o se elimina. |
| O-4 | Zona horaria del negocio | Definir `APP_TIMEZONE` (afecta cuándo "vence hoy"). |
| O-5 | Recuperar contraseña | **No existe.** Propuesta: `POST /api/auth/forgot-password` (token de un solo uso por correo, hash en BD, expira 30 min) + `reset-password`. Prioridad alta antes de salir a producción. |
| O-6 | Varias instancias | Rate limit a Redis (D-011); opcional ShedLock para el scheduler (D-008). |
| O-7 | Tests contra PostgreSQL real | Agregar Testcontainers (requiere Docker) para verificar locks y baseline. |
| O-8 | CI/CD y despliegue | GitHub Actions con `./mvnw verify`; Dockerfile + compose (README lo lista como futuro). |
| O-9 | Auditoría de acciones admin | Quién desactivó/cobró/cambió precio y cuándo (tabla `audit_log`). |
| O-10 | Cambio de contraseña / editar perfil propio, y que el admin cree otros admins | Sin definir. |

## 5. Deuda técnica conocida

- `Invoice` sin uso (ver O-3).
- `Payment` no guarda método de pago ni estado (ver O-1).
- Búsquedas `LIKE '%texto%'` no usan índice; si la escuela supera miles de alumnos, evaluar `pg_trgm`.
- Mails en texto plano simple; plantillas HTML cuando haya diseño.
- Sin i18n de mensajes (todo español fijo).
- La BD local de desarrollo puede contener el usuario `admin` / `admin123` creado por el antiguo `DataSeeder`: **eliminarlo o cambiar su clave** y crear el admin con `BOOTSTRAP_ADMIN_*`.

## 6. Historial

| Fecha | Cambio |
|---|---|
| 2026-10-08 | Auditoría completa (seguridad, buenas prácticas, escalabilidad) → [AUDIT_REPORT.md](AUDIT_REPORT.md). Se corrigieron todos los hallazgos críticos/altos/medios, se implementaron recordatorios de vencimiento, precios, endpoints `me`, búsqueda, Flyway, rate limit, configuración por entorno y 26 tests. Se crea este archivo y `CLAUDE.md`. Se eliminaron `DataSeeder` (admin/admin123 fijo) y su test, sustituidos por `AdminBootstrap`. |
