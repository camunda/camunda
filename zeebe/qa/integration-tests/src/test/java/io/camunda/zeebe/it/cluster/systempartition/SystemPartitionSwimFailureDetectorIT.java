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
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.system.configuration.FailureDetectorType;
import io.camunda.zeebe.broker.system.syspartition.SystemPartition;
import io.camunda.zeebe.broker.system.syspartition.SystemPartitionKeyType;
import io.camunda.zeebe.broker.system.syspartition.detector.MemberLivenessState;
import io.camunda.zeebe.broker.system.syspartition.detector.swim.RegionFailoverValue;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the SWIM failure detector of the system partition sees every running broker as
 * healthy on every broker, reports a stopped broker as gone on every other broker once every
 * observer has not seen it for the detection window, sees it healthy again when it returns, and
 * does not decide on a region failover in a cluster without regions.
 */
@ZeebeIntegration
final class SystemPartitionSwimFailureDetectorIT {

  private static final Duration TIMEOUT = Duration.ofMinutes(1);
  private static final Duration DETECTION_WINDOW = Duration.ofSeconds(6);
  private static final String BROKER_STATE_METRIC =
      "zeebe.system.partition.swim.detector.broker.state";
  private static final String BROKER_GONE_METRIC =
      "zeebe.system.partition.swim.detector.broker.gone.duration";
  private static final String FRESH_REPORTS_METRIC =
      "zeebe.system.partition.swim.detector.reports.fresh";

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(3)
          .withEmbeddedGateway(true)
          .withPartitionsCount(1)
          .withReplicationFactor(3)
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
                        final var detector = systemPartition.getFailureDetector();
                        detector.setType(FailureDetectorType.SWIM);
                        detector.getSwim().setReportInterval(Duration.ofSeconds(1));
                        detector.getSwim().setReportTimeout(Duration.ofSeconds(3));
                        detector.getSwim().setDetectionWindow(DETECTION_WINDOW);
                        detector.getSwim().setRecoveryWindow(Duration.ofSeconds(4));
                      }))
          .build();

  @Test
  void shouldSeeEveryBrokerHealthyOnEveryBroker() {
    // when - then
    awaitEveryBrokerSeesEveryBrokerHealthy();
    Awaitility.await("every broker counts the reports of every broker")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(cluster.brokers().values())
                    .allSatisfy(
                        broker ->
                            assertThat(gauge(broker, FRESH_REPORTS_METRIC, null))
                                .describedAs("reports %s counts", broker.nodeId())
                                .hasValue(3.0)));
  }

  @Test
  void shouldReportStoppedBrokerGoneOnEveryOtherBrokerAfterTheDetectionWindow() {
    // given
    awaitEveryBrokerSeesEveryBrokerHealthy();
    final var stopped = broker("2");
    final var survivors = survivors(stopped);

    // when
    stopped.stop();

    // then - not before every survivor has not seen it for the detection window
    Awaitility.await("every survivor reports the stopped broker as dead")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(survivors)
                    .allSatisfy(
                        survivor -> {
                          assertThat(state(survivor, stopped)).hasValue(MemberLivenessState.DEAD);
                          assertThat(gauge(survivor, BROKER_GONE_METRIC, stopped))
                              .hasValueSatisfying(
                                  gone ->
                                      assertThat(gone)
                                          .isGreaterThanOrEqualTo(
                                              (double) DETECTION_WINDOW.toSeconds()));
                        }));

    // and the survivors still see each other
    assertThat(survivors)
        .allSatisfy(
            survivor ->
                survivors.forEach(
                    other ->
                        assertThat(state(survivor, other)).hasValue(MemberLivenessState.HEALTHY)));
  }

  @Test
  void shouldSeeTheBrokerHealthyAgainWhenItReturns() {
    // given
    awaitEveryBrokerSeesEveryBrokerHealthy();
    final var stopped = broker("2");
    final var survivors = survivors(stopped);
    stopped.stop();
    Awaitility.await("every survivor reports the stopped broker as dead")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(survivors)
                    .allSatisfy(
                        survivor ->
                            assertThat(state(survivor, stopped))
                                .hasValue(MemberLivenessState.DEAD)));

    // when
    stopped.start();

    // then
    Awaitility.await("every survivor sees the broker healthy again")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(survivors)
                    .allSatisfy(
                        survivor -> {
                          assertThat(state(survivor, stopped))
                              .hasValue(MemberLivenessState.HEALTHY);
                          assertThat(gauge(survivor, BROKER_GONE_METRIC, stopped)).hasValue(0.0);
                        }));
  }

  @Test
  void shouldNotDecideOnRegionFailoverInAClusterWithoutRegions() {
    // given
    awaitEveryBrokerSeesEveryBrokerHealthy();
    final var stopped = broker("2");
    final var survivors = survivors(stopped);

    // when - a broker is gone long enough to be reported dead, and then some
    stopped.stop();
    Awaitility.await("every survivor reports the stopped broker as dead")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(survivors)
                    .allSatisfy(
                        survivor ->
                            assertThat(state(survivor, stopped))
                                .hasValue(MemberLivenessState.DEAD)));
    Awaitility.await("the recovery window and more passed")
        .pollDelay(Duration.ofSeconds(10))
        .atMost(Duration.ofSeconds(10).plusSeconds(10))
        .until(() -> true);

    // then - failing over needs regions
    assertThat(survivors)
        .allSatisfy(
            survivor ->
                assertThat(
                        systemPartition(survivor)
                            .keyValueReader()
                            .get(SystemPartitionKeyType.REGION_FAILOVER, new RegionFailoverValue()))
                    .isEmpty());
  }

  private void awaitEveryBrokerSeesEveryBrokerHealthy() {
    Awaitility.await("every broker sees every broker healthy")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(cluster.brokers().values())
                    .allSatisfy(
                        observer ->
                            cluster
                                .brokers()
                                .values()
                                .forEach(
                                    observed ->
                                        assertThat(state(observer, observed))
                                            .describedAs(
                                                "%s sees %s", observer.nodeId(), observed.nodeId())
                                            .hasValue(MemberLivenessState.HEALTHY))));
  }

  private TestStandaloneBroker broker(final String id) {
    return cluster.brokers().get(MemberId.from(id));
  }

  private List<TestStandaloneBroker> survivors(final TestStandaloneBroker stopped) {
    return cluster.brokers().values().stream().filter(broker -> broker != stopped).toList();
  }

  private static SystemPartition systemPartition(final TestStandaloneBroker broker) {
    final var systemPartition = broker.bean(Broker.class).getBrokerContext().getSystemPartition();
    assertThat(systemPartition).describedAs("system partition of %s", broker.nodeId()).isNotNull();
    return systemPartition;
  }

  private static Optional<MemberLivenessState> state(
      final TestStandaloneBroker observer, final TestStandaloneBroker observed) {
    return gauge(observer, BROKER_STATE_METRIC, observed)
        .flatMap(
            value ->
                Arrays.stream(MemberLivenessState.values())
                    .filter(state -> state.code() == value.intValue())
                    .findFirst());
  }

  private static Optional<Double> gauge(
      final TestStandaloneBroker observer,
      final String metric,
      final TestStandaloneBroker observed) {
    final var search = observer.bean(MeterRegistry.class).find(metric);
    if (observed != null) {
      search.tag("broker", observed.nodeId().id());
    }
    return Optional.ofNullable(search.gauge()).map(Gauge::value);
  }
}
