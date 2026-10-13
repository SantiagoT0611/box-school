# Box School — Decisiones y memoria del proyecto

> **Para quien retome este proyecto (persona o IA): lee este archivo primero y actualízalo al final de cada cambio relevante.**
> Aquí está el *por qué* de las decisiones. El *qué* está en el código; el hallazgo-por-hallazgo de la auditoría inicial está en [AUDIT_REPORT.md](AUDIT_REPORT.md). **Qué sigue y en qué orden: [ROADMAP.md](ROADMAP.md).** Informes de auditoría por fase: [audits/](audits/).
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
`MembershipReminderScheduler` (cron `app.membership-reminder.cron`, por defecto 08:00 todos los días, en `app.timezone`) llama a `MembershipReminderService`, que avisa por **correo** a estudiantes `ACTIVE` cuyo vencimiento cae en `[hoy, hoy + N]` (N = `days-before-expiry`, 2). Idempotencia: antes de enviar se inserta una fila en `notification_logs` con clave única `(student, tipo, fecha_de_vencimiento)`; si el envío falla se borra para reintentar al día siguiente/ciclo siguiente; si el servidor estuvo caído el aviso sale igual al volver. Al renovar (cambia la fecha de vencimiento) corresponde un aviso nuevo. Procesa por lotes de 100 con paginación por `id`. Entre un correo y el siguiente hace una pausa (`app.membership-reminder.delay-between-emails-ms`, 1000 ms por defecto) porque los proveedores SMTP limitan los envíos por segundo; si aun así uno falla, el registro se libera y se reintenta en el siguiente ciclo (verificado contra Mailtrap: el plan gratis rechaza ráfagas con `550 Too many emails per second`). Limitación conocida: los correos de código de registro (`MailService.sendQuietly`) son «dispara y olvida» y un fallo solo se loguea; el admin siempre ve el código en la respuesta y puede regenerarlo. **Tras la auditoría de la Fase 0:** el reclamo se libera ante *cualquier* fallo, incluso un `Error` (`finally`); la pausa se aplica también tras un fallo; un candado en memoria impide ejecuciones solapadas (job + disparo manual) y `POST /api/notifications/membership-reminders/run` responde **202** al instante y corre en segundo plano (409 si ya hay una en curso). Solo un corte duro del proceso (`kill -9`) puede dejar un reclamo huérfano. Sin `SPRING_MAIL_HOST` la app arranca y los avisos se omiten con warning (`POST …/run` responde 409 `mail-not-configured`, no un 202 engañoso). Una interrupción del hilo (apagado) corta el lote en vez de enviar el resto en ráfaga. En el frontend, `daysUntilExpiration` de `/students/me` permite además un banner dentro de la app. Mientras haya **una** instancia no hace falta nada más; con varias, la clave única evita duplicados (ShedLock sería solo para evitar trabajo repetido).

### D-009 · Tiempo — *Vigente*
Todo "hoy" sale del bean `Clock` (`AppConfig`), con zona `app.timezone` (`APP_TIMEZONE`, vacío = zona del servidor). Prohibido `LocalDate.now()` suelto: hace imposible testear y puede desfasar el día del vencimiento si el servidor está en UTC. **Ver O-4: falta fijar la zona real del negocio.**

### D-010 · Configuración y secretos — *Vigente*
`application.yml` **sí se versiona** (solo `${VARIABLES}`); antes estaba en `.gitignore` y el repo no era reproducible. Valores reales por variables de entorno (`.env.example` las lista); `application-local.yml` queda ignorado para overrides. Perfil `dev` activa SQL logging y Swagger; Swagger está **apagado por defecto** (`SWAGGER_ENABLED`). Sin credenciales por defecto en el código: el primer admin se crea solo si existen `BOOTSTRAP_ADMIN_USERNAME`/`PASSWORD` (≥ 12 caracteres) y no hay ningún admin (`AdminBootstrap`); los admins no tienen `Student` (`users.student_id` nullable). Variables de correo ahora son las estándar de Spring (`SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD`) — antes `MAIL_HOST…`; `LICENSE_REMINDER_CRON` → `REMINDER_CRON`; `app.license-expiry-reminder.*` → `app.membership-reminder.*`.

