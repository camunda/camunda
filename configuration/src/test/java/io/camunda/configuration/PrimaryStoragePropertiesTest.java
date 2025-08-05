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
import java.time.Duration;
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
public class PrimaryStoragePropertiesTest {

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.primary-storage.directory=/custom/data",
        "camunda.data.primary-storage.runtime-directory=/custom/runtime",
        "camunda.data.primary-storage.disk.monitoring-enabled=false",
        "camunda.data.primary-storage.disk.monitoring-interval=30s",
        "camunda.data.primary-storage.disk.free-space.processing=5GB",
        "camunda.data.primary-storage.disk.free-space.replication=3GB",
        "camunda.data.primary-storage.log-stream.log-index-density=200",
        "camunda.data.primary-storage.log-stream.log-segment-size=256MB"
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetDirectory() {
      assertThat(brokerCfg.getData().getDirectory()).isEqualTo("/custom/data");
    }

    @Test
    void shouldSetRuntimeDirectory() {
      assertThat(brokerCfg.getData().getRuntimeDirectory()).isEqualTo("/custom/runtime");
    }

    @Test
    void shouldSetDiskEnableMonitoring() {
      assertThat(brokerCfg.getData().getDisk().isEnableMonitoring()).isFalse();
    }

    @Test
    void shouldSetDiskMonitoringInterval() {
      assertThat(brokerCfg.getData().getDisk().getMonitoringInterval())
          .isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void shouldSetDiskFreeSpaceProcessing() {
      assertThat(brokerCfg.getData().getDisk().getFreeSpace().getProcessing())
          .isEqualTo(DataSize.ofGigabytes(5));
    }

    @Test
    void shouldSetDiskFreeSpaceReplication() {
      assertThat(brokerCfg.getData().getDisk().getFreeSpace().getReplication())
          .isEqualTo(DataSize.ofGigabytes(3));
    }

    @Test
    void shouldSetLogIndexDensity() {
      assertThat(brokerCfg.getData().getLogIndexDensity()).isEqualTo(200);
    }

    @Test
    void shouldSetLogSegmentSize() {
      assertThat(brokerCfg.getData().getLogSegmentSize()).isEqualTo(DataSize.ofMegabytes(256));
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.data.directory=/legacy/data",
        "zeebe.broker.data.runtimeDirectory=/legacy/runtime",
        "zeebe.broker.data.disk.enableMonitoring=true",
        "zeebe.broker.data.disk.monitoringInterval=5s",
        "zeebe.broker.data.disk.freeSpace.processing=4GB",
        "zeebe.broker.data.disk.freeSpace.replication=2GB",
        "zeebe.broker.data.logIndexDensity=150",
        "zeebe.broker.data.logSegmentSize=64MB"
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetDirectory() {
      assertThat(brokerCfg.getData().getDirectory()).isEqualTo("/legacy/data");
    }

    @Test
    void shouldSetRuntimeDirectory() {
      assertThat(brokerCfg.getData().getRuntimeDirectory()).isEqualTo("/legacy/runtime");
    }

    @Test
    void shouldSetDiskEnableMonitoring() {
      assertThat(brokerCfg.getData().getDisk().isEnableMonitoring()).isTrue();
    }

    @Test
    void shouldSetDiskMonitoringInterval() {
      assertThat(brokerCfg.getData().getDisk().getMonitoringInterval())
          .isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void shouldSetDiskFreeSpaceProcessing() {
      assertThat(brokerCfg.getData().getDisk().getFreeSpace().getProcessing())
          .isEqualTo(DataSize.ofGigabytes(4));
    }

    @Test
    void shouldSetDiskFreeSpaceReplication() {
      assertThat(brokerCfg.getData().getDisk().getFreeSpace().getReplication())
          .isEqualTo(DataSize.ofGigabytes(2));
    }

    @Test
    void shouldSetLogIndexDensity() {
      assertThat(brokerCfg.getData().getLogIndexDensity()).isEqualTo(150);
    }

    @Test
    void shouldSetLogSegmentSize() {
      assertThat(brokerCfg.getData().getLogSegmentSize()).isEqualTo(DataSize.ofMegabytes(64));
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new properties
        "camunda.data.primary-storage.directory=/unified/data",
        "camunda.data.primary-storage.runtime-directory=/unified/runtime",
        "camunda.data.primary-storage.disk.monitoring-enabled=false",
        "camunda.data.primary-storage.disk.monitoring-interval=10s",
        "camunda.data.primary-storage.disk.free-space.processing=8GB",
        "camunda.data.primary-storage.disk.free-space.replication=4GB",
        "camunda.data.primary-storage.log-stream.log-index-density=300",
        "camunda.data.primary-storage.log-stream.log-segment-size=512MB",
        // legacy properties (should be ignored when new ones are present)
        "zeebe.broker.data.directory=/legacy/ignored",
        "zeebe.broker.data.runtimeDirectory=/legacy/ignored",
        "zeebe.broker.data.disk.enableMonitoring=true",
        "zeebe.broker.data.disk.monitoringInterval=60s",
        "zeebe.broker.data.disk.freeSpace.processing=1GB",
        "zeebe.broker.data.disk.freeSpace.replication=512MB",
        "zeebe.broker.data.logIndexDensity=50",
        "zeebe.broker.data.logSegmentSize=32MB"
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetDirectoryFromNew() {
      assertThat(brokerCfg.getData().getDirectory()).isEqualTo("/unified/data");
    }

    @Test
    void shouldSetRuntimeDirectoryFromNew() {
      assertThat(brokerCfg.getData().getRuntimeDirectory()).isEqualTo("/unified/runtime");
    }

    @Test
    void shouldSetDiskEnableMonitoringFromNew() {
      assertThat(brokerCfg.getData().getDisk().isEnableMonitoring()).isFalse();
    }

    @Test
    void shouldSetDiskMonitoringIntervalFromNew() {
      assertThat(brokerCfg.getData().getDisk().getMonitoringInterval())
          .isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void shouldSetDiskFreeSpaceProcessingFromNew() {
      assertThat(brokerCfg.getData().getDisk().getFreeSpace().getProcessing())
          .isEqualTo(DataSize.ofGigabytes(8));
    }

    @Test
    void shouldSetDiskFreeSpaceReplicationFromNew() {
      assertThat(brokerCfg.getData().getDisk().getFreeSpace().getReplication())
          .isEqualTo(DataSize.ofGigabytes(4));
    }

    @Test
    void shouldSetLogIndexDensityFromNew() {
      assertThat(brokerCfg.getData().getLogIndexDensity()).isEqualTo(300);
    }

    @Test
    void shouldSetLogSegmentSizeFromNew() {
      assertThat(brokerCfg.getData().getLogSegmentSize()).isEqualTo(DataSize.ofMegabytes(512));
    }
  }
}
