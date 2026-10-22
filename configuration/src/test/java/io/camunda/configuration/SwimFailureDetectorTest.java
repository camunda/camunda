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
import io.camunda.zeebe.broker.system.configuration.FailureDetectorType;
import io.camunda.zeebe.broker.system.configuration.partitioning.ZoneRole;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Verifies that the properties of the SWIM failure detector bind from the unified configuration, as
 * the tri-region setup script writes them, and reach the broker configuration.
 */
@SpringJUnitConfig({
  UnifiedConfiguration.class,
  BrokerBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("broker")
final class SwimFailureDetectorTest {

  @Nested
  @TestPropertySource(properties = {"camunda.cluster.system-partition.enabled=true"})
  class WithDefaults {
    @Autowired private BrokerBasedProperties brokerCfg;

    @Test
    void shouldNotRunAFailureDetectorByDefault() {
      // when
      final var detector = brokerCfg.getCluster().getSystemPartition().getFailureDetector();

      // then
      assertThat(detector.getType()).isEqualTo(FailureDetectorType.NONE);
      assertThat(detector.getSwim().getReportInterval()).isEqualTo(Duration.ofSeconds(1));
      assertThat(detector.getSwim().getReportTimeout()).isEqualTo(Duration.ofSeconds(5));
      assertThat(detector.getSwim().getDetectionWindow()).isEqualTo(Duration.ofSeconds(30));
      assertThat(detector.getSwim().getRecoveryWindow()).isEqualTo(Duration.ofSeconds(30));
      assertThat(detector.getSwim().getMinObservers()).isEqualTo(2);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.cluster.system-partition.enabled=true",
        "camunda.cluster.system-partition.replication-factor=5",
        "camunda.cluster.system-partition.failure-detector.type=SWIM",
        "camunda.cluster.system-partition.failure-detector.swim.report-interval=2s",
        "camunda.cluster.system-partition.failure-detector.swim.report-timeout=7s",
        "camunda.cluster.system-partition.failure-detector.swim.detection-window=1m",
        "camunda.cluster.system-partition.failure-detector.swim.recovery-window=45s",
        "camunda.cluster.system-partition.failure-detector.swim.min-observers=3",
        "camunda.cluster.membership.failure-timeout=20s",
        "camunda.cluster.partitioning.scheme=ZONE_AWARE",
        "camunda.cluster.partitioning.zone-aware.zones[0].name=region-a",
        "camunda.cluster.partitioning.zone-aware.zones[0].number-of-brokers=3",
        "camunda.cluster.partitioning.zone-aware.zones[0].number-of-replicas=2",
        "camunda.cluster.partitioning.zone-aware.zones[0].priority=1000",
        "camunda.cluster.partitioning.zone-aware.zones[1].name=control",
        "camunda.cluster.partitioning.zone-aware.zones[1].number-of-brokers=1",
        "camunda.cluster.partitioning.zone-aware.zones[1].number-of-replicas=0",
        "camunda.cluster.partitioning.zone-aware.zones[1].priority=1",
        "camunda.cluster.partitioning.zone-aware.zones[1].role=control"
      })
  class WithSwimConfigured {
    @Autowired private BrokerBasedProperties brokerCfg;

    @Test
    void shouldBindTheDetectorAndItsWindowsInSeconds() {
      // when
      final var detector = brokerCfg.getCluster().getSystemPartition().getFailureDetector();

      // then
      assertThat(detector.getType()).isEqualTo(FailureDetectorType.SWIM);
      assertThat(detector.getSwim().getReportInterval()).isEqualTo(Duration.ofSeconds(2));
      assertThat(detector.getSwim().getReportTimeout()).isEqualTo(Duration.ofSeconds(7));
      assertThat(detector.getSwim().getDetectionWindow()).isEqualTo(Duration.ofMinutes(1));
      assertThat(detector.getSwim().getRecoveryWindow()).isEqualTo(Duration.ofSeconds(45));
      assertThat(detector.getSwim().getMinObservers()).isEqualTo(3);
    }

    @Test
    void shouldBindTheControlZoneRoleAndTheSwimFailureTimeoutTheWindowIsCheckedAgainst() {
      // when
      final var zones = brokerCfg.getExperimental().getPartitioning().getZoneAware().zones();

      // then
      assertThat(zones)
          .last()
          .satisfies(zone -> assertThat(zone.role()).isEqualTo(ZoneRole.CONTROL));
      assertThat(brokerCfg.getCluster().getMembership().getFailureTimeout())
          .isEqualTo(Duration.ofSeconds(20));
      assertThat(brokerCfg.getCluster().getSystemPartition().getReplicationFactor()).isEqualTo(5);
    }
  }
}
