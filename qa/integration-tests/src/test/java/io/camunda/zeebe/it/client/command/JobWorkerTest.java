/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.it.clustering.ClusteringRuleExtension;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.it.util.RecordingJobHandler;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import java.util.Map;
import org.assertj.core.data.Offset;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class JobWorkerTest {

  @SuppressWarnings("JUnitMalformedDeclaration")
  @RegisterExtension
  private static final ClusteringRuleExtension CLUSTER = new ClusteringRuleExtension(1, 1, 1);

  private static GrpcClientRule client;

  private final String jobType = Strings.newRandomValidBpmnId();

  @BeforeAll
  static void beforeAll() {
    client = new GrpcClientRule(CLUSTER.getClient());
  }

  @Test
  void shouldActivateJob() {
    // given
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(jobType)
                        .zeebeJobRetries("5")
                        .zeebeTaskHeader("x", "1")
                        .zeebeTaskHeader("y", "2"))
            .done();
    final var processDefinitionKey = client.deployProcess(process);
    final var processInstanceKey =
        client.createProcessInstance(processDefinitionKey, "{\"a\":1, \"b\":2}");

    // when
    final var jobHandler = new RecordingJobHandler();
    final var startTime = System.currentTimeMillis();
    final var builder =
        client
            .getClient()
            .newWorker()
            .jobType(jobType)
            .handler(jobHandler)
            .name("test")
            .timeout(5000);
    try (final var ignored = builder.open()) {
      Awaitility.await("until all jobs are activated")
          .untilAsserted(() -> assertThat(jobHandler.getHandledJobs()).hasSize(1));
    }

    // then
    final var job = jobHandler.getHandledJobs().get(0);
    assertThat(job.getType()).isEqualTo(jobType);
    assertThat(job.getRetries()).isEqualTo(5);
    assertThat(job.getDeadline()).isCloseTo(startTime + 5000, Offset.offset(500L));
    assertThat(job.getWorker()).isEqualTo("test");
    assertThat(job.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(job.getBpmnProcessId()).isEqualTo("process");
    assertThat(job.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(job.getElementId()).isEqualTo("task");
    assertThat(job.getCustomHeaders()).isEqualTo(Map.of("x", "1", "y", "2"));
    assertThat(job.getVariablesAsMap()).isEqualTo(Map.of("a", 1, "b", 2));
  }

  @Test
  void shouldActivateJobsOfDifferentTypes() {
    // given
    final var jobX = client.createSingleJob(jobType + "x");
    final var jobY = client.createSingleJob(jobType + "y");
    final var jobHandlerX = new RecordingJobHandler();
    final var jobHandlerY = new RecordingJobHandler();
    final var builderX = client.getClient().newWorker().jobType(jobType + "x").handler(jobHandlerX);
    final var builderY = client.getClient().newWorker().jobType(jobType + "y").handler(jobHandlerY);

    // when
    try (final var ignoredX = builderX.open();
        final var ignoredY = builderY.open()) {
      Awaitility.await("until all jobs are activated")
          .untilAsserted(() -> assertThat(jobHandlerX.getHandledJobs()).hasSize(1));
      Awaitility.await("until all jobs are activated")
          .untilAsserted(() -> assertThat(jobHandlerY.getHandledJobs()).hasSize(1));
    }

    // then
    assertThat(jobHandlerX.getHandledJobs())
        .hasSize(1)
        .extracting(ActivatedJob::getKey)
        .contains(jobX);
    assertThat(jobHandlerY.getHandledJobs())
        .hasSize(1)
        .extracting(ActivatedJob::getKey)
        .contains(jobY);
  }

  @Test
  void shouldFetchOnlySpecifiedVariables() {
    // given
    client.createSingleJob(jobType, b -> {}, "{\"a\":1, \"b\":2, \"c\":3,\"d\":4}");

    // when
    final var fetchVariables = List.of("a", "b");
    final var jobHandler = new RecordingJobHandler();
    final var builder =
        client
            .getClient()
            .newWorker()
            .jobType(jobType)
            .handler(jobHandler)
            .fetchVariables(fetchVariables);
    try (final var ignored = builder.open()) {
      Awaitility.await("until all jobs are activated")
          .untilAsserted(() -> assertThat(jobHandler.getHandledJobs()).hasSize(1));
    }

    // then
    final ActivatedJob job = jobHandler.getHandledJobs().get(0);
    assertThat(job.getVariablesAsMap()).isEqualTo(Map.of("a", 1, "b", 2));
  }
}
