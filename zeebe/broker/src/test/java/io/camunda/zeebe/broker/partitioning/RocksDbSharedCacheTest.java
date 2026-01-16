/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import static io.camunda.zeebe.broker.partitioning.RocksDbSharedCache.MINIMUM_PARTITION_MEMORY_LIMIT;
import static org.assertj.core.api.Assertions.*;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration.MemoryAllocationStrategy;
import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.util.unit.DataSize;

class RocksDbSharedCacheTest {
  private static final int DEFAULT_PARTITION_COUNT = 1;
  private BrokerCfg brokerCfg;

  @BeforeEach
  void setUp() {
    brokerCfg = new BrokerCfg();
    brokerCfg.getExperimental().getRocksdb().setMemoryLimit(DataSize.ofBytes(512 * 1024 * 1024L));
    // it's already the default, but to be explicit
    brokerCfg
        .getExperimental()
        .getRocksdb()
        .setMemoryAllocationStrategy(MemoryAllocationStrategy.PARTITION);
  }

  /**
   * Mocks both ManagementFactory.getOperatingSystemMXBean() to control total memory. Returns a
   * MockedStatic for use in try-with-resources.
   */
  private static MockedStatic<ManagementFactory> mockMemoryEnvironment(final long totalMemorySize) {
    final MockedStatic<ManagementFactory> managementFactoryMock =
        Mockito.mockStatic(ManagementFactory.class);
    final var osBean = Mockito.mock(com.sun.management.OperatingSystemMXBean.class);
    managementFactoryMock.when(ManagementFactory::getOperatingSystemMXBean).thenReturn(osBean);
    Mockito.when(osBean.getTotalMemorySize()).thenReturn(totalMemorySize);

    return managementFactoryMock;
  }

  @Test
  void shouldThrowIfMemoryPerPartitionTooSmall() {
    // when
    // we only give half of the minimum required memory per partition
    brokerCfg
        .getExperimental()
        .getRocksdb()
        .setMemoryLimit(DataSize.ofBytes(MINIMUM_PARTITION_MEMORY_LIMIT / 2)); // 16MB

    // then it should throw since the memory per partition is too small
    try (final var managementFactoryMock = mockMemoryEnvironment(256L * 1024 * 1024)) { // 256MB
      final var throwable =
          catchThrowable(
              () -> {
                RocksDbSharedCache.validateRocksDbMemory(
                    brokerCfg.getExperimental().getRocksdb(), DEFAULT_PARTITION_COUNT);
              });

      assertThat(throwable)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(
              "Expected the allocated memory for RocksDB per partition to be at least %s bytes, but was %s bytes.",
              MINIMUM_PARTITION_MEMORY_LIMIT, MINIMUM_PARTITION_MEMORY_LIMIT / 2);
    }
  }

  @Test
  void shouldAllocateMemoryPerBroker() {
    // when
    brokerCfg
        .getExperimental()
        .getRocksdb()
        .setMemoryAllocationStrategy(MemoryAllocationStrategy.BROKER);
    final int partitionsCount = 2;
    brokerCfg
        .getExperimental()
        .getRocksdb()
        .setMemoryLimit(DataSize.ofBytes(128L * 1024 * 1024)); // 128MB

    // should still be valid even with 2 partitions since we allocate per broker, we will only
    // allocate 128mb
    try (final var managementFactoryMock = mockMemoryEnvironment(256L * 1024 * 1024)) { // 256MB
      assertThatCode(
              () -> {
                RocksDbSharedCache.validateRocksDbMemory(
                    brokerCfg.getExperimental().getRocksdb(), partitionsCount);
              })
          .doesNotThrowAnyException();
    }

    final int morePartitionsCount = 4;
    // should still be valid even with 4 partitions since we allocate per
    // broker, we will only allocate 128mb
    try (final var managementFactoryMock = mockMemoryEnvironment(256L * 1024 * 1024)) { // 256MB
      assertThatCode(
              () -> {
                RocksDbSharedCache.validateRocksDbMemory(
                    brokerCfg.getExperimental().getRocksdb(), morePartitionsCount);
              })
          .doesNotThrowAnyException();
    }
  }

  @Test
  void shouldAllocateMemoryAutomatically() {
    // when
    brokerCfg
        .getExperimental()
        .getRocksdb()
        .setMemoryAllocationStrategy(MemoryAllocationStrategy.FRACTION);
    brokerCfg.getExperimental().getRocksdb().setMemoryFraction(0.15);
    final int partitionsCount = 1;

    // we have broker with 512mb of ram, where:
    // we allocate 15% of total memory to rocksdb = 76.8mb
    try (final var managementFactoryMock = mockMemoryEnvironment(512L * 1024 * 1024)) {
      assertThatCode(
              () -> {
                RocksDbSharedCache.validateRocksDbMemory(
                    brokerCfg.getExperimental().getRocksdb(), partitionsCount);
              })
          .doesNotThrowAnyException();
    }

    final int morePartitionsCount = 3;
    // should not be valid with 3 partitions since 76.8 / 3 = 25.6mb < 32mb
    try (final var managementFactoryMock = mockMemoryEnvironment(512L * 1024 * 1024)) {
      final var throwable =
          catchThrowable(
              () -> {
                RocksDbSharedCache.validateRocksDbMemory(
                    brokerCfg.getExperimental().getRocksdb(), morePartitionsCount);
              });

      assertThat(throwable)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(
              "Expected the allocated memory for RocksDB per partition to be at least %s bytes, but was %s bytes.",
              MINIMUM_PARTITION_MEMORY_LIMIT, 26_843_545); // 76.8mb / 3 partitions
    }
  }

  @Test
  void shouldReturnFixedMemoryPercentage() {
    // 512MB total
    try (final var managementFactoryMock = mockMemoryEnvironment(512L * 1024 * 1024)) {
      final double fraction = 0.15;
      final long available = RocksDbSharedCache.getFixedMemoryPercentage(fraction);
      // 15% of 512MB
      final long expectedAvailable = Math.round(512L * 1024 * 1024 * fraction);
      // Assert
      assertThat(available).isEqualTo(expectedAvailable);
    }
  }

  @Test
  void shouldThrowIfMemoryFractionIsInvalid() {
    // when
    brokerCfg
        .getExperimental()
        .getRocksdb()
        .setMemoryAllocationStrategy(MemoryAllocationStrategy.FRACTION);

    // when fraction > 1
    brokerCfg.getExperimental().getRocksdb().setMemoryFraction(1.1);

    // then
    var throwable =
        catchThrowable(
            () -> {
              RocksDbSharedCache.validateRocksDbMemory(
                  brokerCfg.getExperimental().getRocksdb(), DEFAULT_PARTITION_COUNT);
            });

    assertThat(throwable)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Expected the memoryFraction for RocksDB FRACTION memory allocation strategy to be between 0 and 1, but was %s.",
            1.1);

    // when fraction <= 0
    brokerCfg.getExperimental().getRocksdb().setMemoryFraction(0.0);

    // then
    throwable =
        catchThrowable(
            () -> {
              RocksDbSharedCache.validateRocksDbMemory(
                  brokerCfg.getExperimental().getRocksdb(), DEFAULT_PARTITION_COUNT);
            });

    assertThat(throwable)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Expected the memoryFraction for RocksDB FRACTION memory allocation strategy to be between 0 and 1, but was %s.",
            0.0);
  }
}
