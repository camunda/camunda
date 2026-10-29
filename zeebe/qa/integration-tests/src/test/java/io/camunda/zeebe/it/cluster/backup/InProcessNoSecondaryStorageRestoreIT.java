/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.protocol.rest.RestoreBrokerStatus;
import io.camunda.client.protocol.rest.RestorePartitionStatus;
import io.camunda.client.protocol.rest.RestorePartitionStatus.StateEnum;
import io.camunda.zeebe.management.cluster.BrokerState;
import io.camunda.zeebe.management.cluster.PartitionState;
import io.camunda.zeebe.management.cluster.PartitionStateCode;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.io.TempDir;

/**
 * Restores without a secondary storage in-process, i.e. triggered over a running broker's REST
 * endpoint while it is in {@code RECOVERING} mode. See {@link NoSecondaryStorageRestoreTestBase}
 * for the shared fixture and test cases, and {@link NoSecondaryStorageRestoreIT} for the standalone
 * counterpart.
 */
final class InProcessNoSecondaryStorageRestoreIT extends NoSecondaryStorageRestoreTestBase {

  private static @TempDir Path backupDir;

  @Override
  protected Path backupDir() {
    return backupDir;
  }

  @Override
  void restore(final RestoreRequest request) {
    final var clusterActuator = enterRecovering();
    final long changeId;
    try (final var client = broker.newClientBuilder().build()) {
      changeId = InProcessRestoreTestUtil.triggerRestore(client, requestBody(request));
    }
    awaitChangeCompletes(clusterActuator, changeId, "restore change plan completes");
    Awaitility.await("broker reports ACTIVE again")
        .timeout(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThat(clusterActuator.getTopology().getBrokers())
                    .flatExtracting(BrokerState::getPartitions)
                    .extracting(PartitionState::getState)
                    .allMatch(state -> state == PartitionStateCode.ACTIVE));
  }

  /**
   * The validation's exceptions map to REST problems: an invalid argument to a 400, an invalid
   * state, e.g. backups that cannot satisfy the request, to a 409.
   */
  @Override
  void assertRestoreRejected(
      final RestoreRequest request,
      final Class<? extends RuntimeException> expectedType,
      final String message) {
    final var expectedStatus = expectedType == IllegalArgumentException.class ? 400 : 409;
    enterRecovering();
    try (final var client = broker.newClientBuilder().build()) {
      final var response =
          InProcessRestoreTestUtil.sendRestoreRequest(client, requestBody(request));
      assertThat(response.statusCode())
          .describedAs("restore REST response: %s".formatted(response.body()))
          .isEqualTo(expectedStatus);
      assertThat(response.body()).contains(message);
    }
  }

  /** A partition whose restore fails is retried rather than reported, so it stays restoring. */
  @Override
  void assertRestoreNotApplied(final RestoreRequest request, final String message) {
    final var clusterActuator = enterRecovering();
    try (final var client = broker.newClientBuilder().build()) {
      final var changeId = InProcessRestoreTestUtil.triggerRestore(client, requestBody(request));
      Awaitility.await("a partition keeps retrying its restore")
          .during(Duration.ofSeconds(20))
          .atMost(Duration.ofSeconds(40))
          .pollInterval(Duration.ofSeconds(2))
          .untilAsserted(
              () -> {
                progressClock(broker, 3000);
                assertThat(InProcessRestoreTestUtil.getRestoreStatus(client).getBrokers())
                    .flatExtracting(RestoreBrokerStatus::getPartitions)
                    .extracting(RestorePartitionStatus::getState)
                    .contains(StateEnum.RESTORING);
                assertThat(clusterActuator.getTopology().getPendingChange())
                    .describedAs("the restore change is still pending")
                    .isNotNull()
                    .satisfies(change -> assertThat(change.getId()).isEqualTo(changeId));
              });
    }
  }

  private static Map<String, Object> requestBody(final RestoreRequest request) {
    final Map<String, Object> body = new HashMap<>();
    if (!request.backupIds().isEmpty()) {
      body.put("backupIds", request.backupIds());
    }
    if (request.from() != null) {
      body.put("from", request.from().toString());
    }
    if (request.to() != null) {
      body.put("to", request.to().toString());
    }
    return body;
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

  /**
   * The broker's clock is pinned, so a retry backoff of the cluster configuration manager only
   * elapses when the clock is moved on, which every poll here does.
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
