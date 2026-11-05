/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.network;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

@ZeebeIntegration
final class SmallMessageSizeTest {
  private static final int VARIABLE_COUNT = 4;
  private static final DataSize MAX_MESSAGE_SIZE = DataSize.ofKilobytes(12);
  private static final String LARGE_TEXT =
      "x".repeat((int) (MAX_MESSAGE_SIZE.toBytes() / VARIABLE_COUNT));

  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withUnifiedConfig(
              c -> {
                c.getCluster().getNetwork().setMaxMessageSize(MAX_MESSAGE_SIZE);
                c.getApi().getLongPolling().setEnabled(false);
              });

  private CamundaClient client;
  private long processInstanceKey;
  private String jobType;

  @BeforeEach
  void givenProcessWithLargeVariables() {
    client = ZEEBE.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(10)).build();
    final var resourcesHelper = new ZeebeResourcesHelper(client);

    // given a process with a job
    jobType = UUID.randomUUID().toString();
    final var processDefinitionKey =
        resourcesHelper.deployProcess(
            Bpmn.createExecutableProcess("PROCESS_" + jobType)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done());

    processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);
    Awaitility.await("job CREATED")
        .until(
            () ->
                RecordingExporter.jobRecords(JobIntent.CREATED)
                    .withProcessInstanceKey(processInstanceKey)
                    .withType(jobType)
                    .exists());

    // with variables that are greater than the message size
    for (int i = 0; i < VARIABLE_COUNT; i++) {
      client
          .newSetVariablesCommand(processInstanceKey)
          .variables(Map.of(String.format("large-%d", i), LARGE_TEXT))
          .send()
          .join();
    }
    Awaitility.await("large variables CREATED")
        .until(
            () ->
                RecordingExporter.variableRecords(VariableIntent.CREATED)
                    .withProcessInstanceKey(processInstanceKey)
                    .filter(v -> v.getValue().getName().startsWith("large"))
                    .limit(4)
                    .count(),
            n -> n == 4);
  }

  @AfterEach
  void closeClient() {
    client.close();
  }

  @Test
  void shouldSkipJobsThatExceedMessageSize() {
    // when (we activate jobs)
    final var response =
        client
            .newActivateJobsCommand()
            .jobType(jobType)
            .maxJobsToActivate(1)
            .send()
            .join(10, TimeUnit.SECONDS);

    // then the job of the process which is too big for the message is ignored
    assertThat(response.getJobs()).isEmpty();
  }

  @Test
  void shouldActivateJobIfRequestVariablesFitIntoMessageSize() {
    // when (we activate job, but select only a subset of the variables - a subset that fits into
    // the message size)
    final var response =
        client
            .newActivateJobsCommand()
            .jobType(jobType)
            .maxJobsToActivate(1)
            .fetchVariables("0")
            .send()
            .join(10, TimeUnit.SECONDS);

    // then (the job is activated)
    assertThat(response.getJobs()).hasSize(1);
    assertThat(response.getJobs().get(0).getProcessInstanceKey()).isEqualTo(processInstanceKey);
  }
}
