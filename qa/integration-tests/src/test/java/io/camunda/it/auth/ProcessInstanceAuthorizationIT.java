/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.protocol.rest.PermissionTypeEnum.CREATE;
import static io.camunda.client.protocol.rest.PermissionTypeEnum.CREATE_PROCESS_INSTANCE;
import static io.camunda.client.protocol.rest.PermissionTypeEnum.READ_PROCESS_DEFINITION;
import static io.camunda.client.protocol.rest.PermissionTypeEnum.READ_PROCESS_INSTANCE;
import static io.camunda.client.protocol.rest.ResourceTypeEnum.PROCESS_DEFINITION;
import static io.camunda.client.protocol.rest.ResourceTypeEnum.RESOURCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.it.utils.BrokerITInvocationProvider;
import io.camunda.it.utils.CamundaClientTestFactory.Authenticated;
import io.camunda.it.utils.CamundaClientTestFactory.Permissions;
import io.camunda.it.utils.CamundaClientTestFactory.User;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.function.Executable;

@TestInstance(Lifecycle.PER_CLASS)
class ProcessInstanceAuthorizationIT {
  private static final String PROCESS_ID_1 = "incident_process_v1";
  private static final String PROCESS_ID_2 = "service_tasks_v1";
  private static final String ADMIN = "admin";
  private static final String USER1 = "user1";
  private static final String USER2 = "user2";
  private static final User ADMIN_USER =
      new User(
          ADMIN,
          "password",
          List.of(
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*"))));
  private static final User USER1_USER =
      new User(
          USER1,
          "password",
          List.of(
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of(PROCESS_ID_1))));
  private static final User USER2_USER =
      new User(
          USER2,
          "password",
          List.of(
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of(PROCESS_ID_2))));

  @RegisterExtension
  static final BrokerITInvocationProvider PROVIDER =
      new BrokerITInvocationProvider()
          .withoutRdbmsExporter()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withUsers(ADMIN_USER, USER1_USER, USER2_USER);

  private boolean initialized;

