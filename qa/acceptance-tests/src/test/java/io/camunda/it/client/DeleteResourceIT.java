/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.waitForProcessInstances;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.CreateBatchOperationResponse;
import io.camunda.client.api.response.DeleteResourceResponse;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.search.enums.BatchOperationType;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.configuration.HistoryDeletion;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for resource deletion via the Camunda client. Tests the
 * /v2/resources/{resourceKey}/deletion endpoint through various scenarios including deletion with
 * and without history, deletion with operation reference, and error handling.
 */
@MultiDbTest
public class DeleteResourceIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withDataConfig(
              config -> {
                final var historyDeletionConfig = new HistoryDeletion();
                historyDeletionConfig.setDelayBetweenRuns(Duration.ofMillis(100));
                historyDeletionConfig.setMaxDelayBetweenRuns(Duration.ofMillis(100));
                config.setHistoryDeletion(historyDeletionConfig);
              });

  private static CamundaClient camundaClient;
  private static long processDefinitionKey;

  @BeforeEach
  public void beforeEach() {
    final var processDefinition =
        Bpmn.createExecutableProcess("process").name("my process").startEvent().endEvent().done();

    // deploy simple process instance
    final Process process =
        deployProcessAndWaitForIt(camundaClient, processDefinition, "process.bpmn");
    processDefinitionKey = process.getProcessDefinitionKey();

    // start a process instance
    camundaClient
        .newCreateInstanceCommand()
        .processDefinitionKey(processDefinitionKey)
        .send()
        .join();
    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionKey(processDefinitionKey).state(ProcessInstanceState.COMPLETED),
        1);
  }

  @Test
  void shouldDeleteDeployedResource() {
    // given - deploy a process and start a process instance
    // when - delete the resource with deleteHistory=false (default)
    final DeleteResourceResponse response =
        camundaClient.newDeleteResourceCommand(processDefinitionKey).send().join();

    // then - deletion should succeed without throwing an exception
    assertThat(response).isNotNull();
    assertThat(response.getResourceKey()).isEqualTo(String.valueOf(processDefinitionKey));
    assertThat(response.getCreateBatchOperationResponse()).isNull();
    waitForProcessInstances(camundaClient, f -> f.processDefinitionKey(processDefinitionKey), 1);
  }

  @Test
  void shouldDeleteResourceWithoutHistory() {
    // given - deploy a process and start a process instance
    // when - delete the resource with deleteHistory=false
    final DeleteResourceResponse response =
        camundaClient
            .newDeleteResourceCommand(processDefinitionKey)
            .deleteHistory(false)
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResourceKey()).isEqualTo(String.valueOf(processDefinitionKey));
    assertThat(response.getCreateBatchOperationResponse()).isNull();
    waitForProcessInstances(camundaClient, f -> f.processDefinitionKey(processDefinitionKey), 1);
  }

  @Test
  void shouldDeleteResourceWithHistory() {
    // given - deploy a process and start a process instance
    // when - delete the resource with deleteHistory=true
    final DeleteResourceResponse response =
        camundaClient
            .newDeleteResourceCommand(processDefinitionKey)
            .deleteHistory(true)
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResourceKey()).isEqualTo(String.valueOf(processDefinitionKey));
    final CreateBatchOperationResponse batchOperation = response.getCreateBatchOperationResponse();
    assertThat(batchOperation).isNotNull();
    assertThat(batchOperation.getBatchOperationKey()).isNotNull();
    assertThat(Long.valueOf(batchOperation.getBatchOperationKey())).isGreaterThan(0);
    assertThat(batchOperation.getBatchOperationType())
        .isEqualTo(BatchOperationType.DELETE_PROCESS_INSTANCE);

    waitForProcessInstances(camundaClient, f -> f.processDefinitionKey(processDefinitionKey), 0);
  }

  @Test
  void shouldFailToDeleteNonExistentResource() {
    // given - a resource key that doesn't exist
    final long nonExistentResourceKey = 999999999L;

    // when/then - should throw a ProblemException
    assertThatThrownBy(
            () -> camundaClient.newDeleteResourceCommand(nonExistentResourceKey).send().join())
        .isInstanceOf(ProblemException.class);
    waitForProcessInstances(camundaClient, f -> f.processDefinitionKey(processDefinitionKey), 1);
  }
}
