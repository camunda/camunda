/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.systempartition;

import static io.camunda.zeebe.it.cluster.systempartition.SwimRegionClusterSupport.REGION_A;
import static io.camunda.zeebe.it.cluster.systempartition.SwimRegionClusterSupport.TIMEOUT;
import static io.camunda.zeebe.it.cluster.systempartition.SwimRegionClusterSupport.regionFailover;
import static io.camunda.zeebe.it.cluster.systempartition.SwimRegionClusterSupport.regionState;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.system.syspartition.detector.RegionState;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a region that is detected as gone, but comes back within the recovery window, is
 * not failed over from. The recovery window is long enough to restart the region in it.
 */
@ZeebeIntegration
final class SystemPartitionSwimTransientOutageIT {
  private static final Duration RECOVERY_WINDOW = Duration.ofSeconds(60);

  @TestZeebe(awaitCompleteTopology = false)
  private final TestCluster cluster = SwimRegionClusterSupport.cluster(RECOVERY_WINDOW);

  private SwimRegionClusterSupport support;

  @BeforeEach
  void setUp() {
    support = new SwimRegionClusterSupport(cluster);
  }

  @Test
  void shouldNotFailOverFromRegionThatComesBackWithinTheRecoveryWindow() {
    // given - region A is detected as gone: every survivor reports it as failed
    support.awaitEveryRegionActive(support.systemPartitionBrokers());
    final var survivors = support.survivorsOfRegionA();
    support.regionA().forEach(TestStandaloneBroker::stop);
    Awaitility.await("every survivor detects region A as gone")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(survivors)
                    .allSatisfy(
                        survivor ->
                            assertThat(regionState(survivor, REGION_A))
                                .isEqualTo(RegionState.FAILED)));

    // when - it comes back before the recovery window is over
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

    // then - the recovery window that detection started would have passed by now, and no failover
    // was decided
    Awaitility.await("the recovery window passed")
        .pollDelay(RECOVERY_WINDOW)
        .atMost(RECOVERY_WINDOW.plusSeconds(10))
        .until(() -> true);
    assertThat(survivors).allSatisfy(survivor -> assertThat(regionFailover(survivor)).isEmpty());
  }
}
