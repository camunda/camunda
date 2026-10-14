/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.systempartition;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.raft.RaftServer.Role;
import io.camunda.configuration.Camunda;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.system.configuration.FailureDetectorType;
import io.camunda.zeebe.broker.system.syspartition.ClusterIdentity;
import io.camunda.zeebe.broker.system.syspartition.SystemPartition;
import io.camunda.zeebe.broker.system.syspartition.SystemPartitionKeyType;
import io.camunda.zeebe.broker.system.syspartition.detector.MemberLivenessState;
import io.camunda.zeebe.broker.system.syspartition.detector.ticktock.ClusterViewValue;
import io.camunda.zeebe.broker.system.syspartition.detector.ticktock.TickValue;
import io.camunda.zeebe.qa.util.actuator.PrometheusActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the tick-tock failure detector of the system partition sees every running broker,
 * reports a stopped broker as gone on every other broker, survives leader changes without reporting
 * anyone gone, and that the cluster view rebuilds from the log on restart.
 */
@ZeebeIntegration
final class SystemPartitionFailureDetectorIT {

  private static final Duration TIMEOUT = Duration.ofMinutes(1);
  private static final int MISSED_TICKS_THRESHOLD = 5;
  private static final String BROKER_STATE_METRIC =
      "zeebe.system.partition.failure.detector.broker.state";

  private static TestCluster cluster(final Consumer<Camunda> config) {
    return TestCluster.builder()
        .withBrokersCount(3)
        .withEmbeddedGateway(true)
        .withPartitionsCount(1)
        .withReplicationFactor(3)
        .withBrokerConfig(
            broker ->
                broker.withUnifiedConfig(
                    cfg -> {
                      cfg.getCluster().getSystemPartition().setEnabled(true);
                      config.accept(cfg);
                    }))
        .build();
  }

  private static long tickId(final TestStandaloneBroker broker) {
    return systemPartition(broker)
        .keyValueReader()
        .get(SystemPartitionKeyType.TICK, new TickValue())
        .map(TickValue::getTickId)
        .orElse(0L);
  }

  private static Map<String, Long> view(final TestStandaloneBroker broker) {
    return systemPartition(broker)
        .keyValueReader()
        .get(SystemPartitionKeyType.CLUSTER_VIEW, new ClusterViewValue())
        .map(ClusterViewValue::lastSeenTicks)
        .map(Map::copyOf)
        .orElse(Map.of());
  }

  private static Optional<MemberLivenessState> state(
      final TestStandaloneBroker observer, final TestStandaloneBroker observed) {
    return Optional.ofNullable(
            observer
                .bean(MeterRegistry.class)
                .find(BROKER_STATE_METRIC)
                .tag("broker", observed.nodeId().id())
                .gauge())
        .map(Gauge::value)
        .flatMap(
            value ->
                Arrays.stream(MemberLivenessState.values())
                    .filter(state -> state.code() == value.intValue())
                    .findFirst());
  }

  private static TestStandaloneBroker brokerWithRole(final TestCluster cluster, final Role role) {
    return Awaitility.await("system partition has a %s".formatted(role))
        .atMost(TIMEOUT)
        .until(
            () ->
                cluster.brokers().values().stream()
                    .filter(TestStandaloneBroker::isStarted)
                    .filter(broker -> systemPartition(broker).raftPartition().getRole() == role)
                    .findFirst(),
            Optional::isPresent)
        .orElseThrow();
  }

  private static SystemPartition systemPartition(final TestStandaloneBroker broker) {
    final var systemPartition = broker.bean(Broker.class).getBrokerContext().getSystemPartition();
    assertThat(systemPartition).describedAs("system partition of %s", broker.nodeId()).isNotNull();
    return systemPartition;
  }

