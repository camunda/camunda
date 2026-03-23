/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.it.util.TestHelper.waitForProcessInstances;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class ProcessDefinitionStatisticsAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String TEST_USERNAME_READ_PROCESS_DEFINITION =
      "testUserReadProcessDefinition";
  private static final String TEST_USERNAME_READ_PROCESS_INSTANCE = "testUserReadProcessInstance";
  private static final String TEST_USERNAME_READ_SPECIFIC_PROCESS_INSTANCE =
      "testUserReadSpecificProcessInstance";
  private static final String SPECIFIC_PROCESS_ID = "process-auth-specific";

  @UserDefinition
  private static final TestUser TEST_USER_WITH_READ_PROCESS_DEFINITION =
      new TestUser(
          TEST_USERNAME_READ_PROCESS_DEFINITION,
          "password",
          List.of(
              new Permissions(
                  ResourceType.PROCESS_DEFINITION,
                  PermissionType.READ_PROCESS_DEFINITION,
                  List.of("*"))));

  @UserDefinition
  private static final TestUser TEST_USER_WITH_READ_PROCESS_INSTANCE =
      new TestUser(
          TEST_USERNAME_READ_PROCESS_INSTANCE,
          "password",
          List.of(
              new Permissions(
                  ResourceType.PROCESS_DEFINITION,
                  PermissionType.READ_PROCESS_INSTANCE,
                  List.of("*"))));

  @UserDefinition
  private static final TestUser TEST_USER_WITH_READ_SPECIFIC_INSTANCE =
      new TestUser(
          TEST_USERNAME_READ_SPECIFIC_PROCESS_INSTANCE,
          "password",
          List.of(
              new Permissions(
                  ResourceType.PROCESS_DEFINITION,
                  PermissionType.READ_PROCESS_INSTANCE,
                  List.of(SPECIFIC_PROCESS_ID))));

  private static CamundaClient camundaClient;

  @Test
  void shouldReturnEmptyStatisticsForReadProcessDefinitionPermission(
      @Authenticated(TEST_USERNAME_READ_PROCESS_DEFINITION) final CamundaClient userClient) {
    // given
    final long processDefinitionKey = createProcessInstances(deployCompleteBPMN(), 2);

    // when
    final var actual =
        userClient.newProcessDefinitionElementStatisticsRequest(processDefinitionKey).execute();

    // then
    assertThat(actual).isEmpty();
  }

  @Test
  void shouldAllowReadProcessInstancePermission(
      @Authenticated(TEST_USERNAME_READ_PROCESS_INSTANCE) final CamundaClient userClient) {
    // given
    final long processDefinitionKey = createProcessInstances(deployCompleteBPMN(), 2);

    // when
    final var actual =
        userClient.newProcessDefinitionElementStatisticsRequest(processDefinitionKey).execute();

    // then
    assertThat(actual).hasSize(2);
  }

  @Test
  void shouldAllowReadProcessInstancePermissionForSpecificProcess(
      @Authenticated(TEST_USERNAME_READ_SPECIFIC_PROCESS_INSTANCE) final CamundaClient userClient) {
    // given
    final var allowedProcessDefinitionKey = createProcessInstances(deployCompleteSpecificBPMN(), 1);
    final var notAllowedProcessDefinitionKey = createProcessInstances(deployCompleteBPMN(), 2);

    // when
    final var allowedActual =
        userClient
            .newProcessDefinitionElementStatisticsRequest(allowedProcessDefinitionKey)
            .send()
            .join();
    final var notAllowedActual =
        userClient
            .newProcessDefinitionElementStatisticsRequest(notAllowedProcessDefinitionKey)
            .send()
            .join();

    // then
    assertThat(allowedActual).hasSize(2);
    assertThat(notAllowedActual).isEmpty();
  }

  private long createProcessInstances(
      final long processDefinitionKey, final int numberOfInstances) {
    for (int i = 0; i < numberOfInstances; i++) {
      camundaClient
          .newCreateInstanceCommand()
          .processDefinitionKey(processDefinitionKey)
          .send()
          .join();
    }
    waitForProcessInstances(
        camundaClient,
        f -> f.processDefinitionKey(processDefinitionKey).state(ProcessInstanceState.COMPLETED),
        numberOfInstances);
    return processDefinitionKey;
  }

  private static long deployCompleteBPMN() {
    final var processId = "process-auth-" + UUID.randomUUID();
    final var processModel = Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();
    return deployResource(processModel, "complete-auth.bpmn")
        .getProcesses()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private static long deployCompleteSpecificBPMN() {
    final var processModel =
        Bpmn.createExecutableProcess(SPECIFIC_PROCESS_ID).startEvent().endEvent().done();
    return deployResource(processModel, SPECIFIC_PROCESS_ID + ".bpmn")
        .getProcesses()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private static io.camunda.client.api.response.DeploymentEvent deployResource(
      final BpmnModelInstance processModel, final String resourceName) {
    return camundaClient
        .newDeployResourceCommand()
        .addProcessModel(processModel, resourceName)
        .send()
        .join();
  }
}
