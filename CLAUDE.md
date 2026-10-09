# box-school (backend)

Sistema de gestión administrativa para una escuela de boxeo: estudiantes con membresía mensual/quincenal, pagos, precios, administradores y aviso por correo 2 días antes del vencimiento.

## Antes de tocar nada

1. **Lee [docs/DECISIONS.md](docs/DECISIONS.md)** — contiene el contexto, el contrato de API, las decisiones tomadas (y por qué) y las decisiones abiertas. No contradigas una decisión vigente sin registrar una nueva que la reemplace.
2. **Al terminar un cambio relevante, actualiza `docs/DECISIONS.md`** (nueva entrada `D-0xx`, contrato de API si cambió, historial). Esa es la memoria del proyecto.
3. Auditoría inicial y su estado: [docs/AUDIT_REPORT.md](docs/AUDIT_REPORT.md).

## Comandos

```bash
./mvnw test                      # 26 tests; H2 en modo PostgreSQL + migraciones reales (no necesita BD ni Docker)
./mvnw spring-boot:run           # requiere variables de entorno: ver .env.example
```

## Reglas del proyecto

- Esquema solo por Flyway (`src/main/resources/db/migration`); **nunca editar una migración aplicada**, crear `V3__…`. `ddl-auto=validate`.
- Nada de secretos ni credenciales por defecto en el código o en `application.yml`; todo por variables de entorno.
- "Hoy" siempre desde el bean `Clock`, nunca `LocalDate.now()` suelto.
- Cada endpoint nuevo: `@PreAuthorize` + matcher en `SecurityConfig`; el estudiante accede a lo suyo por rutas `/me`, nunca por un id que él envíe.
- Listados siempre paginados; evitar N+1 (`@EntityGraph`); cambios de estado financiero con bloqueo/transacción.
- Toda funcionalidad nueva lleva tests (copiar el estilo de `ApiFlowIntegrationTest`).
- Código en inglés, mensajes de la API en español. Errores siempre como `ApiErrorResponse`.
