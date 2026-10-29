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

import com.zaxxer.hikari.HikariDataSource;
import io.camunda.application.commons.pt.LazyPhysicalTenantResource.State;
import io.camunda.application.commons.pt.PhysicalTenantResourceUnavailableException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class LazyPoolTest {

  private static final RdbmsConnection FIRST = new RdbmsConnection("jdbc:h2:mem:first", "a", "1");
  private static final RdbmsConnection SECOND = new RdbmsConnection("jdbc:h2:mem:second", "b", "2");

  private final List<RdbmsConnection> built = new ArrayList<>();

  @Test
  void shouldBuildThePoolWithTheSettingsCurrentAtFirstUse() {
    // given
    final var pool = new LazyPool("tenanta", FIRST, this::build);
    assertThat(pool.updateIfNotCreated(SECOND)).isTrue();

    // when
    final var hikari = pool.get();

    // then
    assertThat(built).containsExactly(SECOND);
    assertThat(hikari.getJdbcUrl()).isEqualTo(SECOND.url());
    pool.close();
  }

  @Test
  void shouldNotTakeNewSettingsOnceThePoolExists() {
    // given
    final var pool = new LazyPool("tenanta", FIRST, this::build);
    pool.get();

    // when
    final var updated = pool.updateIfNotCreated(SECOND);

    // then
    assertThat(updated).isFalse();
    assertThat(built).containsExactly(FIRST);
    pool.close();
  }

  @Test
  void shouldCloseBeforeFirstUse() {
    // given
    final var pool = new LazyPool("tenanta", FIRST, this::build);

    // when
    pool.close();

    // then
    assertThat(pool.state()).isEqualTo(State.CLOSED);
    assertThat(built).isEmpty();
    assertThat(pool.updateIfNotCreated(SECOND)).isFalse();
    assertThatThrownBy(pool::get).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldCloseTheBuiltPool() {
    // given
    final var pool = new LazyPool("tenanta", FIRST, this::build);
    final var hikari = pool.get();

    // when
    pool.close();

    // then
    assertThat(hikari.isClosed()).isTrue();
    assertThat(pool.state()).isEqualTo(State.CLOSED);
  }

  @Test
  void shouldCloseAfterAFailedBuild() {
    // given
    final var pool =
        new LazyPool(
            "tenanta",
            FIRST,
            settings -> {
              throw new IllegalStateException("no driver");
            });
    assertThatThrownBy(pool::get).isInstanceOf(PhysicalTenantResourceUnavailableException.class);
    assertThat(pool.state()).isEqualTo(State.FAILED);

    // when
    pool.close();

    // then
    assertThat(pool.state()).isEqualTo(State.CLOSED);
  }

  @Test
  void shouldCloseTwice() {
    // given
    final var pool = new LazyPool("tenanta", FIRST, this::build);
    final var hikari = pool.get();
    pool.close();

    // when
    pool.close();

    // then
    assertThat(hikari.isClosed()).isTrue();
    assertThat(pool.state()).isEqualTo(State.CLOSED);
  }

  private HikariDataSource build(final RdbmsConnection settings) {
    built.add(settings);
    final var hikari = new HikariDataSource();
    hikari.setJdbcUrl(settings.url());
    hikari.setUsername(settings.username());
    hikari.setPassword(settings.password());
    return hikari;
  }
}
