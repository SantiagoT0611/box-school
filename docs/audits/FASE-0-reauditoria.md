# Re-auditoría Fase 0 — verificación de las correcciones (commit `d3157e8`)

- **Fecha:** 2026-10-12
- **Alcance:** `git diff 252bef2..d3157e8` (42 archivos) y, de forma independiente, todo el backend en `d3157e8`.
- **Auditor:** subagente independiente y escéptico, sin el historial de cómo se escribió el código (worktree aislado; solo H2 y PostgreSQL 16 de Testcontainers; variables del sistema retiradas; sin correos ni acceso a la base real).
- **Skills aplicadas:** `code-audit` · `backend-security` · `backend-scalability` · `backend-best-practices`
- **Resultado de la puerta:** ❌ **SIGUE REABIERTA** — 1 alto, 3 medios abiertos (+ M-2 aplazado con excepción).
- Informe previo: [FASE-0-verificacion-postgres.md](FASE-0-verificacion-postgres.md)

> Las pruebas de ataque del auditor (22 pruebas, 7 archivos, `reaudit/`) quedaron en su worktree sin versionar; sus casos se convirtieron en regresión permanente en el repositorio (ver ROADMAP).

## Resumen
Las tres correcciones altas (A-1, A-2, A-3) están **cerradas de verdad**: resistieron 46 variantes de ruta, 30 rondas × 12 hilos sobre PostgreSQL real y una matriz de 34 rutas de actuator, y cada una falla si se les quita la corrección. No se encontraron críticos. Lo que no estaba bien: el límite de cuerpo de 64 KB (M-8) se evade con `PUT/PATCH/DELETE` de formulario (OOM anónimo), M-1 quedó a medias, el hermetismo de M-9 no cubre `SPRING_DATASOURCE_*` y varias correcciones declaradas no tenían ningún test que fallara al quitarlas.

**Abiertos: 🔴 0 · 🟠 1 · 🟡 3 · 🔵 12.**

## Verificación de correcciones

| Hallazgo | ¿Cerrado? | Evidencia |
|---|---|---|
| A-1 rate limit por ruta codificada | **Sí** | 46 variantes en Tomcat real; la mutación `getRequestURI()` falla `RateLimitIntegrationTest` |
| A-2 doble precio activo | **Sí** | 30 rondas × 12 hilos en PostgreSQL: 0 malas, solo 201/409; V6 sobre duplicados conserva el de mayor id |
| A-3 health/SMTP | **Sí** | matriz de 34 rutas; solo `liveness`/`readiness` públicos |
| M-1 edición pisa pago | **Parcial** | `updateStudent`/`changeStatus` bien; **`register` y `regenerateRegistrationCode` siguen pisando** (N-2) |
| M-3 cotas de precio | Sí | `priceBounds…` + mutación |
| M-4 recordatorios 202 + candado | Sí | dos llamadas simultáneas → un solo envío (R-3, R-4) |
| M-7 BD caída = 503 | Sí | `DatabaseOutageTest` + mutación |
| M-8 límite de cuerpo | **No** | `PUT/PATCH/DELETE` form-urlencoded se lee entero antes de la cadena (N-1) |
| M-9 tests herméticos | **Parcial** | `SPRING_DATASOURCE_URL` se cuela (N-3) |
| M-10 cobertura | **Parcial** | 12 mutaciones sin test (N-4) |
| M-11 despliegue | **Parcial** | `APP_TIMEZONE=` en blanco cae a la zona del servidor (R-8) |
| B-2, B-3, B-4, B-6, B-9, B-13, B-14 | Sí | con las salvedades de N-4 |
| B-5 PII en logs | Parcial | Hibernate `SqlExceptionHelper` sigue escribiendo `Detail: Key (…)` (R-6) |
| B-11 / B-12 / B-17 | Parcial | residuos R-5, R-7 |
| M-5 / M-6 / M-2 | Excepciones aceptadas | registradas en D-012 y ROADMAP |

## Hallazgos abiertos

### 🟠 Alto
- **N-1 · El límite de 64 KB se evade con `PUT`/`PATCH`/`DELETE` de formulario: un anónimo puede agotar la memoria** — `SecurityConfig.java:73`, `RequestSizeLimitFilter.java:54`, `application.yml`. [Ya existía; M-8 no lo cerraba]
  - Spring Boot registra `OrderedFormContentFilter` (orden −9900, antes de la cadena de seguridad): para cualquier `PUT/PATCH/DELETE` con `application/x-www-form-urlencoded`, en cualquier ruta y sin autenticar, copia el cuerpo entero a un `String` antes de que el filtro de tamaño pueda actuar.
  - Demostrado (Tomcat real): `PUT` anónimo de 64 MB → 413 pero **+257 MB de heap**; con `-Xmx300m`, un `PUT` de 400 MB → `OutOfMemoryError`. Con `spring.mvc.formcontent.filter.enabled=false` el mismo ataque cuesta +2 MB.
  - Corrección: `spring.mvc.formcontent.filter.enabled: false` + registrar `RequestSizeLimitFilter` con `Ordered.HIGHEST_PRECEDENCE`; test que lo verifique; corregir D-023.

