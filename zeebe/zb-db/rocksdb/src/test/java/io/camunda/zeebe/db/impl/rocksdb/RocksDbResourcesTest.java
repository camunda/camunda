/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration.MemoryAllocationStrategy;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbResources.RuntimeInfo;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbResources.Shared;
import java.util.ArrayList;
import java.util.List;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class RocksDbResourcesTest {
  private static final long MIB = 1024 * 1024L;
  private static final RuntimeInfo RUNTIME = new RuntimeInfo(1024 * MIB, 3);

  private final List<RocksDbResources> resources = new ArrayList<>();

  @AfterEach
  void tearDown() {
    for (final var resource : resources) {
      if (resource instanceof final Shared shared) {
        CloseHelper.closeAll(shared.getSharedWriteBufferManager(), shared.getSharedCache());
      }
    }
  }

  @Test
  void shouldUseFractionWithoutMinimum() {
    // given
    final var config =
        new RocksDbConfiguration()
            .setMemoryAllocationStrategy(MemoryAllocationStrategy.FRACTION)
            .setMemoryFraction(0.25);

    // when
    final var actual = of(config, RUNTIME);

    // then
    assertThat(actual.writeBufferBudgetPerPartition())
        .isEqualTo(shared(256 * MIB).writeBufferBudgetPerPartition());
  }

  @Test
  void shouldUseFractionWhenAboveMinimum() {
    // given
    final var config =
        new RocksDbConfiguration()
            .setMemoryAllocationStrategy(MemoryAllocationStrategy.FRACTION)
            .setMemoryFraction(0.25)
            .setMemoryMinimum(128 * MIB);

    // when
    final var actual = of(config, RUNTIME);

    // then
    assertThat(actual.writeBufferBudgetPerPartition())
        .isEqualTo(shared(256 * MIB).writeBufferBudgetPerPartition());
  }

  @Test
  void shouldUseMinimumWhenFractionIsBelowIt() {
    // given
    final var config =
        new RocksDbConfiguration()
            .setMemoryAllocationStrategy(MemoryAllocationStrategy.FRACTION)
            .setMemoryFraction(0.25)
            .setMemoryMinimum(384 * MIB);

    // when
    final var actual = of(config, RUNTIME);

    // then
    assertThat(actual.writeBufferBudgetPerPartition())
        .isEqualTo(shared(384 * MIB).writeBufferBudgetPerPartition());
  }

  @Test
  void shouldIgnoreMinimumForBrokerStrategy() {
    // given
    final var config =
        new RocksDbConfiguration()
            .setMemoryAllocationStrategy(MemoryAllocationStrategy.BROKER)
            .setMemoryLimit(96 * MIB)
            .setMemoryMinimum(384 * MIB);

    // when
    final var actual = of(config, RUNTIME);

    // then
    assertThat(actual.writeBufferBudgetPerPartition())
        .isEqualTo(shared(96 * MIB).writeBufferBudgetPerPartition());
  }

  @Test
  void shouldIgnoreMinimumForPartitionStrategy() {
    // given
    final var config =
        new RocksDbConfiguration()
            .setMemoryAllocationStrategy(MemoryAllocationStrategy.PARTITION)
            .setMemoryLimit(96 * MIB)
            .setMemoryMinimum(384 * MIB);

    // when
    final var actual = of(config, RUNTIME);

    // then
    assertThat(actual.writeBufferBudgetPerPartition())
        .isEqualTo(new RocksDbResources.PerPartition(96 * MIB).writeBufferBudgetPerPartition());
  }

  private RocksDbResources of(final RocksDbConfiguration config, final RuntimeInfo runtime) {
    final var created = RocksDbResources.of(config, runtime);
    resources.add(created);
    return created;
  }

  private Shared shared(final long memoryLimit) {
    final var created = new Shared(memoryLimit, RUNTIME.partitionCount());
    resources.add(created);
    return created;
  }
}
