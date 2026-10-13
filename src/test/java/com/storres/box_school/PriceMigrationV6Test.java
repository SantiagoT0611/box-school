package com.storres.box_school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * Regresion de V6 (re-auditoria Fase 0, N-4): sobre una base que YA tiene precios duplicados activos,
 * la migracion conserva activo el mas reciente de cada tipo, no rompe los pagos que referencian al
 * desactivado y deja el indice UNIQUE funcionando. Usa las migraciones reales con Flyway sobre H2.
 */
class PriceMigrationV6Test {

    private static final String URL =
            "jdbc:h2:mem:v6dup;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    @Test
    void duplicatesAreCleanedKeepingTheNewestAndTheUniqueIndexIsEnforced() throws Exception {
        // 1) Esquema hasta V5 (antes de que existiera la restriccion)
        Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration")
                .target("5").load().migrate();

        try (Connection c = DriverManager.getConnection(URL, "sa", ""); Statement st = c.createStatement()) {
            // 2) Estado "malo" que la carrera de A-2 podia producir: 3 activos MONTHLY + 1 BIWEEKLY + 1 inactivo
            st.execute("insert into prices (type, amount, duration_days, active, created_at) values "
                    + "('MONTHLY', 50000, 30, true, current_timestamp),"
                    + "('MONTHLY', 55000, 30, true, current_timestamp),"
                    + "('MONTHLY', 60000, 30, true, current_timestamp),"
                    + "('BIWEEKLY', 30000, 15, true, current_timestamp),"
                    + "('MONTHLY', 40000, 30, false, current_timestamp)");
            st.execute("insert into students (first_name, last_name, email, phone, registration_date, expiration_date, status) "
                    + "values ('A','B','a@b.test','3000000000', current_date, current_date, 'ACTIVE')");
            // paga con el precio id=1 (que la migracion va a desactivar)
            st.execute("insert into payments (payment_date, period_start, period_end, amount_paid, student_id, price_id) "
                    + "values (current_date, current_date, current_date, 50000, 1, 1)");
        }

        // 3) Se aplica V6
        Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration").load().migrate();

        try (Connection c = DriverManager.getConnection(URL, "sa", ""); Statement st = c.createStatement()) {
            List<String> actives = new ArrayList<>();
            try (ResultSet rs = st.executeQuery(
                    "select type || ':' || id from prices where active = true order by type, id")) {
                while (rs.next()) {
                    actives.add(rs.getString(1));
                }
            }
            assertThat(actives).as("queda un activo por tipo: el de mayor id").containsExactly("BIWEEKLY:4", "MONTHLY:3");

            try (ResultSet rs = st.executeQuery("select count(*) from prices where active_type is not null")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(2);
            }
            try (ResultSet rs = st.executeQuery("select count(*) from payments where price_id = 1")) {
                rs.next();
                assertThat(rs.getInt(1)).as("el pago del precio desactivado sigue intacto").isEqualTo(1);
            }

            // 4) El indice UNIQUE rechaza un segundo activo del mismo tipo aunque se inserte por SQL
            assertThatThrownBy(() -> st.execute(
                    "insert into prices (type, amount, duration_days, active, created_at, active_type) "
                            + "values ('MONTHLY', 1, 30, true, current_timestamp, 'MONTHLY')"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
