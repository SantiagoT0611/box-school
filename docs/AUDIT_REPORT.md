# Informe de auditoría — box-school (backend)

Fecha: 2026-10-08 · Alcance: todo `src/` + `pom.xml` + configuración · Criterios: seguridad, buenas prácticas/mantenibilidad, escalabilidad.
Estado del código **al momento de auditar**; la columna *Estado* indica qué se hizo después. Decisiones asociadas en [DECISIONS.md](DECISIONS.md).

## Resumen

El proyecto tenía una base ordenada (capas claras, DTOs, BCrypt, JWT, paginación, `open-in-view=false`, secretos por variables de entorno), pero **no era seguro ni estaba completo**: 4 hallazgos críticos, 9 altos, 15 medios y varios menores. Lo más grave: cualquier usuario autenticado podía crear estudiantes y leer los de otros, cualquiera que supiera un email podía tomar la cuenta de un estudiante, y el seeder dejaba un admin `admin/admin123`. La funcionalidad central pedida (aviso de vencimiento a 2 días) y la gestión de precios no existían.
**Todos los hallazgos fueron corregidos o implementados** (excepto lo marcado "Abierto"). 26 tests automáticos verifican el comportamiento.

## Hallazgos

### 🔴 Crítico

| # | Hallazgo | Dónde (original) | Riesgo | Estado |
|---|---|---|---|---|
| C1 | Admin con credenciales fijas `admin` / `admin123` creado en cualquier entorno | `config/DataSeeder.java` | Toma total del sistema si llega a producción | ✅ Eliminado → `AdminBootstrap` por variables de entorno, ≥ 12 caracteres, solo si no hay admin |
| C2 | `POST /api/students` sin control de rol (`@PreAuthorize` comentado) | `StudentController:32` | Cualquier estudiante crea/inscribe alumnos | ✅ Solo ADMIN (URL + método) |
| C3 | IDOR: `GET /api/students/{id}` sin verificar dueño | `StudentController:48` | Un estudiante lee datos personales (email, teléfono) de todos | ✅ Admin-only; el estudiante usa `GET /students/me` |
| C4 | Registro por solo email: quien conozca el email de un alumno crea su usuario | `AuthServiceImpl.register` | Apropiación de cuentas, acceso a sus datos y pagos | ✅ Código de registro de un solo uso (hash + expiración) — D-004 |

### 🟠 Alto

| # | Hallazgo | Dónde | Riesgo | Estado |
|---|---|---|---|---|
| A1 | `@PreAuthorize("… principal.student.id")` inválida (el principal es `UserDetails`, sin `student`) y `AccessDeniedException` capturada por el handler genérico | `PaymentController:36`, `GlobalExceptionHandler:118` | Estudiantes siempre reciben **500** en vez de ver sus pagos; permisos denegados reportados como 500 | ✅ Endpoints `/me`; 401/403 correctos |
| A2 | Cuenta deshabilitada seguía operando con su token (no se validaba `isEnabled`) y deshabilitar al estudiante no tocaba su usuario | `JwtAuthenticationFilter`, `StudentServiceImpl` | "Deshabilitar" no cortaba el acceso hasta 24 h | ✅ Filtro exige `isEnabled`; `deactivate` sincroniza `users.enabled` |
| A3 | Handler genérico devolvía `ex.getMessage()` en 500; `Collectors.toMap` rompía con 2 errores en un campo | `GlobalExceptionHandler` | Fuga de detalles internos; validación → 500 | ✅ Mensaje genérico, detalle al log; merge de errores |
| A4 | Enumeración de usuarios: login con usuario inexistente respondía 404 "estudiante no encontrado" | `CustomUserDetailsService` | Permite descubrir usernames válidos | ✅ `UsernameNotFoundException` → 401 idéntico a clave incorrecta |
| A5 | Sin rate limiting en login/registro | — | Fuerza bruta de claves y de códigos | ✅ 10 req/min/IP en `/api/auth/**` (D-011) |
| A6 | `ddl-auto=update` + `show-sql=true`; sin migraciones | `application.yml` | Esquema no reproducible ni auditable; cambios destructivos silenciosos; SQL en logs | ✅ Flyway + `validate`; `show-sql` solo en `dev` |
| A7 | Pagos concurrentes del mismo alumno sin bloqueo | `PaymentServiceImpl` | Dos pagos simultáneos pisan el vencimiento (se cobra sin extender) | ✅ `PESSIMISTIC_WRITE` sobre el estudiante |
| A8 | **Funcionalidad faltante**: recordatorio de vencimiento; el yml tenía propiedades huérfanas y `MAIL_HOST` sin valor por defecto (la app no arrancaba sin él); sin `spring-boot-starter-mail` | `application.yml`, `pom.xml` | Requisito central sin cumplir | ✅ Scheduler + servicio idempotente + correo opcional (D-008) |
| A9 | **Funcionalidad faltante**: ningún endpoint para crear precios | `PriceService` sin controller | Imposible registrar un pago (siempre "no hay precio activo") | ✅ `PriceController` |

