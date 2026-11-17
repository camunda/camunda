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
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProcessDefinitionDeletionScanTest {

  private static final int DEPLOYMENT_PARTITION = 1;
  private static final long KEY = 42L;

  private final ProcessDefinitionDeletionScan scan =
      new ProcessDefinitionDeletionScan(DEPLOYMENT_PARTITION);

  private static DefinitionInfo info(final long key) {
    return new DefinitionInfo(key, "order-process", 3, "<default>");
  }

  @Test
  void shouldFlagDefinitionDrainingWithoutCoordination() {
    // given - draining on partitions 2 and 3, but P1 tracks no pending deletion for either: the
    // deletion coordination was never set up, so it can never finish
    final var partitionStates =
        Map.of(
            1, Map.<Long, PersistedProcessState>of(),
            2, Map.of(KEY, DRAINING),
            3, Map.of(KEY, DRAINING));

    // when
    final var findings = scan.scan(partitionStates, Map.of(KEY, info(KEY)), Map.of(), Set.of());

    // then
    assertThat(findings)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.definition().processDefinitionKey()).isEqualTo(KEY);
              assertThat(f.drainingPartitions()).containsExactly(2, 3);
              assertThat(f.uncoordinatedPartitions()).containsExactly(2, 3);
              assertThat(f.orphanedInstances()).isFalse();
            });
  }

  @Test
  void shouldNotFlagHealthyInProgressDrain() {
    // given - P1 has already finalized locally (removed the definition from its own state) while
    // partition 2 is still DRAINING; the pending coordination entry for partition 2 still exists,
    // so the deletion is on track and must not be reported
    final var partitionStates =
        Map.of(
            1, Map.<Long, PersistedProcessState>of(),
            2, Map.of(KEY, DRAINING));

    // when
    final var findings =
        scan.scan(partitionStates, Map.of(KEY, info(KEY)), Map.of(KEY, Set.of(2)), Set.of());

    // then
    assertThat(findings).isEmpty();
  }

  @Test
  void shouldFlagOnlyThePartitionsMissingCoordination() {
    // given - draining on 2 and 3; P1 still tracks partition 2 (healthy) but not partition 3
    // (stuck)
    final var partitionStates =
        Map.of(
            1, Map.<Long, PersistedProcessState>of(),
            2, Map.of(KEY, DRAINING),
            3, Map.of(KEY, DRAINING));

    // when
    final var findings =
        scan.scan(partitionStates, Map.of(KEY, info(KEY)), Map.of(KEY, Set.of(2)), Set.of());

    // then
    assertThat(findings)
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.drainingPartitions()).containsExactly(2, 3);
              assertThat(f.uncoordinatedPartitions()).containsExactly(3);
            });
  }

  @Test
  void shouldReportOrphanedInstancesWhenPresent() {
    // given - a stuck definition that still has active instances somewhere in the cluster
    final var partitionStates =
        Map.of(
            1, Map.<Long, PersistedProcessState>of(),
            2, Map.of(KEY, DRAINING));

    // when
    final var findings = scan.scan(partitionStates, Map.of(KEY, info(KEY)), Map.of(), Set.of(KEY));

    // then
    assertThat(findings).singleElement().satisfies(f -> assertThat(f.orphanedInstances()).isTrue());
  }

  @Test
  void shouldNotFlagActiveDefinitionThatIsNotDraining() {
    // given - the definition is ACTIVE, not DRAINING, so it is not being deleted at all
    final var partitionStates =
        Map.of(
            1, Map.of(KEY, ACTIVE),
            2, Map.of(KEY, ACTIVE));

    // when
    final var findings = scan.scan(partitionStates, Map.of(), Map.of(), Set.of());

    // then
    assertThat(findings).isEmpty();
  }

  @Test
  void shouldFlagOnlyTheStuckDefinitionAmongMany() {
    // given - key 42 stuck (draining on 2, no coordination); key 99 healthy (draining on 2,
    // tracked)
    final var partitionStates =
        Map.of(
            1, Map.<Long, PersistedProcessState>of(),
            2, Map.of(42L, DRAINING, 99L, DRAINING));

    // when
    final var findings =
        scan.scan(
            partitionStates,
            Map.of(42L, info(42L), 99L, info(99L)),
            Map.of(99L, Set.of(2)),
            Set.of());

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
    assertThatThrownBy(() -> scan.scan(partitionStates, Map.of(KEY, info(KEY)), Map.of(), Set.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("deployment partition");
  }
}
