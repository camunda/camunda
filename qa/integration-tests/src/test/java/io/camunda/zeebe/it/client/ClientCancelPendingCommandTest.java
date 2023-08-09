/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.gateway.metrics.LongPollingMetrics;
import io.camunda.zeebe.it.clustering.ClusteringRuleExtension;
import io.camunda.zeebe.qa.util.jobstream.JobStreamServiceAssert;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class ClientCancelPendingCommandTest {
  @RegisterExtension
  private static final ClusteringRuleExtension CLUSTER =
      new ClusteringRuleExtension(1, 1, 1, cfg -> {});

  private final ZeebeClient client = CLUSTER.getClient();

  @Test
  void shouldCancelCommandOnFutureCancellation() {
    // given
    final var future =
        client
            .newActivateJobsCommand()
            .jobType("type")
            .maxJobsToActivate(10)
            .requestTimeout(Duration.ofHours(1))
            .send();
    final var metrics = new LongPollingMetrics();
    Awaitility.await("until we have one polling client")
        .untilAsserted(() -> assertThat(metrics.getBlockedRequestsCount("type")).isOne());

    // when - create some jobs after cancellation; the notification will trigger long polling to
    // remove cancelled requests. unfortunately we can't tell when cancellation is finished
    future.cancel(true);

    // then
    Awaitility.await("until no long polling clients are waiting")
        .untilAsserted(() -> assertThat(metrics.getBlockedRequestsCount("type")).isZero());
  }

  @Test
  void shouldRemoveStreamOnCancel() {
    // given
    final var uniqueWorkerName = UUID.randomUUID().toString();
    final var stream =
        client
            .newStreamJobsCommand()
            .jobType("jobs")
            .consumer(ignored -> {})
            .workerName(uniqueWorkerName)
            .send();

    // when
    awaitStreamRegistered(uniqueWorkerName);
    stream.cancel(true);

    // then
    awaitStreamRemoved(uniqueWorkerName);
  }

  private void awaitStreamRegistered(final String workerName) {
    final var brokerBridge = CLUSTER.getBrokerBridge(0);
    final var jobStreamService = brokerBridge.getJobStreamService().orElseThrow();

    Awaitility.await("until a stream with the worker name '%s' is registered".formatted(workerName))
        .untilAsserted(
            () ->
                JobStreamServiceAssert.assertThat(jobStreamService)
                    .hasStreamWithWorker(1, workerName));
  }

  private void awaitStreamRemoved(final String workerName) {
    final var brokerBridge = CLUSTER.getBrokerBridge(0);
    final var jobStreamService = brokerBridge.getJobStreamService().orElseThrow();

    Awaitility.await("until no stream with worker name '%s' is registered".formatted(workerName))
        .untilAsserted(
            () ->
                JobStreamServiceAssert.assertThat(jobStreamService)
                    .doesNotHaveStreamWithWorker(workerName));
  }
}
