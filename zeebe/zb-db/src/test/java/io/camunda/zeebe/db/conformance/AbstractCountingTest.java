/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pins {@code count}, {@code countEqualPrefix} and {@code isEmpty}. */
public abstract class AbstractCountingTest extends ConformanceDbTestBase {

  private ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbLong> cf;

  @BeforeEach
  void createColumnFamily() {
    cf = columnFamily(Cf.ONE, new DbCompositeKey<>(new DbLong(), new DbLong()), new DbLong());
  }

  @Test
  void shouldReportEmptyColumnFamily() {
    // then
    assertThat(cf.isEmpty()).isTrue();
    assertThat(cf.count()).isZero();
    assertThat(cf.countEqualPrefix(dbLong(1))).isZero();
    assertThat(db.isEmpty(Cf.ONE, context)).isTrue();
  }

  @Test
  void shouldReportNonEmptyColumnFamily() {
    // given
    cf.insert(longPair(1, 1), dbLong(0));

    // then
    assertThat(cf.isEmpty()).isFalse();
    assertThat(cf.count()).isEqualTo(1);
    assertThat(db.isEmpty(Cf.ONE, context)).isFalse();
  }

  @Test
  void shouldCountAllEntries() {
    // given
    cf.insert(longPair(1, 1), dbLong(0));
    cf.insert(longPair(1, 2), dbLong(0));
    cf.insert(longPair(2, 1), dbLong(0));
    cf.insert(longPair(-1, 1), dbLong(0));

    // then
    assertThat(cf.count()).isEqualTo(4);
  }

  @Test
  void shouldCountEntriesOfPrefixOnly() {
    // given
    cf.insert(longPair(1, 1), dbLong(0));
    cf.insert(longPair(1, 2), dbLong(0));
    cf.insert(longPair(2, 1), dbLong(0));
    cf.insert(longPair(-1, 1), dbLong(0));

    // then
    assertThat(cf.countEqualPrefix(dbLong(1))).isEqualTo(2);
    assertThat(cf.countEqualPrefix(dbLong(2))).isEqualTo(1);
    assertThat(cf.countEqualPrefix(dbLong(-1))).isEqualTo(1);
    assertThat(cf.countEqualPrefix(dbLong(3))).isZero();
  }

  @Test
  void shouldUpdateCountsAfterDeletes() {
    // given
    cf.insert(longPair(1, 1), dbLong(0));
    cf.insert(longPair(1, 2), dbLong(0));

    // when
    cf.deleteExisting(longPair(1, 1));

    // then
    assertThat(cf.count()).isEqualTo(1);
    assertThat(cf.countEqualPrefix(dbLong(1))).isEqualTo(1);

    // when
    cf.deleteExisting(longPair(1, 2));

    // then
    assertThat(cf.isEmpty()).isTrue();
    assertThat(cf.count()).isZero();
  }

  @Test
  void shouldCountPerColumnFamily() {
    // given
    cf.insert(longPair(1, 1), dbLong(0));
    final var other = longColumnFamily(Cf.TWO);
    other.insert(dbLong(1), dbLong(0));
    other.insert(dbLong(2), dbLong(0));

    // then
    assertThat(cf.count()).isEqualTo(1);
    assertThat(other.count()).isEqualTo(2);
    assertThat(db.isEmpty(Cf.THREE, context)).isTrue();
  }
}
