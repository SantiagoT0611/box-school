-- V6: un solo precio activo por tipo, garantizado por la base de datos (auditoria Fase 0, A-2).
-- El SELECT ... FOR UPDATE no alcanza: con READ COMMITTED la segunda transaccion no ve el precio recien
-- insertado y creaba un segundo activo del mismo tipo, lo que dejaba todos los pagos de ese tipo en 500.
-- La unicidad se expresa con una columna auxiliar (active_type = type mientras el precio esta activo, NULL
-- si no) y un indice UNIQUE: portable entre PostgreSQL y H2 (H2 no soporta indices parciales) y los NULL
-- no chocan entre si. La entidad Price la mantiene sincronizada.

-- Por si una base ya tuviera duplicados: se conserva activo el mas reciente de cada tipo y se desactivan
-- los anteriores (el historial no se borra).
update prices set active = false
 where active = true
   and id < (select max(p2.id) from prices p2 where p2.type = prices.type and p2.active = true);

alter table prices add column active_type varchar(50);
update prices set active_type = type where active = true;
create unique index uk_prices_one_active_per_type on prices (active_type);

-- Indice redundante: idx_students_status_expiration (status, expiration_date) ya cubre las consultas reales.
drop index if exists idx_students_expiration_date;
