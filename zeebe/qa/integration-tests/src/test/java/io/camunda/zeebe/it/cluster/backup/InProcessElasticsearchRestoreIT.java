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
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Restores with Elasticsearch as the secondary storage in-process, i.e. triggered over a running
 * broker's REST endpoint while it is in {@code RECOVERING} mode. See {@link
 * ElasticsearchRestoreTestBase} for the shared fixture and test cases, and {@link
 * ElasticsearchRestoreIT} for the standalone counterpart.
 */
@Testcontainers
final class InProcessElasticsearchRestoreIT extends ElasticsearchRestoreTestBase {

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH =
      TestSearchContainers.createDefaultElasticsearchContainer();

  private static @TempDir Path backupDir;

  @Override
  protected Path backupDir() {
    return backupDir;
  }

  @Override
  protected String elasticsearchUrl() {
    return "http://" + ELASTICSEARCH.getHttpHostAddress();
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
   * The validation's exceptions map to REST problems: an invalid argument to a 400, a missing
   * backup to a 404, an invalid state, e.g. backups that cannot satisfy the request, to a 409.
   */
  @Override
  void assertRestoreRejected(
      final RestoreRequest request,
      final Class<? extends RuntimeException> expectedType,
      final String message) {
    final var expectedStatus =
        expectedType == IllegalArgumentException.class
            ? 400
            : expectedType == NoSuchElementException.class ? 404 : 409;
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

  private static void awaitChangeCompletes(
      final ClusterActuator clusterActuator, final long changeId, final String alias) {
    Awaitility.await(alias)
        .timeout(Duration.ofMinutes(2))
        .untilAsserted(
            () ->
                ClusterActuatorAssert.assertThat(clusterActuator)
                    .hasCompletedChanges(changeId)
                    .doesNotHavePendingChanges());
  }
}
