# Box School — Cómo seguimos (ROADMAP)

> Complementa a [DECISIONS.md](DECISIONS.md) (el *por qué* y el contrato de API). Aquí está **qué sigue, en qué orden y cuándo se considera terminado**.
> Se actualiza al cerrar cada fase: marcar casillas, enlazar el informe de auditoría y anotar en el Historial de `DECISIONS.md`.

## 1. Estado actual (2026-10-08)

- Backend auditado y corregido, 26 tests en verde, publicado en `origin/main` (commit `0738df1`). Informe base: [AUDIT_REPORT.md](AUDIT_REPORT.md).
- Probado solo con H2 en modo PostgreSQL. **No** verificado aún en PostgreSQL real ni con correo real (Fase 0).
- Frontend: **no iniciado, bloqueado hasta recibir mockups** (ver Fase 5).

### Decisiones del dueño que condicionan lo que sigue
- **País: Colombia** (COP). Pasarela candidata: **Wompi o PayU**.
- **El cobro en línea es opcional**: si la escuela no quiere pagar la comisión, el sistema debe funcionar completo con una **zona de pago manual** (QR del banco + número de cuenta + datos de transferencia), donde el estudiante reporta su pago y el admin lo confirma.
- Orden elegido: primero **verificar en PostgreSQL + recuperar contraseña**, luego pagos.

---

## 2. Puerta de calidad por fase (regla global, no negociable)

**Una fase no se da por cerrada hasta pasar esta puerta.** Se ejecuta al terminar cada sección, antes del commit de cierre. Postura: *el código nuevo está mal hasta que se demuestre lo contrario*.

1. **Auditoría independiente y escéptica, en un subagente separado.** Recibe solo el diff/código final y los checklists (sin el historial de cómo se escribió) para evitar sesgo de confirmación. Se aplican **todas** estas skills:
   - `code-audit` — formato del informe y criterio de severidades.
   - `backend-security` — OWASP: autenticación/autorización/IDOR, inyección, secretos, CORS, rate limit, logs sin PII, dependencias.
   - `backend-scalability` — N+1, índices, paginación, estado en memoria, concurrencia/locks, trabajo asíncrono.
   - `backend-best-practices` — capas, errores, nombres, duplicación, tests, documentación.
2. **Revisión de seguridad específica de la fase con "intentos de romperlo" por escrito**: qué se intentó (IDOR, reuso de tokens/códigos, fuerza bruta, payloads maliciosos, carreras de concurrencia, webhooks falsos…), qué ocurrió y qué test lo cubre.
3. **Informe detallado** en `docs/audits/FASE-N-<nombre>.md` usando [audits/TEMPLATE.md](audits/TEMPLATE.md): resumen con conteo por severidad, hallazgos 🔴🟠🟡🔵 con `archivo:línea` / riesgo / corrección, "lo que está bien", riesgos residuales y estado de cada hallazgo.
4. **Criterio de cierre:**
   - Cero hallazgos críticos o altos abiertos (si aparece uno, la fase se reabre).
   - Medios corregidos, o con excepción justificada y registrada en `DECISIONS.md`.
   - `./mvnw test` en verde, con los tests de seguridad de la fase incluidos.
5. **Registro:** entrada en el Historial de `DECISIONS.md` y enlace al informe desde la fase correspondiente de este archivo.

Un hallazgo no se descarta por "ser improbable": se corrige o se acepta por escrito con su razón.

---

## 3. Fases

Leyenda: ⬜ pendiente · 🟦 en curso · ✅ cerrada (con informe enlazado)

### ✅ Línea base — auditoría inicial
Informe: [audits/LINEA-BASE-auditoria-inicial.md](audits/LINEA-BASE-auditoria-inicial.md) → [AUDIT_REPORT.md](AUDIT_REPORT.md).

---

### 🟦 Fase 0 — Verificar en PostgreSQL real *(bloqueante; en curso)*
**Objetivo:** comprobar lo que H2 no puede (riesgos residuales del informe base).