### D-011 · Rate limiting en memoria sobre `/api/auth/**` — *Vigente (revisar al escalar)*
10 peticiones/minuto/IP (`AUTH_RATE_LIMIT_PER_MINUTE`) → 429 con `Retry-After`. Ventana fija en memoria del proceso: suficiente para una instancia. **Decide por la ruta decodificada** (`getServletPath()`): `/api/%61uth/login` llega al mismo controlador y antes no se contaba (auditoría Fase 0, A-1); tiene test sobre Tomcat real (`RateLimitIntegrationTest`). Cada bloqueo registra IP y ruta en el log. **Al pasar a varias instancias hay que moverlo a Redis (p. ej. Bucket4j)**, o cada instancia permitirá su propio cupo. Detrás de proxy: `FORWARD_HEADERS_STRATEGY=native` para ver la IP real.

### D-012 · El esquema lo gobierna Flyway — *Vigente*
`ddl-auto=validate`. **Nunca editar una migración ya aplicada**; los cambios van en `V3__…sql`, etc. SQL compatible con PostgreSQL y H2 (para que los tests usen las mismas migraciones). Para una BD existente creada por Hibernate (`ddl-auto=update`): `baseline-on-migrate` + `baseline-version: 1` la toma como V1 y aplica V2. Cada entidad nueva/cambio de columna exige su migración o el test `contextLoads` falla (esa es la red de seguridad). **Verificado en PostgreSQL real (2026-10-08):** la base de desarrollo creada por Hibernate tenía restos que V1 no contemplaba (columna `payments.amount`, FK a la tabla `price`, sin UNIQUE en `students.email`, usernames con mayúsculas); se resolvieron con V3 (normalizar), V4 (columna heredada) y **V5 (migración Java `db.migration.V5__repair_legacy_schema`)**, que compara con el esquema esperado, repara y es idempotente. Las migraciones que dependen del estado real de la base se escriben en Java (SQL condicional no es portable PostgreSQL/H2) y llevan test que reproduce el esquema heredado. **Excepciones aceptadas tras la auditoría de la Fase 0 (M-5, M-6):** V3 y V4 no se editan (cambiarían su checksum y romperían la base que ya las aplicó). V4 borra `payments.amount` sin verificar que el dato esté en `amount_paid`, y V3/V4 confirman antes de que V5 detecte emails duplicados por mayúsculas. **Antes de adoptar con Flyway una base heredada que NO sea la del dueño** (ya migrada), ejecutar: `select count(*) from payments where amount_paid is null or amount_paid = 0;` (debe ser 0 o los datos de `amount` ya estar copiados) y `select lower(email), count(*) from students group by 1 having count(*) > 1;` (debe estar vacío). Las migraciones destructivas futuras llevan precondición en Java. `baseline-on-migrate` queda **apagado** por defecto (`FLYWAY_BASELINE_ON_MIGRATE=true` solo en la primera adopción de una base heredada), para no marcar un baseline en una base equivocada.

### D-013 · Contrato de errores — *Vigente*
`ApiErrorResponse {timestamp, code, message, errors?}`. Mapeo: validación 400 (errores por campo), regla de negocio 400, no encontrado 404, duplicado/integridad/lock optimista 409, credenciales 401, permisos 403, rate limit 429, cualquier otro 500 con mensaje genérico (el detalle solo va al log). `GlobalExceptionHandler` extiende `ResponseEntityExceptionHandler` para que JSON malformado, tipos incorrectos, métodos no permitidos, rutas inexistentes y `sort` inválido den su 4xx correcto en vez de 500. Credenciales: usuario inexistente y clave incorrecta devuelven exactamente lo mismo.

### D-014 · Rendimiento y paginación — *Vigente*
Todo listado es paginado (default 20, máx. 100). N+1 evitado con `@EntityGraph` en pagos (precio). Índices: `students(expiration_date)`, `students(status, expiration_date)`, `payments(student_id)`, `payments(payment_date)`, `prices(type, active)`. Servicios `@Transactional(readOnly = true)` por defecto, escritura marcada explícitamente. `open-in-view=false`. Filtros de búsqueda con `Specification` (el `%`/`_` del usuario se escapa).

