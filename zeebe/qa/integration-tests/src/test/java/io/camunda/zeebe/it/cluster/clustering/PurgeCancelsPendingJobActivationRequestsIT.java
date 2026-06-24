/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.response.ActivateJobsResponse;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that pending REST long-poll job activation requests are cancelled when a cluster purge
 * occurs. The purge increments the cluster incarnation number, which triggers the REST gateway to
 * fail all open long-poll requests with a "purge" error so that clients can reconnect cleanly.
 *
 * <p>The gRPC gateway does not need this handling since gRPC immediately detects client
 * disconnects.
 */
@ZeebeIntegration
final class PurgeCancelsPendingJobActivationRequestsIT {

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(1)
          .withGatewaysCount(1)
          .withPartitionsCount(1)
          .withEmbeddedGateway(false)
          .withGatewayConfig(
              g ->
                  g.withUnauthenticatedAccess()
                      .withUnifiedConfig(cfg -> cfg.getApi().getLongPolling().setEnabled(true)))
          .build();

  @BeforeEach
  void setUp() {
    RecordingExporter.reset();
  }

  @Test
  void shouldCancelPendingRestLongPollRequestsOnPurge() {
    try (final var client = cluster.newClientBuilder().preferRestOverGrpc(true).build()) {
      // given — send a long-poll activate request for a job type that will never have jobs;
      // the request timeout is long enough that it will remain pending without the purge
      final CompletableFuture<ActivateJobsResponse> pendingActivation =
          client
              .newActivateJobsCommand()
              .jobType("rest-purge-test")
              .maxJobsToActivate(1)
              .requestTimeout(Duration.ofMinutes(5))
              .send()
              .toCompletableFuture();

      // wait until the long-poll ACTIVATE command has reached the broker, confirming that the
      // gateway's long-poll handler is engaged and the request is queued
      RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATE)
          .findFirst()
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "Expected at least one job batch ACTIVATE command, but none found"));

      // when — trigger a cluster purge, which increments the incarnation number
      final var actuator = ClusterActuator.of(cluster.availableGateway());
      final var purgeResponse = actuator.purge(false);

      // wait for the purge to complete so the incarnation change fully propagates
      Awaitility.await("purge completes")
          .timeout(Duration.ofMinutes(2))
          .untilAsserted(
              () -> ClusterActuatorAssert.assertThat(cluster).hasAppliedChanges(purgeResponse));

      // then — the pending request should fail with a purge-related error
      assertThat((CompletionStage<ActivateJobsResponse>) pendingActivation)
          .failsWithin(30, TimeUnit.SECONDS)
          .withThrowableThat()
          .withMessageContaining("purge");
    }
  }
}
