# Auditoría Fase 0 — Verificación en PostgreSQL real

- **Fecha:** 2026-10-08
- **Alcance principal:** `git diff 0c7fb9d..252bef2` (commits `3dd18d6` y `252bef2`): migraciones V3, V4, V5 (Java), `PostgresLegacyUpgradeTest` + `legacy-schema.sql`, pausa en `MembershipReminderService`, textos de correo, `MailServiceTest`, `scripts/smoke_test.py`, configuración y pom.
- **Alcance secundario:** todo el backend en HEAD (`252bef2`), con foco en seguridad. Esta fue la **primera revisión independiente** de la línea base.
- **Auditor:** subagente independiente y escéptico, sin el historial de cómo se escribió el código. Trabajó en un worktree aislado y sin acceso a la base real del dueño.
- **Skills aplicadas:** `code-audit` · `backend-security` · `backend-scalability` · `backend-best-practices`
- **Resultado de la puerta:** ❌ **REABIERTA** — 3 hallazgos altos abiertos (todos de la línea base; el diff de la Fase 0 en sí no introduce críticos ni altos).
- Relacionado: [ROADMAP.md](../ROADMAP.md) · [DECISIONS.md](../DECISIONS.md) · [Plantilla](TEMPLATE.md)

> Los tests de ataque del auditor (29 tests, 7 archivos, 11 rojos a propósito) están en el worktree `.claude/worktrees/agent-a6bdeaf7eaab9c20d/src/test/java/com/storres/box_school/audit/` (rama `worktree-agent-a6bdeaf7eaab9c20d`). Cada uno se convierte en test de regresión permanente cuando se corrige su hallazgo.

## Estado de corrección (2026-10-11)

Implementadas las correcciones de los 3 altos, de todos los medios (M-2 aplazado a la Fase 2; M-5/M-6 con excepción registrada en D-012) y de 11 bajos; cada una con test de regresión (detalle y casillas en [ROADMAP.md](../ROADMAP.md), Fase 0). **Pendientes para cerrar la fase:** (1) ejecutar con Docker los tests de PostgreSQL real que cubren A-2 y M-1, (2) re-auditoría acotada e independiente. Mientras tanto la puerta sigue **reabierta**; las casillas de "Cierre" de abajo reflejan el momento de la auditoría original.

## Resumen

El diff de la Fase 0 está bien hecho en lo central: V5 es atómica, idempotente, portable PostgreSQL/H2, funciona en un esquema distinto de `public` y se aplica desde el JAR ejecutable; el bloqueo de fila de pagos y los dos tests de actualización heredada detectan mutaciones reales. Al atacar el resto del backend aparecieron **3 problemas altos** que impiden cerrar la fase: el rate limit se evade con una letra codificada en la URL, dos precios activos del mismo tipo pueden coexistir tras un doble envío y bloquean todos los pagos de ese tipo, y `/actuator/health` (público) abre una conexión SMTP por petición.

**Conteo: 🔴 0 · 🟠 3 · 🟡 11 · 🔵 17.** Todos abiertos al momento del informe. No se encontró forma de saltarse autenticación ni autorización, leer datos ajenos ni ejecutar código.

---

## Hallazgos

### 🔴 Crítico
Ninguno.

### 🟠 Alto

- **A-1 · El rate limit de `/api/auth/**` se evade codificando una letra de la ruta** — `security/RateLimitFilter.java:46` · [Línea base]
  - Riesgo: `shouldNotFilter` usa `request.getRequestURI()`, que **no está decodificado**, mientras Tomcat, Spring MVC y Spring Security sí decodifican. `POST /api/%61uth/login` (o `/%61pi/auth/login`) llega al mismo controlador y a la regla `permitAll`, pero el filtro lo ignora → intentos ilimitados de contraseña y de código de registro, y DoS de CPU (cada login hace BCrypt, ~52 ms medidos). `StrictHttpFirewall` solo bloquea `%2e %2f %5c %25 %3b`.
  - Demostración (`AuditHttpAttackTest#rateLimitCanBeBypassedWithPercentEncodedSegment`, Tomcat real): ruta normal ×14 → `401`×7 y luego `429`×7; con la IP bloqueada, `/api/%61uth/login` ×40 → **40 de 40 `401`, ningún `429`**; con clave correcta → `200` con token. `X-Forwarded-For` falso con `forward-headers-strategy=none` sigue en `429` (esa parte está bien).
  - Corrección mínima: decidir por la ruta decodificada y normalizada que ve Spring Security (`request.getServletPath()` o el mismo matcher de `SecurityConfig`) + `RateLimitFilterTest` con variantes codificadas. Hoy el filtro no tiene ningún test (la mutación `shouldNotFilter → true` deja los 34 tests en verde).
  - Estado: ⬜ abierto

