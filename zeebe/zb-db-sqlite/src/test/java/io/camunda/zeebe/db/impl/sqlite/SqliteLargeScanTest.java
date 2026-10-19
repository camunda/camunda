/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.conformance.ConformanceDbTestBase;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DbTenantAwareKey;
import io.camunda.zeebe.db.impl.DbTenantAwareKey.PlacementType;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

final class SqliteLargeScanTest extends ConformanceDbTestBase {

  private static final int COUNT = 600;

  @Override
  protected <T extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      ZeebeDbFactory<T> factory() {
    return new SqliteDbFactory<>();
  }

  @Test
  void shouldVisitAllRowsWhenValuesFillBatchBeforeItsRowLimit() {
    // given values so large that a batch ends because of its size, not its number of rows
    final var cfLarge = columnFamily(Cf.TWO, new DbLong(), new DbString());
    final var largeValue = dbString("x".repeat(600_000));
    for (long i = 0; i < 12; i++) {
      cfLarge.insert(dbLong(i), largeValue);
    }

    // when
    final var visited = new ArrayList<Long>();
    cfLarge.whileTrue(
        (k, v) -> {
          visited.add(k.getValue());
          return true;
        });

    // then
    assertThat(visited).containsExactly(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);
  }

  @Test
  void shouldVisitAllRowsOfTenantAwareKeyWithValuesAcrossBatches() {
    // given
    final var tenant = new DbString();
    final var wrapped = new DbLong();
    final var key = new DbTenantAwareKey<>(tenant, wrapped, PlacementType.PREFIX);
    final var cf = columnFamily(Cf.ONE, key, new DbString());
    final var largeValue = dbString("x".repeat(2_000));
    tenant.wrapString("<default>");
    for (long i = 0; i < COUNT; i++) {
      wrapped.wrapLong(2251799813685249L + i);
      cf.insert(key, largeValue);
    }

    // when
    final var visited = new ArrayList<Long>();
    cf.whileTrue(
        (k, v) -> {
          visited.add(k.wrappedKey().getValue());
          return true;
        });

    // then
    assertThat(visited).hasSize(COUNT).doesNotHaveDuplicates().isSorted();
  }
}
