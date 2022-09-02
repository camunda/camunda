/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.api.response.ActivateJobsResponse;
import io.camunda.zeebe.gateway.Loggers;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.process.test.assertions.BpmnAssert;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.qa.util.actuator.LoggersActuator;
import io.camunda.zeebe.qa.util.testcontainers.ContainerLogsDumper;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.test.util.record.RecordStream;
import io.zeebe.containers.ZeebeGatewayNode;
import io.zeebe.containers.ZeebePort;
import io.zeebe.containers.cluster.ZeebeCluster;
import io.zeebe.containers.engine.ContainerEngine;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.StreamSupport;
import org.agrona.CloseHelper;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
final class LongPollingIT {
  private final Network network = Network.newNetwork();
  private final ZeebeCluster cluster =
      ZeebeCluster.builder()
          .withBrokersCount(1)
          .withGatewaysCount(1)
          .withPartitionsCount(1)
          .withEmbeddedGateway(false)
          .withImage(ZeebeTestContainerDefaults.defaultTestImage())
          .withGatewayConfig(this::configureGateway)
          .build();

  @SuppressWarnings("unused")
  @RegisterExtension
  final ContainerLogsDumper logsWatcher = new ContainerLogsDumper(cluster::getNodes);

  @Container
  private final ContainerEngine engine =
      ContainerEngine.builder().withCluster(cluster).withIdlePeriod(Duration.ofSeconds(5)).build();

  @BeforeEach
  void beforeEach() {
    // set log level for long polling to trace to have more debugging info when the test fails
    for (final var gateway : cluster.getGateways().values()) {
      LoggersActuator.of(gateway).set(Loggers.LONG_POLLING.getName(), Level.TRACE);
    }
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(network);
  }

  // regression test of https://github.com/camunda/zeebe/issues/9658
  @RepeatedTest(100)
  void shouldActivateAndCompleteJobsInTime() throws InterruptedException, TimeoutException {
    // given
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("foo"))
            .endEvent()
            .done();

    try (final var client = engine.createClient()) {
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
      engine.waitForIdleState(Duration.ofSeconds(30));
      records()
          .withIntent(JobBatchIntent.ACTIVATE)
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
      engine.waitForIdleState(Duration.ofSeconds(30));
      assertThat(records())
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

  @SuppressWarnings("unchecked")
  private RecordStream records() {
    return new RecordStream(
        StreamSupport.stream(BpmnAssert.getRecordStream().records().spliterator(), false)
            .map(r -> (Record<RecordValue>) r));
  }

  private void configureGateway(final ZeebeGatewayNode<?> gateway) {
    gateway
        .withEnv("ZEEBE_GATEWAY_LONGPOLLING_ENABLED", "true")
        // https://github.com/camunda-community-hub/zeebe-test-container/issues/332
        .addExposedPort(ZeebePort.MONITORING.getPort());
  }
}