  @BeforeEach
  void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    if (!initialized) {
      deployResource(adminClient, "process/incident_process_v1.bpmn");
      deployResource(adminClient, "process/service_tasks_v1.bpmn");

      startProcessInstance(adminClient, PROCESS_ID_1);
      startProcessInstance(adminClient, PROCESS_ID_2);

      waitForFlowNodeInstancesBeingExported(adminClient, 4);
      initialized = true;
    }
  }

  @TestTemplate
  public void searchShouldReturnAuthorizedProcessInstances(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newProcessInstanceQuery().send().join();
    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getProcessDefinitionId()).isEqualTo(PROCESS_ID_1);
  }

  @TestTemplate
  void getByKeyShouldReturnAuthorizedProcessInstance(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final var processInstanceKey = getProcessInstanceKey(adminClient, PROCESS_ID_1);
    // when
    final var result = camundaClient.newProcessInstanceGetRequest(processInstanceKey).send().join();
    // then
    assertThat(result).isNotNull();
    assertThat(result.getProcessDefinitionId()).isEqualTo(PROCESS_ID_1);
  }

  @TestTemplate
  void getByKeyShouldReturnForbiddenForUnauthorizedProcessInstance(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final var processInstanceKey = getProcessInstanceKey(adminClient, PROCESS_ID_2);
    // when
    final Executable executeGet =
        () -> camundaClient.newProcessInstanceGetRequest(processInstanceKey).send().join();
    // then
    final var problemException = assertThrows(ProblemException.class, executeGet);
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'");
  }

  @TestTemplate
  public void searchShouldReturnAuthorizedFlowNodeInstances(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final long processDefinitionKey = getProcessDefinitionKey(adminClient, PROCESS_ID_1);
    // when
    final var result = camundaClient.newFlownodeInstanceQuery().send().join();
    // then
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().getFirst().getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(result.items().getLast().getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
  }

  @TestTemplate
  void getByKeyShouldReturnAuthorizedFlowNodeInstance(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final var flowNodeInstanceKey = getAnyFlowNodeInstanceKey(adminClient, PROCESS_ID_1);
    // when
    final var result =
        camundaClient.newFlowNodeInstanceGetRequest(flowNodeInstanceKey).send().join();
    // then
    assertThat(result).isNotNull();
    assertThat(result.getProcessDefinitionKey())
        .isEqualTo(getProcessDefinitionKey(adminClient, PROCESS_ID_1));
  }

  @TestTemplate
  void getByKeyShouldReturnForbiddenForUnauthorizedFlowNodeInstance(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final var flowNodeInstanceKey = getAnyFlowNodeInstanceKey(adminClient, PROCESS_ID_2);
    // when
    final Executable executeGet =
        () -> camundaClient.newFlowNodeInstanceGetRequest(flowNodeInstanceKey).send().join();
    // then
    final var problemException = assertThrows(ProblemException.class, executeGet);
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'");
  }

  @TestTemplate
  public void searchShouldReturnAuthorizedIncidents(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when
    final var result = camundaClient.newIncidentQuery().send().join();
    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getProcessDefinitionId()).isEqualTo(PROCESS_ID_1);
  }

  @TestTemplate
  void getByKeyShouldReturnAuthorizedIncident(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final var incidentKey = getAnyIncidentKey(adminClient, PROCESS_ID_1);
    // when
    final var result = camundaClient.newIncidentGetRequest(incidentKey).send().join();
    // then
    assertThat(result).isNotNull();
    assertThat(result.getProcessDefinitionKey())
        .isEqualTo(getProcessDefinitionKey(adminClient, PROCESS_ID_1));
  }

  @TestTemplate
  void getByKeyShouldReturnForbiddenForUnauthorizedIncident(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER2) final CamundaClient camundaClient) {
    // given
    final var incidentKey = getAnyIncidentKey(adminClient, PROCESS_ID_1);
    // when
    final Executable executeGet =
        () -> camundaClient.newIncidentGetRequest(incidentKey).send().join();
    // then
    final var problemException = assertThrows(ProblemException.class, executeGet);
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'");
  }

  @TestTemplate
  public void searchShouldReturnAuthorizedVariables(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final var processInstanceKey = getProcessInstanceKey(adminClient, PROCESS_ID_1);
    // when
    final var result = camundaClient.newVariableQuery().send().join();
    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getProcessInstanceKey()).isEqualTo(processInstanceKey);
  }

  @TestTemplate
  void getByKeyShouldReturnAuthorizedVariable(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final var processInstanceKey = getProcessInstanceKey(adminClient, PROCESS_ID_1);
    final var variableKey = getAnyVariableKey(adminClient, processInstanceKey);
    // when
    final var result = camundaClient.newVariableGetRequest(variableKey).send().join();
    // then
    assertThat(result).isNotNull();
    assertThat(result.getProcessInstanceKey()).isEqualTo(processInstanceKey);
  }

  @TestTemplate
  void getByKeyShouldReturnForbiddenForUnauthorizedVariable(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final var processInstanceKey = getProcessInstanceKey(adminClient, PROCESS_ID_2);
    final var variableKey = getAnyVariableKey(adminClient, processInstanceKey);
    // when
    final Executable executeGet =
        () -> camundaClient.newVariableGetRequest(variableKey).send().join();
    // then
    final var problemException = assertThrows(ProblemException.class, executeGet);
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'");
  }

  private long getProcessInstanceKey(final CamundaClient camundaClient, final String processId) {
    return camundaClient
        .newProcessInstanceQuery()
        .filter(f -> f.processDefinitionId(processId))
        .send()
        .join()
        .items()
        .getFirst()
        .getProcessInstanceKey();
  }

  private long getAnyFlowNodeInstanceKey(
      final CamundaClient camundaClient, final String processId) {
    return camundaClient
        .newFlownodeInstanceQuery()
        .filter(f -> f.processDefinitionId(processId))
        .send()
        .join()
        .items()
        .getFirst()
        .getFlowNodeInstanceKey();
  }

  private long getAnyIncidentKey(final CamundaClient camundaClient, final String processId) {
    return camundaClient
        .newIncidentQuery()
        .filter(f -> f.processDefinitionId(processId))
        .send()
        .join()
        .items()
        .getFirst()
        .getIncidentKey();
  }

  private long getAnyVariableKey(final CamundaClient camundaClient, final long processInstanceKey) {
    return camundaClient
        .newVariableQuery()
        .filter(f -> f.processInstanceKey(processInstanceKey))
        .send()
        .join()
        .items()
        .getFirst()
        .getVariableKey();
  }

  private long getProcessDefinitionKey(final CamundaClient adminClient, final String processId) {
    return adminClient
        .newProcessDefinitionQuery()
        .filter(f -> f.processDefinitionId(processId))
        .send()
        .join()
        .items()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private void deployResource(final CamundaClient camundaClient, final String resourceName) {
    camundaClient.newDeployResourceCommand().addResourceFromClasspath(resourceName).send().join();
  }

  private void startProcessInstance(final CamundaClient camundaClient, final String processId) {
    camundaClient.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
  }

  private void waitForFlowNodeInstancesBeingExported(
      final CamundaClient camundaClient, final int expectedCount) {
    Awaitility.await("should receive data from ES")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () ->
                assertThat(camundaClient.newFlownodeInstanceQuery().send().join().items())
                    .hasSize(expectedCount));
  }
}