- **A-2 · Un doble envío de "crear precio" deja dos precios activos del mismo tipo y bloquea los pagos con 500** — `service/PriceServiceImpl.java:37`, `repository/PriceRepository.java:22-25`; sin restricción en V1/V2 · [Línea base]
  - Riesgo: el comentario "dos admins creando a la vez no pueden dejar dos precios activos" es falso. `findActiveForUpdate` filtra por `active = true`; en READ COMMITTED la segunda transacción espera el bloqueo, relee la fila, ve `active=false` y devuelve 0 filas (no ve el precio recién insertado), e inserta otro activo. Con el primer precio de un tipo ni siquiera hay fila que bloquear. Después `findByTypeAndActiveTrue` lanza `IncorrectResultSizeDataAccessException` y la API no puede repararse sola.
  - Demostración (`AuditPostgresTest#priceDoubleSubmitCanLeaveTwoActivePrices`, PostgreSQL 16 real): dos `POST /api/prices` simultáneos → `[201, 201]`, 2 precios `BIWEEKLY` activos. Consecuencias: `POST /api/payments/students/{id}` → **500**; `GET /api/prices/active/BIWEEKLY` → **500**; un nuevo `POST /api/prices` de ese tipo → **500** (hay que arreglar con SQL a mano). Disparador realista: doble clic en "Guardar precio" o reintento de red.
  - Corrección mínima: garantizarlo en la base. V6 portable (PostgreSQL/H2): columna auxiliar `active_type` con índice UNIQUE (los NULL no chocan), mantenida por la entidad (`active ? type.name() : null`); en PostgreSQL puro basta `create unique index … on prices(type) where active`. Capturar la violación y responder 409.
  - Estado: ⬜ abierto

- **A-3 · `/actuator/health` es público y, con correo configurado, abre una conexión SMTP por cada petición anónima y devuelve 503 si el proveedor falla** — `config/SecurityConfig.java:57`, `application.yml:51-59` · [Línea base]
  - Riesgo: Spring Boot registra `MailHealthIndicator` cuando existe `JavaMailSender`, es decir, en la configuración de producción prevista. Cada `GET /actuator/health` sin token hace conexión y autenticación SMTP reales: amplificación sin autenticación (hilo retenido hasta el timeout, 5 s) y martilleo del login SMTP del proveedor; además el estado global pasa a `DOWN` (503) cuando cae el proveedor de correo (falsas alarmas/reinicios y fallo del `smoke_test.py`).
  - Demostración (`AuditHealthSmtpTest`, servidor SMTP falso): 1 GET anónimo → **503 `{"status":"DOWN"}` en 5167 ms**; 6 peticiones → 6 conexiones SMTP. Con el JAR real, `/actuator/health/liveness` da 200.
  - Corrección mínima: `management.health.mail.enabled=false`; dejar público solo `/actuator/health/liveness` y `/readiness`; apuntar los monitores a `liveness`.
  - Estado: ⬜ abierto

### 🟡 Medio

- **M-1 · Actualización perdida: editar el perfil o activar/desactivar pisa la extensión de un pago concurrente** — `service/StudentServiceImpl.java:93-105` (`updateStudent`), `:150-158` (`changeStatus`), `model/entity/Student.java` (sin `@Version` ni `@DynamicUpdate`) · [Línea base]
  - Esos métodos leen sin bloqueo y Hibernate escribe todas las columnas, incluida `expiration_date` con el valor viejo. Si un pago confirma entre la lectura y el commit, el pago queda registrado pero el vencimiento se revierte. Demostrado con intercalado determinista (`AuditPostgresTest#profileEditCanOverwriteAPaymentExtension`): `period_end` del pago = 2026-12-09, `students.expiration_date` final = 2026-11-09. Probabilidad baja, silencioso y sobre dinero; contradice D-006.
  - Corrección: `findByIdForUpdate(id)` en `updateStudent` y `changeStatus`, o `@Version` (V6; ya existe el 409 para `ObjectOptimisticLockingFailureException`).
  - Estado: ⬜ abierto

