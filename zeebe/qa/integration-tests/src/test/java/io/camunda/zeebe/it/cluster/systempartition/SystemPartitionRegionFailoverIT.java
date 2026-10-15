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
import io.camunda.zeebe.broker.system.syspartition.detector.ticktock.RegionFailoverValue;
import io.camunda.zeebe.broker.system.syspartition.detector.ticktock.TickValue;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.actuator.PrometheusActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.awaitility.Awaitility;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies region failure detection on a 3x3x1 cluster: two data regions of 3 brokers and a control
 * region of 1 broker, which hosts no data partitions. The system partition is replicated on 5
 * brokers, the first 2 of each data region and the control broker, so either data region can fail
 * while the other one and the control broker keep a quorum. When every system partition broker of a
 * data region is dead, the leader commits a region failover to the other data region.
 */
@ZeebeIntegration
final class SystemPartitionRegionFailoverIT {

  private static final Duration TIMEOUT = Duration.ofMinutes(2);
  private static final int MISSED_TICKS_THRESHOLD = 5;
  private static final String REGION_A = "region-a";
  private static final String REGION_B = "region-b";
  private static final String CONTROL = "control";
  private static final String REGION_STATE_METRIC =
      "zeebe.system.partition.failure.detector.region.state";
  private static final String REGION_FAILOVER_TICK_METRIC =
      "zeebe.system.partition.failure.detector.region.failover.tick";

