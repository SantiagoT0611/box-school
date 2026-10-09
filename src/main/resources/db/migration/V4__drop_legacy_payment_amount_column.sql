-- V4: limpieza de un resto de versiones anteriores del modelo.
-- Las bases creadas con Hibernate ddl-auto=update pueden tener payments.amount (NOT NULL) de cuando la
-- entidad se llamaba distinto; Hibernate update nunca borra columnas y esa columna hace fallar TODO INSERT
-- de pagos (el dato real vive en amount_paid). En bases nuevas no existe y esta sentencia no hace nada.
alter table payments drop column if exists amount;