- **M-2 · Un doble clic registra dos pagos (sin idempotencia)** — `service/PaymentServiceImpl.java:49-79` · [Línea base]
  - Dos `POST /api/payments/students/{id}` simultáneos con el mismo cuerpo → `[201, 201]`, 2 filas, +60 días (`AuditPostgresTest#doubleClickPaymentRegistersTwice`). El bloqueo serializa pero no deduplica.
  - Corrección: cabecera `Idempotency-Key` guardada en `payments.reference` con UNIQUE (ya planificada en Fase 2), o rechazar un pago idéntico del mismo estudiante dentro de N segundos.
  - Estado: ⬜ abierto

- **M-3 · `PriceRequest` sin cotas: duraciones y montos absurdos pasan o fallan con mensajes confusos** — `model/dto/PriceRequest.java:18-23` · [Línea base]
  - `durationDays=36500` → 201 y un pago extiende hasta 2126; `durationDays=2147483647` → 201 y luego **todo pago de ese tipo da 409** (la fecha desborda `date`); `amount=1e30` → 409 opaco en vez de 400. Un error de tecleo del admin rompe los cobros de ese tipo.
  - Corrección: `@Max(366)` en `durationDays`, `@Digits(integer=10, fraction=2)` y `@DecimalMax` en `amount`; mapear el desbordamiento a 400.
  - Estado: ⬜ abierto

- **M-4 · `POST /api/notifications/membership-reminders/run` es síncrono y, tras la pausa añadida en la Fase 0, retiene el hilo HTTP todo el lote** — `controller/NotificationController.java:25-27`, `service/MembershipReminderService.java:113-123` · [Alcance Fase 0]
  - 12 alumnos con pausa de 250 ms → 3223 ms de petición; con el default (1000 ms) y 100 alumnos ≈ 100 s (un proxy corta a 60 s con 504 mientras el trabajo sigue). Cada reintento apila otro hilo de Tomcat y `/api/notifications/**` no tiene rate limit. La idempotencia evita duplicados, no el bloqueo. El `Thread.sleep` no retiene conexión de BD (bien).
  - Corrección: el endpoint solo dispara la tarea (`202 Accepted`) en un ejecutor propio con candado `AtomicBoolean` contra ejecuciones solapadas; `@Scheduled` sigue llamando al servicio directamente.
  - Estado: ⬜ abierto

- **M-5 · V4 borra `payments.amount` sin comprobar que el dato esté en `amount_paid`** — `db/migration/V4__drop_legacy_payment_amount_column.sql:5` · [Alcance Fase 0]
  - Demostrado en PostgreSQL 16 (`AuditMigrationScenariosTest`): (a) base heredada sin `amount_paid` y con 2 pagos en `amount` (130 000) → Flyway termina con éxito y el dato desaparece; (b) `amount_paid` nullable y vacía → queda `NULL` y se pierde el 50 000 original. Riesgo solo para copias/respaldos anteriores a Flyway restaurados; una base nueva no tiene la columna. El test permanente tiene `payments` vacía y no cubre este camino.
  - Corrección: **no editar V4** (cambiaría su checksum y rompería la base real). Registrar excepción en `DECISIONS.md` y documentar una consulta previa obligatoria (`select count(*) from payments where amount_paid is null or amount_paid = 0`); para migraciones destructivas futuras, Java con precondición; mover los dos escenarios al test permanente.
  - Estado: ⬜ abierto (excepción justificable)

- **M-6 · V3→V4→V5 fallan tarde y no forman un grupo atómico; el comentario de V3 promete una protección que no existe** — `V3__normalize_usernames_and_emails.sql:1-4`, `V5__repair_legacy_schema.java:24-26` · [Alcance Fase 0]
  - V3 dice que "si dos filas colisionaran, la restricción UNIQUE hace fallar la migración", pero en una base heredada **no** hay UNIQUE sobre `students.email` (justo lo que repara V5). Con `ANA@legacy.test` y `ana@legacy.test`: V3 y V4 confirman (emails fusionados, `amount` ya borrada), V5 falla con `23505` y la app no arranca; base a medio migrar. V5 revierte completa.
  - Corrección: documentar la verificación previa de duplicados (`select lower(email), count(*) … having count(*) > 1`), corregir el comentario de V3 sin editar su SQL; precondiciones antes de pasos destructivos en el futuro.
  - Estado: ⬜ abierto (excepción justificable)

- **M-7 · Una caída de la base de datos se ve como 401 "token inválido"** — `security/JwtAuthenticationFilter.java:58` · [Línea base]
  - El `catch` solo cubre `JwtException | IllegalArgumentException | UsernameNotFoundException`. Un `DataAccessException` en `loadUserByUsername` sale del filtro, el contenedor despacha a `/error` (que exige autenticación) y el cliente recibe 401. Demostrado con token válido + BD caída. El frontend cerraría la sesión de todos y los monitores no verían 5xx.
  - Corrección: capturar `DataAccessException`, registrar y responder 503 con `ApiErrorResponse`.
  - Estado: ⬜ abierto

