# Verificación final de la Fase 0 (commit `280ece7`)

- **Fecha:** 2026-10-12
- **Auditor:** el mismo subagente independiente de la re-auditoría (worktree aislado; solo H2 y PostgreSQL 16 de Testcontainers; sin la base real, sin correos, sin push).
- **Resultado de la puerta:** ✅ **APROBADA — la Fase 0 puede cerrarse** (0 críticos, 0 altos abiertos).
- Informes previos: [FASE-0-verificacion-postgres.md](FASE-0-verificacion-postgres.md) → [FASE-0-reauditoria.md](FASE-0-reauditoria.md)

## Veredicto
0 críticos, 0 altos abiertos; N-1 a N-4 cerrados con evidencia reproducida; sin regresiones de severidad media ni alta (3 bajos nuevos, todos tratados abajo). `REQUIRE_DOCKER=true ./mvnw test`: **77 tests, 0 fallos, 0 omitidos** en orden normal y con `-Dsurefire.runOrder=reversealphabetical`.

## Verificación

| Hallazgo | ¿Cerrado? | Evidencia |
|---|---|---|
| **N-1** `PUT/PATCH/DELETE` form-urlencoded gigante | **Sí** | Los mismos ataques que rompieron `d3157e8`: `PUT` anónimo de 64 MB → pico de heap **+2 MB** (antes +257 MB); `PATCH` chunked +2 MB (antes +258); con `-Xmx300m` y 400 MB: sin `OutOfMemoryError`, liveness 200 (antes OOM). Mutaciones (orden `LOWEST_PRECEDENCE`, filtro de formularios activo) → fallan los tests |
| **N-2** `register`/`regenerate` pisan un pago | **Sí** | PostgreSQL, interleaving determinista: vencimiento final = el posterior al pago (antes volvía al inicial). Sin `@DynamicUpdate` → falla `StudentDynamicUpdateTest` |
| **N-3** hermetismo del datasource | **Sí** | Con variables falsas `SPRING_DATASOURCE_URL`, `SWAGGER_ENABLED`, `FORWARD_HEADERS_STRATEGY`, `CORS_ALLOWED_ORIGINS`, `REGISTRATION_CODE_HOURS`: valores efectivos de prueba. El `Guard` **aborta de verdad** el contexto con una URL fuera de la máquina (1,1 s, antes de conectar) |
| **N-4** mutaciones sin test | **Sí** | 10 de 10 mutaciones antes "No detectadas" ahora fallan (A-3 health.mail, M-4 síncrono, M-8 chunked, M-9 correo, B-9, B-11, B-12, B-14, M-11, V6) |
| R-3, R-4, R-6, R-8, R-9, R-11, R-12 | **Sí** | interrupción: 1 de 4 enviados (antes 4 sin pausa); `mail-not-configured`; 0 líneas `Detail: Key` frente a 307 violaciones; zona en blanco → `America/Bogota`; `DockerAvailableTest`; mismas 46 variantes de ruta |
| R-5 `/actuator/**` | Parcial (aceptado) | anon 401, estudiante 403, sondas públicas 200. Resto: `/error` autenticado, `sort` por columna sensible, cuerpo de error de despacho |
| R-7, R-10, R-1, R-2, B-15 | Aceptados y documentados | ver DECISIONS |

## Hallazgos nuevos de la verificación (3 bajos)

| # | Hallazgo | Tratamiento |
|---|---|---|
| **NR-1** | `register` concurrente con `deactivate` dejaba la **cuenta habilitada** para un estudiante INACTIVO (consecuencia de `@DynamicUpdate`: ya no se revertía la desactivación) | ✅ **Corregido**: `findByEmailForUpdate` (bloqueo de fila) y el hash BCrypt se calcula antes de tomar el bloqueo. Test determinista `registerRacingWithDeactivate…` (entrelazado con un espía de `RegistrationCodeService`); **verificado por mutación**: sin el bloqueo falla |
| **NR-2** | Las respuestas 413 salen sin cabeceras CORS ni de seguridad (el filtro de tamaño quedó fuera de la cadena) | 🔵 Aceptado. Un navegador vería un fallo CORS opaco en un cuerpo > 64 KB, que ningún cliente legítimo envía hoy. **A tener en cuenta en la Fase 2:** las imágenes del QR/comprobante chocarán con el límite y exigirán una excepción de tamaño por ruta |
| **NR-3** | `SPRING_FLYWAY_URL/USER/PASSWORD` no estaban vigilados por el `Guard` | ✅ **Corregido**: el `Guard` valida también `spring.flyway.url`; `HermeticGuardTest` (3 tests) prueba que aborta |

NR-1 y NR-3 se corrigieron **después** de la verificación del auditor (commit posterior); su verificación es la mutación propia descrita y `81` tests en verde.

## Mutaciones (re-ejecutadas por el auditor)

| Mutación | ¿Detectada? | Test |
|---|---|---|
| A-3 `health.mail.enabled: true` | **Sí** | `ConfigurationDefaultsTest`, `SecurityHardeningTest` |
| M-4 `triggerAsync` síncrono | **Sí** | `MembershipReminderServiceTest#triggerAsyncReturnsBefore…` |
| M-8 sin corte chunked | **Sí** | `SecurityHardeningTest#chunkedBodyMadeOfManySmallFields…` |
| M-9 sin exclusión de correo | **Sí** (con `SPRING_MAIL_HOST` definido) | `SecurityHardeningTest#testsAreHermetic…` |
| B-9 baseline `true` por defecto | **Sí** | `ConfigurationDefaultsTest` |
| B-11 `User.student` EAGER | **Sí** | `ConfigurationDefaultsTest` |
| B-12 `toLowerCase()` sin `Locale` | **Sí** | `ConfigurationDefaultsTest` |
| B-14 SHA-256 sin clave | **Sí** | `UnitTests#storedHashIsAKeyedHmac…` |
| M-11 zona por defecto vacía | **Sí** | `ConfigurationDefaultsTest` |
| V6 sin limpieza de duplicados | **Sí** | `PriceMigrationV6Test` |
| N-1 orden / filtro de formularios; N-2 `@DynamicUpdate`; R-3 interrupción | **Sí** | tests propios |
| N-2 `regenerate` sin `findByIdForUpdate` | No (inocua con `@DynamicUpdate`) | — |

## Criterio de cierre

| Criterio | Estado |
|---|---|
| 0 críticos | ✅ |
| 0 altos | ✅ |
| Medios corregidos o con excepción justificada | ✅ (M-2 → Fase 2; M-5/M-6 excepción en D-012) |
| `REQUIRE_DOCKER=true ./mvnw test` en verde | ✅ 81 tests, 0 omitidos |
| `DECISIONS.md` / `ROADMAP.md` actualizados | ✅ |

## Para la Fase 2 (anotado)
- Excepción de tamaño por ruta para imágenes (QR y comprobantes) frente al límite de 64 KB y revisión de cabeceras CORS en 413.
- Idempotencia de pagos (`reference` UNIQUE / `Idempotency-Key`), aplazada desde M-2.
