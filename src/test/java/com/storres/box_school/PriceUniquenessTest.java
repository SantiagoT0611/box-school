package com.storres.box_school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.storres.box_school.model.entity.Price;
import com.storres.box_school.model.shared.PaymentType;
import com.storres.box_school.repository.PriceRepository;

/**
 * Regresion de A-2 (auditoria Fase 0): "un solo precio activo por tipo" lo garantiza la BASE DE DATOS
 * (indice UNIQUE de V6), no un SELECT ... FOR UPDATE que con READ COMMITTED no ve la fila recien insertada.
 */
@HermeticTest
@SpringBootTest
@Transactional
class PriceUniquenessTest {

    @Autowired
    PriceRepository prices;

    private static Price price(PaymentType type, boolean active) {
        Price p = new Price();
        p.setType(type);
        p.setAmount(new BigDecimal("50000.00"));
        p.setDurationDays(30);
        p.setActive(active);
        p.setCreatedAt(LocalDateTime.now());
        return p;
    }

    @Test
    void databaseRejectsASecondActivePriceOfTheSameType() {
        prices.saveAndFlush(price(PaymentType.MONTHLY, true));

        assertThatThrownBy(() -> prices.saveAndFlush(price(PaymentType.MONTHLY, true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void historyAndOtherTypesAreStillAllowed() {
        assertThatCode(() -> {
            prices.saveAndFlush(price(PaymentType.MONTHLY, false)); // historial: cualquier cantidad
            prices.saveAndFlush(price(PaymentType.MONTHLY, false));
            prices.saveAndFlush(price(PaymentType.MONTHLY, true));  // un activo por tipo
            prices.saveAndFlush(price(PaymentType.BIWEEKLY, true)); // otro tipo: independiente
        }).doesNotThrowAnyException();

        assertThat(prices.findByActiveTrueOrderByType()).hasSize(2);
    }

    @Test
    void activeTypeColumnFollowsTheActiveFlag() {
        Price p = prices.saveAndFlush(price(PaymentType.MONTHLY, true));
        assertThat(p.getActiveType()).isEqualTo("MONTHLY");

        p.setActive(false);
        prices.saveAndFlush(p);
        assertThat(p.getActiveType()).isNull();

        // al quedar libre, otro precio del mismo tipo puede activarse
        assertThatCode(() -> prices.saveAndFlush(price(PaymentType.MONTHLY, true))).doesNotThrowAnyException();
    }
}