  private static void awaitEveryBrokerSeesEveryBrokerHealthy(final TestCluster cluster) {
    // a broker never seen counts as alive for the first ticks, so also wait until it is seen
    Awaitility.await("every broker sees every broker healthy")
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              for (final var observer : cluster.brokers().values()) {
                assertThat(view(observer))
                    .describedAs("view of %s", observer.nodeId())
                    .containsOnlyKeys(
                        cluster.brokers().values().stream()
                            .map(broker -> broker.nodeId().id())
                            .toList());
                for (final var observed : cluster.brokers().values()) {
                  assertThat(state(observer, observed))
                      .describedAs("%s sees %s healthy", observer.nodeId(), observed.nodeId())
                      .contains(MemberLivenessState.HEALTHY);
                }
              }
            });
  }

  @Nested
  final class WithTickTock {

    @TestZeebe
    private final TestCluster cluster =
        cluster(
            cfg -> {
              final var detector = cfg.getCluster().getSystemPartition().getFailureDetector();
              detector.setType(FailureDetectorType.TICK_TOCK);
              detector.getTickTock().setTickInterval(Duration.ofMillis(100));
              detector.getTickTock().setMissedTicksThreshold(MISSED_TICKS_THRESHOLD);
            });

    /** The series the Zeebe dashboard queries, so renaming a metric fails here first. */
    @Test
    void shouldExportMetricsQueriedByDashboard() {
      // given
      awaitEveryBrokerSeesEveryBrokerHealthy(cluster);
      final var leader = brokerWithRole(cluster, Role.LEADER);

      // when
      final var scraped = PrometheusActuator.of(leader).metrics();

      // then
      assertThat(scraped)
          .contains(
              "zeebe_system_partition_failure_detector_broker_state{",
              "zeebe_system_partition_failure_detector_missed_ticks{",
              "zeebe_system_partition_failure_detector_tick{",
              "zeebe_system_partition_failure_detector_tocks_received_total{",
              "zeebe_system_partition_failure_detector_tick_commit_latency_seconds_bucket{")
          .containsPattern(
              "atomix_role\\{[^}]*partition=\"0\"[^}]*physicalTenant=\"system\"[^}]*} 3\\.0");
    }

    @Test
    void shouldSeeEveryBroker() {
      // when
      awaitEveryBrokerSeesEveryBrokerHealthy(cluster);

      // then
      for (final var broker : cluster.brokers().values()) {
        final var tickId = tickId(broker);
        assertThat(view(broker))
            .hasSize(3)
            .allSatisfy(
                (member, lastSeen) ->
                    assertThat(lastSeen).isGreaterThanOrEqualTo(tickId - MISSED_TICKS_THRESHOLD));
      }
    }

    @Test
    void shouldDetectStoppedBrokerOnEveryOtherBroker() {
      // given
      awaitEveryBrokerSeesEveryBrokerHealthy(cluster);
      final var stopped = brokerWithRole(cluster, Role.FOLLOWER);

      // when
      stopped.stop();

      // then
      final var observers =
          cluster.brokers().values().stream().filter(broker -> broker != stopped).toList();
      Awaitility.await("every other broker suspects the stopped broker")
          .atMost(TIMEOUT)
          .untilAsserted(
              () ->
                  assertThat(observers)
                      .allSatisfy(
                          observer ->
                              assertThat(state(observer, stopped))
                                  .hasValueSatisfying(
                                      state ->
                                          assertThat(state)
                                              .isIn(
                                                  MemberLivenessState.SUSPECT,
                                                  MemberLivenessState.DEAD))));
      Awaitility.await("every other broker considers the stopped broker dead")
          .atMost(TIMEOUT)
          .untilAsserted(
              () ->
                  assertThat(observers)
                      .allSatisfy(
                          observer ->
                              assertThat(state(observer, stopped))
                                  .contains(MemberLivenessState.DEAD)));
    }

    @Test
    void shouldSeeRestartedBrokerHealthyAgain() {
      // given
      awaitEveryBrokerSeesEveryBrokerHealthy(cluster);
      final var restarted = brokerWithRole(cluster, Role.FOLLOWER);
      restarted.stop();
      Awaitility.await("the stopped broker is dead")
          .atMost(TIMEOUT)
          .untilAsserted(
              () ->
                  assertThat(state(brokerWithRole(cluster, Role.LEADER), restarted))
                      .contains(MemberLivenessState.DEAD));

      // when
      restarted.start();

      // then
      awaitEveryBrokerSeesEveryBrokerHealthy(cluster);
    }

    @Test
    void shouldKeepTickingWithoutFalseDetectionWhenLeaderChanges() {
      // given
      awaitEveryBrokerSeesEveryBrokerHealthy(cluster);
      final var oldLeader = brokerWithRole(cluster, Role.LEADER);
      final var survivors =
          cluster.brokers().values().stream().filter(broker -> broker != oldLeader).toList();
      final var tickBefore = tickId(survivors.getFirst());

      // when
      oldLeader.stop();

      // then - the new leader continues the ticks, and no survivor ever suspects another
      Awaitility.await("the new leader keeps ticking")
          .atMost(TIMEOUT)
          .untilAsserted(
              () ->
                  assertThat(survivors)
                      .allSatisfy(
                          broker ->
                              assertThat(tickId(broker))
                                  .isGreaterThan(tickBefore + 2L * MISSED_TICKS_THRESHOLD)));
      for (final var observer : survivors) {
        for (final var observed : survivors) {
          assertThat(state(observer, observed))
              .describedAs("%s sees %s healthy", observer.nodeId(), observed.nodeId())
              .contains(MemberLivenessState.HEALTHY);
        }
      }
    }

    @Test
    void shouldRebuildSameViewAfterRestart() {
      // given
      awaitEveryBrokerSeesEveryBrokerHealthy(cluster);
      final var restarted = brokerWithRole(cluster, Role.FOLLOWER);
      final var other =
          cluster.brokers().values().stream()
              .filter(broker -> broker != restarted)
              .findFirst()
              .orElseThrow();

      // when
      restarted.stop();
      restarted.start();

      // then - both brokers hold the same view at the same tick
      Awaitility.await("the restarted broker holds the same view as another")
          .atMost(TIMEOUT)
          .untilAsserted(
              () -> {
                final var otherState = systemPartition(other).state().orElseThrow().entries();
                final var restartedState =
                    systemPartition(restarted).state().orElseThrow().entries();
                assertThat(restartedState.get(SystemPartitionKeyType.TICK.id()))
                    .isEqualTo(otherState.get(SystemPartitionKeyType.TICK.id()));
                assertThat(restartedState.get(SystemPartitionKeyType.CLUSTER_VIEW.id()))
                    .isEqualTo(otherState.get(SystemPartitionKeyType.CLUSTER_VIEW.id()));
              });
    }
  }

  @Nested
  final class WithoutFailureDetector {

    @TestZeebe private final TestCluster cluster = cluster(cfg -> {});

    @Test
    void shouldNotTick() {
      // given - the leader recorded the cluster identity, so it processed its leadership
      Awaitility.await("every broker reads the cluster identity")
          .atMost(TIMEOUT)
          .until(
              () ->
                  cluster.brokers().values().stream()
                      .allMatch(
                          broker ->
                              ClusterIdentity.read(systemPartition(broker).keyValueReader())
                                  .isPresent()));

      // then
      for (final var broker : cluster.brokers().values()) {
        assertThat(tickId(broker)).isZero();
        assertThat(state(broker, broker)).isEmpty();
      }
    }
  }
}
