/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import static io.camunda.zeebe.broker.system.configuration.RocksdbCfg.MINIMUM_PARTITION_MEMORY_LIMIT;
import static org.assertj.core.api.Assertions.*;

import io.camunda.zeebe.broker.system.configuration.RocksdbCfg;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration.MemoryAllocationStrategy;
import java.lang.management.ManagementFactory;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.util.unit.DataSize;

class RocksDbSharedCacheTest {
  private static final int DEFAULT_PARTITION_COUNT = 1;
  private RocksdbCfg rocksdbCfg;

  @BeforeEach
  void setUp() {
    rocksdbCfg = new RocksdbCfg();
    rocksdbCfg.setMemoryLimit(DataSize.ofBytes(512 * 1024 * 1024L));
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

  static Stream<Arguments> provideMemoryAllocationScenarios() {
    return Stream.of(
        // Expected to pass:   128MB <= 50% of 256MB
        Arguments.of(DataSize.ofBytes(127L * 1024 * 1024), 0.5, false),
        // Expected to fail:  129MB > 50% of 256MB
        Arguments.of(DataSize.ofBytes(129L * 1024 * 1024), 0.5, true),
        // Expected to pass: 129MB < 60% of 256MB
        Arguments.of(DataSize.ofBytes(129L * 1024 * 1024), 0.6, false));
  }

  static Stream<Arguments> provideMaxMemoryFractionScenarios() {
    // should only accept values between 0 and 1 (exclusive of 0) or -1 (disabled)
    return Stream.of(
        Arguments.of(-1.0, false), // Disabled (default value is -1), valid
        Arguments.of(0.5, false), // Valid
        Arguments.of(1.0, false), // Valid
        Arguments.of(1.1, true), // Invalid > 1
        Arguments.of(-0.1, true), // Invalid < 0
        Arguments.of(0.0, true)); // Invalid == 0
  }

  @ParameterizedTest
  @MethodSource("provideMaxMemoryFractionScenarios")
  void shouldValidateMaxMemoryFractionConfiguration(
      final double maxMemoryFraction, final boolean shouldThrow) {
    // when
    rocksdbCfg.setMemoryAllocationStrategy(MemoryAllocationStrategy.PARTITION);
    rocksdbCfg.setMaxMemoryFraction(maxMemoryFraction);
    rocksdbCfg.setMemoryLimit(DataSize.ofBytes(128L * 1024 * 1024L));
    // then
    try (final var ignored = mockMemoryEnvironment(256L * 1024 * 1024)) { // 256MB
      if (shouldThrow) {
        final var throwable =
            catchThrowable(() -> rocksdbCfg.validateRocksDbMemory(DEFAULT_PARTITION_COUNT));

        assertThat(throwable)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(
                "Expected the maxMemoryFraction for RocksDB to be between 0 and 1 (exclusive of 0) or -1 (disabled), but was %s.",
                maxMemoryFraction);
      } else {
        assertThatCode(() -> rocksdbCfg.validateRocksDbMemory(DEFAULT_PARTITION_COUNT))
            .doesNotThrowAnyException();
      }
    }
  }

  @ParameterizedTest
  @MethodSource("provideMemoryAllocationScenarios")
  void shouldValidateMemoryAllocationAgainstMaxFraction(
      final DataSize memoryLimit, final double fraction, final boolean shouldThrow) {
    // when
    rocksdbCfg.setMemoryAllocationStrategy(MemoryAllocationStrategy.PARTITION);
    rocksdbCfg.setMaxMemoryFraction(fraction);
    rocksdbCfg.setMemoryLimit(memoryLimit);

    // then
    try (final var ignored = mockMemoryEnvironment(256L * 1024 * 1024)) { // 256MB
      if (shouldThrow) {
        final var throwable =
            catchThrowable(() -> rocksdbCfg.validateRocksDbMemory(DEFAULT_PARTITION_COUNT));

        assertThat(throwable)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(
                "Expected the allocated memory for RocksDB to be below or equal 50.00 % of ram memory");
      } else {
        assertThatCode(() -> rocksdbCfg.validateRocksDbMemory(DEFAULT_PARTITION_COUNT))
            .doesNotThrowAnyException();
      }
    }
  }

  @Test
  void shouldThrowIfTriesToAllocateMoreThanFractionalOfRam() {
    // when
    rocksdbCfg.setMemoryAllocationStrategy(MemoryAllocationStrategy.PARTITION);
    rocksdbCfg.setMaxMemoryFraction(0.100);
    rocksdbCfg.setMemoryLimit(DataSize.ofBytes(30L * 1024 * 1024)); // 30MB
    // then when we allocate more than 10% of the memory to rocks db, we expect an exception
    // 10% of 256MB is 25.6MB. 30MB is > 25.6MB.
    try (final var managementFactoryMock = mockMemoryEnvironment(256L * 1024 * 1024)) { // 256MB
      final var throwable =
          catchThrowable(() -> rocksdbCfg.validateRocksDbMemory(DEFAULT_PARTITION_COUNT));

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
    rocksdbCfg.setMemoryAllocationStrategy(MemoryAllocationStrategy.PARTITION);
    rocksdbCfg.setMemoryLimit(DataSize.ofBytes(200L * 1024 * 1024)); // 200MB
    // then when we allocate more than half of the memory to rocks db, we expect NO exception
    try (final var managementFactoryMock = mockMemoryEnvironment(256L * 1024 * 1024)) { // 256MB
      assertThatCode(() -> rocksdbCfg.validateRocksDbMemory(DEFAULT_PARTITION_COUNT))
          .doesNotThrowAnyException();
    }
  }

  @Test
  void shouldThrowIfMemoryPerPartitionTooSmall() {
    // when
    // we only give half of the minimum required memory per partition
    rocksdbCfg.setMemoryAllocationStrategy(MemoryAllocationStrategy.PARTITION);
    rocksdbCfg.setMemoryLimit(DataSize.ofBytes(MINIMUM_PARTITION_MEMORY_LIMIT / 2)); // 16MB

    // then it should throw since the memory per partition is too small
    try (final var managementFactoryMock = mockMemoryEnvironment(256L * 1024 * 1024)) { // 256MB
      final var throwable =
          catchThrowable(() -> rocksdbCfg.validateRocksDbMemory(DEFAULT_PARTITION_COUNT));

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
    rocksdbCfg.setMemoryAllocationStrategy(MemoryAllocationStrategy.BROKER);
    final int partitionsCount = 2;
    rocksdbCfg.setMemoryLimit(DataSize.ofBytes(128L * 1024 * 1024)); // 128MB

    // should still be valid even with 2 partitions since we allocate per broker, we will only
    // allocate 128mb
    try (final var managementFactoryMock = mockMemoryEnvironment(256L * 1024 * 1024)) { // 256MB
      assertThatCode(() -> rocksdbCfg.validateRocksDbMemory(partitionsCount))
          .doesNotThrowAnyException();
    }

    final int morePartitionsCount = 4;
    // should still be valid even with 4 partitions since we allocate per
    // broker, we will only allocate 128mb
    try (final var managementFactoryMock = mockMemoryEnvironment(256L * 1024 * 1024)) { // 256MB
      assertThatCode(() -> rocksdbCfg.validateRocksDbMemory(morePartitionsCount))
          .doesNotThrowAnyException();
    }
  }

  @Test
  void shouldNotFailAllocatingMoreThanHalfOfRamIfFraction() {
    // when
    rocksdbCfg.setMemoryAllocationStrategy(MemoryAllocationStrategy.FRACTION);
    rocksdbCfg.setMemoryFraction(0.600);
    rocksdbCfg.setMaxMemoryFraction(0.500);
    final int partitionsCount = 1;

    // we try to allocate 60% of ram to rocksdb, and the max is 50%, but
    // since we are in FRACTION mode it should be allowed.
    try (final var managementFactoryMock = mockMemoryEnvironment(1024L * 1024 * 1024)) {
      assertThatCode(() -> rocksdbCfg.validateRocksDbMemory(partitionsCount))
          .doesNotThrowAnyException();
    }
  }

  @Test
  void shouldAllocateMemoryAutomatically() {
    // when
    rocksdbCfg.setMemoryAllocationStrategy(MemoryAllocationStrategy.FRACTION);
    rocksdbCfg.setMemoryFraction(0.15);
    final int partitionsCount = 1;

    // we have broker with 512mb of ram, where:
    // we allocate 15% of total memory to rocksdb = 76.8mb
    try (final var managementFactoryMock = mockMemoryEnvironment(512L * 1024 * 1024)) {
      assertThatCode(() -> rocksdbCfg.validateRocksDbMemory(partitionsCount))
          .doesNotThrowAnyException();
    }

    final int morePartitionsCount = 3;
    // should not be valid with 3 partitions since 76.8 / 3 = 25.6mb < 32mb
    try (final var managementFactoryMock = mockMemoryEnvironment(512L * 1024 * 1024)) {
      final var throwable =
          catchThrowable(() -> rocksdbCfg.validateRocksDbMemory(morePartitionsCount));

      assertThat(throwable)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(
              "Expected the allocated memory for RocksDB per partition to be at least %s bytes, but was %s bytes.",
              MINIMUM_PARTITION_MEMORY_LIMIT, 26_843_545); // 76.8mb / 3 partitions
    }
  }

  @Test
  void shouldThrowIfMemoryFractionIsInvalid() {
    // when
    rocksdbCfg.setMemoryAllocationStrategy(MemoryAllocationStrategy.FRACTION);

    // when fraction > 1
    rocksdbCfg.setMemoryFraction(1.1);

    // then
    var throwable = catchThrowable(() -> rocksdbCfg.validateRocksDbMemory(DEFAULT_PARTITION_COUNT));

    assertThat(throwable)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Expected the memoryFraction for RocksDB FRACTION memory allocation strategy to be between 0 and 1, but was %s.",
            1.1);

    // when fraction <= 0
    rocksdbCfg.setMemoryFraction(0.0);

    // then
    throwable = catchThrowable(() -> rocksdbCfg.validateRocksDbMemory(DEFAULT_PARTITION_COUNT));

    assertThat(throwable)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Expected the memoryFraction for RocksDB FRACTION memory allocation strategy to be between 0 and 1, but was %s.",
            0.0);
  }

  static Stream<Arguments> provideExceedsTotalSystemMemoryScenarios() {
    return Stream.of(
        // 512MB limit with PARTITION strategy, 1 partition, 256MB total system memory
        Arguments.of(
            512L * 1024 * 1024,
            MemoryAllocationStrategy.PARTITION,
            1,
            256L * 1024 * 1024,
            new String[] {"512 MB", "256 MB", "PARTITION"}),
        // 512MB limit with BROKER strategy, 1 partition, 256MB total system memory
        Arguments.of(
            512L * 1024 * 1024,
            MemoryAllocationStrategy.BROKER,
            1,
            256L * 1024 * 1024,
            new String[] {"BROKER"}),
        // 128MB per partition with 4 partitions = 512MB, 256MB total system memory
        Arguments.of(
            128L * 1024 * 1024,
            MemoryAllocationStrategy.PARTITION,
            4,
            256L * 1024 * 1024,
            new String[] {"512 MB", "256 MB", "Partitions count: 4"}));
  }

  @ParameterizedTest
  @MethodSource("provideExceedsTotalSystemMemoryScenarios")
  void shouldThrowIfRequestedMemoryExceedsTotalSystemMemory(
      final long memoryLimitBytes,
      final MemoryAllocationStrategy strategy,
      final int partitionsCount,
      final long totalSystemMemory,
      final String[] expectedMessageParts) {
    // given
    rocksdbCfg.setMemoryLimit(DataSize.ofBytes(memoryLimitBytes));
    rocksdbCfg.setMemoryAllocationStrategy(strategy);

    // when/then
    try (final var ignored = mockMemoryEnvironment(totalSystemMemory)) {
      final var throwable = catchThrowable(() -> rocksdbCfg.validateRocksDbMemory(partitionsCount));

      var assertion =
          assertThat(throwable)
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("Requested RocksDB memory")
              .hasMessageContaining("exceeds total system memory")
              .hasMessageContaining(
                  "Consider reducing the value of CAMUNDA_DATA_PRIMARYSTORAGE_ROCKSDB_MEMORYLIMIT");

      for (final String part : expectedMessageParts) {
        assertion = assertion.hasMessageContaining(part);
      }
    }
  }
}
