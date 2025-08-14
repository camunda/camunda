/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import java.util.Properties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.util.unit.DataSize;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  BrokerBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("broker")
public class RocksDbPropertiesTest {

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.primary-storage.rocks-db.statistics-enabled=true",
        "camunda.data.primary-storage.rocks-db.access-metrics=fine",
        "camunda.data.primary-storage.rocks-db.memory-limit=1GB",
        "camunda.data.primary-storage.rocks-db.max-open-files=1000",
        "camunda.data.primary-storage.rocks-db.max-write-buffer-number=8",
        "camunda.data.primary-storage.rocks-db.min-write-buffer-number-to-merge=4",
        "camunda.data.primary-storage.rocks-db.io-rate-bytes-per-second=10485760",
        "camunda.data.primary-storage.rocks-db.wal-disabled=false",
        "camunda.data.primary-storage.rocks-db.sst-partitioning-enabled=false",
        "camunda.data.primary-storage.rocks-db.column-family-options.max_write_buffer_number=12",
        "camunda.data.primary-storage.rocks-db.column-family-options.write_buffer_size=67108864",
        "camunda.data.primary-storage.rocks-db.column-family-options.compaction_pri=kOldestSmallestSeqFirst"
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetEnableStatistics() {
      assertThat(brokerCfg.getExperimental().getRocksdb().isEnableStatistics()).isTrue();
    }

    @Test
    void shouldSetMemoryLimit() {
      assertThat(brokerCfg.getExperimental().getRocksdb().getMemoryLimit())
          .isEqualTo(DataSize.ofGigabytes(1));
    }

    @Test
    void shouldSetMaxOpenFiles() {
      assertThat(brokerCfg.getExperimental().getRocksdb().getMaxOpenFiles()).isEqualTo(1000);
    }

    @Test
    void shouldSetMaxWriteBufferNumber() {
      assertThat(brokerCfg.getExperimental().getRocksdb().getMaxWriteBufferNumber()).isEqualTo(8);
    }

    @Test
    void shouldSetMinWriteBufferNumberToMerge() {
      assertThat(brokerCfg.getExperimental().getRocksdb().getMinWriteBufferNumberToMerge())
          .isEqualTo(4);
    }

    @Test
    void shouldSetIoRateBytesPerSecond() {
      assertThat(brokerCfg.getExperimental().getRocksdb().getIoRateBytesPerSecond())
          .isEqualTo(10485760);
    }

    @Test
    void shouldSetDisableWal() {
      assertThat(brokerCfg.getExperimental().getRocksdb().isDisableWal()).isFalse();
    }

    @Test
    void shouldSetEnableSstPartitioning() {
      assertThat(brokerCfg.getExperimental().getRocksdb().isEnableSstPartitioning()).isFalse();
    }

    @Test
    void shouldSetAccessMetrics() {
      assertThat(brokerCfg.getExperimental().getRocksdb().getAccessMetrics()).isEqualTo(Kind.FINE);
    }