### D-015 · Estrategia de tests — *Vigente*
H2 modo PostgreSQL + Flyway real + `ddl-auto=validate`. `ApiFlowIntegrationTest` cubre seguridad (401/403/IDOR), registro, activación/desactivación, pagos, precios, búsquedas. `MembershipReminderIntegrationTest` cubre ventana, idempotencia y reintento (sin transacción de test, como en producción). `PostgresLegacyUpgradeTest` (Testcontainers + `postgres:16-alpine`) cubre lo que H2 no puede: actualización de una base heredada (`legacy-schema.sql` reproduce lo que dejó Hibernate), restricciones reales y pagos simultáneos con `FOR UPDATE`; se omite sin Docker. Los tests nuevos de esquema/concurrencia deben ir ahí, no en H2. Se comprobó por mutación que detectan el fallo que tuvo la base real. **Tests herméticos (M-9):** todo test de Spring lleva `@HermeticTest` (perfil `test` + `@TestPropertySource` con prioridad sobre las variables de entorno): ninguna variable de la máquina (`JWT_SECRET`, `SPRING_MAIL_*`, `APP_TIMEZONE`) cambia su resultado ni envía correos reales, y el perfil `test` se superpone al `application.yml` REAL, así que también se ejercita la configuración de producción (Swagger apagado, sondas, errores sin mensaje, límites). Además `HermeticTest` fija `spring.datasource.*` y aborta el contexto si la URL no es H2/localhost (re-auditoría N-3), y `ConfigurationDefaultsTest` lee el `application.yml` real SIN superponer el perfil `test` para vigilar los valores por defecto peligrosos (baseline, zona horaria, Swagger, health.mail, formcontent, errores). `REQUIRE_DOCKER=true ./mvnw test` falla si Docker no está disponible, en vez de omitir en silencio los tests de PostgreSQL real (usar en CI y antes de cerrar una fase). Cuidado: si se renombra un archivo de `src/test/resources`, ejecutar `mvn clean` (un `application.yml` viejo en `target/test-classes` tapaba la configuración real). Nota: Flyway 11 avisa que PostgreSQL 18 (el de desarrollo) es más nuevo que su última versión probada (17); funciona, pero conviene actualizar Flyway al subir de versión de Spring Boot.

### D-016 · Convenciones — *Vigente*
Código en inglés, mensajes al usuario en español. Se corrigió `StudentNotFoundExcepcion` → `StudentNotFoundException` y `desactive` → `deactivate`. Emails y usernames se normalizan a minúsculas. Sin código comentado ni muerto. Cambios de contrato de API deben anotarse en la sección 2 de este archivo.

### D-017 · Pagos con dos modalidades: zona manual (QR + cuenta) y pasarela opcional — *Decidida; implementación en ROADMAP Fase 2*
**Contexto:** la escuela opera en **Colombia** (COP). Las pasarelas candidatas (**Wompi** o **PayU**) cobran comisión y el cliente puede no querer pagarla. **Decisión:** el sistema debe funcionar completo sin pasarela. Modalidades: (1) **efectivo** registrado por el admin; (2) **transferencia manual**: una "zona de pago" muestra el **QR del banco, número de cuenta y datos de transferencia** (configurables por el admin en `payment_settings`), el estudiante reporta su pago (referencia + comprobante opcional) y el admin lo aprueba o rechaza; (3) **pasarela** (Wompi primero, PayU por la misma interfaz `PaymentGateway`), activable con un flag. Las modalidades 2 y 3 se encienden/apagan por separado. Se construye primero la 2 porque no depende de terceros.

### D-018 · Un pago solo extiende la membresía cuando queda `APPROVED` — *Decidida*
`Payment` tendrá `status` (`PENDING|APPROVED|REJECTED`) y `method` (`CASH|BANK_TRANSFER|GATEWAY`). La extensión del vencimiento (cálculo de periodo + bloqueo `findByIdForUpdate`, hoy en `PaymentServiceImpl.payMembership`) se mueve a un único `approve(payment)` usado por las tres modalidades. **Nunca** se aprueba por el redirect del navegador ni por datos del cliente; el webhook de la pasarela exige firma válida **y** consulta de la transacción a la API de la pasarela, e idempotencia por `reference`. El monto siempre sale del precio vigente.