### 🟡 Medio
- **N-2 · M-1 a medias: `register` y `regenerateRegistrationCode` pisan un pago concurrente** — `StudentServiceImpl.java:139`, `AuthServiceImpl.java:62-86`, `Student.java` (sin `@DynamicUpdate`). Demostrado en PostgreSQL con interleaving determinista (vencimiento final = el inicial, 1 pago registrado). En `register` la ventana incluye ~50–100 ms de BCrypt dentro de la transacción. Corrección: `@DynamicUpdate` en `Student` (causa raíz) y bloqueo en `regenerateRegistrationCode`.
- **N-3 · El hermetismo no cubre el datasource**: con `SPRING_DATASOURCE_URL` definido, `./mvnw test` ejecutaría Flyway y `deleteAll()` contra esa base (`HermeticTest.java`, `application-test.yml`). Hoy no es explotable en esta máquina (usa `DB_URL`), pero D-015 lo afirmaba. Corrección: fijar `spring.datasource.*`, `springdoc.*`, `server.forward-headers-strategy`, `app.cors.*` en `HermeticTest` y un guard que aborte si la URL no es H2/localhost.
- **N-4 · Nueve correcciones "con test de regresión" no tenían ningún test que fallara al quitarlas**: A-3 (correo en health), M-4 (disparo síncrono), M-8 (corte chunked), M-9 (exclusión de correo), B-9 (baseline por defecto), B-11 (LAZY), B-12 (`Locale`), B-14 (HMAC), M-11 (zona por defecto), V6 (limpieza de duplicados).

### 🔵 Bajo
- **R-1** `active_type` puede desincronizarse si alguien toca `prices` por SQL directo (409 permanente / 2 activos). Latente: ningún `UPDATE` JPQL/nativo existe. Aceptado y documentado.
- **R-2** Los códigos de registro emitidos antes de la actualización (SHA-256) dejan de coincidir; documentar "regenerar pendientes tras desplegar".
- **R-3** El bucle de recordatorios ignora `InterruptedException` (en un apagado envía en ráfaga).
- **R-4** `POST …/run` responde `started` aunque el correo no esté configurado.
- **R-5** `GET /actuator` accesible para estudiantes; `GET /error` autenticado → 500 con `status:999`; el cuerpo del error de despacho no es `ApiErrorResponse`; `?sort=registrationCodeHash` aceptado.
- **R-6** Hibernate `SqlExceptionHelper` registra `Detail: Key (email)=(…)` (PII).
- **R-7** `ApiErrorResponse` usa `LocalDateTime.now()`; `V5` usa `toLowerCase()` sin `Locale`; `RegisterRequest` expone la clave `passwordWithinBcryptLimit`.
- **R-8** `APP_TIMEZONE=` en blanco cae a la zona del servidor.
- **R-9** Sin Docker los 5 tests de PostgreSQL se omiten en silencio (la suite parece verde).
- **R-10** Un secreto JWT de texto plano alfanumérico de 32–40 caracteres se rechaza por decodificarse como Base64; mensaje confuso.
- **R-11** Dos derivaciones de ruta distintas en dos filtros.
- **R-12** Ruido de logs con traza completa en cada petición durante una caída de BD.

## Seguridad — intentos de romperlo (resumen)
Resistidos: 46 variantes de ruta contra el rate limit, `X-Forwarded-For`/`X-Real-IP`/`Forwarded` falsos, doble envío de precio (30 rondas), `/actuator/**` (34 rutas × 3 identidades), acceso directo a `/error` y variantes, cuerpos gigantes en login (JSON, chunked, multipart, gzip), `Content-Length` en el límite (65 536 pasa / 65 537 → 413), recordatorios simultáneos, clave de 200 bytes, tokens sin `exp`/`alg=none`/emisor ajeno, arranque contra base heredada sin variable de baseline (falla con mensaje claro).
Rotos: **cuerpo gigante `PUT/PATCH/DELETE` form-urlencoded (N-1)**, pérdida de pago con `register`/`regenerate` (N-2), desincronización de `active_type` por SQL manual (R-1), contaminación por `SPRING_DATASOURCE_URL` (N-3).

## Mutaciones
28 mutaciones: 16 detectadas, 12 no. Sin test: A-3 `health.mail.enabled=true`, M-4 disparo síncrono, M-8 corte chunked, M-9 exclusión de correo, B-9 baseline por defecto `true`, B-11 `EAGER`, B-12 `toLowerCase()` sin `Locale`, B-14 SHA-256 sin clave, M-11 zona por defecto, V6 sin limpieza de duplicados, B-5/B-6 (logs).

## Lo que está bien
A-1/A-2/A-3 realmente cerrados con regresión que falla al quitarlos; el patrón "índice único + 409" es la solución correcta; el filtro JWT distingue token inválido de fallo de infraestructura; HMAC con pimienta y comparación en tiempo constante; `RequestSizeLimitFilter` no rompe endpoints legítimos; sin `LazyInitializationException`; sin N+1 (sentencias por petición: `/prices/active` 2, `/students/me` 2, `/payments/me` 3); sin secretos en el árbol.

## Riesgos residuales
No se verificó V6 sobre la base real (prohibido) ni sobre PostgreSQL 18; sin escaneo CVE; sin carga real ni SMTP real; M-1 detectado 1 de 1 (tasa no medida).

## Veredicto
**La Fase 0 no puede cerrarse**: N-1 (alto) y N-2/N-3/N-4 (medios) abiertos; todos de corrección pequeña y ya verificada por el auditor en lo esencial. Tras corregirlos, `./mvnw test` debe seguir en 61+/61+ con Docker.
