/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.systempartition;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.configuration.Zone;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.system.configuration.FailureDetectorType;
import io.camunda.zeebe.broker.system.configuration.partitioning.ZoneRole;
import io.camunda.zeebe.broker.system.syspartition.SystemPartition;
import io.camunda.zeebe.broker.system.syspartition.SystemPartitionKeyType;
import io.camunda.zeebe.broker.system.syspartition.detector.RegionState;
import io.camunda.zeebe.broker.system.syspartition.detector.swim.RegionFailoverValue;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.awaitility.Awaitility;
import org.jspecify.annotations.Nullable;

/**
 * A 3x3x1 cluster with the SWIM failure detector, and what the tests read from its brokers: two
 * data regions of 3 brokers and a control region of 1 broker, which hosts no data partitions. The
 * system partition is replicated on 5 brokers, the first 2 of each data region and the control
 * broker, so either data region can be gone while the other and the control broker keep a quorum.
 */
final class SwimRegionClusterSupport {
  static final Duration TIMEOUT = Duration.ofMinutes(2);
  static final Duration DETECTION_WINDOW = Duration.ofSeconds(6);
  static final String REGION_A = "region-a";
  static final String REGION_B = "region-b";
  static final String CONTROL = "control";
  private static final String REGION_STATE_METRIC =
      "zeebe.system.partition.swim.detector.region.state";
  private static final String REGION_FAILOVER_METRIC =
      "zeebe.system.partition.swim.detector.region.failover";

  private final TestCluster cluster;

  SwimRegionClusterSupport(final TestCluster cluster) {
    this.cluster = cluster;
  }

  static TestCluster cluster(final Duration recoveryWindow) {
    return TestCluster.builder()
        .withBrokersCount(7)
        .withEmbeddedGateway(true)
        .withPartitionsCount(2)
        .withReplicationFactor(4)
        .multiZone(
            List.of(
                new Zone(REGION_A, 3, 2, 1000),
                new Zone(REGION_B, 3, 2, 500),
                new Zone(CONTROL, 1, 0, 1, ZoneRole.CONTROL)))
        .withBrokerConfig(
            broker ->
                broker.withUnifiedConfig(
                    cfg -> {
                      final var membership = cfg.getCluster().getMembership();
                      membership.setGossipInterval(Duration.ofMillis(100));
                      membership.setProbeInterval(Duration.ofMillis(500));
                      membership.setProbeTimeout(Duration.ofMillis(500));
                      membership.setFailureTimeout(Duration.ofSeconds(3));
                      membership.setSyncInterval(Duration.ofSeconds(3));
                      final var systemPartition = cfg.getCluster().getSystemPartition();
                      systemPartition.setEnabled(true);
                      systemPartition.setReplicationFactor(5);
                      systemPartition.setSnapshotPeriod(Duration.ofSeconds(10));
                      final var detector = systemPartition.getFailureDetector();
                      detector.setType(FailureDetectorType.SWIM);
                      detector.getSwim().setReportInterval(Duration.ofSeconds(1));
                      detector.getSwim().setReportTimeout(Duration.ofSeconds(3));
                      detector.getSwim().setDetectionWindow(DETECTION_WINDOW);
                      detector.getSwim().setRecoveryWindow(recoveryWindow);
                    }))
        .build();
  }

  TestStandaloneBroker broker(final String zone, final int nodeId) {
    return cluster.brokers().get(MemberId.from(zone, nodeId));
  }

  /** The brokers running the system partition. */
  List<TestStandaloneBroker> systemPartitionBrokers() {
    return List.of(
        broker(REGION_A, 0),
        broker(REGION_A, 1),
        broker(REGION_B, 0),
        broker(REGION_B, 1),
        broker(CONTROL, 0));
  }

  List<TestStandaloneBroker> regionA() {
    return List.of(broker(REGION_A, 0), broker(REGION_A, 1), broker(REGION_A, 2));
  }

  /** The brokers running the system partition outside of region A. */
  List<TestStandaloneBroker> survivorsOfRegionA() {
    return List.of(broker(REGION_B, 0), broker(REGION_B, 1), broker(CONTROL, 0));
  }

  void awaitEveryRegionActive(final List<TestStandaloneBroker> observers) {
    Awaitility.await("every system partition broker sees every region active")
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(observers)
                    .allSatisfy(
                        observer -> {
                          for (final var region : List.of(REGION_A, REGION_B, CONTROL)) {
                            assertThat(regionState(observer, region))
                                .describedAs("%s sees %s", observer.nodeId(), region)
                                .isEqualTo(RegionState.ACTIVE);
                          }
                        }));
  }

  /** Awaits every broker to apply the same failover decided after the given time. */
  RegionFailoverValue awaitRegionFailover(
      final List<TestStandaloneBroker> brokers, final long afterMillis) {
    final var failovers =
        Awaitility.await("every broker applied a region failover after " + afterMillis)
            .atMost(TIMEOUT)
            .until(
                () -> brokers.stream().map(SwimRegionClusterSupport::regionFailover).toList(),
                all ->
                    all.stream()
                        .allMatch(
                            failover ->
                                failover.isPresent()
                                    && failover.get().getDecidedAtMillis() > afterMillis));
    final var first = failovers.getFirst().orElseThrow();
    assertThat(failovers)
        .describedAs("every broker applies the same failover")
        .allSatisfy(
            failover -> {
              assertThat(failover.orElseThrow().getDecidedAtMillis())
                  .isEqualTo(first.getDecidedAtMillis());
              assertThat(failover.orElseThrow().getGoneBrokers()).isEqualTo(first.getGoneBrokers());
            });
    return first;
  }

  static @Nullable SystemPartition systemPartitionOf(final TestStandaloneBroker broker) {
    return broker.bean(Broker.class).getBrokerContext().getSystemPartition();
  }

  static Optional<RegionFailoverValue> regionFailover(final TestStandaloneBroker broker) {
    return systemPartitionOf(broker)
        .keyValueReader()
        .get(SystemPartitionKeyType.REGION_FAILOVER, new RegionFailoverValue());
  }

  static @Nullable RegionState regionState(
      final TestStandaloneBroker observer, final String region) {
    final var gauge =
        observer.bean(MeterRegistry.class).find(REGION_STATE_METRIC).tag("region", region).gauge();
    if (gauge == null) {
      return null;
    }
    final var code = (int) gauge.value();
    return Arrays.stream(RegionState.values())
        .filter(state -> state.code() == code)
        .findFirst()
        .orElse(null);
  }

  /** Returns the epoch seconds at which the failover from region A to B was decided. */
  static Optional<Double> failoverMetric(final TestStandaloneBroker observer) {
    return Optional.ofNullable(
            observer
                .bean(MeterRegistry.class)
                .find(REGION_FAILOVER_METRIC)
                .tag("region", REGION_A)
                .tag("target_region", REGION_B)
                .gauge())
        .map(Gauge::value);
  }
}