### D-019 · Puerta de calidad obligatoria al cerrar cada fase — *Decidida (regla permanente)*
Ninguna fase del ROADMAP se da por cerrada sin una **auditoría independiente y escéptica** del código nuevo (subagente separado, sin el historial de cómo se escribió) aplicando `code-audit`, `backend-security`, `backend-scalability` y `backend-best-practices`, más "intentos de romperlo" por escrito, con un **informe detallado** en `docs/audits/FASE-N-*.md` ([plantilla](audits/TEMPLATE.md)). Cierre = 0 hallazgos críticos/altos abiertos, medios corregidos o con excepción registrada, tests en verde. **Motivo:** la auditoría base la hizo la misma sesión que implementó las correcciones (sesgo de confirmación); el dueño pidió rigor y escepticismo explícitos en seguridad y buenas prácticas. Detalle del procedimiento: [ROADMAP.md §2](ROADMAP.md).

### D-020 · El frontend no se inicia hasta recibir los mockups del dueño — *Decidida (bloqueo)*
El dueño enviará **mockups** y de ahí saldrán las decisiones de tipografía, diseño y UI/UX. Hasta entonces no se crea el proyecto Angular ni se eligen paleta, fuentes o componentes. Al recibirlos: skill `ui-ux-engineer` → `docs/DESIGN.md` → registrar aquí → recién crear el frontend (consume el contrato de la sección 2). El backend se mantiene listo (contrato estable, CORS configurable, errores uniformes). Cada módulo del frontend también pasa por la puerta de calidad (D-019) adaptada.

### D-021 · Un solo precio activo por tipo, garantizado por la base de datos — *Vigente*
`SELECT … FOR UPDATE` no alcanza con READ COMMITTED (la segunda transacción no ve el precio recién insertado): un doble clic en «guardar precio» dejaba dos activos y TODOS los pagos de ese tipo daban 500. V6 añade `prices.active_type` (= tipo mientras el precio está activo, `NULL` si no) con índice `UNIQUE`: portable PostgreSQL/H2 (H2 no soporta índices parciales) y los `NULL` no chocan. La entidad lo sincroniza (`@PrePersist/@PreUpdate`). El perdedor de la carrera recibe 409; el invariante se prueba en PostgreSQL real con peticiones concurrentes. **Limitación aceptada (re-auditoría R-1):** `active_type` solo se mantiene mientras los cambios pasen por la entidad; un `UPDATE`/`INSERT` manual sobre `prices` debe actualizar también `active_type` (si no, 409 permanente o dos activos). No existe ningún `UPDATE` JPQL/nativo sobre `prices` en el código.

### D-022 · Endpoints públicos mínimos y errores sin autenticación — *Vigente*
Solo `GET /actuator/health/liveness` y `/readiness` son públicos (no tocan SMTP ni datos); el agregado `/actuator/health` exige autenticación y `management.health.mail.enabled=false` (el indicador abría una conexión SMTP por consulta anónima: amplificación y falsas alarmas). El despacho `ERROR` (`/error`) se permite sin autenticar para que 400/404/500 se vean como tales (no como un 401 engañoso); no filtra nada porque `server.error.include-message/stacktrace=never`. `/actuator/**` (incluido el índice y el health agregado) es solo ADMIN salvo las dos sondas. Un fallo de acceso a datos al validar el JWT responde **503**, no 401 (una caída de la BD no debe cerrar la sesión de todos).

