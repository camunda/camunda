/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.client;

import static io.camunda.zeebe.gateway.metrics.LongPollingMetricsDoc.RequestsQueuedKeyNames.TYPE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.gateway.metrics.LongPollingMetrics.LongPollingMetricsDoc;
import io.camunda.zeebe.qa.util.actuator.JobStreamActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.jobstream.JobStreamActuatorAssert;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@AutoCloseResources
@ZeebeIntegration
final class ClientCancelPendingCommandTest {
  @TestZeebe(initMethod = "initTestStandaloneBroker")
  private static TestStandaloneBroker zeebe;

  @AutoCloseResource private final ZeebeClient client = zeebe.newClientBuilder().build();

  @SuppressWarnings("unused")
  static void initTestStandaloneBroker() {
    zeebe = new TestStandaloneBroker();
  }

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
    final var registry = zeebe.bean(MeterRegistry.class);
    final var blockedRequestCount = LongPollingMetricsDoc.REQUESTS_QUEUED_CURRENT;
    Awaitility.await("until we have one polling client")
        .untilAsserted(
            () ->
                assertThat(
                        registry
                            .get(blockedRequestCount.getName())
                            .tag(TYPE.asString(), "type")
                            .gauge())
                    .returns(1.0, Gauge::value));

    // when - create some jobs after cancellation; the notification will trigger long polling to
    // remove cancelled requests. unfortunately we can't tell when cancellation is finished
    future.cancel(true);

    // then
    Awaitility.await("until no long polling clients are waiting")
        .untilAsserted(
            () ->
                assertThat(
                        registry
                            .get(blockedRequestCount.getName())
                            .tag(TYPE.asString(), "type")
                            .gauge())
                    .returns(0.0, Gauge::value));
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
    final var actuator = JobStreamActuator.of(zeebe);
    Awaitility.await("until a stream with the worker name '%s' is registered".formatted(workerName))
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(actuator)
                    .remoteStreams()
                    .haveWorker(1, workerName));
  }

  private void awaitStreamRemoved(final String workerName) {
    final var actuator = JobStreamActuator.of(zeebe);
    Awaitility.await("until no stream with worker name '%s' is registered".formatted(workerName))
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(actuator)
                    .remoteStreams()
                    .doNotHaveWorker(workerName));
  }
}