- **M-8 · Sin límite de tamaño de cuerpo en endpoints públicos** — `application.yml`, sin filtro · [Línea base]
  - `POST /api/auth/login` con un `username` de 19 millones de caracteres se lee y parsea entero y recién entonces responde 400 por `@Size`; solo el límite interno de Jackson (20 M) lo corta. Con A-1 es amplificación de memoria sin autenticación.
  - Corrección: filtro que rechace `Content-Length` > 64 KB en `/api/**` y `StreamReadConstraints.maxStringLength(100_000)`.
  - Estado: ⬜ abierto

- **M-9 · Los tests no son herméticos: las variables de entorno del sistema pisan la configuración de test** — `src/test/resources/application.yml`, `PostgresLegacyUpgradeTest.java:37-45` · [Alcance Fase 0]
  - Una variable `JWT_SECRET` reemplaza el `jwt.secret` de test; con `SPRING_MAIL_HOST` definido (la máquina del dueño lo tiene), `PostgresLegacyUpgradeTest` no mockea `MailService` y su `POST /api/students` intenta un envío SMTP real. En la práctica `./mvnw test` envía correos reales a Mailtrap y usa el secreto real. Además, el `application.yml` de test **sustituye** al principal: ningún test ejercita la configuración real (Swagger apagado, `include-message: never`, probes, `forward-headers-strategy`); el auditor la verificó a mano con el JAR.
  - Corrección: `@MockitoBean MailService` en `PostgresLegacyUpgradeTest` y `BoxSchoolApplicationTests`; `@TestPropertySource(properties={"jwt.secret=…","spring.autoconfigure.exclude=…MailSenderAutoConfiguration"})` en una meta-anotación común; un test de humo que cargue el `application.yml` principal con valores ficticios.
  - Estado: ⬜ abierto

- **M-10 · Huecos de cobertura en lo nuevo y en lo crítico; un chequeo del smoke test es vacío** — `scripts/smoke_test.py:69-70` y tests · [Alcance Fase 0 / Línea base]
  - Mutaciones que **ningún test detecta**: eliminar `pauseBetweenEmails()` (delay=0 en tests), `RateLimitFilter.shouldNotFilter → true` (cero tests de 429), quitar `requireIssuer` del JWT. Mutaciones sí detectadas: V3/V4/V5 anuladas, `@Lock` de pagos, `isEnabled()`, expiración del código, comprobación del código. `smoke_test.py:70` acepta `s in (401, 404, 200)` para Swagger: no puede fallar.
  - Corrección: tests con delay > 0 (reloj/`sleeper` inyectable) incluyendo el camino de fallo, `RateLimitFilterTest`, test de `iss`/`exp`, y exigir 404 en el smoke test si no es perfil dev.
  - Estado: ⬜ abierto

- **M-11 · Trampas de despliegue en `.env.example` y defaults** — `.env.example:24,31`, `application.yml:46` · [Línea base]
  - `SPRING_PROFILES_ACTIVE=dev` en la plantilla: copiado a producción deja **Swagger público**, DEBUG y SQL formateado; `FORWARD_HEADERS_STRATEGY` (default `none`) no aparece: detrás de un proxy todos los clientes comparten IP y el límite 10/min/IP pasa a ser global (un atacante bloquea el login de todos); `APP_TIMEZONE=` vacío usa la zona del servidor: en una nube en UTC los vencimientos cambian de día a las 19:00 hora colombiana.
  - Corrección: `SPRING_PROFILES_ACTIVE=` vacío con comentario, listar `FORWARD_HEADERS_STRATEGY`, y `${APP_TIMEZONE:America/Bogota}`.
  - Estado: ⬜ abierto

### 🔵 Bajo / sugerencia