**Hecho (2026-10-08)** — con respaldo previo (`backups/box-school_antes-de-flyway_*.sql`, fuera del repo) y una copia de la base para las pruebas de escritura:
- [x] Arranque con la BD **existente**: Flyway toma V1 como baseline y aplica V2–V5; Hibernate `validate` pasa en PostgreSQL 18. Aplicado también a la base real (`box-school`): 4 estudiantes intactos, historial de Flyway V1–V5 sin errores.
- [x] Recorrido completo por HTTP con [`scripts/smoke_test.py`](../scripts/smoke_test.py) (36 chequeos): precio → estudiante → registro con código → permisos 401/403 → pagos → deshabilitar. **36/36.**
- [x] **Seis pagos simultáneos** al mismo estudiante: los 6 dan 201 y el vencimiento suma exactamente +180 días, periodos contiguos (valida `PESSIMISTIC_WRITE` en PostgreSQL real).
- [x] Reproducción en test del esquema heredado: `LegacySchemaRepairTest` (H2) y **`PostgresLegacyUpgradeTest` (PostgreSQL 16 real en Docker/Testcontainers)**: esquema creado "a la Hibernate" → Flyway baseline → V2–V5 → `validate`, login de usuarios heredados y 8 pagos simultáneos. Comprobado por mutación: con V5 desactivada esos tests fallan con el mismo error que tuvo la base real.
- [x] Eliminado el usuario `admin` (admin123) de la base real (solo la cuenta; el estudiante vinculado quedó intacto). `salito` es el único administrador.
- [x] `APP_TIMEZONE=America/Bogota` fijada como variable de usuario.

**Hallazgos de esta verificación** (H2 no podía mostrarlos; todos corregidos):
1. 🔴 **Login roto para usuarios existentes:** `Salito` y `Sofi` tenían mayúsculas y el login normaliza a minúsculas → no habrían podido entrar. → `V3` normaliza usernames y emails (falla si hay colisiones, no pisa datos).
2. 🔴 **Todo pago fallaba (409):** la tabla `payments` real tenía una columna heredada `amount NOT NULL` → `V4` la elimina.
3. 🔴 **Todo pago fallaba (409):** `payments.price_id` tenía además una FK vieja hacia la tabla huérfana `price` → `V5`.
4. 🟠 **`students.email` sin UNIQUE** en la base real (V1 sí lo define): permitía duplicados por carrera → `V5` lo agrega.
- Causa común: `ddl-auto=update` deja restos y V1 asumía una base "limpia". `V5__repair_legacy_schema` (migración Java) compara con lo esperado y repara; es idempotente y falla en lugar de tocar datos.
- La tabla huérfana `price` (vacía) queda en la base; se puede borrar a mano cuando quieras (`drop table price;`), no estorba.

**Pendiente de la Fase 0**
- [x] Correo con **Mailtrap** (SMTP real; bandeja de pruebas): el servidor envía el código de registro y los recordatorios; con la pausa de 11 s entre correos, 2 de 2 enviados en una corrida y 0 en la segunda (idempotente). **Hallazgo:** el plan gratis de Mailtrap rechaza ráfagas (`550 Too many emails per second`, incluso a 1 envío/s); el servicio liberó el registro y lo reenvió en el ciclo siguiente, como se diseñó → se añadió `app.membership-reminder.delay-between-emails-ms` (por defecto 1000 ms, `REMINDER_DELAY_MS`). Para probar en la sandbox gratuita usar `REMINDER_DELAY_MS=11000`.
- [x] El dueño confirmó en su bandeja de Mailtrap la llegada de los correos (código de registro y avisos). Se corrigieron tildes y formato de fecha; `MailServiceTest` cubre remitente y UTF-8. **Antes de producción definir `MAIL_FROM`.**
- [ ] Puerto: Docker Desktop ocupa el **8080** en esta máquina; arrancar la app con `SERVER_PORT=8082` (o liberar el puerto).
- [ ] **Puerta de calidad** de la fase con auditor independiente (foco: migraciones, configuración, despliegue, secretos) → `docs/audits/FASE-0-verificacion-postgres.md`.

### ⬜ Fase 1 — Recuperar y cambiar contraseña (O-5)
**Objetivo:** que un estudiante que olvidó su clave pueda recuperarla sin intervención del admin, sin abrir huecos de seguridad.

