/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.api.response.DeleteResourceResponse;
import io.camunda.client.api.response.Process;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.BatchOperationCreatedResult;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.BatchOperationCreatedResult.BatchOperationTypeEnum;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for resource deletion via the Camunda client. Tests the
 * /v2/resources/{resourceKey}/deletion endpoint through various scenarios including deletion with
 * and without history, deletion with operation reference, and error handling.
 */
@MultiDbTest
public class DeleteResourceGrcpIT {

  @MultiDbTestApplication static final TestStandaloneBroker BROKER = new TestStandaloneBroker();

  private static CamundaClient camundaClient;
  private static long processDefinitionKey;

  @BeforeAll
  public static void beforeAll() {
    camundaClient = BROKER.newClientBuilder().preferRestOverGrpc(false).build();
  }

  @BeforeEach
  public void beforeEach() {

    final var processDefinition =
        Bpmn.createExecutableProcess("process").name("my process").startEvent().endEvent().done();

    final Process process =
        deployProcessAndWaitForIt(camundaClient, processDefinition, "process.bpmn");
    processDefinitionKey = process.getProcessDefinitionKey();
  }

  @Test
  void shouldDeleteDeployedResource() {
    // given - deploy a process
    // when - delete the resource
    final DeleteResourceResponse response =
        camundaClient.newDeleteResourceCommand(processDefinitionKey).send().join();

    // then - deletion should succeed without throwing an exception
    assertThat(response).isNotNull();
    assertThat(response.getResourceKey()).isEqualTo(String.valueOf(processDefinitionKey));
    assertThat((BatchOperationCreatedResult) response.getBatchOperationCreatedResult()).isNull();
  }

  @Test
  void shouldDeleteResourceWithoutHistory() {
    // given - deploy a process
    // when - delete the resource with deleteHistory=false (default)
    final DeleteResourceResponse response =
        camundaClient.newDeleteResourceCommand(processDefinitionKey, false).send().join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResourceKey()).isEqualTo(String.valueOf(processDefinitionKey));
    assertThat((BatchOperationCreatedResult) response.getBatchOperationCreatedResult()).isNull();
  }

  @Test
  void shouldDeleteResourceWithHistory() {
    // given - deploy a process
    // when - delete the resource with deleteHistory=true
    final DeleteResourceResponse response =
        camundaClient.newDeleteResourceCommand(processDefinitionKey, true).send().join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getResourceKey()).isEqualTo(String.valueOf(processDefinitionKey));
    final BatchOperationCreatedResult batchOperation =
        (BatchOperationCreatedResult) response.getBatchOperationCreatedResult();
    assertThat(batchOperation).isNotNull();
    assertThat(batchOperation.getBatchOperationKey()).isNotNull();
    assertThat(batchOperation.getBatchOperationType())
        .isEqualTo(BatchOperationTypeEnum.DELETE_PROCESS_INSTANCE);
  }

  @Test
  void shouldFailToDeleteNonExistentResource() {
    // given - a resource key that doesn't exist
    final long nonExistentResourceKey = 999999999L;

    // when/then - should throw a ProblemException
    assertThatThrownBy(
            () -> camundaClient.newDeleteResourceCommand(nonExistentResourceKey).send().join())
        .isInstanceOf(ClientStatusException.class);
  }
}