- **B-1 · Oráculo de tiempo para cuentas deshabilitadas** — `AuthServiceImpl.java:48`, `CustomUserDetailsService.java:31`. Usuario existente con clave mala 52 ms = inexistente 52 ms (bien), pero deshabilitado 1 ms (Spring comprueba `enabled` antes del BCrypt): revela qué usernames son estudiantes desactivados. Corrección: comparar contra un hash ficticio también en ese camino.
- **B-2 · BCrypt rechaza claves de más de 72 *bytes* y `@Size(max=72)` cuenta caracteres** — `RegisterRequest.java:31`. 72 × "ñ" en `register` da **500**. Corrección: constraint de longitud en bytes UTF-8.
- **B-3 · El reclamo se confirma antes de enviar: un `Error` o un `kill` lo deja huérfano para siempre** — `MembershipReminderService.java:94-109`. `catch (Exception)` no cubre `Error`; con `StackOverflowError` simulado: 1 reclamo huérfano y 0 reenvíos. D-008 afirma que un fallo libera el registro; no es cierto ante fallos duros. Impacto: un aviso perdido.
- **B-4 · La pausa solo ocurre tras un envío exitoso** — `MembershipReminderService.java:101-108`. (Sospecha, no probada contra proveedor real.) Si el proveedor limita ráfagas, el primer rechazo va seguido de intentos inmediatos que probablemente fallan y el resto del lote cae hasta el día siguiente. Corrección: pausar con espera creciente también en el camino de fallo.
- **B-5 · PII y ruido en logs** — `GlobalExceptionHandler.java:108-112`. Cada 409 por email duplicado se registra en WARN con traza completa y `Detail: Key (email)=(...)`. Corrección: registrar sin traza ni valores.
- **B-6 · Sin rastro de intentos fallidos** — `GlobalExceptionHandler.java:73`, `RateLimitFilter`. Solo se loguea la clase de la excepción, sin IP ni usuario; los 429 no se registran. No hay forma de detectar fuerza bruta.
- **B-7 · `scripts/smoke_test.py`**: dos corridas en el mismo minuto con el límite por defecto pueden tropezar con el 429; deja un precio `MONTHLY` activo de 80 000 en la base objetivo; su chequeo de health fallará si cae el SMTP (A-3). Sus 36 chequeos coinciden con lo que dice ROADMAP.
- **B-8 · Documentación desactualizada**: `DECISIONS.md:31` ("26 tests"; son 34), `ROADMAP.md:8-9` (contradice la Fase 0), D-002 ("secreto Base64": también acepta texto plano ≥32), D-008 (ver B-3), D-011 (`FORWARD_HEADERS_STRATEGY` no está en `.env.example`).
- **B-9 · `baseline-on-migrate: true` permanente en producción** — `application.yml:21-22`. Con una base ajena y no vacía, Flyway marca V1, V2 falla y queda una fila `BASELINE` en la base equivocada. Activarlo solo por variable de entorno en la primera adopción.
- **B-10 · V5**: sin checksum (editarla no se detecta); `ensureUnique` toma un índice único **parcial** como total. Aceptable con el test actual; anotarlo.
- **B-11 · Escalabilidad menor**: `User.student` es `@OneToOne` EAGER → una petición autenticada trivial cuesta 3 sentencias SQL (D-002 dice 1); `idx_students_expiration_date` es redundante con `idx_students_status_expiration`; las listas no tienen orden por defecto (paginación inestable).
- **B-12 · Tiempo y localización**: `ApiErrorResponse.java:26` usa `LocalDateTime.now()` en vez del `Clock` (viola la regla del proyecto); `toLowerCase()/toUpperCase()` sin `Locale.ROOT` en 5 sitios (`AuthServiceImpl:101`, `StudentMapper:36`, `AdminBootstrap:51`, `RegistrationCodeService:58`, `StudentSpecifications:26`); el `lower()` de V3 depende de la colación.
- **B-13 · JWT**: `init` acepta cualquier cadena de ≥32 caracteres, sin entropía; `exp` no es obligatorio (un token sin `exp` se aceptó). Exigir `requireExpiration()`.
- **B-14 · Código de registro con SHA-256 sin sal**: ~49,5 bits; con un volcado de la BD un GPU lo agotaría en horas (estimación del auditor, no medida), dentro de las 72 h. La afirmación "un volcado no permite activar cuentas" es optimista. Corrección: HMAC con pimienta en variable de entorno o más longitud.
- **B-15 · Rate limit**: solo por IP, ventana fija (ráfaga hasta 2× en el borde), sin límite por cuenta; `evictExpired` recorre todo el mapa en cada petición con ≥1000 IPs.
- **B-16 · Trabajo asíncrono**: el correo del código se despacha (`@Async`) **antes** del commit de `create`/`regenerate`; `@Scheduled` usa el planificador por defecto de 1 hilo que el job bloquea todo el lote; la cola del ejecutor `@Async` no tiene tope.
- **B-17 · Información menor**: `GET /actuator` (índice) responde 200 a cualquier autenticado, incluidos estudiantes; `?sort=registrationCodeHash` se acepta en listados de admin; `GET /error` autenticado devuelve 500.