### 🟡 Medio

| # | Hallazgo | Estado |
|---|---|---|
| M1 | Sin `AuthenticationEntryPoint`: requests sin token recibían 403 en vez de 401 | ✅ |
| M2 | `/students/expired` mezclaba vencidos con "vencen en 3 días" | ✅ `/expired` y `/expiring?days=` separados, solo activos |
| M3 | `StudentResponse` sin `id` (el admin no puede referenciar alumnos); `PriceResponse.id` no se mapeaba | ✅ |
| M4 | N+1 al listar pagos (`price` perezoso en el mapper) | ✅ `@EntityGraph` |
| M5 | Sin índices en vencimiento, estado, `payments.student_id` | ✅ V2 (D-014) |
| M6 | Validación débil: email de registro sin `@Email`, clave sin mínimo ni máximo (BCrypt trunca a 72 bytes), teléfono libre, `PUT` exigía todos los campos aunque el servicio tenía `null`-checks muertos, `validateId` aceptaba 0 | ✅ |
| M7 | JWT con API deprecada, secreto no validado al arrancar, expiración fija 24 h, sin issuer | ✅ API 0.13, validación en `@PostConstruct`, `jwt.expiration-minutes`, `iss` |
| M8 | CORS fijo a `localhost:4200` en código y `allowCredentials(true)` innecesario | ✅ `CORS_ALLOWED_ORIGINS`, sin credentials (el token va en header) |
| M9 | Swagger público siempre | ✅ apagado por defecto (`SWAGGER_ENABLED`) / on en `dev` |
| M10 | `DELETE` físico de estudiantes con pagos → error 500 por FK y pérdida de historial financiero | ✅ Eliminado; se deshabilita (D-007) |
| M11 | Un solo test (`contextLoads`) que exigía BD real | ✅ 26 tests con H2 + Flyway |
| M12 | `application.yml` en `.gitignore`: repo no reproducible | ✅ Versionado sin secretos + `.env.example` |
| M13 | Logs con emails (PII) y un `log.warn("no existe")` que se escribía incluso cuando el estudiante sí existía | ✅ Se loguean ids |
| M14 | Estado `EXPIRED` declarado pero nunca usado (riesgo de desincronización) | ✅ Eliminado (D-005) |
| M15 | `size` de paginación sin tope (`?size=1000000`) y `sort` inválido → 500 | ✅ máx. 100; `sort` inválido → 400 |

### 🔵 Bajo / sugerencia

- Typos de nomenclatura (`StudentNotFoundExcepcion`, `desactive`) ✅ · código comentado y muerto (`register`, `JwtService`) ✅ · `pom.xml` con placeholders vacíos ✅ · README desactualizado ✅ · `JwtAuthenticationFilter` registrado dos veces como filtro ✅ · `GenerateJwtSecret` con API deprecada ✅ · `PaymentRequest.studentId` redundante con la URL ✅ · `Student` con listas `payments/invoices/user` sin uso que forzaban cargas extra ✅.

## Lo que ya estaba bien

- Arquitectura en capas limpia, DTOs separados de entidades, constructor injection (`@RequiredArgsConstructor`).
- BCrypt para claves; JWT con firma verificada; sesión `STATELESS`; CSRF deshabilitado correctamente para API con token en header.
- Secretos ya salían por variables de entorno (el `application.yml` local solo contenía referencias `${…}` y nunca estuvo versionado).
- Dinero en `BigDecimal` con `precision=19, scale=2`; fechas con `java.time`.
- Paginación en los listados; `open-in-view=false`; enums como `STRING`.
- `GlobalExceptionHandler` con respuesta de error uniforme (base que se conservó y reforzó).

## Riesgos residuales (no verificables en este entorno)

1. **No se probó contra PostgreSQL real**, solo H2 en modo PostgreSQL. Probar en local: arranque con BD limpia (V1+V2) y con la BD existente (baseline en V1 + V2).
2. Envío real de correo no probado (sin servidor SMTP); la lógica está cubierta con un `MailService` simulado.
3. Falta recuperación de contraseña, pago en línea y facturación → ver **Decisiones abiertas** en [DECISIONS.md](DECISIONS.md).
