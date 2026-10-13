package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * V5: repara bases creadas ANTES de Flyway con Hibernate ddl-auto=update, que dejo restos que V1 no tiene:
 * <ul>
 *   <li>una clave foranea vieja payments.price_id -> price (tabla singular, huerfana) que hace fallar TODO pago,</li>
 *   <li>restricciones UNIQUE ausentes (p. ej. students.email), que V1 si define.</li>
 * </ul>
 * Es una migracion Java porque el SQL condicional no es portable entre PostgreSQL y H2 (tests).
 * En una base creada por V1 todo esto ya existe y la migracion no cambia nada (idempotente).
 * Si hubiera datos que impidan una restriccion (p. ej. emails duplicados) FALLA a proposito, con el
 * error de la base, para resolverlo a mano: nunca borra ni modifica datos.
 */
public class V5__repair_legacy_schema extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        DatabaseMetaData meta = connection.getMetaData();
        String schema = connection.getSchema();

        // Claves foraneas viejas hacia la tabla huerfana "price"
        for (String fk : foreignKeysTo(meta, schema, "payments", "price")) {
            execute(connection, "alter table payments drop constraint \"" + fk + "\"");
        }

        // Claves foraneas esperadas (las mismas que V1)
        ensureForeignKey(connection, meta, schema, "payments", "price_id", "prices", "fk_payments_price");
        ensureForeignKey(connection, meta, schema, "payments", "student_id", "students", "fk_payments_student");
        ensureForeignKey(connection, meta, schema, "users", "student_id", "students", "fk_users_student");
        ensureForeignKey(connection, meta, schema, "invoices", "student_id", "students", "fk_invoices_student");

        // Restricciones UNIQUE esperadas (las mismas que V1)
        ensureUnique(connection, meta, schema, "students", "email", "uk_students_email");
        ensureUnique(connection, meta, schema, "users", "username", "uk_users_username");
        ensureUnique(connection, meta, schema, "users", "student_id", "uk_users_student");
        ensureUnique(connection, meta, schema, "invoices", "invoice_number", "uk_invoices_number");
    }

    // ------------------------------------------------------------------ helpers

    private static void ensureForeignKey(Connection c, DatabaseMetaData meta, String schema, String table,
            String column, String referencedTable, String name) throws SQLException {
        if (!hasTable(meta, schema, table) || !hasTable(meta, schema, referencedTable)) {
            return;
        }
        try (ResultSet rs = meta.getImportedKeys(null, schema, ident(meta, table))) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("FKCOLUMN_NAME"))
                        && referencedTable.equalsIgnoreCase(rs.getString("PKTABLE_NAME"))) {
                    return; // ya existe
                }
            }
        }
        execute(c, "alter table " + table + " add constraint " + name + " foreign key (" + column
                + ") references " + referencedTable + " (id)");
    }

    private static void ensureUnique(Connection c, DatabaseMetaData meta, String schema, String table,
            String column, String name) throws SQLException {
        if (!hasTable(meta, schema, table)) {
            return;
        }
        Map<String, List<String>> uniqueIndexes = new LinkedHashMap<>();
        try (ResultSet rs = meta.getIndexInfo(null, schema, ident(meta, table), true, false)) {
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                String columnName = rs.getString("COLUMN_NAME");
                if (indexName != null && columnName != null && !rs.getBoolean("NON_UNIQUE")) {
                    uniqueIndexes.computeIfAbsent(indexName, k -> new ArrayList<>()).add(columnName.toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        boolean exists = uniqueIndexes.values().stream().anyMatch(cols -> cols.equals(List.of(column)));
        if (!exists) {
            execute(c, "alter table " + table + " add constraint " + name + " unique (" + column + ")");
        }
    }

    /** Nombres de las claves foraneas de {@code table} que apuntan a {@code referencedTable}. */
    private static List<String> foreignKeysTo(DatabaseMetaData meta, String schema, String table,
            String referencedTable) throws SQLException {
        List<String> names = new ArrayList<>();
        if (!hasTable(meta, schema, table)) {
            return names;
        }
        try (ResultSet rs = meta.getImportedKeys(null, schema, ident(meta, table))) {
            while (rs.next()) {
                if (referencedTable.equalsIgnoreCase(rs.getString("PKTABLE_NAME"))) {
                    names.add(rs.getString("FK_NAME"));
                }
            }
        }
        return names;
    }

    private static boolean hasTable(DatabaseMetaData meta, String schema, String table) throws SQLException {
        try (ResultSet rs = meta.getTables(null, schema, ident(meta, table), new String[] { "TABLE" })) {
            return rs.next();
        }
    }

    /** Los metadatos distinguen mayusculas segun la base: PostgreSQL guarda minusculas, otras mayusculas. */
    private static String ident(DatabaseMetaData meta, String name) throws SQLException {
        if (meta.storesUpperCaseIdentifiers()) {
            return name.toUpperCase(java.util.Locale.ROOT);
        }
        return meta.storesLowerCaseIdentifiers() ? name.toLowerCase(java.util.Locale.ROOT) : name;
    }

    private static void execute(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }
}