---

## Seguridad — intentos de romperlo

| # | Ataque intentado | Resultado | Evidencia / test |
|---|---|---|---|
| 1 | Evadir matchers: `/api/students/`, `//api/students`, `/api/./students`, `;jsessionid`, `%3b`, `%2f`, `/api/%73tudents`, `/API/…`, `.json`, `/..;/`, `/actuator/health/../env` (39 rutas × anónimo y estudiante) | **Resistido** (401/403/404/400) | `AuditHttpAttackTest#matcherEvasion` |
| 2 | Métodos no cubiertos (HEAD, OPTIONS, TRACE, PUT, DELETE, PATCH, PROPFIND) con token de estudiante; `DELETE /api/students/1` admin | **Resistido** (403/400/405) | ídem |
| 3 | `/actuator/env`, `/beans`, `/heapdump`, `/mappings`, `/h2-console` | **Resistido** (401 anónimo, 404 autenticado) | ídem + JAR real |
| 4 | IDOR: estudiante lee `/api/students/{otro}`, `/api/payments/students/{otro}`, crea precios/estudiantes/pagos | **Resistido** (403) | `ApiFlowIntegrationTest#studentCannotUse…` + matriz |
| 5 | JWT `alg=none` | **Resistido** (401) | `AuditHttpAttackTest#jwtForgeries` |
| 6 | JWT expirado / issuer ajeno / sin issuer | **Resistido** (401) | ídem |
| 7 | JWT de usuario inexistente / deshabilitado / `sub` en mayúsculas / con espacio | **Resistido** (401) | ídem |
| 8 | Cabeceras `Authorization` mal formadas | **Resistido** (401) | ídem |
| 9 | **Fuerza bruta de login saltando el rate limit** con `/api/%61uth/login` | **ROTO**: 40/40 sin 429; con clave correcta → 200 | A-1 |
| 10 | Evadir el rate limit con `X-Forwarded-For` (strategy `none`) | **Resistido** | ídem |
| 11 | Variantes de ruta: `;x=1`, `//`, `/./`, `/API/`, `/login/`, `%6c` | **Resistido** (firewall o contadas) | ídem |
| 12 | Enumeración por mensaje en login | **Resistido** (cuerpo idéntico) | `ApiFlowIntegrationTest#wrongPasswordAndUnknownUser…` |
| 13 | Enumeración por tiempo en login | **Parcial**: deshabilitado 1 ms vs 52 ms | B-1 |
| 14 | Enumeración en registro (email inexistente / con usuario / INACTIVE) | **Resistido** (mismo 400 y cuerpo) | `…#registrationErrorsAreIndistinguishable` |
| 15 | Código: reutilizar, expirar, regenerar, 33 caracteres, estudiante INACTIVE | **Resistido** | `AuditPostgresTest#registrationCodeLifecycleAttacks` |
| 16 | Dos registros simultáneos con el mismo código | **Resistido** (`[409, 201]`, 1 usuario) | `…#concurrentRegistrations…` |
| 17 | Dos altas simultáneas del mismo email | **Resistido** (`[409, 201]`) | ídem |
| 18 | Fuerza bruta online del código | **Inviable** (≈4e14 intentos) | análisis; B-14 offline |
| 19 | Mass assignment | **Resistido** (los DTOs no tienen `status`/`role`/`expiration`) | lectura de DTOs |
| 20 | Inyección: `LIKE` con `%`, `_`, `\`; JPQL | **Resistido** (Criteria parametrizado) | `ApiFlowIntegrationTest#searchFilters…` |
| 21 | Inyección de cabecera de correo (`\r\nBcc:`) | **Resistido** (400) | `…#priceAndPasswordEdgeCases` |
| 22 | CORS: preflight desde origen ajeno | **Resistido** (403) | `…#headersCorsAndErrorBodies` |
| 23 | Cabeceras de seguridad | **Correctas** (`nosniff`, `X-Frame-Options: DENY`, `no-store`) | ídem |
| 24 | Fuga en errores (URI mala, JSON truncado, tipo erróneo, 500) | **Resistido** (sin traza ni versión) | ídem + JAR real |
| 25 | Cuerpo gigante en endpoint público (19 MB) | **ROTO** | M-8 |
| 26 | Health público como amplificador SMTP | **ROTO** | A-3 |
| 27 | Doble creación de precio | **ROTO** | A-2 |
| 28 | Edición concurrente pisando un pago | **ROTO** | M-1 |
| 29 | Doble clic de pago | **ROTO** (2 pagos) | M-2 |
| 30 | Caída de BD con token válido | **ROTO** (401 en vez de 5xx) | M-7 |
| 31 | Secretos en repo e historial git (`git log -p`, 19 revisiones) | **Limpio** (solo `jwt.secret` de test y la mención histórica del seeder) | revisión |
| 32 | Dependencias | Boot 3.5.10, Tomcat 10.1.50, Spring Security 6.5.7, JJWT 0.13.0, PostgreSQL 42.7.9, Flyway 11.7.2, springdoc 2.8.15 (actuales). **Sin escaneo CVE** | residuales |

