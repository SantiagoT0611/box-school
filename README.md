# Boxing School — Backend

API REST para la gestión administrativa de una escuela de boxeo: estudiantes, membresías, pagos, precios y recordatorios de vencimiento por correo. Autenticación JWT con roles `ADMIN` y `USER` (estudiante).

> Decisiones de diseño, contrato de API y pendientes: **[docs/DECISIONS.md](docs/DECISIONS.md)**. Informe de auditoría: [docs/AUDIT_REPORT.md](docs/AUDIT_REPORT.md).

## Tecnologías

Java 21 · Spring Boot 3.5 (Web, Data JPA, Security, Validation, Mail, Actuator) · PostgreSQL · Flyway · JJWT · springdoc (Swagger) · Lombok · Maven

## Cómo funciona (resumen)

1. El **admin** inscribe a un estudiante → recibe un **código de registro** de un solo uso (y le llega por correo al estudiante).
2. El **estudiante** activa su cuenta con su email + ese código (`POST /api/auth/register`) y desde ahí consulta su membresía (`GET /api/students/me`) y sus pagos (`GET /api/payments/me`).
3. El **admin** define precios (`POST /api/prices`), registra pagos (`POST /api/payments/students/{id}`), ve quién tiene la membresía vencida (`/api/students/expired`) o por vencer (`/api/students/expiring`) y puede deshabilitar/habilitar estudiantes.
4. Cada día (08:00 por defecto) el sistema envía un correo a los estudiantes activos cuya membresía vence en los próximos 2 días.

## Ejecución local

1. Crear la base de datos PostgreSQL vacía (por ejemplo `boxing_school`). Las tablas las crea Flyway al arrancar.
2. Definir las variables de entorno (ver [.env.example](.env.example)). Obligatorias: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`. Para crear el primer administrador: `BOOTSTRAP_ADMIN_USERNAME` y `BOOTSTRAP_ADMIN_PASSWORD` (mínimo 12 caracteres).
   - Secreto JWT: ejecutar `com.storres.box_school.util.GenerateJwtSecret` (imprime uno aleatorio).
   - Correo (opcional): `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`. Sin host la app funciona pero no envía correos.
3. Arrancar:

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Con el perfil `dev`, Swagger queda en `http://localhost:8080/swagger-ui.html`. Salud: `GET /actuator/health`.

### Si ya tenías una base creada con la versión anterior

Flyway la adopta automáticamente (`baseline-version: 1`) y aplica la migración V2. **Elimina o cambia la clave del usuario `admin` / `admin123`** que creaba el antiguo seeder, y crea tu administrador con las variables `BOOTSTRAP_ADMIN_*`.
Las variables de correo cambiaron de nombre (`MAIL_HOST` → `SPRING_MAIL_HOST`, etc.) y `LICENSE_REMINDER_CRON` → `REMINDER_CRON`.

## Tests

```bash
./mvnw test
```

Usan H2 en modo PostgreSQL con las mismas migraciones Flyway: no requieren base de datos ni Docker.

## Estructura

```
config/       configuración (seguridad, propiedades, manejo global de errores, bootstrap de admin)
controller/   endpoints REST
service/      lógica de negocio (+ correo y recordatorios)
repository/   acceso a datos (Spring Data)
security/     JWT, filtros, rate limit, handlers 401/403
mapper/ model/ exception/ util/
resources/db/migration/   migraciones Flyway
docs/         DECISIONS.md (memoria del proyecto) y AUDIT_REPORT.md
```

## Autor

Santiago Torres — proyecto de práctica profesional backend.
