/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import static io.camunda.zeebe.broker.partitioning.PartitionManagerImpl.MINIMUM_PARTITION_MEMORY_LIMIT;
import static org.assertj.core.api.Assertions.*;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ClusterCfg;
import io.camunda.zeebe.broker.system.configuration.ExperimentalCfg;
import io.camunda.zeebe.broker.system.configuration.RocksdbCfg;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration.MemoryAllocationStrategy;
import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.util.unit.DataSize;

class PartitionManagerImplTest {
  private BrokerCfg brokerCfg;
  private ClusterCfg clusterCfg;
  private DataSize dataSize;
  private RocksdbCfg rocksdbCfg;

  @BeforeEach
  void setUp() {
    brokerCfg = Mockito.mock(BrokerCfg.class);
    clusterCfg = Mockito.mock(ClusterCfg.class);
    dataSize = Mockito.mock(DataSize.class);
    rocksdbCfg = Mockito.mock(RocksdbCfg.class);
    Mockito.when(brokerCfg.getCluster()).thenReturn(clusterCfg);
    Mockito.when(clusterCfg.getPartitionsCount()).thenReturn(1);
    final var experimentalCfg = Mockito.mock(ExperimentalCfg.class);
    Mockito.when(brokerCfg.getExperimental()).thenReturn(experimentalCfg);
    Mockito.when(experimentalCfg.getRocksdb()).thenReturn(rocksdbCfg);
    Mockito.when(rocksdbCfg.getMemoryAllocationStrategy())
        .thenReturn(MemoryAllocationStrategy.PARTITION);
    Mockito.when(rocksdbCfg.getMemoryLimit()).thenReturn(dataSize);
  }

  private static MockedStatic<ManagementFactory> mockTotalMemorySize(final long memorySize) {
    final MockedStatic<ManagementFactory> managementFactoryMock =
        Mockito.mockStatic(ManagementFactory.class);
    final var osBean = Mockito.mock(com.sun.management.OperatingSystemMXBean.class);
    managementFactoryMock.when(ManagementFactory::getOperatingSystemMXBean).thenReturn(osBean);
    Mockito.when(osBean.getTotalMemorySize()).thenReturn(memorySize);
    return managementFactoryMock;
  }

  @Test
  void shouldNotThrowIfMemoryAllocationBelowOrEqualHalfOfRam() {
    // when
    Mockito.when(clusterCfg.getPartitionsCount()).thenReturn(2);
    Mockito.when(dataSize.toBytes()).thenReturn(64L * 1024 * 1024); // 64MB

    // then we expect no exception when getting the shared cache since we only allocate 50% of ram
    // memory. 2 * 64MB = 128MB which is 50% of 256MB. The default memory allocation strategy is per
    // partition.
    try (final var managementFactoryMock = mockTotalMemorySize(256L * 1024 * 1024)) { // 256MB
      assertThatCode(() -> PartitionManagerImpl.getSharedCache(brokerCfg))
          .doesNotThrowAnyException();
    }
  }

  @Test
  void shouldThrowIfTriesToAllocateMoreThanHalfOfRam() {
    // when
    Mockito.when(dataSize.toBytes()).thenReturn(200L * 1024 * 1024); // 200MB

    // then when we allocate more than half of the memory to rocks db, we expect an exception
    try (final var managementFactoryMock = mockTotalMemorySize(256L * 1024 * 1024)) { // 256MB
      assertThatThrownBy(() -> PartitionManagerImpl.getSharedCache(brokerCfg))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(
              "Expected the allocated memory for RocksDB to be below or equal half of ram memory, but was 78.13 %");
    }
  }

  @Test
  void shouldThrowIfMemoryPerPartitionTooSmall() {
    // when
    // we only give half of the minimum required memory per partition
    Mockito.when(dataSize.toBytes()).thenReturn(MINIMUM_PARTITION_MEMORY_LIMIT / 2); // 16MB

    // then it should throw since the memory per partition is too small
    try (final var managementFactoryMock = mockTotalMemorySize(256L * 1024 * 1024)) { // 256MB
      assertThatThrownBy(() -> PartitionManagerImpl.getSharedCache(brokerCfg))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(
              "Expected the allocated memory for RocksDB per partition to be at least %s bytes, but was %s bytes.",
              MINIMUM_PARTITION_MEMORY_LIMIT, MINIMUM_PARTITION_MEMORY_LIMIT / 2);
    }
  }

  @Test
  void shouldAllocateMemoryPerBroker() {
    // when
    Mockito.when(rocksdbCfg.getMemoryAllocationStrategy())
        .thenReturn(MemoryAllocationStrategy.BROKER);
    Mockito.when(clusterCfg.getPartitionsCount()).thenReturn(2);
    Mockito.when(dataSize.toBytes()).thenReturn(128L * 1024 * 1024); // 128MB

    // should still be valid even with 2 partitions since we allocate per broker, we will only
    // allocate 128mb
    try (final var managementFactoryMock = mockTotalMemorySize(256L * 1024 * 1024)) { // 256MB
      assertThatCode(() -> PartitionManagerImpl.getSharedCache(brokerCfg))
          .doesNotThrowAnyException();
    }
  }
}