    @Test
    void shouldInitializeColumnFamilyOptions() {
      final Properties columnFamilyOptions =
          brokerCfg.getExperimental().getRocksdb().getColumnFamilyOptions();
      assertThat(columnFamilyOptions).isNotNull();
      assertThat(columnFamilyOptions).hasSize(3);
      assertThat(columnFamilyOptions.getProperty("max_write_buffer_number")).isEqualTo("12");
      assertThat(columnFamilyOptions.getProperty("write_buffer_size")).isEqualTo("67108864");
      assertThat(columnFamilyOptions.getProperty("compaction_pri"))
          .isEqualTo("kOldestSmallestSeqFirst");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.experimental.rocksdb.enableStatistics=false",
        "zeebe.broker.experimental.rocksdb.accessMetrics=fine",
        "zeebe.broker.experimental.rocksdb.memoryLimit=5GB",
        "zeebe.broker.experimental.rocksdb.maxOpenFiles=500",
        "zeebe.broker.experimental.rocksdb.maxWriteBufferNumber=4",
        "zeebe.broker.experimental.rocksdb.minWriteBufferNumberToMerge=2",
        "zeebe.broker.experimental.rocksdb.ioRateBytesPerSecond=5242880",
        "zeebe.broker.experimental.rocksdb.disableWal=true",
        "zeebe.broker.experimental.rocksdb.enableSstPartitioning=true",
        "zeebe.broker.experimental.rocksdb.columnFamilyOptions.write_buffer_size=16777216",
        "zeebe.broker.experimental.rocksdb.columnFamilyOptions.max_write_buffer_number=2",
        "zeebe.broker.experimental.rocksdb.columnFamilyOptions.write_buffer_size=33554432",
        "zeebe.broker.experimental.rocksdb.columnFamilyOptions.compaction_pri=kMinOverlappingRatio"
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetEnableStatistics() {
      assertThat(brokerCfg.getExperimental().getRocksdb().isEnableStatistics()).isFalse();
    }

    @Test
    void shouldSetMemoryLimit() {
      assertThat(brokerCfg.getExperimental().getRocksdb().getMemoryLimit())
          .isEqualTo(DataSize.ofGigabytes(5));
    }

    @Test
    void shouldSetMaxOpenFiles() {
      assertThat(brokerCfg.getExperimental().getRocksdb().getMaxOpenFiles()).isEqualTo(500);
    }

    @Test
    void shouldSetMaxWriteBufferNumber() {
      assertThat(brokerCfg.getExperimental().getRocksdb().getMaxWriteBufferNumber()).isEqualTo(4);
    }

    @Test
    void shouldSetMinWriteBufferNumberToMerge() {
      assertThat(brokerCfg.getExperimental().getRocksdb().getMinWriteBufferNumberToMerge())
          .isEqualTo(2);
    }

    @Test
    void shouldSetIoRateBytesPerSecond() {
      assertThat(brokerCfg.getExperimental().getRocksdb().getIoRateBytesPerSecond())
          .isEqualTo(5242880);
    }

    @Test
    void shouldSetDisableWal() {
      assertThat(brokerCfg.getExperimental().getRocksdb().isDisableWal()).isTrue();
    }

    @Test
    void shouldSetEnableSstPartitioning() {
      assertThat(brokerCfg.getExperimental().getRocksdb().isEnableSstPartitioning()).isTrue();
    }

    @Test
    void shouldSetAccessMetrics() {
      assertThat(brokerCfg.getExperimental().getRocksdb().getAccessMetrics()).isEqualTo(Kind.FINE);
    }

    @Test
    void shouldSetLegacyColumnFamilyOptions() {
      final Properties columnFamilyOptions =
          brokerCfg.getExperimental().getRocksdb().getColumnFamilyOptions();
      assertThat(columnFamilyOptions).isNotNull();
      assertThat(columnFamilyOptions).hasSize(3);
      assertThat(columnFamilyOptions.getProperty("write_buffer_size")).isEqualTo("33554432");
      assertThat(columnFamilyOptions.getProperty("compaction_pri"))
          .isEqualTo("kMinOverlappingRatio");
      assertThat(columnFamilyOptions.getProperty("max_write_buffer_number")).isEqualTo("2");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new properties
        "camunda.data.primary-storage.rocks-db.statistics-enabled=true",
        "camunda.data.primary-storage.rocks-db.access-metrics=fine",
        "camunda.data.primary-storage.rocks-db.memory-limit=4GB",
        "camunda.data.primary-storage.rocks-db.max-open-files=2000",
        "camunda.data.primary-storage.rocks-db.max-write-buffer-number=12",
        "camunda.data.primary-storage.rocks-db.min-write-buffer-number-to-merge=6",
        "camunda.data.primary-storage.rocks-db.io-rate-bytes-per-second=20971520",
        "camunda.data.primary-storage.rocks-db.wal-disabled=false",
        "camunda.data.primary-storage.rocks-db.sst-partitioning-enabled=false",
        "camunda.data.primary-storage.rocks-db.column-family-options.compaction_pri=kOldestSmallestSeqFirst",
        // legacy properties (should be ignored when new ones are present)
        "zeebe.broker.experimental.rocksdb.enableStatistics=false",
        "zeebe.broker.experimental.rocksdb.accessMetrics=none",
        "zeebe.broker.experimental.rocksdb.memoryLimit=512MB",
        "zeebe.broker.experimental.rocksdb.maxOpenFiles=100",
        "zeebe.broker.experimental.rocksdb.maxWriteBufferNumber=2",
        "zeebe.broker.experimental.rocksdb.minWriteBufferNumberToMerge=1",
        "zeebe.broker.experimental.rocksdb.ioRateBytesPerSecond=1048576",
        "zeebe.broker.experimental.rocksdb.disableWal=true",
        "zeebe.broker.experimental.rocksdb.enableSstPartitioning=true",
        "zeebe.broker.experimental.rocksdb.columnFamilyOptions.compaction_pri=kMinOverlappingRatio"
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetEnableStatisticsFromNew() {
      assertThat(brokerCfg.getExperimental().getRocksdb().isEnableStatistics()).isTrue();
    }

    @Test
    void shouldSetMemoryLimitFromNew() {
      assertThat(brokerCfg.getExperimental().getRocksdb().getMemoryLimit())
          .isEqualTo(DataSize.ofGigabytes(4));
    }

    @Test
    void shouldSetMaxOpenFilesFromNew() {
      assertThat(brokerCfg.getExperimental().getRocksdb().getMaxOpenFiles()).isEqualTo(2000);
    }

    @Test
    void shouldSetMaxWriteBufferNumberFromNew() {
      assertThat(brokerCfg.getExperimental().getRocksdb().getMaxWriteBufferNumber()).isEqualTo(12);
    }

    @Test
    void shouldSetMinWriteBufferNumberToMergeFromNew() {
      assertThat(brokerCfg.getExperimental().getRocksdb().getMinWriteBufferNumberToMerge())
          .isEqualTo(6);
    }

    @Test
    void shouldSetIoRateBytesPerSecondFromNew() {
      assertThat(brokerCfg.getExperimental().getRocksdb().getIoRateBytesPerSecond())
          .isEqualTo(20971520);
    }

    @Test
    void shouldSetDisableWalFromNew() {
      assertThat(brokerCfg.getExperimental().getRocksdb().isDisableWal()).isFalse();
    }

    @Test
    void shouldSetEnableSstPartitioningFromNew() {
      assertThat(brokerCfg.getExperimental().getRocksdb().isEnableSstPartitioning()).isFalse();
    }

    @Test
    void shouldSetAccessMetrics() {
      assertThat(brokerCfg.getExperimental().getRocksdb().getAccessMetrics()).isEqualTo(Kind.FINE);
    }

    @Test
    void shouldSetColumnFamilyOptionsFromNew() {
      final Properties columnFamilyOptions =
          brokerCfg.getExperimental().getRocksdb().getColumnFamilyOptions();
      assertThat(columnFamilyOptions).isNotNull();
      // New properties should take precedence over legacy ones
      assertThat(columnFamilyOptions).hasSize(1);
      assertThat(columnFamilyOptions.getProperty("compaction_pri"))
          .isEqualTo("kOldestSmallestSeqFirst");
    }
  }
}