## Pruebas específicas de migraciones (V3 / V4 / V5)

| Escenario (PostgreSQL 16 real, Flyway programático) | Resultado |
|---|---|
| Base limpia V1→V5 y segunda ejecución | **Correcto**: 0 migraciones la segunda vez. Checksum de V5 = `NULL` (B-10) |
| Esquema heredado en un schema distinto (`?currentSchema=escuela`) | **Correcto** |
| JAR ejecutable (`java -jar`) contra base heredada | **Correcto**: V5 se encuentra y aplica |
| `payments.amount` con datos y sin `amount_paid` | **Pérdida de datos** silenciosa (M-5) |
| `amount_paid` nullable y vacía | **Pérdida de datos** silenciosa (M-5) |
| Emails duplicados por mayúsculas | V3 y V4 confirman, V5 falla: base a medio migrar; V5 se revierte completa (M-6) |
| Base ajena no vacía con `baseline-on-migrate` | V2 falla; fila `BASELINE` en la base equivocada (B-9) |
| Identificadores con mayúsculas entre comillas | **No probado** |

## Escalabilidad y rendimiento

- **Sin N+1 en listados:** `GET /api/students?size=100` → 2 sentencias; `GET /api/payments?size=100` → 2 (el `@EntityGraph` funciona); `/expiring` → 2. Paginación obligatoria con tope 100.
- **Costo fijo por petición autenticada:** `GET /api/prices/active` = 3 sentencias; `/students/me` = 3; `/payments/me` = 4 (dos son solo autenticación por el `@OneToOne` EAGER; B-11).
- **Job de recordatorios con 30 000 alumnos:** `EXPLAIN ANALYZE` 0,7 ms; `ORDER BY id LIMIT 100` no usa `idx_students_status_expiration`, irrelevante hasta ~100 000 alumnos. Búsqueda `LIKE '%q%'`: Seq Scan 11,5 ms (aceptable; deuda `pg_trgm` anotada).
- **Concurrencia de recordatorios:** 3 ejecuciones en paralelo con 40 alumnos → 40 envíos, 0 duplicados, 0 faltantes.
- **Estado en memoria:** rate limiter por proceso (D-011), más fácil de evadir con varias instancias o IPv6 (B-15).
- **Síncrono que debería ser asíncrono:** el endpoint de recordatorios (M-4). El job no retiene conexión de BD durante SMTP/pausa (correcto).
- **Pool:** HikariCP por defecto (10); sin corregir A-1, el BCrypt de un login sin límite satura CPU antes que el pool.

## Buenas prácticas y mantenibilidad

- Capas claras (controller → service → repository), DTOs, `@Transactional(readOnly=true)` por defecto, excepciones traducidas a `ApiErrorResponse`. `UsernameAlreadyExistsException` se reutiliza para "este estudiante ya tiene cuenta" (nombre engañoso).
- Duplicación: la normalización de usernames/emails está en 4 sitios más V3 en SQL (B-12).
- Tests: `ApiFlowIntegrationTest` es `@Transactional` (no ejercita fallos de commit ni carga perezosa; los de recordatorios y `PostgresLegacyUpgradeTest` sí son no transaccionales). El `application.yml` de test oculta el principal (M-9). Las aserciones de la Fase 0 son significativas salvo los huecos de M-10.
- Código muerto: `Invoice` + `InvoiceRepository` sin uso (conocido, O-3).
- Documentación: B-8; faltan las excepciones M-5/M-6 y una guía de "respaldo y verificación previa" para actualizar bases heredadas.

## Lo que está bien