  @TestZeebe(awaitCompleteTopology = false)
  private final TestCluster cluster =
      TestCluster.builder()
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
                        final var systemPartition = cfg.getCluster().getSystemPartition();
                        systemPartition.setEnabled(true);
                        systemPartition.setReplicationFactor(5);
                        systemPartition.setSnapshotPeriod(Duration.ofSeconds(10));
                        final var detector = systemPartition.getFailureDetector();
                        detector.setType(FailureDetectorType.TICK_TOCK);
                        detector.getTickTock().setTickInterval(Duration.ofMillis(100));
                        detector.getTickTock().setMissedTicksThreshold(MISSED_TICKS_THRESHOLD);
                      }))
          .build();

  private List<TestStandaloneBroker> systemPartitionBrokers;

  @BeforeEach
  void setUp() {
    systemPartitionBrokers =
        List.of(
            broker(REGION_A, 0),
            broker(REGION_A, 1),
            broker(REGION_B, 0),
            broker(REGION_B, 1),
            broker(CONTROL, 0));
  }

  @Test
  void shouldPlaceDataPartitionsInDataRegionsOnly() {
    // when
    awaitEveryRegionActive(systemPartitionBrokers);

    // then - every replica of the 2 data partitions with replication factor 4 is in a data region
    final var dataBrokers =
        cluster.brokers().values().stream()
            .filter(broker -> !broker.nodeId().isInZone(CONTROL))
            .toList();
    Awaitility.await("every data partition replica started")
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        dataBrokers.stream()
                            .mapToInt(broker -> PartitionsActuator.of(broker).query().size())
                            .sum())
                    .isEqualTo(2 * 4));

    // and the control broker runs the system partition only
    final var control = broker(CONTROL, 0);
    assertThat(PartitionsActuator.of(control).query()).isEmpty();
    assertThat(systemPartitionOf(control)).isNotNull();
    for (final var region : List.of(REGION_A, REGION_B)) {
      assertThat(systemPartitionOf(broker(region, 2)))
          .describedAs("the third broker of %s runs no system partition", region)
          .isNull();
    }
    assertThat(systemPartitionOf(control).raftPartition().members()).hasSize(5);
  }

  @Test
  void shouldCommitRegionFailoverWhenEveryBrokerOfDataRegionIsDead() {
    // given
    awaitEveryRegionActive(systemPartitionBrokers);

    // when
    broker(REGION_A, 0).stop();
    broker(REGION_A, 1).stop();
    broker(REGION_A, 2).stop();

    // then - every survivor applies the same failover, decided on a tick where region A is dead
    final var survivors = List.of(broker(REGION_B, 0), broker(REGION_B, 1), broker(CONTROL, 0));
    final var failover =
        Awaitility.await("every survivor applied the region failover")
            .atMost(TIMEOUT)
            .until(
                () -> survivors.stream().map(this::regionFailover).toList(),
                failovers -> failovers.stream().allMatch(Optional::isPresent))
            .getFirst()
            .orElseThrow();
    assertThat(failover.getFailedRegion()).isEqualTo(REGION_A);
    assertThat(failover.getTargetRegion()).isEqualTo(REGION_B);
    assertThat(failover.getTocks())
        .containsKeys("region-a_0", "region-a_1", "region-b_0", "region-b_1", "control_0")
        .allSatisfy(
            (member, lastSeen) -> {
              if (member.startsWith(REGION_A)) {
                assertThat(failover.getTickId() - lastSeen).isGreaterThan(MISSED_TICKS_THRESHOLD);
              } else {
                assertThat(failover.getTickId() - lastSeen)
                    .isLessThanOrEqualTo(MISSED_TICKS_THRESHOLD);
              }
            });
    Awaitility.await("every survivor reports the failover tick and region A failed")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(survivors)
                    .allSatisfy(
                        survivor -> {
                          assertThat(failoverTickMetric(survivor))
                              .isEqualTo((double) failover.getTickId());
                          assertThat(regionState(survivor, REGION_A)).isEqualTo(RegionState.FAILED);
                        }));

    // and the series the Zeebe dashboard queries are exported
    assertThat(PrometheusActuator.of(survivors.getFirst()).metrics())
        .contains(
            "zeebe_system_partition_failure_detector_region_state{",
            "zeebe_system_partition_failure_detector_region_failover_tick{");

    // and the failover is committed once, later ticks leave it unchanged
    final var tickAfter = tickId(survivors.getFirst()) + 2L * MISSED_TICKS_THRESHOLD;
    Awaitility.await("more ticks are committed")
        .atMost(TIMEOUT)
        .until(() -> tickId(survivors.getFirst()) > tickAfter);
    assertThat(regionFailover(survivors.getFirst()))
        .hasValueSatisfying(value -> assertThat(value.getTickId()).isEqualTo(failover.getTickId()));
  }

  @Test
  void shouldCommitAnotherRegionFailoverWhenTheRegionFailsAgain() {
    // given - region A failed over, and then came back
    awaitEveryRegionActive(systemPartitionBrokers);
    final var regionA = List.of(broker(REGION_A, 0), broker(REGION_A, 1), broker(REGION_A, 2));
    final var survivors = List.of(broker(REGION_B, 0), broker(REGION_B, 1), broker(CONTROL, 0));
    regionA.forEach(TestStandaloneBroker::stop);
    final var first = awaitRegionFailover(survivors, 0);
    regionA.forEach(TestStandaloneBroker::start);
    Awaitility.await("every survivor sees region A active again")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(survivors)
                    .allSatisfy(
                        survivor ->
                            assertThat(regionState(survivor, REGION_A))
                                .isEqualTo(RegionState.ACTIVE)));

    // when
    regionA.forEach(TestStandaloneBroker::stop);

    // then - the failover is replaced by one decided after the first
    final var second = awaitRegionFailover(survivors, first.getTickId());
    assertThat(second.getFailedRegion()).isEqualTo(REGION_A);
    assertThat(second.getTargetRegion()).isEqualTo(REGION_B);
    Awaitility.await("every survivor reports the latest failover tick")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(survivors)
                    .allSatisfy(
                        survivor ->
                            assertThat(failoverTickMetric(survivor))
                                .isEqualTo((double) second.getTickId())));
  }

  @Test
  void shouldNotFailOverWhileDataRegionHasLiveBroker() {
    // given
    awaitEveryRegionActive(systemPartitionBrokers);
    final var stopped = broker(REGION_A, 0);

    // when
    stopped.stop();

    // then - region A is suspect, never failed, and no failover is committed
    final var observers =
        systemPartitionBrokers.stream().filter(broker -> broker != stopped).toList();
    Awaitility.await("every other broker suspects region A")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(observers)
                    .allSatisfy(
                        observer ->
                            assertThat(regionState(observer, REGION_A))
                                .isEqualTo(RegionState.SUSPECT)));
    final var tickAfterDead = tickId(observers.getFirst()) + 2L * MISSED_TICKS_THRESHOLD;
    Awaitility.await("the stopped broker is long dead")
        .atMost(TIMEOUT)
        .until(() -> tickId(observers.getFirst()) > tickAfterDead);
    assertThat(observers)
        .allSatisfy(
            observer -> {
              assertThat(regionFailover(observer)).isEmpty();
              assertThat(regionState(observer, REGION_A)).isEqualTo(RegionState.SUSPECT);
            });
  }

  /** Awaits every broker to apply the same failover decided after the given tick. */
  private RegionFailoverValue awaitRegionFailover(
      final List<TestStandaloneBroker> brokers, final long afterTick) {
    return Awaitility.await("every broker applied a region failover after tick " + afterTick)
        .atMost(TIMEOUT)
        .until(
            () -> brokers.stream().map(this::regionFailover).toList(),
            failovers ->
                failovers.stream()
                    .allMatch(
                        failover -> failover.isPresent() && failover.get().getTickId() > afterTick))
        .getFirst()
        .orElseThrow();
  }

  private TestStandaloneBroker broker(final String zone, final int nodeId) {
    return cluster.brokers().get(MemberId.from(zone, nodeId));
  }

  private void awaitEveryRegionActive(final List<TestStandaloneBroker> observers) {
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

  private static @Nullable SystemPartition systemPartitionOf(final TestStandaloneBroker broker) {
    return broker.bean(Broker.class).getBrokerContext().getSystemPartition();
  }

  private long tickId(final TestStandaloneBroker broker) {
    return systemPartitionOf(broker)
        .keyValueReader()
        .get(SystemPartitionKeyType.TICK, new TickValue())
        .map(TickValue::getTickId)
        .orElse(0L);
  }

  private Optional<RegionFailoverValue> regionFailover(final TestStandaloneBroker broker) {
    return systemPartitionOf(broker)
        .keyValueReader()
        .get(SystemPartitionKeyType.REGION_FAILOVER, new RegionFailoverValue());
  }

  private static @Nullable RegionState regionState(
      final TestStandaloneBroker observer, final String region) {
    final var gauge =
        observer.bean(MeterRegistry.class).find(REGION_STATE_METRIC).tag("region", region).gauge();
    if (gauge == null) {
      return null;
    }
    final var code = (int) gauge.value();
    return List.of(RegionState.values()).stream()
        .filter(state -> state.code() == code)
        .findFirst()
        .orElse(null);
  }

  private static @Nullable Double failoverTickMetric(final TestStandaloneBroker observer) {
    return Optional.ofNullable(
            observer
                .bean(MeterRegistry.class)
                .find(REGION_FAILOVER_TICK_METRIC)
                .tag("region", REGION_A)
                .tag("target_region", REGION_B)
                .gauge())
        .map(Gauge::value)
        .orElse(null);
  }
}
