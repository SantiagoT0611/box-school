# Auditoría Fase N — <nombre de la fase>

- **Fecha:** AAAA-MM-DD
- **Alcance:** commits / archivos auditados (`git diff <base>..HEAD`)
- **Auditor:** subagente independiente (sin historial de cómo se escribió el código) + revisión del autor
- **Skills aplicadas:** `code-audit` · `backend-security` · `backend-scalability` · `backend-best-practices`
- **Resultado de la puerta:** ✅ aprobada / ❌ reabierta
- Relacionado: [ROADMAP.md](../ROADMAP.md) · [DECISIONS.md](../DECISIONS.md)

## Resumen
2–3 líneas: estado general y conteo de hallazgos por severidad (🔴 n · 🟠 n · 🟡 n · 🔵 n) y cuántos quedan abiertos.

## Hallazgos

Severidades: 🔴 crítico (explotable remotamente / expone datos / evita auth) · 🟠 alto · 🟡 medio · 🔵 bajo.

### 🔴 Crítico
- **[Título]** — `archivo:línea`
  - Riesgo: por qué es explotable o rompe el sistema.
  - Corrección: qué cambiar (snippet mínimo si ayuda).
  - Estado: ⬜ abierto / ✅ corregido en `<commit>` / ⚠ aceptado (ver D-0xx)

### 🟠 Alto
*(misma estructura)*

### 🟡 Medio
*(misma estructura)*

### 🔵 Bajo / sugerencia
*(misma estructura)*

## Seguridad — revisión específica de la fase
Checklist (`backend-security`): autenticación · autorización por endpoint y IDOR · validación de input · inyección · secretos/config · CORS y rate limit · datos sensibles en logs/respuestas · dependencias nuevas.

### Intentos de romperlo
| # | Ataque intentado | Resultado | Test que lo cubre |
|---|---|---|---|
| 1 | *(p. ej. leer el pago de otro estudiante)* | *(403)* | `ApiFlowIntegrationTest#...` |

## Escalabilidad y rendimiento
`backend-scalability`: consultas nuevas (N+1, índices), paginación, estado en memoria, concurrencia y bloqueos, trabajo que debería ser asíncrono, carga esperada y trade-offs asumidos.

## Buenas prácticas y mantenibilidad
`backend-best-practices`: capas, manejo de errores, duplicación, nombres, tests de la lógica crítica, documentación actualizada (DECISIONS/ROADMAP/contrato de API).

## Lo que está bien
Reconocer explícitamente las prácticas correctas encontradas (un informe solo con negativos es menos creíble).

## Riesgos residuales
Lo que no se pudo verificar en este entorno y cómo se verificará.

## Cierre
- [ ] 0 críticos/altos abiertos
- [ ] Medios corregidos o con excepción registrada en `DECISIONS.md`
- [ ] `./mvnw test` en verde (n tests)
- [ ] Historial de `DECISIONS.md` actualizado y fase marcada en `ROADMAP.md`
