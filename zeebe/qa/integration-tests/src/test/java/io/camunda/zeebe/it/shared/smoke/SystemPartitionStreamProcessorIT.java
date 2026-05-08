/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.shared.smoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.systempartition.SystemPartition;
import java.time.Duration;

/**
 * Smoke test for the system-partition StreamProcessor.
 *
 * <p>Phase 7 minimum scope: verifies that a broker started with {@code
 * experimental.systemPartition.enabled=true} reaches a steady state with the system partition
 * leader-elected. Full end-to-end coverage (scale-up via BPMN, backup via BPMN) is deferred — see
 * plan tasks 31, 32 for the deferred specs.
 */
@ZeebeIntegration
final class SystemPartitionStreamProcessorIT {

  @TestZeebe
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withUnauthenticatedAccess()
          .withProperty("zeebe.broker.experimental.systemPartition.enabled", true);

  /**
   * Verifies that the broker starts cleanly with the system partition enabled and that the
   * single-node replica elects itself leader within 30 seconds.
   *
   * <p>This is a startup-level smoke test: the {@code @TestZeebe} harness starts the broker before
   * the test body runs; any exception during startup fails the test implicitly. The Awaitility
   * assertion then confirms that {@link SystemPartitionStep} ran, the StreamProcessor opened,
   * EngineProcessors registered, BPMN auto-deployer fired, and the Raft replica reached the LEADER
   * role.
   */
  @SmokeTest
  void shouldStartSystemPartition() {
    // given — broker started by the @TestZeebe harness with systemPartition.enabled=true

    // when — system partition was bootstrapped during broker startup

    // then — the facade must be non-null and report isLeader() == true within 30 s
    await("system partition to become leader")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final SystemPartition systemPartition = broker.bean(SystemPartition.class);
              assertThat(systemPartition)
                  .as("SystemPartition bean should be present in the Spring context")
                  .isNotNull();
              assertThat(systemPartition.isLeader())
                  .as("Single-node system partition should have elected itself leader")
                  .isTrue();
            });
  }
}