- [ ] Migración `V6`: tabla `password_reset_tokens` (hash SHA-256, expira a los 30 min, uso único) y columna `users.password_changed_at`.
- [ ] `POST /api/auth/forgot-password`: siempre responde **202 genérico** (no revela si el email existe), con rate limit, envía correo.
- [ ] `POST /api/auth/reset-password` (token + nueva clave).
- [ ] `POST /api/auth/change-password` (autenticado; pide la clave actual).
- [ ] Al cambiar la clave, los JWT emitidos antes dejan de valer (`iat` vs `password_changed_at` en `JwtAuthenticationFilter`).
- **Reutilizar:** hash/uso único de `RegistrationCodeService`, `MailService.sendQuietly`, `RateLimitFilter`, plantilla de `ApiFlowIntegrationTest`.
- **Tests de ataque:** token expirado · reutilizado · adivinado · email inexistente (misma respuesta y mismo tiempo aprox.) · token viejo tras cambiar la clave · fuerza bruta contra reset.

**Puerta de calidad:** foco en enumeración de usuarios, timing, vida/entropía del token y que no se loguee ningún token.

---

### ⬜ Fase 2 — Pagos con dos modalidades (O-1)
**Principio (D-017/D-018):** un pago **solo extiende la membresía cuando queda `APPROVED`**. Nunca por el redirect del navegador ni por datos que envíe el cliente; el monto sale siempre del precio vigente.

#### 2a. Modelo y migración `V7`
- [ ] `payments` gana: `status` (`PENDING|APPROVED|REJECTED`; filas existentes → `APPROVED`), `method` (`CASH|BANK_TRANSFER|GATEWAY`), `reference`, `proof_image` (opcional), `reviewed_by`, `reviewed_at`, `rejection_reason`. `period_start/end` pasan a nullable hasta aprobar.
- [ ] Tabla `payment_settings` (una fila): banco, tipo y número de cuenta, titular, documento, instrucciones, imagen del **QR**, flags `transfer_enabled` y `gateway_enabled`.
- [ ] Refactor: extraer de `PaymentServiceImpl.payMembership` el cálculo de periodo + `findByIdForUpdate` a un `approve(payment)` reutilizable por las tres modalidades.

#### 2b. Zona de pago manual (QR + cuenta) — **se hace primero; no depende de terceros**
- [ ] Admin: `PUT /api/payments/settings` (datos bancarios + subir QR; validar tipo real de imagen y tamaño ≤ 512 KB; guardado en BD por ahora).
- [ ] Estudiante: `GET /api/payments/bank-info` (datos + QR) y `POST /api/payments/me/transfer-report` (`type`, `reference`, comprobante opcional) → pago `PENDING`.
- [ ] Admin: `GET /api/payments/pending`, `POST /api/payments/{id}/approve|reject` (con motivo). Aprobar extiende la membresía y avisa por correo al estudiante.
- [ ] El pago en efectivo existente (`POST /api/payments/students/{id}`) pasa a `APPROVED` inmediato con `method=CASH`.
- **Tests de ataque:** IDOR (ver/aprobar pagos ajenos) · doble aprobación · aprobar uno ya rechazado · un pendiente que **no** debe extender · archivo disfrazado de imagen · referencia duplicada · monto manipulado.

#### 2c. Pasarela (Wompi primero; PayU después por la misma interfaz) — *solo si la escuela la quiere*
- [ ] Interfaz `PaymentGateway` + `WompiGateway`, activable con `gateway_enabled` y credenciales por variables de entorno.
- [ ] `POST /api/payments/me/checkout`: crea pago `PENDING` con `reference` única y devuelve lo necesario para abrir el widget (incluida la firma de integridad).
- [ ] `POST /api/payments/webhooks/wompi` (público, fuera del JWT): verificar la firma del evento, **consultar la transacción a la API de la pasarela antes de confiar**, idempotente por `reference`, y recién entonces aprobar.
- **Tests de ataque:** webhook sin firma / con firma falsa / repetido / con monto distinto / con referencia inexistente.
- **Pendiente del dueño:** cuenta sandbox de Wompi y confirmar que la escuela acepta la comisión. Sin eso 2c queda en pausa y el sistema funciona completo con 2b.

