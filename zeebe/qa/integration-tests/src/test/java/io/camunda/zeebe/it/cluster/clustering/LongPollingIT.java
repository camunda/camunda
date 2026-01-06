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
import io.camunda.zeebe.gateway.Loggers;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.qa.util.actuator.LoggersActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.event.Level;

@ZeebeIntegration
final class LongPollingIT {
  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(1)
          .withGatewaysCount(1)
          .withPartitionsCount(1)
          .withEmbeddedGateway(false)
          .withGatewayConfig(this::configureGateway)
          .build();

  @BeforeEach
  void beforeEach() {
    // set log level for long polling to trace to have more debugging info when the test fails
    for (final var gateway : cluster.gateways().values()) {
      LoggersActuator.of(gateway).set(Loggers.LONG_POLLING.getName(), Level.TRACE);
    }
  }

  @RegressionTest("https://github.com/camunda/camunda/issues/9658")
  void shouldActivateAndCompleteJobsInTime() {
    // given
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("foo"))
            .endEvent()
            .done();

    try (final var client = cluster.newClientBuilder().build()) {
      final var deploymentEvent =
          client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

      // when - send the ActivateJobs request first, before the process instance is created
      final var jobs =
          client
              .newActivateJobsCommand()
              .jobType("foo")
              .maxJobsToActivate(1)
              .requestTimeout(Duration.ofSeconds(30))
              .send();

      // wait until the first activate command before starting the process instance to ensure long
      // polling will come in effect
      RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATE)
          .findFirst()
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "Expected at least one job batch ACTIVATE command, but none found"));
      client
          .newCreateInstanceCommand()
          .processDefinitionKey(deploymentEvent.getProcesses().get(0).getProcessDefinitionKey())
          .requestTimeout(Duration.ofMinutes(1))
          .send()
          .join();

      // then - ensure that we tried to activate before the job was created, and that we activated
      // it AGAIN after it was created, without the client sending a new request
      assertThat(RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATE).limit(2).toList())
          .as("long polling should trigger a second ACTIVATE command without the client doing so")
          .extracting(Record::getValueType, Record::getIntent)
          .containsSubsequence(
              Tuple.tuple(ValueType.JOB_BATCH, JobBatchIntent.ACTIVATE),
              Tuple.tuple(ValueType.JOB_BATCH, JobBatchIntent.ACTIVATE));
      assertThat((CompletionStage<ActivateJobsResponse>) jobs)
          .succeedsWithin(30, TimeUnit.SECONDS)
          .extracting(ActivateJobsResponse::getJobs)
          .asList()
          .hasSize(1);
    }
  }

  private void configureGateway(final TestGateway<?> gateway) {
    gateway.withUnifiedConfig(cfg -> cfg.getApi().getLongPolling().setEnabled(true));
  }
}
