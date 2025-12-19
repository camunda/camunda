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
   * Mocks both ManagementFactory.getOperatingSystemMXBean() and getMemoryMXBean() to control total
   * memory, heap max, and non-heap max for testing memory allocation strategies. Returns a
   * MockedStatic for use in try-with-resources.
   */
  private static MockedStatic<ManagementFactory> mockMemoryEnvironment(
      final long totalMemorySize, final long heapMax, final long nonHeapMax) {
    final MockedStatic<ManagementFactory> managementFactoryMock =
        Mockito.mockStatic(ManagementFactory.class);
    final var osBean = Mockito.mock(com.sun.management.OperatingSystemMXBean.class);
    final var memoryBean = Mockito.mock(java.lang.management.MemoryMXBean.class);
    final var heapUsage = Mockito.mock(java.lang.management.MemoryUsage.class);
    final var nonHeapUsage = Mockito.mock(java.lang.management.MemoryUsage.class);

    managementFactoryMock.when(ManagementFactory::getOperatingSystemMXBean).thenReturn(osBean);
    managementFactoryMock.when(ManagementFactory::getMemoryMXBean).thenReturn(memoryBean);
    Mockito.when(osBean.getTotalMemorySize()).thenReturn(totalMemorySize);
    Mockito.when(memoryBean.getHeapMemoryUsage()).thenReturn(heapUsage);
    Mockito.when(memoryBean.getNonHeapMemoryUsage()).thenReturn(nonHeapUsage);
    Mockito.when(heapUsage.getMax()).thenReturn(heapMax);
    Mockito.when(nonHeapUsage.getMax()).thenReturn(nonHeapMax);

    return managementFactoryMock;
  }

  /** Overload for cases where only totalMemorySize is relevant. */
  private static MockedStatic<ManagementFactory> mockMemoryEnvironment(final long totalMemorySize) {
    return mockMemoryEnvironment(totalMemorySize, 0, 0);
  }

  @Test
  void shouldNotThrowIfMemoryAllocationBelowOrEqualHalfOfRam() {
    // when
    brokerCfg.getExperimental().getRocksdb().setMaxMemoryFraction(0.5);
    brokerCfg
        .getExperimental()
        .getRocksdb()
        .setMemoryLimit(DataSize.ofBytes(64L * 1024 * 1024)); // 64MB
    final int partitionsCount = 2;
    // then we expect no exception when getting the shared cache since we only allocate 50% of ram
    // memory. 2 * 64MB = 128MB which is 50% of 256MB. The default memory allocation strategy is per
    // partition.
    try (final var managementFactoryMock = mockMemoryEnvironment(256L * 1024 * 1024)) { // 256MB
      assertThatCode(
              () -> {
                RocksDbSharedCache.validateRocksDbMemory(
                    brokerCfg.getExperimental().getRocksdb(), partitionsCount);
              })
          .doesNotThrowAnyException();
    }
  }

  @Test
  void shouldThrowIfTriesToAllocateMoreThanHalfOfRam() {
    // when
    brokerCfg.getExperimental().getRocksdb().setMaxMemoryFraction(0.5);
    brokerCfg
        .getExperimental()
        .getRocksdb()
        .setMemoryLimit(DataSize.ofBytes(200L * 1024 * 1024)); // 200MB
    // then when we allocate more than half of the memory to rocks db, we expect an exception
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
              "Expected the allocated memory for RocksDB to be below or equal 50.00 % of ram memory, but was 78.13 %");
    }
  }

  @Test
  void shouldThrowIfTriesToAllocateMoreThanFractionalOfRam() {
    // when
    brokerCfg.getExperimental().getRocksdb().setMaxMemoryFraction(0.100);
    brokerCfg
        .getExperimental()
        .getRocksdb()
        .setMemoryLimit(DataSize.ofBytes(30L * 1024 * 1024)); // 30MB
    // then when we allocate more than 10% of the memory to rocks db, we expect an exception
    // 10% of 256MB is 25.6MB. 30MB is > 25.6MB.
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
              "Expected the allocated memory for RocksDB to be below or equal 10.00 % of ram memory, but was 11.72 %");
    }
  }

  @Test
  void shouldNotThrowIfTriesToAllocateMoreThanHalfOfRamByDefault() {
    // when
    // maxMemoryPercentage is -1 by default (disabled)
    brokerCfg
        .getExperimental()
        .getRocksdb()
        .setMemoryLimit(DataSize.ofBytes(200L * 1024 * 1024)); // 200MB
    // then when we allocate more than half of the memory to rocks db, we expect NO exception
    try (final var managementFactoryMock = mockMemoryEnvironment(256L * 1024 * 1024)) { // 256MB
      assertThatCode(
              () -> {
                RocksDbSharedCache.validateRocksDbMemory(
                    brokerCfg.getExperimental().getRocksdb(), DEFAULT_PARTITION_COUNT);
              })
          .doesNotThrowAnyException();
    }
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
  void shouldNotFailAllocatingMoreThanHalfOfRamIfAuto() {
    // when
    brokerCfg
        .getExperimental()
        .getRocksdb()
        .setMemoryAllocationStrategy(MemoryAllocationStrategy.AUTO);
    brokerCfg.getExperimental().getRocksdb().setMaxMemoryFraction(0.500);
    final int partitionsCount = 1;

    // we have broker with 1024mb of ram, where:
    // - max heap is 128mb
    // - max non-heap is 128mb
    // so available memory is 1024 - 128 - 128 = 768mb with a
    // ROCKSDB_OVERHEAD_FACTOR of 0.15 we can allocate  652.8mb to rocksdb. This is more than half
    // of the
    // ram, but since we are in AUTO mode it should be allowed.
    try (final var managementFactoryMock =
        mockMemoryEnvironment(1024L * 1024 * 1024, 128L * 1024 * 1024, 128L * 1024 * 1024)) {
      assertThatCode(
              () -> {
                RocksDbSharedCache.validateRocksDbMemory(
                    brokerCfg.getExperimental().getRocksdb(), partitionsCount);
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
        .setMemoryAllocationStrategy(MemoryAllocationStrategy.AUTO);
    final int partitionsCount = 1;

    // we have broker with 512mb of ram, where:
    // - max heap is 128mb
    // - max non-heap is 128mb
    // so available memory is 512 - 128 - 128 = 256mb with a
    // ROCKSDB_OVERHEAD_FACTOR of 0.15 we can allocate 217.6mb to rocksdb
    try (final var managementFactoryMock =
        mockMemoryEnvironment(512L * 1024 * 1024, 128L * 1024 * 1024, 128L * 1024 * 1024)) {
      assertThatCode(
              () -> {
                RocksDbSharedCache.validateRocksDbMemory(
                    brokerCfg.getExperimental().getRocksdb(), partitionsCount);
              })
          .doesNotThrowAnyException();
    }

    final int morePartitionsCount = 7;
    // should not be valid with 7 partitions since 217.6 / 7 = 31.08mb < 32mb
    try (final var managementFactoryMock =
        mockMemoryEnvironment(512L * 1024 * 1024, 128L * 1024 * 1024, 128L * 1024 * 1024)) {
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
              MINIMUM_PARTITION_MEMORY_LIMIT, 32_595_733); // 217.6mb / 7 partitions
    }
  }

  @Test
  void shouldReturnExpectedAvailableMemoryCapacityWhenNonHeapNotSet() {
    // 512MB total, 128MB heap, -1 non-heap triggers fallback
    try (final var managementFactoryMock =
        mockMemoryEnvironment(512L * 1024 * 1024, 128L * 1024 * 1024, -1)) {
      final long available = RocksDbSharedCache.getAvailableMemoryCapacity();
      // 25% of 512MB = 128MB fallback for non-heap
      final long expectedNonHeap = 128L * 1024 * 1024;
      final long expectedAvailable =
          (long) (((512L * 1024 * 1024) - (128L * 1024 * 1024) - expectedNonHeap) * (1.0 - 0.15));
      // Assert
      assertThat(available).isEqualTo(expectedAvailable);
    }
  }
}
