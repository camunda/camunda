/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.management.cluster.BrokerState;
import io.camunda.zeebe.management.cluster.PartitionState;
import io.camunda.zeebe.management.cluster.PartitionStateCode;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.io.TempDir;

/**
 * Restores from a time range of backups in-process, i.e. triggered over a running broker's REST
 * endpoint while it is in {@code RECOVERING} mode. See {@link RdbmsRangeRestoreTestBase} for the
 * shared fixture and test cases, and {@link RdbmsRangeRestoreIT} for the standalone counterpart.
 */
final class InProcessRdbmsRangeRestoreIT extends RdbmsRangeRestoreTestBase {

  private static @TempDir Path backupDir;

  @Override
  protected Path backupDir() {
    return backupDir;
  }

  @Override
  void restoreFromTimeRange(final Interval interval) throws Exception {
    final var clusterActuator = enterRecovering();
    final long changeId;
    try (final var client = broker.newClientBuilder().build()) {
      changeId =
          InProcessRestoreTestUtil.triggerRestore(
              client, Map.of("from", interval.start().toString(), "to", interval.end().toString()));
    }
    awaitChangeCompletesAndBrokerActive(clusterActuator, changeId);
  }

  @Override
  void restoreWithoutArguments() throws Exception {
    final var clusterActuator = enterRecovering();
    final long changeId;
    try (final var client = broker.newClientBuilder().build()) {
      changeId = InProcessRestoreTestUtil.triggerRestore(client, Map.of());
    }
    awaitChangeCompletesAndBrokerActive(clusterActuator, changeId);
  }

  @Override
  void assertRestoreFailsForMissingBackup(final Interval interval) throws Exception {
    enterRecovering();
    try (final var client = broker.newClientBuilder().build()) {
      final var response =
          InProcessRestoreTestUtil.sendRestoreRequest(
              client, Map.of("from", interval.start().toString(), "to", interval.end().toString()));
      assertThat(response.statusCode())
          .describedAs("restore REST response: %s".formatted(response.body()))
          .isEqualTo(409);
      assertThat(response.body()).contains("No usable range found");
    }
  }

  private ClusterActuator enterRecovering() {
    final var clusterActuator = ClusterActuator.of(broker);
    final long toRecovering;
    try (final var client = broker.newClientBuilder().build()) {
      toRecovering = InProcessRestoreTestUtil.changeMode(client, "RECOVERING", false);
    }
    awaitChangeCompletes(clusterActuator, toRecovering, "broker transitions to RECOVERING");
    return clusterActuator;
  }

  private void awaitChangeCompletesAndBrokerActive(
      final ClusterActuator clusterActuator, final long changeId) {
    awaitChangeCompletes(clusterActuator, changeId, "restore change plan completes");

    Awaitility.await("broker reports ACTIVE again")
        .timeout(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              final var topology = clusterActuator.getTopology();
              assertThat(topology.getBrokers())
                  .flatExtracting(BrokerState::getPartitions)
                  .extracting(PartitionState::getState)
                  .allMatch(state -> state == PartitionStateCode.ACTIVE);
            });
  }

  /**
   * Awaits a cluster configuration change plan completing. The broker's system clock is pinned (see
   * {@code RdbmsRangeRestoreTestBase#configureBroker}), so the {@code
   * ClusterConfigurationManager}'s internal retry backoff (used e.g. while polling for a partition
   * to settle into its new role after a mode change) will not elapse on its own; this progresses
   * the clock on every poll so any such scheduled retry gets a chance to fire.
   */
  private void awaitChangeCompletes(
      final ClusterActuator clusterActuator, final long changeId, final String alias) {
    Awaitility.await(alias)
        .timeout(Duration.ofSeconds(90))
        .pollInterval(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              progressClock(broker, 3000);
              ClusterActuatorAssert.assertThat(clusterActuator)
                  .hasCompletedChanges(changeId)
                  .doesNotHavePendingChanges();
            });
  }
}
