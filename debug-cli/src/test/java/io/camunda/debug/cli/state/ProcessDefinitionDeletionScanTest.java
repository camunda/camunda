/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.state;

import static io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState.ACTIVE;
import static io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState.DRAINING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.debug.cli.state.ProcessDefinitionDeletionScan.DefinitionInfo;
import io.camunda.zeebe.engine.state.deployment.PersistedProcess.PersistedProcessState;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProcessDefinitionDeletionScanTest {

  private static final int DEPLOYMENT_PARTITION = 1;
  private static final long KEY = 42L;

  private final ProcessDefinitionDeletionScan scan =
      new ProcessDefinitionDeletionScan(DEPLOYMENT_PARTITION);

  private static DefinitionInfo info(final long key) {
    return new DefinitionInfo(key, "order-process", 3, "<default>", true);
  }

  @Test
  void shouldFlagDefinitionDrainingWhileAbsentFromDeploymentPartition() {
    // given - draining on partitions 2 and 3, already gone from partitions 1 (deployment) and 4
    final var partitionStates =
        Map.of(
            1, Map.<Long, PersistedProcessState>of(),
            2, Map.of(KEY, DRAINING),
            3, Map.of(KEY, DRAINING),
            4, Map.<Long, PersistedProcessState>of());

    // when
    final var findings = scan.scan(partitionStates, Map.of(KEY, info(KEY)));

    // then
    assertThat(findings)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.definition().processDefinitionKey()).isEqualTo(KEY);
              assertThat(f.drainingPartitions()).containsExactly(2, 3);
              assertThat(f.absentPartitions()).containsExactly(1, 4);
            });
  }

  @Test
  void shouldNotFlagDefinitionStillPresentOnDeploymentPartition() {
    // given - draining everywhere, including the deployment partition: a normal in-progress drain
    final var partitionStates =
        Map.of(
            1, Map.of(KEY, DRAINING),
            2, Map.of(KEY, DRAINING));

    // when
    final var findings = scan.scan(partitionStates, Map.of(KEY, info(KEY)));

    // then
    assertThat(findings).isEmpty();
  }

  @Test
  void shouldNotFlagWhenDeploymentPartitionStillHasItActive() {
    // given - deployment partition still holds the definition (here ACTIVE), so it was not deleted
    final var partitionStates =
        Map.of(
            1, Map.of(KEY, ACTIVE),
            2, Map.of(KEY, DRAINING));

    // when
    final var findings = scan.scan(partitionStates, Map.of(KEY, info(KEY)));

    // then
    assertThat(findings).isEmpty();
  }

  @Test
  void shouldNotFlagActiveStragglerWithNoDraining() {
    // given - definition gone from the deployment partition but the straggler is ACTIVE, not
    // DRAINING; without a draining partition there is nothing stranded to reconcile. Such a
    // definition is not reported DRAINING anywhere, so it never enters the metadata map.
    final var partitionStates =
        Map.of(
            1, Map.<Long, PersistedProcessState>of(),
            2, Map.of(KEY, ACTIVE));

    // when
    final var findings = scan.scan(partitionStates, Map.of());

    // then
    assertThat(findings).isEmpty();
  }

  @Test
  void shouldFlagOnlyTheStrandedDefinitionAmongMany() {
    // given - key 42 stranded (draining on 2, gone from 1); key 99 healthy (draining everywhere)
    final var partitionStates =
        Map.of(
            1, Map.of(99L, DRAINING),
            2, Map.of(42L, DRAINING, 99L, DRAINING));

    // when
    final var findings = scan.scan(partitionStates, Map.of(42L, info(42L), 99L, info(99L)));

    // then
    assertThat(findings)
        .singleElement()
        .satisfies(f -> assertThat(f.definition().processDefinitionKey()).isEqualTo(42L));
  }

  @Test
  void shouldThrowWhenDeploymentPartitionStateIsMissing() {
    // given - no state for the deployment partition
    final var partitionStates = Map.of(2, Map.of(KEY, DRAINING));

    // when / then
    assertThatThrownBy(() -> scan.scan(partitionStates, Map.of(KEY, info(KEY))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("deployment partition");
  }
}
