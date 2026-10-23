/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.conformance.ConformanceDbTestBase;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

/**
 * A key whose component is outside of its declared domain cannot match a stored row. Reading it is
 * a lookup that finds nothing, as in RocksDB, whereas writing it would store the row out of order
 * and therefore fails.
 */
final class SqliteOutOfDomainKeyTest extends ConformanceDbTestBase {

  @Override
  protected <T extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<T> factory() {
    return new SqliteDbFactory<>();
  }

  @Test
  void shouldMissOnGetOfNegativeKey() {
    // given
    final var cf = filled();

    // then
    assertThat(cf.get(negative())).isNull();
  }

  @Test
  void shouldMissOnExistsOfNegativeKey() {
    // given
    final var cf = filled();

    // then
    assertThat(cf.exists(negative())).isFalse();
  }

  @Test
  void shouldCountNothingForNegativePrefix() {
    // given
    final var cf = filled();

    // then
    assertThat(cf.countEqualPrefix(negative())).isZero();
  }

  @Test
  void shouldVisitNothingForNegativePrefix() {
    // given
    final var cf = filled();

    // when
    final var visited = new ArrayList<Long>();
    cf.whileEqualPrefix(
        negative(),
        (key, value) -> {
          visited.add(1L);
        });

    // then
    assertThat(visited).isEmpty();
  }

  @Test
  void shouldVisitNothingForNegativeStartKey() {
    // given
    final var cf = filled();

    // when
    final var visited = new ArrayList<Long>();
    cf.whileTrue(
        negative(),
        (key, value) -> {
          visited.add(1L);
          return true;
        });

    // then
    assertThat(visited).isEmpty();
  }

  @Test
  void shouldNotDeleteNegativeKey() {
    // given
    final var cf = filled();

    // then
    cf.deleteIfExists(negative());
    assertThat(cf.count()).isEqualTo(1);
  }

  @Test
  void shouldRejectInsertOfNegativeKey() {
    // given
    final var cf = filled();

    // then
    assertThatThrownBy(() -> cf.insert(negative(), dbLong(0)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("non-negative");
  }

  @Test
  void shouldRejectUpdateOfNegativeKey() {
    // given
    final var cf = filled();

    // then
    assertThatThrownBy(() -> cf.update(negative(), dbLong(0)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("non-negative");
  }

  @Test
  void shouldRejectUpsertOfNegativeKey() {
    // given
    final var cf = filled();

    // then
    assertThatThrownBy(() -> cf.upsert(negative(), dbLong(0)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("non-negative");
  }

  private io.camunda.zeebe.db.ColumnFamily<DbForeignKey<DbLong>, DbLong> filled() {
    final var cf = columnFamily(Cf.ONE, new DbForeignKey<>(new DbLong(), Cf.TWO), new DbLong());
    cf.insert(new DbForeignKey<>(dbLong(1), Cf.TWO), dbLong(10));
    return cf;
  }

  private static DbForeignKey<DbLong> negative() {
    return new DbForeignKey<>(dbLong(-1), Cf.TWO);
  }
}