**Puerta de calidad:** se corre **por separado para 2b y para 2c** (dos informes). Énfasis: flujo de dinero, idempotencia, firmas, concurrencia, validación de archivos.

---

### ⬜ Fase 3 — Extras de administración *(según prioridad del dueño)*
- [ ] Resumen para el dashboard: activos, vencidos, por vencer, ingresos del mes.
- [ ] Crear otros administradores y editar perfil propio (O-10).
- [ ] Bitácora de acciones del admin `audit_log` (O-9): quién desactivó, cobró, aprobó o cambió un precio, y cuándo.
- [ ] Decidir facturas (O-3): implementarlas sobre pagos aprobados o eliminar `Invoice`.
- [ ] Refresh token, si el frontend lo exige (O-2).

**Puerta de calidad:** una por ítem implementado (o agrupada si son pequeños, dejando constancia).

---

### ⬜ Fase 4 — Operación
- [ ] GitHub Actions: `./mvnw verify` en cada push + escaneo de dependencias.
- [ ] `Dockerfile` + `docker-compose` (app + PostgreSQL) y guía de despliegue.
- [ ] Si se escala a varias instancias: rate limit en Redis y ShedLock (O-6).

**Puerta de calidad:** contenedor, secretos, superficie expuesta, imagen base, usuario no-root.

---

### ⏸ Fase 5 — Frontend (Angular): **PREPARADO, NO INICIADO**
> **Bloqueado hasta que el dueño envíe los mockups** (D-020). No se escribe código de frontend ni se eligen tipografías, paleta o componentes antes de verlos.

**Material que se necesita del dueño**
- [ ] Mockups/capturas de todas las pantallas: login y registro con código · mi membresía · pagar (QR/cuenta y, si aplica, pasarela) · mis pagos · panel admin (estudiantes, vencidos/por vencer, pagos pendientes, precios, ajustes de pago).
- [ ] Logo y branding si existen; referencias visuales que le gusten.
- [ ] ¿Solo web o también móvil/PWA?

**Cuando lleguen los mockups**
1. Usar la skill `ui-ux-engineer` para derivar el sistema de diseño (paleta, tipografía, espaciado, componentes) **a partir de los mockups**.
2. Documentarlo en `docs/DESIGN.md` y registrar la decisión en `DECISIONS.md`.
3. Recién entonces crear el proyecto Angular (carpeta/repositorio aparte), consumiendo el contrato de [DECISIONS.md §2](DECISIONS.md).
4. Cada módulo cierra con su puerta de calidad adaptada: almacenamiento del token, XSS, rutas protegidas por rol (la autorización real sigue siendo del backend), accesibilidad, rendimiento de listas.

**Preparación del backend que ya está lista:** contrato de API estable, Swagger en perfil `dev`, CORS por entorno (`CORS_ALLOWED_ORIGINS`), errores en español con formato uniforme, `daysUntilExpiration` / `membershipExpired` para banners.

---

## 4. Riesgos y puntos a vigilar
- QR/comprobantes guardados en BD: aceptable por tamaño limitado; migrar a almacenamiento de objetos si crece.
- La aprobación manual depende de que el admin revise a tiempo (mejora: correo al admin ante un pago pendiente).
- Webhook público: siempre firma + consulta a la pasarela; jamás confiar en el cuerpo recibido.
- Auditarse a uno mismo sesga: por eso el subagente independiente y el informe por escrito.
- Cada migración nueva debe ser un `V*` nuevo; nunca editar una aplicada (D-012).

## 5. Pendientes del dueño (resumen)
1. Confirmar zona horaria (`America/Bogota`).
2. Cuenta sandbox de Wompi/PayU y decisión sobre la comisión (solo para 2c).
3. Datos bancarios y QR reales de la escuela (para 2b; en desarrollo se usan de prueba).
4. **Mockups del frontend** (desbloquea la Fase 5).
5. Decidir facturación (O-3) y si se necesitarán más administradores (O-10).