### D-023 · Límites de entrada — *Vigente*
Cuerpo máximo de **64 KB** en `/api/**` (413 con `Content-Length`; lectura cortada si es chunked). El filtro es de servlet con `HIGHEST_PRECEDENCE` y `spring.mvc.formcontent.filter.enabled=false` (la API es solo JSON): sin eso `OrderedFormContentFilter` copiaba a memoria el cuerpo completo de cualquier `PUT/PATCH/DELETE` de formulario, anónimo y antes de la cadena de seguridad (OOM; re-auditoría N-1). y strings JSON de máximo 100 000 caracteres (Jackson). Precios: duración ≤ 366 días, monto ≤ 8 enteros y 2 decimales. Contraseña ≤ 72 **bytes** (BCrypt). El token JWT debe traer `exp` e `iss`. Los códigos de registro se guardan con HMAC-SHA256 y una pimienta derivada de `jwt.secret`: un volcado de la BD no permite probar candidatos offline; rotar `jwt.secret` —o actualizar desde la versión que usaba SHA-256 sin clave— invalida los códigos pendientes (basta regenerarlos).

## 4. Decisiones abiertas (necesitan al dueño)

| ID | Tema | Estado / propuesta |
|---|---|---|
| **O-1** | **Pago en línea por el estudiante.** Hoy solo el admin registra pagos. | **Decidida en parte** → D-017/D-018 (Colombia; zona manual QR+cuenta con aprobación del admin + pasarela Wompi/PayU opcional). **Pendiente del dueño:** cuenta sandbox y decisión sobre la comisión (solo para la pasarela); datos bancarios y QR reales (para la zona manual). Plan en ROADMAP Fase 2. |
| O-2 | Refresh token / duración de sesión | Hoy 120 min sin refresh. Propuesta: refresh opaco rotativo en BD cuando el frontend lo pida (ROADMAP Fase 3). |
| O-3 | Facturación | La entidad `Invoice` y su tabla existen pero no se usan. Definir si hay factura por pago (¿electrónica DIAN?) o se elimina (ROADMAP Fase 3). |
| O-4 | Zona horaria del negocio | **Resuelta:** `America/Bogota`, fijada como `APP_TIMEZONE` (2026-10-08). |
| O-5 | Recuperar contraseña | **No existe.** Es la **siguiente tarea** tras verificar en PostgreSQL (ROADMAP Fase 1): `forgot-password` (token de un solo uso por correo, hash en BD, expira 30 min) + `reset-password` + `change-password`, invalidando JWT anteriores. |
| O-6 | Varias instancias | Rate limit a Redis (D-011); opcional ShedLock para el scheduler (D-008). |
| O-7 | Tests contra PostgreSQL real | **Resuelta (2026-10-08):** Testcontainers con `PostgresLegacyUpgradeTest`. Pendiente solo decidir si CI (Fase 4) ejecutará esos tests (requiere Docker en el runner; GitHub Actions lo trae). |
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
| 2026-10-12 | **Re-auditoría independiente de la Fase 0** (commit `d3157e8`): A-1/A-2/A-3 confirmados cerrados; abrió N-1 (alto: límite de cuerpo evadible con `PUT` de formulario → OOM anónimo), N-2 (`register`/`regenerate` pisaban pagos), N-3 (hermetismo sin datasource), N-4 (12 correcciones sin test). Corregidos todos (+ R-3..R-12, ver ROADMAP) con 16 tests nuevos y 8 mutaciones verificadas: **77 tests, 0 omitidos**. D-009: `APP_TIMEZONE` en blanco = `America/Bogota`. Informe: [audits/FASE-0-reauditoria.md](audits/FASE-0-reauditoria.md). Pendiente: verificación final del auditor para cerrar la fase. |
| 2026-10-11 | **Correcciones de la auditoría de la Fase 0** (puerta aún pendiente de re-auditoría): A-1 rate limit por ruta decodificada; A-2 V6 precio activo único (D-021); A-3 solo sondas públicas (D-022); M-1 bloqueo de fila al editar/activar; M-3 cotas de precio; M-4 recordatorios 202 + candado; M-7 503 ante caída de BD; M-8 límite de cuerpo (D-023); M-9 tests herméticos (D-015); M-11 `.env.example`, `APP_TIMEZONE` por defecto `America/Bogota`, `baseline-on-migrate` apagado; B-2, B-3, B-4, B-5, B-6, B-11 (`User.student` LAZY), B-12 (`Locale.ROOT`), B-13, B-14 (HMAC). Excepciones registradas: M-5/M-6 (D-012). Aplazados: M-2 (idempotencia de pagos → Fase 2 con `reference` UNIQUE). Numeración futura: recuperar contraseña = V7, pagos = V8. |
| 2026-10-10 | **Auditoría independiente de la Fase 0** (subagente aislado, sin historial; alcance: diff de la fase + todo el backend). Resultado: **fase REABIERTA** — 🔴 0 · 🟠 3 · 🟡 11 · 🔵 17. Altos (todos de la línea base): rate limit evadible con una letra codificada en la URL (A-1), doble envío de precio deja 2 activos y bloquea los pagos (A-2), `/actuator/health` público abre SMTP por petición (A-3). Sin forma de saltarse autenticación/autorización ni de leer datos ajenos (32 ataques). Informe: [audits/FASE-0-verificacion-postgres.md](audits/FASE-0-verificacion-postgres.md). Correcciones pendientes en ROADMAP Fase 0; M-5 y M-6 candidatas a excepción (V3/V4 no se editan). |
| 2026-10-08 | **Fase 0 (textos de correo):** el dueño confirmó en su bandeja de Mailtrap la llegada de los 6 correos de prueba. Se corrigieron tildes y el formato de fecha (`dd/MM/yyyy`) y se añadió `MailServiceTest` (remitente, UTF-8 con tildes, comportamiento sin correo configurado). **Para uso real definir `MAIL_FROM`** con una dirección válida: sin él se usa `SPRING_MAIL_USERNAME` como remitente (en Mailtrap es un identificador, no un email). 34 tests. |
| 2026-10-08 | **Fase 0 (correo):** prueba con Mailtrap sobre una copia de la base: admin creado por `BOOTSTRAP_ADMIN_*` en PostgreSQL real, código de registro y recordatorios enviados por SMTP, reintento tras el rechazo por límite de envíos e idempotencia verificados. Nueva pausa configurable entre correos (D-008). Pendiente: confirmación del dueño en su bandeja y la auditoría independiente. |
| 2026-10-08 | **Fase 0 (continuación):** eliminado el usuario `admin` viejo de la base real; `APP_TIMEZONE=America/Bogota`; Testcontainers con PostgreSQL real (`PostgresLegacyUpgradeTest`, 3 tests, validados por mutación); 31 tests. Pendiente: correo real con Mailtrap y la auditoría independiente de la fase. |
| 2026-10-08 | **Fase 0 (parcial):** verificación en PostgreSQL 18 real con la base existente. Hallazgos y correcciones: V3 (normalizar usernames/emails; sin esto `Salito`/`Sofi` no podían entrar), V4 y V5 (restos de `ddl-auto=update` que rompían todo pago y dejaban `students.email` sin UNIQUE). 6 pagos simultáneos correctos en PostgreSQL. Migraciones aplicadas a la base real (respaldo previo). Nuevo `scripts/smoke_test.py`. 28 tests. Numeración futura: recuperar contraseña = V6, pagos = V7. Detalle en ROADMAP Fase 0. |
| 2026-10-08 | Se crea [ROADMAP.md](ROADMAP.md) (fases 0–5) y [audits/](audits/) con plantilla de informe. Decisiones del dueño: Colombia, pagos en dos modalidades (zona manual QR+cuenta y pasarela Wompi/PayU opcional) → D-017/D-018; puerta de calidad obligatoria por fase con auditor independiente → D-019; frontend bloqueado hasta recibir mockups → D-020. Orden elegido: verificar en PostgreSQL → recuperar contraseña → pagos. |
| 2026-10-08 | Auditoría completa (seguridad, buenas prácticas, escalabilidad) → [AUDIT_REPORT.md](AUDIT_REPORT.md). Se corrigieron todos los hallazgos críticos/altos/medios, se implementaron recordatorios de vencimiento, precios, endpoints `me`, búsqueda, Flyway, rate limit, configuración por entorno y 26 tests. Se crea este archivo y `CLAUDE.md`. Se eliminaron `DataSeeder` (admin/admin123 fijo) y su test, sustituidos por `AdminBootstrap`. |
