/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.systempartition;

import static io.camunda.zeebe.it.cluster.systempartition.SwimRegionClusterSupport.CONTROL;
import static io.camunda.zeebe.it.cluster.systempartition.SwimRegionClusterSupport.DETECTION_WINDOW;
import static io.camunda.zeebe.it.cluster.systempartition.SwimRegionClusterSupport.REGION_A;
import static io.camunda.zeebe.it.cluster.systempartition.SwimRegionClusterSupport.REGION_B;
import static io.camunda.zeebe.it.cluster.systempartition.SwimRegionClusterSupport.TIMEOUT;
import static io.camunda.zeebe.it.cluster.systempartition.SwimRegionClusterSupport.failoverMetric;
import static io.camunda.zeebe.it.cluster.systempartition.SwimRegionClusterSupport.regionFailover;
import static io.camunda.zeebe.it.cluster.systempartition.SwimRegionClusterSupport.regionState;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.system.syspartition.detector.RegionState;
import io.camunda.zeebe.broker.system.syspartition.detector.swim.GoneBroker;
import io.camunda.zeebe.qa.util.actuator.PrometheusActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies region failure detection from SWIM on a 3x3x1 cluster: when every broker of a data
 * region is gone, the system partition leader detects it after the detection window, waits the
 * recovery window for the region to come back, and then commits a region failover to the other data
 * region with the brokers that were out of sight and for how long.
 */
@ZeebeIntegration
final class SystemPartitionSwimRegionFailoverIT {
  private static final Duration RECOVERY_WINDOW = Duration.ofSeconds(4);

  @TestZeebe(awaitCompleteTopology = false)
  private final TestCluster cluster = SwimRegionClusterSupport.cluster(RECOVERY_WINDOW);

  private SwimRegionClusterSupport support;

  @BeforeEach
  void setUp() {
    support = new SwimRegionClusterSupport(cluster);
  }

  @Test
  void shouldCommitRegionFailoverWithEvidenceWhenEveryBrokerOfDataRegionIsGone() {
    // given
    support.awaitEveryRegionActive(support.systemPartitionBrokers());
    final var survivors = support.survivorsOfRegionA();

    // when
    support.regionA().forEach(TestStandaloneBroker::stop);

    // then - every survivor applies the same failover
    final var failover = support.awaitRegionFailover(survivors, 0);
    assertThat(failover.getFailedRegion()).isEqualTo(REGION_A);
    assertThat(failover.getTargetRegion()).isEqualTo(REGION_B);
    assertThat(failover.getLeaderId()).isIn("region-b_0", "region-b_1", "control_0");
    assertThat(failover.getTerm()).isPositive();

    // and it names the brokers that were out of sight, also the one not running the system
    // partition, and for how long: the detection window plus the recovery window, less the age of
    // the observers' latest report
    final var minGoneMillis = DETECTION_WINDOW.plus(RECOVERY_WINDOW).minusSeconds(1).toMillis();
    assertThat(failover.getGoneBrokers())
        .extracting(GoneBroker::memberId)
        .containsExactly("region-a_0", "region-a_1", "region-a_2");
    assertThat(failover.getGoneBrokers())
        .allSatisfy(
            gone -> {
              assertThat(gone.goneForMillis()).isGreaterThanOrEqualTo(minGoneMillis);
              assertThat(gone.confirmations()).isGreaterThanOrEqualTo(2);
            });

    // and the leader waited the recovery window between detecting the region and acting
    assertThat(failover.getDecidedAtMillis() - failover.getDetectedAtMillis())
        .isGreaterThanOrEqualTo(RECOVERY_WINDOW.toMillis() - 500);

    // and every survivor reports region A as failed, and when the failover was decided
    Awaitility.await("every survivor reports region A failed and the failover")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(survivors)
                    .allSatisfy(
                        survivor -> {
                          assertThat(regionState(survivor, REGION_A)).isEqualTo(RegionState.FAILED);
                          assertThat(failoverMetric(survivor))
                              .hasValue((double) (failover.getDecidedAtMillis() / 1000));
                        }));

