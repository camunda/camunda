/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.*;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.ConsistencyPolicy;
import io.camunda.client.api.command.ProblemException;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class ProcessInstanceAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String PROCESS_ID_1 = "incident_process_v1";
  private static final String PROCESS_ID_2 = "service_tasks_v1";
  private static final String ADMIN = "admin";
  private static final String USER1 = "user1";
  private static final String USER2 = "user2";

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          "password",
          List.of(
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*"))));

  @UserDefinition
  private static final TestUser USER1_USER =
      new TestUser(
          USER1,
          "password",
          List.of(
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of(PROCESS_ID_1))));

  @UserDefinition
  private static final TestUser USER2_USER =
      new TestUser(
          USER2,
          "password",
          List.of(
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of(PROCESS_ID_2))));

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient adminClient) {
    deployResource(adminClient, "process/incident_process_v1.bpmn");
    deployResource(adminClient, "process/service_tasks_v1.bpmn");

    startProcessInstance(adminClient, PROCESS_ID_1);
    startProcessInstance(adminClient, PROCESS_ID_2);
    waitForProcessBeingExported(adminClient);
  }

  @Test
  public void searchShouldReturnAuthorizedProcessInstances(
      @Authenticated(USER1) final CamundaClient camundaClient) {

    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getProcessDefinitionId()).isEqualTo(PROCESS_ID_1);
  }

  @Test
  public void searchShouldNotReturnNotauthorizedProcessInstances(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.processDefinitionId(PROCESS_ID_2))
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();
    // then
    assertThat(result.items()).hasSize(0);
  }

  @Test
  void getByKeyShouldReturnAuthorizedProcessInstance(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final var processInstanceKey = getProcessInstanceKey(adminClient, PROCESS_ID_1);
    // when
    final var result =
        camundaClient
            .newProcessInstanceGetRequest(processInstanceKey)
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();
    // then
    assertThat(result).isNotNull();
    assertThat(result.getProcessDefinitionId()).isEqualTo(PROCESS_ID_1);
  }

  @Test
  void getByKeyShouldReturnForbiddenForUnauthorizedProcessInstance(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final var processInstanceKey = getProcessInstanceKey(adminClient, PROCESS_ID_2);
    // when
    final ThrowingCallable executeGet =
        () ->
            camundaClient
                .newProcessInstanceGetRequest(processInstanceKey)
                .consistencyPolicy(ConsistencyPolicy.noWait())
                .send()
                .join();
    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeGet).actual();
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'");
  }

  @Test
  public void searchShouldReturnAuthorizedElementInstances(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final long processDefinitionKey = getProcessDefinitionKey(adminClient, PROCESS_ID_1);
    // when
    final var result =
        camundaClient
            .newElementInstanceSearchRequest()
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();
    // then
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().getFirst().getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(result.items().getLast().getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
  }

  @Test
  void getByKeyShouldReturnAuthorizedElementInstance(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final var elementInstanceKey = getAnyElementInstanceKey(adminClient, PROCESS_ID_1);
    // when
    final var result =
        camundaClient
            .newElementInstanceGetRequest(elementInstanceKey)
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();
    // then
    assertThat(result).isNotNull();
    assertThat(result.getProcessDefinitionKey())
        .isEqualTo(getProcessDefinitionKey(adminClient, PROCESS_ID_1));
  }

  @Test
  void getByKeyShouldReturnForbiddenForUnauthorizedElementInstance(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final var elementInstanceKey = getAnyElementInstanceKey(adminClient, PROCESS_ID_2);
    // when
    final ThrowingCallable executeGet =
        () ->
            camundaClient
                .newElementInstanceGetRequest(elementInstanceKey)
                .consistencyPolicy(ConsistencyPolicy.noWait())
                .send()
                .join();
    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeGet).actual();
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'");
  }

  @Test
  public void searchShouldReturnAuthorizedIncidents(
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // when
    final var result =
        camundaClient
            .newIncidentSearchRequest()
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();
    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getProcessDefinitionId()).isEqualTo(PROCESS_ID_1);
  }

  @Test
  void getByKeyShouldReturnAuthorizedIncident(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final var incidentKey = getAnyIncidentKey(adminClient, PROCESS_ID_1);
    // when
    final var result =
        camundaClient
            .newIncidentGetRequest(incidentKey)
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();
    // then
    assertThat(result).isNotNull();
    assertThat(result.getProcessDefinitionKey())
        .isEqualTo(getProcessDefinitionKey(adminClient, PROCESS_ID_1));
  }

  @Test
  void getByKeyShouldReturnForbiddenForUnauthorizedIncident(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER2) final CamundaClient camundaClient) {
    // given
    final var incidentKey = getAnyIncidentKey(adminClient, PROCESS_ID_1);
    // when
    final ThrowingCallable executeGet =
        () ->
            camundaClient
                .newIncidentGetRequest(incidentKey)
                .consistencyPolicy(ConsistencyPolicy.noWait())
                .send()
                .join();
    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeGet).actual();
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'");
  }

  @Test
  public void searchShouldReturnAuthorizedVariables(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final var processInstanceKey = getProcessInstanceKey(adminClient, PROCESS_ID_1);
    // when
    final var result =
        camundaClient
            .newVariableSearchRequest()
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();
    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getProcessInstanceKey()).isEqualTo(processInstanceKey);
  }

  @Test
  void getByKeyShouldReturnAuthorizedVariable(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final var processInstanceKey = getProcessInstanceKey(adminClient, PROCESS_ID_1);
    final var variableKey = getAnyVariableKey(adminClient, processInstanceKey);
    // when
    final var result =
        camundaClient
            .newVariableGetRequest(variableKey)
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();
    // then
    assertThat(result).isNotNull();
    assertThat(result.getProcessInstanceKey()).isEqualTo(processInstanceKey);
  }

  @Test
  void getByKeyShouldReturnForbiddenForUnauthorizedVariable(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(USER1) final CamundaClient camundaClient) {
    // given
    final var processInstanceKey = getProcessInstanceKey(adminClient, PROCESS_ID_2);
    final var variableKey = getAnyVariableKey(adminClient, processInstanceKey);
    // when
    final ThrowingCallable executeGet =
        () ->
            camundaClient
                .newVariableGetRequest(variableKey)
                .consistencyPolicy(ConsistencyPolicy.noWait())
                .send()
                .join();
    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeGet).actual();
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'");
  }

  private long getProcessInstanceKey(final CamundaClient camundaClient, final String processId) {
    return camundaClient
        .newProcessInstanceSearchRequest()
        .filter(f -> f.processDefinitionId(processId))
        .consistencyPolicy(ConsistencyPolicy.noWait())
        .send()
        .join()
        .items()
        .getFirst()
        .getProcessInstanceKey();
  }

  private long getAnyElementInstanceKey(final CamundaClient camundaClient, final String processId) {
    return camundaClient
        .newElementInstanceSearchRequest()
        .filter(f -> f.processDefinitionId(processId))
        .consistencyPolicy(ConsistencyPolicy.noWait())
        .send()
        .join()
        .items()
        .getFirst()
        .getElementInstanceKey();
  }

  private long getAnyIncidentKey(final CamundaClient camundaClient, final String processId) {
    return camundaClient
        .newIncidentSearchRequest()
        .filter(f -> f.processDefinitionId(processId))
        .consistencyPolicy(ConsistencyPolicy.noWait())
        .send()
        .join()
        .items()
        .getFirst()
        .getIncidentKey();
  }

  private long getAnyVariableKey(final CamundaClient camundaClient, final long processInstanceKey) {
    return camundaClient
        .newVariableSearchRequest()
        .filter(f -> f.processInstanceKey(processInstanceKey))
        .consistencyPolicy(ConsistencyPolicy.noWait())
        .send()
        .join()
        .items()
        .getFirst()
        .getVariableKey();
  }

  private long getProcessDefinitionKey(final CamundaClient adminClient, final String processId) {
    return adminClient
        .newProcessDefinitionSearchRequest()
        .filter(f -> f.processDefinitionId(processId))
        .consistencyPolicy(ConsistencyPolicy.noWait())
        .send()
        .join()
        .items()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private static void deployResource(final CamundaClient camundaClient, final String resourceName) {
    camundaClient.newDeployResourceCommand().addResourceFromClasspath(resourceName).send().join();
  }

  private static void startProcessInstance(
      final CamundaClient camundaClient, final String processId) {
    camundaClient.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
  }

  private static void waitForProcessBeingExported(final CamundaClient camundaClient) {
    Awaitility.await("should receive data from ES")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              assertThat(
                      camundaClient
                          .newProcessInstanceSearchRequest()
                          .filter(
                              filter ->
                                  filter.processDefinitionId(
                                      fn -> fn.in(PROCESS_ID_1, PROCESS_ID_2)))
                          .consistencyPolicy(ConsistencyPolicy.noWait())
                          .send()
                          .join()
                          .items())
                  .hasSize(2); // PROCESS_ID_1 and PROCESS_ID_2
              assertThat(
                      camundaClient
                          .newVariableSearchRequest()
                          .consistencyPolicy(ConsistencyPolicy.noWait())
                          .send()
                          .join()
                          .items())
                  .hasSize(2); // One per process instance
              assertThat(
                      camundaClient
                          .newElementInstanceSearchRequest()
                          .consistencyPolicy(ConsistencyPolicy.noWait())
                          .send()
                          .join()
                          .items())
                  .hasSize(4); // Start event and service task for each process
            });
  }
}
