/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.application.commons.rdbms.SharedConnectionBudget.Limits;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class SharedConnectionBudgetTest {

  private static final Duration SHORT = Duration.ofMillis(100);

  private final DataSource pool = pool();

  @Test
  void shouldKeepATenantsGuaranteeWhileAnotherTenantTakesTheWholeBurst() throws Exception {
    // given
    final var budget =
        SharedConnectionBudget.of(Map.of("busy", new Limits(2, 10), "quiet", new Limits(2, 10)), 4);
    final var busy = budget.dataSourceFor("busy", pool, SHORT);
    final var quiet = budget.dataSourceFor("quiet", pool, SHORT);

    // when
    final var held = take(busy, 6);

    // then
    assertThat(budget.poolSize()).isEqualTo(8);
    assertThatThrownBy(busy::getConnection).isInstanceOf(SQLTransientConnectionException.class);
    assertThat(take(quiet, 2)).hasSize(2);
    assertThatThrownBy(quiet::getConnection).isInstanceOf(SQLTransientConnectionException.class);
    assertThat(held).hasSize(6);
  }

  @Test
  void shouldCapATenantAtItsMaximumEvenWithBurstLeft() throws Exception {
    // given
    final var budget = SharedConnectionBudget.of(Map.of("tenant", new Limits(1, 3)), 10);
    final var tenant = budget.dataSourceFor("tenant", pool, SHORT);

    // when
    take(tenant, 3);

    // then
    assertThatThrownBy(tenant::getConnection)
        .isInstanceOf(SQLTransientConnectionException.class)
        .hasMessageContaining("maximum");
  }

  @Test
  void shouldHandABurstConnectionToAWaitingTenantWhenAnotherClosesOne() throws Exception {
    // given
    final var budget =
        SharedConnectionBudget.of(Map.of("a", new Limits(0, 5), "b", new Limits(0, 5)), 2);
    final var a = budget.dataSourceFor("a", pool, SHORT);
    final var b = budget.dataSourceFor("b", pool, Duration.ofSeconds(10));
    final var heldByA = take(a, 2);

    // when
    final var waiting = CompletableFuture.supplyAsync(() -> connect(b));
    heldByA.getFirst().close();

    // then
    assertThat(waiting.get(10, TimeUnit.SECONDS)).isNotNull();
    assertThat(budget.inUse("a")).isEqualTo(1);
    assertThat(budget.inUse("b")).isEqualTo(1);
  }

  @Test
  void shouldGiveBackAPermitOnceWhenAConnectionIsClosedTwice() throws Exception {
    // given
    final var budget = SharedConnectionBudget.of(Map.of("tenant", new Limits(1, 1)), 0);
    final var tenant = budget.dataSourceFor("tenant", pool, SHORT);
    final var connection = tenant.getConnection();

    // when
    connection.close();
    connection.close();

    // then
    assertThat(budget.inUse("tenant")).isZero();
    take(tenant, 1);
    assertThatThrownBy(tenant::getConnection).isInstanceOf(SQLTransientConnectionException.class);
  }

  private static List<Connection> take(final DataSource dataSource, final int count)
      throws SQLException {
    final var held = new ArrayList<Connection>();
    for (int i = 0; i < count; i++) {
      held.add(dataSource.getConnection());
    }
    return held;
  }

  private static Connection connect(final DataSource dataSource) {
    try {
      return dataSource.getConnection();
    } catch (final SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  private static DataSource pool() {
    final var pool = mock(DataSource.class);
    try {
      when(pool.getConnection()).thenAnswer(ignored -> mock(Connection.class));
    } catch (final SQLException e) {
      throw new IllegalStateException(e);
    }
    return pool;
  }
}