    // and the series the Zeebe dashboard queries are exported
    assertThat(PrometheusActuator.of(survivors.getFirst()).metrics())
        .contains(
            "zeebe_system_partition_swim_detector_broker_state{",
            "zeebe_system_partition_swim_detector_broker_gone_duration{",
            "zeebe_system_partition_swim_detector_region_state{",
            "zeebe_system_partition_swim_detector_region_failover{",
            "zeebe_system_partition_swim_detector_reports_fresh");

    // and the failover is committed once, later reports leave it unchanged
    Awaitility.await("more reports are exchanged")
        .pollDelay(Duration.ofSeconds(10))
        .atMost(Duration.ofSeconds(10).plusSeconds(10))
        .until(() -> true);
    assertThat(regionFailover(survivors.getFirst()))
        .hasValueSatisfying(
            value ->
                assertThat(value.getDecidedAtMillis()).isEqualTo(failover.getDecidedAtMillis()));
  }

  @Test
  void shouldCommitAnotherRegionFailoverWhenTheRegionIsGoneAgain() {
    // given - region A failed over, and then came back
    support.awaitEveryRegionActive(support.systemPartitionBrokers());
    final var survivors = support.survivorsOfRegionA();
    support.regionA().forEach(TestStandaloneBroker::stop);
    final var first = support.awaitRegionFailover(survivors, 0);
    support.regionA().forEach(TestStandaloneBroker::start);
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
    support.regionA().forEach(TestStandaloneBroker::stop);

    // then - a failover decided after the first replaces it
    final var second = support.awaitRegionFailover(survivors, first.getDecidedAtMillis());
    assertThat(second.getFailedRegion()).isEqualTo(REGION_A);
    assertThat(second.getTargetRegion()).isEqualTo(REGION_B);
  }

  @Test
  void shouldNotFailOverWhileDataRegionHasABrokerSeen() {
    // given
    support.awaitEveryRegionActive(support.systemPartitionBrokers());
    final var stopped = support.broker(REGION_A, 0);

    // when
    stopped.stop();

    // then - region A is suspect, never failed, and no failover is decided
    final var observers =
        support.systemPartitionBrokers().stream().filter(broker -> broker != stopped).toList();
    Awaitility.await("every other broker reports region A as suspect")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(observers)
                    .allSatisfy(
                        observer ->
                            assertThat(regionState(observer, REGION_A))
                                .isEqualTo(RegionState.SUSPECT)));
    Awaitility.await("the detection and recovery windows passed twice over")
        .pollDelay(DETECTION_WINDOW.plus(RECOVERY_WINDOW).multipliedBy(2))
        .atMost((DETECTION_WINDOW.plus(RECOVERY_WINDOW).multipliedBy(2)).plusSeconds(10))
        .until(() -> true);
    assertThat(observers)
        .allSatisfy(
            observer -> {
              assertThat(regionFailover(observer)).isEmpty();
              assertThat(regionState(observer, REGION_A)).isEqualTo(RegionState.SUSPECT);
            });
  }

  @Test
  void shouldNotTreatTheControlRegionAsADataRegion() {
    // given
    support.awaitEveryRegionActive(support.systemPartitionBrokers());
    final var control = support.broker(CONTROL, 0);
    final var observers = List.of(support.broker(REGION_A, 0), support.broker(REGION_B, 0));

    // when - the control broker is gone, which leaves a quorum of 4 of the 5 system partition
    // brokers
    control.stop();

    // then - it is reported as a failed region, but there is no data region to fail over from
    Awaitility.await("the observers report the control region as failed")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(observers)
                    .allSatisfy(
                        observer ->
                            assertThat(regionState(observer, CONTROL))
                                .isEqualTo(RegionState.FAILED)));
    Awaitility.await("the recovery window passed")
        .pollDelay(RECOVERY_WINDOW.plusSeconds(6))
        .atMost(RECOVERY_WINDOW.plusSeconds(6).plusSeconds(10))
        .until(() -> true);
    assertThat(observers).allSatisfy(observer -> assertThat(regionFailover(observer)).isEmpty());
  }
}