- Autorización sólida: doble capa (matchers + `@PreAuthorize`) y autoservicio por `/me`; 39 rutas × 2 identidades y 8 métodos sin evasión.
- JWT bien validado: firma y algoritmo explícitos, issuer exigido, expiración, usuario recargado de la BD en cada petición (deshabilitar corta al instante).
- Registro por código: errores indistinguibles, un solo uso, expiración, regeneración invalida el anterior, comparación en tiempo constante, resistente a carreras.
- V5 robusta: atómica, idempotente, falla en lugar de tocar datos, funciona en otro schema y desde el JAR.
- Mutaciones detectadas: V3, V4, V5 y el `@Lock` de pagos (D-015 cierto para esos casos).
- Dinero: el monto sale siempre del precio vigente; pagos simultáneos con periodos contiguos; recordatorios idempotentes por clave única.
- Configuración real verificada con el JAR: Swagger apagado, `/actuator/env` → 401, errores sin versión de Tomcat, liveness/readiness expuestos.
- Manejo de errores: cero trazas al cliente, 400/415/404 uniformes, 500 genérico.
- Sin secretos en el árbol ni en el historial; `.env` ignorado; sin credenciales por defecto.
- La pausa de correo funciona y no retiene conexión de BD; `MailServiceTest` verifica UTF-8 con ida y vuelta real del MIME.
- Rendimiento base razonable: sin N+1, índices usados por el job, paginación con tope.

## Riesgos residuales (no verificados)

- La base real del dueño no se tocó (prohibido): "4 estudiantes intactos" y el 36/36 del smoke test sobre PostgreSQL 18 no se verificaron de forma independiente; todo se reprodujo en PostgreSQL 16 (Flyway avisa que 18 es más nueva que su última versión probada).
- Dependencias: sin escaneo NVD/OSV; revisar con `dependency-check`/Dependabot al configurar CI.
- Identificadores con mayúsculas entre comillas y `search_path` múltiple: razonado, no probado.
- Temporización de enumeración en `register` (email existente vs inexistente): sospecha de bajo riesgo.
- TLS/HSTS, proxy inverso, `forward-headers-strategy=native` y límites reales de Tomcat/proxy: sin artefactos de despliegue.
- Comportamiento real de un SMTP bajo límite de ráfaga (B-4) y STARTTLS/AUTH: solo servidor falso local.
- Carga: sin tráfico real; las cifras son de volumen (30 000 filas) y conteo de sentencias.

## Veredicto

**La Fase 0 NO puede cerrarse todavía.** Hay 3 altos (A-1, A-2, A-3), todos de la línea base que nunca tuvo revisión independiente; el diff de la Fase 0 no introduce críticos ni altos. Para cerrar:
1. Corregir A-1 (rate limit por ruta decodificada + test), A-2 (restricción de unicidad del precio activo en V6 + test PostgreSQL) y A-3 (health sin SMTP).
2. Corregir o registrar excepción justificada en `DECISIONS.md` para los medios; los de mejor relación esfuerzo/riesgo: M-1, M-3, M-4, M-7, M-8, M-9, M-11. M-5 y M-6 admiten excepción (la única base real ya migró y V3/V4 no pueden editarse sin romper su checksum).
3. Convertir los tests de ataque en regresión permanente.

## Cierre
- [ ] 0 críticos/altos abiertos (hoy: 0 críticos, 3 altos)
- [ ] Medios corregidos o con excepción registrada en `DECISIONS.md` (hoy: 11 abiertos)
- [x] `./mvnw test` en verde: 34 tests, 0 fallos
- [ ] Historial de `DECISIONS.md` actualizado y fase marcada en `ROADMAP.md` (historial actualizado; fase **reabierta**)

---

**Ejecutado por el auditor:** `git log`, `git diff 0c7fb9d..HEAD --stat`, `git merge --ff-only 252bef2` (solo en su worktree), `git log -p` y búsqueda de secretos en todas las revisiones; `mvnw -B test` con `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`/`JWT_SECRET` retiradas del entorno → **34 tests, 0 fallos, 0 omitidos**, antes y después de añadir sus tests (`-Dtest='!Audit*'`); suite de ataque `-Dtest='Audit*'` → 29 tests, 18 verdes y 11 rojos a propósito (A-1, A-2, A-3, M-1, M-2, M-4, M-5 ×2, M-6, M-7, B-3); mutaciones con `git checkout` posterior (V3, V4, V5, `@Lock`, pausa, rate limit, issuer, `isEnabled`, expiración y comprobación del código); `mvnw -DskipTests package` + `java -jar` contra PostgreSQL de Testcontainers; `mvnw dependency:list`. Solo se usó H2 o PostgreSQL 16-alpine de Testcontainers; no se tocó la base del dueño ni se enviaron correos reales.
